import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Collapse,
  Drawer,
  Grid,
  Input,
  Modal,
  Row,
  Space,
  Switch,
  Tag,
  Tree,
  Typography,
  message,
} from 'antd';
import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Editor, { DiffEditor } from '@monaco-editor/react';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';
import { useThemeMode } from '../theme/ThemeContext.jsx';
import { configureMonacoThemes, getMonacoThemeName } from '../utils/monacoTheme.js';

const { Title, Text } = Typography;

function encodeBase64(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function pathSegments(path) {
  return String(path || '')
    .replaceAll('\\', '/')
    .split('/')
    .filter(Boolean);
}

function normalizePath(path) {
  return String(path || '').replaceAll('\\', '/');
}

function buildTree(paths) {
  const root = new Map();
  paths.forEach((path) => {
    const parts = pathSegments(path);
    let level = root;
    parts.forEach((part, idx) => {
      const isLeaf = idx === parts.length - 1;
      const rawPath = String(path || '');
      const dirKey = `dir:${parts.slice(0, idx + 1).join('/')}`;
      const nodeKey = isLeaf ? rawPath : dirKey;
      if (!level.has(part)) {
        level.set(part, { key: nodeKey, title: part, children: new Map(), leaf: false, path: null });
      }
      const node = level.get(part);
      if (isLeaf) {
        node.leaf = true;
        node.key = rawPath;
        node.path = rawPath;
      }
      level = node.children;
    });
  });
  const toTreeData = (map) => Array.from(map.values())
    .sort((a, b) => Number(a.leaf) - Number(b.leaf) || a.title.localeCompare(b.title))
    .map((item) => ({
      key: item.key,
      title: item.title,
      path: item.path,
      isLeaf: item.leaf && item.children.size === 0,
      selectable: item.leaf && item.children.size === 0,
      children: toTreeData(item.children),
    }));
  return toTreeData(root);
}

function matchesEditablePath(gitPath, editablePath) {
  if (!gitPath || !editablePath) {
    return false;
  }
  const g = String(gitPath).replaceAll('\\', '/');
  const e = String(editablePath).replaceAll('\\', '/');
  return g === e || g.endsWith(`/${e}`);
}

function detectLanguage(path) {
  const normalized = String(path || '').toLowerCase();
  if (normalized.endsWith('.md') || normalized.endsWith('.markdown')) return 'markdown';
  if (normalized.endsWith('.yml') || normalized.endsWith('.yaml')) return 'yaml';
  if (normalized.endsWith('.json')) return 'json';
  if (normalized.endsWith('.js') || normalized.endsWith('.jsx')) return 'javascript';
  if (normalized.endsWith('.ts') || normalized.endsWith('.tsx')) return 'typescript';
  if (normalized.endsWith('.java')) return 'java';
  if (normalized.endsWith('.kt') || normalized.endsWith('.kts')) return 'kotlin';
  if (normalized.endsWith('.py')) return 'python';
  if (normalized.endsWith('.sh')) return 'shell';
  if (normalized.endsWith('.xml')) return 'xml';
  if (normalized.endsWith('.html') || normalized.endsWith('.htm')) return 'html';
  if (normalized.endsWith('.css') || normalized.endsWith('.scss')) return 'css';
  if (normalized.endsWith('.sql')) return 'sql';
  return 'plaintext';
}

function formatRange(range) {
  if (!range) {
    return '';
  }
  return `L${range.startLineNumber}:C${range.startColumn} - L${range.endLineNumber}:C${range.endColumn}`;
}

function buildReworkInstruction(requests) {
  return requests.map((request, index) => {
    const lines = [
      `${index + 1}.`,
      `Path: ${request.path}`,
      `Scope: ${request.scope}`,
      `Instruction: ${request.instruction}`,
    ];
    if (request.range) {
      lines.push(`Range: ${formatRange(request.range)}`);
    }
    if (request.selectedText) {
      lines.push('Selected fragment:');
      lines.push('```');
      lines.push(request.selectedText);
      lines.push('```');
    }
    return lines.join('\n');
  }).join('\n\n');
}

function composeReworkInstruction(instructionFromForm, requests) {
  const parts = [];
  const manual = (instructionFromForm || '').trim();
  const fromRequests = buildReworkInstruction(requests || []);
  if (manual) {
    parts.push(manual);
  }
  if (fromRequests.trim()) {
    parts.push(fromRequests);
  }
  return parts.join('\n\n');
}

function resolveDiscardUnavailableReason(reasonCode) {
  switch (reasonCode) {
    case 'rework_target_missing':
      return 'Rework target node is missing.';
    case 'rework_target_kind_unsupported':
      return 'Rework target must be an AI/Command node.';
    case 'rework_target_checkpoint_disabled':
      return 'Rollback before rework is unavailable because checkpoint creation is disabled on the target node.';
    case 'target_checkpoint_not_found':
      return 'Checkpoint is not available yet for the rework target node.';
    default:
      return 'Discard to checkpoint is currently unavailable.';
  }
}

function resolveReworkDiscardBlockedReason(reasonCode, blockedByRequests) {
  if (blockedByRequests) {
    return 'Rollback before rework is unavailable while Rework requests are present. Keep changes so the agent can apply requested edits.';
  }
  return resolveDiscardUnavailableReason(reasonCode);
}

const EMPTY_REQUEST_DRAFT = {
  scope: 'file',
  path: '',
  selectedText: '',
  range: null,
  instruction: '',
};

export default function HumanGate() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');
  const gateId = searchParams.get('gateId');
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);

  const [run, setRun] = useState(null);
  const [gate, setGate] = useState(null);
  const [changes, setChanges] = useState([]);
  const [summary, setSummary] = useState({ files_changed: 0, added_lines: 0, removed_lines: 0, status_label: '—' });
  const [selectedPath, setSelectedPath] = useState('');
  const [originalContent, setOriginalContent] = useState('');
  const [modifiedContent, setModifiedContent] = useState('');
  const [loadingDiff, setLoadingDiff] = useState(false);
  const [loading, setLoading] = useState(false);

  const [approveComment, setApproveComment] = useState('');
  const [reworkComment, setReworkComment] = useState('');
  const [reworkInstruction, setReworkInstruction] = useState('');
  const [submitting, setSubmitting] = useState(null);
  const [editedByPath, setEditedByPath] = useState({});

  const [activeActionPanel, setActiveActionPanel] = useState(null); // approve | rework | null
  const [reworkRequests, setReworkRequests] = useState([]);
  const [addRequestModalOpen, setAddRequestModalOpen] = useState(false);
  const [requestDraft, setRequestDraft] = useState(EMPTY_REQUEST_DRAFT);
  const [contextMenuState, setContextMenuState] = useState({ open: false, x: 0, y: 0 });
  const [keepChanges, setKeepChanges] = useState(true);
  const screens = Grid.useBreakpoint();

  const diffEditorRef = useRef(null);
  const modifiedEditorRef = useRef(null);
  const reworkDecorationIdsRef = useRef([]);

  const isInput = gate?.gate_kind === 'human_input';
  const isApproval = gate?.gate_kind === 'human_approval';
  const keepChangesSelectable = gate?.payload?.rework_keep_changes_selectable === true;
  const reworkMode = keepChanges ? 'keep' : 'discard';
  const reworkDiscardAvailable = gate?.payload?.rework_discard_available === true;
  const reworkDiscardUnavailableReason = gate?.payload?.rework_discard_unavailable_reason || '';
  const isDiscardPolicy = reworkMode === 'discard';
  const hasAnyReworkRequests = reworkRequests.length > 0;
  const discardBlockedByRequests = reworkDiscardAvailable && hasAnyReworkRequests;
  const effectiveDiscardAvailable = reworkDiscardAvailable && !discardBlockedByRequests;
  const reworkDiscardBlockedReason = resolveReworkDiscardBlockedReason(reworkDiscardUnavailableReason, discardBlockedByRequests);
  const reworkDiscardBlocked = isDiscardPolicy && !effectiveDiscardAvailable;
  const hasManualReworkInstruction = !!(reworkInstruction || '').trim();
  const canSubmitRework = hasAnyReworkRequests || hasManualReworkInstruction;
  const userInstructions = gate?.payload?.user_instructions || '';
  const editableArtifacts = Array.isArray(gate?.payload?.human_input_artifacts) ? gate.payload.human_input_artifacts : [];

  const viewFiles = useMemo(() => (changes || []).map((item) => item.path).filter(Boolean), [changes]);
  const treeData = useMemo(() => buildTree(viewFiles), [viewFiles]);

  const selectedEditable = useMemo(() => {
    if (!isInput || !selectedPath) {
      return null;
    }
    const selectedNormalized = normalizePath(selectedPath);
    return editableArtifacts.find((artifact) => {
      const workspacePath = artifact?.workspace_path;
      if (!workspacePath) {
        return false;
      }
      return normalizePath(workspacePath) === selectedNormalized;
    }) || null;
  }, [editableArtifacts, isInput, selectedPath]);
  const selectedGitChange = useMemo(() => {
    const selectedNormalized = normalizePath(selectedPath);
    return changes.find((item) => normalizePath(item.path) === selectedNormalized) || null;
  }, [changes, selectedPath]);
  const selectedIsEditable = !!selectedEditable;
  const selectedLanguage = useMemo(() => detectLanguage(selectedPath), [selectedPath]);
  const diffLayoutMode = screens?.lg ? 'desktop' : 'mobile';
  const diffEditorKey = `${gateId || 'gate'}:${selectedGitChange?.path || selectedPath || 'empty'}:${selectedLanguage}:${diffLayoutMode}`;
  const currentEditableContent = selectedEditable
    ? (editedByPath[selectedEditable.path] ?? selectedEditable.content ?? '')
    : '';

  const refresh = async () => {
    if (!runId || !gateId) {
      return;
    }
    setLoading(true);
    try {
      const runData = await apiRequest(`/runs/${runId}`);
      const currentGate = runData?.current_gate;
      if (!currentGate || currentGate.gate_id !== gateId) {
        message.warning('Gate is no longer active');
        navigate(`/run-console?runId=${runId}`);
        return;
      }
      const changesData = await apiRequest(`/gates/${gateId}/changes`);
      setRun(runData);
      setGate(currentGate);
      setChanges(changesData.git_changes || []);
      setSummary(changesData.git_summary || { files_changed: 0, added_lines: 0, removed_lines: 0, status_label: '—' });
      if (!selectedPath) {
        const first = (changesData.git_changes || [])[0]?.path || '';
        setSelectedPath(first);
      }
    } catch (err) {
      message.error(err.message || 'Failed to load gate');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [runId, gateId]);

  useEffect(() => {
    const loadDiff = async () => {
      if (!selectedPath || !gateId) {
        setOriginalContent('');
        setModifiedContent('');
        return;
      }
      if (!selectedGitChange) {
        setOriginalContent('');
        setModifiedContent('');
        return;
      }
      setLoadingDiff(true);
      try {
        const pathForDiff = selectedGitChange?.path || selectedPath;
        const data = await apiRequest(`/gates/${gateId}/diff?path=${encodeURIComponent(pathForDiff)}`);
        setOriginalContent(data.original_content || '');
        setModifiedContent(data.modified_content || '');
      } catch (err) {
        message.error(err.message || 'Failed to load diff');
        setOriginalContent('');
        setModifiedContent('');
      } finally {
        setLoadingDiff(false);
      }
    };
    loadDiff();
  }, [selectedPath, selectedGitChange, gateId]);

  useEffect(() => {
    if (!contextMenuState.open) {
      return undefined;
    }
    const close = () => setContextMenuState({ open: false, x: 0, y: 0 });
    window.addEventListener('click', close);
    window.addEventListener('scroll', close, true);
    return () => {
      window.removeEventListener('click', close);
      window.removeEventListener('scroll', close, true);
    };
  }, [contextMenuState.open]);

  useEffect(() => {
    setKeepChanges(gate?.payload?.rework_keep_changes !== false);
  }, [gate?.gate_id, gate?.resource_version, gate?.payload?.rework_keep_changes]);

  useEffect(() => {
    if (discardBlockedByRequests && !keepChanges) {
      setKeepChanges(true);
      message.info('Discard to checkpoint was disabled because Rework requests are present.');
    }
  }, [discardBlockedByRequests, keepChanges]);

  const handleKeepChangesToggle = (checked) => {
    if (!checked && !effectiveDiscardAvailable) {
      message.warning(reworkDiscardBlockedReason);
      return;
    }
    setKeepChanges(checked);
  };

  const applyReworkDecorations = useCallback(() => {
    try {
      const editor = modifiedEditorRef.current;
      if (!editor) {
        return;
      }
      const model = editor.getModel();
      if (!model || (typeof model.isDisposed === 'function' && model.isDisposed())) {
        return;
      }
      if (!isApproval || !selectedPath) {
        reworkDecorationIdsRef.current = editor.deltaDecorations(reworkDecorationIdsRef.current, []);
        return;
      }
      const fileRequests = reworkRequests.filter((request) => {
        const path = request?.path || '';
        return matchesEditablePath(path, selectedPath) || matchesEditablePath(selectedPath, path);
      });
      const decorations = fileRequests.map((request, index) => {
        const startLine = Number.isInteger(request?.range?.startLineNumber) && request.range.startLineNumber > 0
          ? request.range.startLineNumber
          : 1;
        return {
          range: {
            startLineNumber: startLine,
            startColumn: 1,
            endLineNumber: startLine,
            endColumn: 1,
          },
          options: {
            isWholeLine: true,
            linesDecorationsClassName: 'human-gate-rework-marker-line',
            glyphMarginClassName: 'human-gate-rework-marker-glyph',
            glyphMarginHoverMessage: { value: `Rework request #${index + 1}\n\n${request.instruction || ''}` },
          },
        };
      });
      reworkDecorationIdsRef.current = editor.deltaDecorations(reworkDecorationIdsRef.current, decorations);
    } catch (_) {
      // ignore Monaco transient dispose races during layout recalculation
    }
  }, [isApproval, reworkRequests, selectedPath]);

  useEffect(() => {
    applyReworkDecorations();
  }, [applyReworkDecorations, modifiedContent, originalContent]);

  useEffect(() => () => {
    const editor = modifiedEditorRef.current;
    if (!editor) {
      return;
    }
    const model = editor.getModel();
    if (!model || (typeof model.isDisposed === 'function' && model.isDisposed())) {
      return;
    }
    reworkDecorationIdsRef.current = editor.deltaDecorations(reworkDecorationIdsRef.current, []);
  }, []);

  const goBack = () => navigate(`/run-console?runId=${runId}`);

  const openAddRequestModalFromSelection = () => {
    if (!selectedPath) {
      message.warning('Select a file first');
      return;
    }
    const editor = modifiedEditorRef.current;
    if (!editor) {
      setRequestDraft({ ...EMPTY_REQUEST_DRAFT, scope: 'file', path: selectedPath });
      setAddRequestModalOpen(true);
      return;
    }

    const selection = editor.getSelection();
    const model = editor.getModel();
    const hasSelection = selection && !selection.isEmpty();
    if (!hasSelection || !model) {
      setRequestDraft({ ...EMPTY_REQUEST_DRAFT, scope: 'file', path: selectedPath });
      setAddRequestModalOpen(true);
      return;
    }

    const selectedText = model.getValueInRange(selection) || '';
    setRequestDraft({
      scope: 'selection',
      path: selectedPath,
      selectedText,
      range: {
        startLineNumber: selection.startLineNumber,
        startColumn: selection.startColumn,
        endLineNumber: selection.endLineNumber,
        endColumn: selection.endColumn,
      },
      instruction: '',
    });
    setAddRequestModalOpen(true);
  };

  const addReworkRequest = () => {
    const instruction = (requestDraft.instruction || '').trim();
    if (!instruction) {
      message.warning('Instruction is required');
      return;
    }
    const request = {
      id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      path: requestDraft.path || selectedPath,
      scope: requestDraft.scope || 'file',
      instruction,
      selectedText: requestDraft.selectedText || '',
      range: requestDraft.range || null,
      createdAt: Date.now(),
    };
    setReworkRequests((prev) => [...prev, request]);
    setAddRequestModalOpen(false);
    setRequestDraft(EMPTY_REQUEST_DRAFT);
    message.success('Rework request added');
  };

  const removeReworkRequest = (id) => {
    setReworkRequests((prev) => prev.filter((item) => item.id !== id));
  };

  const approve = async () => {
    if (!gate) return false;
    setSubmitting('approve');
    try {
      await apiRequest(`/gates/${gate.gate_id}/approve`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          comment: approveComment,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Gate approved');
      goBack();
      return true;
    } catch (err) {
      message.error(err.message || 'Failed to approve gate');
      await refresh();
      return false;
    } finally {
      setSubmitting(null);
    }
  };

  const requestRework = async () => {
    if (!gate) return false;
    if (reworkDiscardBlocked) {
      message.warning(reworkDiscardBlockedReason);
      return false;
    }
    if (!canSubmitRework) {
      message.warning('Rework instruction is required');
      return false;
    }
    const mergedInstruction = composeReworkInstruction(reworkInstruction, reworkRequests);
    setSubmitting('rework');
    try {
      await apiRequest(`/gates/${gate.gate_id}/request-rework`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          comment: reworkComment,
          instruction: mergedInstruction,
          keep_changes: keepChanges,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Rework requested');
      goBack();
      return true;
    } catch (err) {
      message.error(err.message || 'Failed to request rework');
      await refresh();
      return false;
    } finally {
      setSubmitting(null);
    }
  };

  const submitInput = async () => {
    if (!gate) return;
    const changedArtifacts = editableArtifacts
      .map((artifact) => ({
        artifact,
        content: editedByPath[artifact.path],
      }))
      .filter(({ content, artifact }) => content !== undefined && content !== (artifact.content || ''));
    if (changedArtifacts.length === 0) {
      message.warning('No editable artifacts changed');
      return;
    }
    setSubmitting('submit');
    try {
      await apiRequest(`/gates/${gate.gate_id}/submit-input`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          artifacts: changedArtifacts.map(({ artifact, content }) => ({
            artifact_key: artifact.artifact_key,
            path: artifact.path,
            scope: artifact.scope || 'run',
            content_base64: encodeBase64(content),
          })),
          comment: 'submitted from unified human gate',
        }),
      });
      message.success('Input submitted');
      goBack();
    } catch (err) {
      message.error(err.message || 'Failed to submit input');
      await refresh();
    } finally {
      setSubmitting(null);
    }
  };

  const reworkItems = reworkRequests.map((request, idx) => ({
    key: request.id,
    label: `#${idx + 1} • ${request.path} • ${request.scope}`,
    extra: (
      <Button
        type="text"
        danger
        size="small"
        icon={<DeleteOutlined />}
        onClick={(event) => {
          event.stopPropagation();
          removeReworkRequest(request.id);
        }}
      />
    ),
    children: (
      <Space direction="vertical" size={8} style={{ width: '100%' }}>
        {request.range && <Text type="secondary">Range: {formatRange(request.range)}</Text>}
        <Text><Text strong>Instruction:</Text> {request.instruction}</Text>
        {request.selectedText && (
          <pre className="code-block" style={{ margin: 0, maxHeight: 220, overflow: 'auto' }}>{request.selectedText}</pre>
        )}
      </Space>
    ),
  }));

  return (
    <div className="human-approval-gate-page">
      <div className="page-header">
        <div>
          <Title level={3} style={{ margin: 0 }}>Human Gate</Title>
        </div>
        <Space>
          <StatusTag value={gate?.status || 'awaiting'} />
          {selectedIsEditable && <Tag color="green">Editable</Tag>}
        </Space>
      </div>

      <Card className="human-approval-summary">
        <div className="human-approval-summary-row">
          <Space size={16}>
            <Text strong>{summary.files_changed || 0} files changed</Text>
            <Text style={{ color: '#16a34a' }}>+{summary.added_lines || 0}</Text>
            <Text style={{ color: '#dc2626' }}>-{summary.removed_lines || 0}</Text>
            <Tag>{summary.status_label || (isInput ? 'Awaiting input' : 'Ready for review')}</Tag>
          </Space>
          <Space>
            {isApproval && (
              <>
                <Button onClick={() => setActiveActionPanel('approve')}>Approve</Button>
                <Button type="default" danger onClick={() => setActiveActionPanel('rework')}>Rework</Button>
              </>
            )}
            {isInput && (
              <Button
                onClick={submitInput}
                loading={submitting === 'submit'}
                type="default"
              >
                Submit input
              </Button>
            )}
          </Space>
        </div>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={6}>
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Card title="Node instruction" className="human-gate-node-instruction-card">
              <pre className="human-gate-main-instruction-content">
                {userInstructions || '—'}
              </pre>
            </Card>
            <Card title="Changed Files" loading={loading} className="human-approval-file-list">
              <Tree
                showLine={{ showLeafIcon: false }}
                blockNode
                treeData={treeData}
                defaultExpandAll
                switcherIcon={({ isLeaf, expanded }) => (isLeaf ? null : (expanded ? '-' : '+'))}
                selectedKeys={selectedPath ? [selectedPath] : []}
                onSelect={(keys, info) => {
                  if (!info?.node?.isLeaf) {
                    return;
                  }
                  setSelectedPath(String(info?.node?.path || keys[0] || ''));
                }}
                titleRender={(node) => {
                  const editable = node.isLeaf && editableArtifacts.some(
                    (a) => a?.workspace_path && normalizePath(a.workspace_path) === normalizePath(node.path)
                  );
                  return (
                    <span>
                      {node.title}
                      {editable && <EditOutlined style={{ marginLeft: 6, opacity: 0.6 }} />}
                    </span>
                  );
                }}
              />
            </Card>
          </Space>
        </Col>

        <Col xs={24} lg={18}>
          <Card
            title={selectedPath ? (
              <div className="human-gate-editor-title">
                <span className="human-gate-editor-path">{selectedPath}</span>
                <span className="human-gate-editor-status">{String(selectedGitChange?.status || 'modified').toLowerCase()}</span>
              </div>
            ) : 'Diff viewer'}
            loading={loadingDiff}
          >
            {selectedIsEditable ? (
              <Editor
                height="520px"
                defaultLanguage="markdown"
                beforeMount={configureMonacoThemes}
                theme={monacoTheme}
                value={currentEditableContent}
                onChange={(value) => {
                  if (!selectedEditable) return;
                  if (value === undefined) return;
                  setEditedByPath((prev) => ({ ...prev, [selectedEditable.path]: value ?? '' }));
                }}
                options={{
                  readOnly: false,
                  minimap: { enabled: false },
                  wordWrap: 'on',
                  mouseWheelScrollSensitivity: 0.6,
                  fastScrollSensitivity: 1,
                  automaticLayout: true,
                }}
              />
            ) : (
              <div
                onContextMenu={(event) => {
                  if (!isApproval) {
                    return;
                  }
                  event.preventDefault();
                  setContextMenuState({ open: true, x: event.clientX, y: event.clientY });
                }}
                style={{ position: 'relative' }}
              >
                <DiffEditor
                  key={diffEditorKey}
                  height="520px"
                  beforeMount={configureMonacoThemes}
                  theme={monacoTheme}
                  original={originalContent}
                  modified={modifiedContent}
                  language={selectedLanguage}
                  onMount={(editorInstance) => {
                    diffEditorRef.current = editorInstance;
                    modifiedEditorRef.current = editorInstance.getModifiedEditor();
                    applyReworkDecorations();
                  }}
                  onUnmount={(editorInstance) => {
                    diffEditorRef.current = null;
                    modifiedEditorRef.current = null;
                    reworkDecorationIdsRef.current = [];
                  }}
                  options={{
                    readOnly: true,
                    renderSideBySide: isApproval,
                    useInlineViewWhenSpaceIsLimited: false,
                    contextmenu: false,
                    glyphMargin: true,
                    minimap: { enabled: false },
                    wordWrap: 'on',
                    mouseWheelScrollSensitivity: 0.6,
                    fastScrollSensitivity: 1,
                    automaticLayout: true,
                  }}
                />
                {contextMenuState.open && isApproval && (
                  <div
                    style={{
                      position: 'fixed',
                      left: contextMenuState.x,
                      top: contextMenuState.y,
                      zIndex: 1500,
                      background: 'var(--surface)',
                      border: '1px solid var(--border)',
                      borderRadius: 6,
                      padding: 6,
                      boxShadow: '0 8px 24px rgba(0,0,0,0.16)',
                    }}
                  >
                    <Button
                      type="text"
                      danger
                      onClick={() => {
                        setContextMenuState({ open: false, x: 0, y: 0 });
                        openAddRequestModalFromSelection();
                      }}
                    >
                      Request Rework
                    </Button>
                  </div>
                )}
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <Modal
        title="Request Rework"
        open={addRequestModalOpen}
        onCancel={() => {
          setAddRequestModalOpen(false);
          setRequestDraft(EMPTY_REQUEST_DRAFT);
        }}
        onOk={addReworkRequest}
        okText="Add request"
        okButtonProps={{ disabled: !(requestDraft.instruction || '').trim() }}
      >
        {requestDraft.scope === 'selection' ? (
          <Space direction="vertical" size={10} style={{ width: '100%' }}>
            <Text>Rework will be requested only for the selected code fragment.</Text>
            <pre className="code-block" style={{ margin: 0, maxHeight: 220, overflow: 'auto' }}>{requestDraft.selectedText || '—'}</pre>
            <Input.TextArea
              rows={4}
              value={requestDraft.instruction}
              onChange={(event) => setRequestDraft((prev) => ({ ...prev, instruction: event.target.value }))}
              placeholder="Describe what should be reworked in this fragment"
            />
          </Space>
        ) : (
          <Space direction="vertical" size={10} style={{ width: '100%' }}>
            <Text>Rework will be requested for the whole file.</Text>
            <Input.TextArea
              rows={4}
              value={requestDraft.instruction}
              onChange={(event) => setRequestDraft((prev) => ({ ...prev, instruction: event.target.value }))}
              placeholder="Describe what should be reworked in the file"
            />
          </Space>
        )}
      </Modal>

      <Drawer
        title={activeActionPanel === 'approve' ? 'Approve Project Changes' : 'Request Project Rework'}
        placement="right"
        width={460}
        open={activeActionPanel !== null}
        onClose={() => setActiveActionPanel(null)}
      >
        <div className="human-gate-drawer-content">
          <div className="human-gate-drawer-body">
            <div className="human-gate-drawer-hero">
              <Text className="human-gate-drawer-kicker">Current action</Text>
              <Text className="human-gate-drawer-title">
                {activeActionPanel === 'approve'
                  ? 'You are approving this human gate.'
                  : 'You are requesting rework for this human gate.'}
              </Text>
              {activeActionPanel === 'rework' && keepChanges && (
                <Text className="human-gate-drawer-alert is-danger">
                  Attention! All current changes will be committed; next gates will show only requested modifications.
                </Text>
              )}
              {activeActionPanel === 'rework' && !keepChanges && (
                <Text className="human-gate-drawer-alert is-warning">
                  Attention! Changes will be rolled back to the latest checkpoint before rework.
                </Text>
              )}
            </div>

            {activeActionPanel === 'approve' && (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Text className="muted">Comment</Text>
                <Input.TextArea
                  rows={4}
                  value={approveComment}
                  onChange={(event) => setApproveComment(event.target.value)}
                />
              </Space>
            )}

            {activeActionPanel === 'rework' && (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Text className="muted">Rework requests</Text>
                {reworkRequests.length === 0 ? (
                  <Text type="secondary">No rework requests added yet.</Text>
                ) : (
                  <Collapse items={reworkItems} />
                )}

                <Text className="muted">Instruction</Text>
                <Input.TextArea
                  rows={3}
                  value={reworkInstruction}
                  onChange={(event) => setReworkInstruction(event.target.value)}
                />

                <Text className="muted">Changes handling policy</Text>
                <div style={{ display: 'grid', gap: 6 }}>
                  <Tag color={isDiscardPolicy ? 'orange' : 'blue'} style={{ width: 'fit-content' }}>
                    {isDiscardPolicy ? 'Discard to checkpoint' : 'Keep changes'}
                  </Tag>
                  <Switch
                    checked={keepChanges}
                    onChange={handleKeepChangesToggle}
                    disabled={!keepChangesSelectable || submitting !== null}
                    checkedChildren="Keep"
                    unCheckedChildren="Discard"
                    style={{ width: "fit-content" }}
                  />
                  {reworkDiscardBlocked && (
                    <Text type="danger">{reworkDiscardBlockedReason}</Text>
                  )}
                </div>

                <Text className="muted">Comment</Text>
                <Input.TextArea
                  rows={2}
                  value={reworkComment}
                  onChange={(event) => setReworkComment(event.target.value)}
                />
              </Space>
            )}
          </div>

          <div className="human-gate-drawer-footer">
            {activeActionPanel === 'approve' && (
              <Button
                onClick={async () => {
                  const ok = await approve();
                  if (!ok) return;
                  setActiveActionPanel(null);
                }}
                loading={submitting === 'approve'}
              >
                Approve
              </Button>
            )}
            {activeActionPanel === 'rework' && (
              <Button
                type="default"
                danger
                onClick={async () => {
                  const ok = await requestRework();
                  if (!ok) return;
                  setActiveActionPanel(null);
                }}
                loading={submitting === 'rework'}
                disabled={!canSubmitRework}
              >
                Request rework
              </Button>
            )}
          </div>
        </div>
      </Drawer>
    </div>
  );
}
