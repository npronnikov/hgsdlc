import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Input, Radio, Row, Space, Tag, Tree, Typography, message } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Editor from '@monaco-editor/react';
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

function buildTree(paths) {
  const root = new Map();
  paths.forEach((path) => {
    const parts = pathSegments(path);
    let level = root;
    parts.forEach((part, idx) => {
      if (!level.has(part)) {
        level.set(part, { key: parts.slice(0, idx + 1).join('/'), title: part, children: new Map(), leaf: false });
      }
      const node = level.get(part);
      if (idx === parts.length - 1) {
        node.leaf = true;
      }
      level = node.children;
    });
  });
  const toTreeData = (map) => Array.from(map.values())
    .sort((a, b) => Number(a.leaf) - Number(b.leaf) || a.title.localeCompare(b.title))
    .map((item) => ({
      key: item.key,
      title: item.title,
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
  const [patch, setPatch] = useState('');
  const [loadingDiff, setLoadingDiff] = useState(false);
  const [loading, setLoading] = useState(false);

  const [comment, setComment] = useState('');
  const [instruction, setInstruction] = useState('');
  const [reworkMode, setReworkMode] = useState('discard');
  const [submitting, setSubmitting] = useState(null);
  const [editedByPath, setEditedByPath] = useState({});

  const isInput = gate?.gate_kind === 'human_input';
  const isApproval = gate?.gate_kind === 'human_approval';
  const reworkDiscardAvailable = gate?.payload?.rework_discard_available !== false;
  const userInstructions = gate?.payload?.user_instructions || '';
  const editableArtifacts = Array.isArray(gate?.payload?.human_input_artifacts) ? gate.payload.human_input_artifacts : [];

  const viewFiles = useMemo(() => (changes || []).map((item) => item.path).filter(Boolean), [changes]);

  const treeData = useMemo(() => buildTree(viewFiles), [viewFiles]);

  const selectedEditable = useMemo(
    () => editableArtifacts.find((artifact) => matchesEditablePath(selectedPath, artifact.path) || matchesEditablePath(artifact.path, selectedPath)) || null,
    [editableArtifacts, selectedPath]
  );
  const selectedGitChange = useMemo(() => changes.find((item) => item.path === selectedPath) || null, [changes, selectedPath]);
  const selectedIsEditable = isInput && !!selectedEditable;
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
        setPatch('');
        return;
      }
      if (!selectedGitChange) {
        setPatch('');
        return;
      }
      setLoadingDiff(true);
      try {
        const data = await apiRequest(`/gates/${gateId}/diff?path=${encodeURIComponent(selectedPath)}`);
        setPatch(data.patch || '');
      } catch (err) {
        message.error(err.message || 'Failed to load diff');
        setPatch('');
      } finally {
        setLoadingDiff(false);
      }
    };
    loadDiff();
  }, [selectedPath, selectedGitChange, gateId]);

  const goBack = () => navigate(`/run-console?runId=${runId}`);

  const approve = async () => {
    if (!gate) return;
    setSubmitting('approve');
    try {
      await apiRequest(`/gates/${gate.gate_id}/approve`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          comment,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Gate approved');
      goBack();
    } catch (err) {
      message.error(err.message || 'Failed to approve gate');
      await refresh();
    } finally {
      setSubmitting(null);
    }
  };

  const requestRework = async () => {
    if (!gate) return;
    setSubmitting('rework');
    try {
      await apiRequest(`/gates/${gate.gate_id}/request-rework`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          mode: reworkDiscardAvailable ? reworkMode : 'keep',
          comment,
          instruction,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Rework requested');
      goBack();
    } catch (err) {
      message.error(err.message || 'Failed to request rework');
      await refresh();
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

  return (
    <div className="human-approval-gate-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Human Gate</Title>
        <Space>
          <StatusTag value={gate?.status || 'awaiting'} />
          {selectedIsEditable && <Tag color="green">Editable</Tag>}
          <Button onClick={goBack}>Back to Run Console</Button>
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
            <Text type="secondary" className="mono">{gate?.gate_kind || '—'}</Text>
            <Text type="secondary" className="mono">{gate?.node_id || '—'}</Text>
          </Space>
        </div>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={6}>
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
                setSelectedPath(String(keys[0] || ''));
              }}
            />
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title={selectedPath || 'Diff viewer'} loading={loadingDiff}>
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
                  automaticLayout: true,
                }}
              />
            ) : (
              <Editor
                height="520px"
                defaultLanguage="diff"
                beforeMount={configureMonacoThemes}
                theme={monacoTheme}
                value={patch || 'No diff available for selected file.'}
                options={{
                  readOnly: true,
                  minimap: { enabled: false },
                  wordWrap: 'on',
                  automaticLayout: true,
                }}
              />
            )}
          </Card>
        </Col>

        <Col xs={24} lg={6}>
          <Card title="Gate" loading={loading}>
            <Text className="muted">Instruction</Text>
            <pre className="code-block" style={{ marginTop: 8, maxHeight: 220, overflow: 'auto' }}>
              {userInstructions || '—'}
            </pre>
            <div style={{ marginTop: 12, marginLeft: 0 }}>
              <Text className="muted">Comment</Text>
              <Input.TextArea
                rows={3}
                style={{ marginTop: 8, marginLeft: 0 }}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
              />
            </div>
            {isApproval && (
              <>
                <div style={{ marginTop: 12 }}>
                  <Text className="muted">Rework instruction</Text>
                  <Input.TextArea
                    rows={4}
                    style={{ marginTop: 8, marginLeft: 0 }}
                    value={instruction}
                    onChange={(e) => setInstruction(e.target.value)}
                  />
                </div>
                {reworkDiscardAvailable ? (
                  <Radio.Group
                    value={reworkMode}
                    onChange={(event) => setReworkMode(event.target.value)}
                    optionType="button"
                    buttonStyle="solid"
                    style={{ marginTop: 12, width: '100%' }}
                  >
                    <Radio.Button value="keep">Keep</Radio.Button>
                    <Radio.Button value="discard">Discard</Radio.Button>
                  </Radio.Group>
                ) : (
                  <Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
                    Current changes will be preserved
                  </Text>
                )}
                <Space style={{ marginTop: 12 }}>
                  <Button onClick={approve} loading={submitting === 'approve'}>Approve</Button>
                  <Button onClick={requestRework} loading={submitting === 'rework'} danger>
                    Request rework
                  </Button>
                </Space>
              </>
            )}
            {isInput && (
              <Button
                style={{ marginTop: 12 }}
                onClick={submitInput}
                loading={submitting === 'submit'}
                type="default"
              >
                Submit input
              </Button>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
