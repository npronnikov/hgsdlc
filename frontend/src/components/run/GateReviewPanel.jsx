import { useEffect, useMemo, useState } from 'react';
import { Button, Collapse, Input, Modal, Progress, Radio, Segmented, Space, Switch, Tree, Typography, message } from 'antd';
import { DeleteOutlined, InfoCircleOutlined, PlusOutlined } from '@ant-design/icons';
import Editor, { DiffEditor } from '@monaco-editor/react';
import { useGateReview, EMPTY_REQUEST_DRAFT } from '../../hooks/useGateReview.js';
import { useGateChanges, detectLanguage, matchesAnyEditablePath, normalizePath } from '../../hooks/useGateChanges.js';
import { useThemeMode } from '../../theme/ThemeContext.jsx';
import { getMonacoThemeName } from '../../utils/monacoTheme.js';
import { StageTracker } from './StageTracker.jsx';
import MarkdownPreview from '../MarkdownPreview.jsx';

const { Text } = Typography;

const STATUS_BADGE = {
  modified: { letter: 'M', color: '#faad14' },
  added: { letter: 'A', color: '#52c41a' },
  deleted: { letter: 'D', color: '#ff4d4f' },
  renamed: { letter: 'R', color: '#1677ff' },
  untracked: { letter: 'U', color: '#52c41a' },
};

function FileTreeTitle({ nodeData }) {
  const { title, change } = nodeData;
  if (!change) return <span>{title}</span>;
  const badge = STATUS_BADGE[String(change.status).toLowerCase()] || null;
  return (
    <span className="file-tree-title">
      <span className="file-tree-name">{title}</span>
      {badge && <span className="file-tree-badge" style={{ color: badge.color }}>{badge.letter}</span>}
      {(change.added > 0 || change.removed > 0) && (
        <span className="file-tree-stats">
          {change.added > 0 && <span className="file-tree-plus">+{change.added}</span>}
          {change.removed > 0 && <span className="file-tree-minus">-{change.removed}</span>}
        </span>
      )}
      {change.binary && <span className="file-tree-binary">bin</span>}
    </span>
  );
}

export function GateReviewPanel({ runId, gate, gates = [], onDecision, onRefresh }) {
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);
  const gateId = gate?.gate_id;

  const changesHook = useGateChanges(gateId);
  const {
    changes, summary, treeData, viewFiles,
    selectedPath, setSelectedPath,
    originalContent, modifiedContent, patchContent, loadingDiff,
    diffMode, setDiffMode,
    viewedFiles, viewedCount, toggleViewed,
    loadChanges,
  } = changesHook;

  const contextNodeIds = useMemo(() => {
    const artifacts = gate?.payload?.execution_context_artifacts;
    if (!Array.isArray(artifacts) || artifacts.length === 0) return null;
    const ids = artifacts.map((a) => a.source_node_id).filter(Boolean);
    return ids.length > 0 ? new Set(ids) : null;
  }, [gate?.payload?.execution_context_artifacts]);

  const reviewHook = useGateReview(gate, { onDecision, onRefresh });
  const {
    approveComment, setApproveComment,
    reworkComment, setReworkComment,
    reworkInstruction, setReworkInstruction,
    submitting,
    reworkRequests, addReworkRequest, removeReworkRequest,
    keepChanges, handleKeepChangesToggle, keepChangesSelectable,
    effectiveDiscardAvailable, reworkDiscardBlocked, reworkDiscardBlockedReason,
    canSubmitRework,
    approve, requestRework,
  } = reviewHook;

  const [reworkModalOpen, setReworkModalOpen] = useState(false);
  const [reworkDraft, setReworkDraft] = useState({ ...EMPTY_REQUEST_DRAFT });

  const openReworkModal = () => {
    setReworkDraft({ ...EMPTY_REQUEST_DRAFT, path: selectedPath || '' });
    setReworkModalOpen(true);
  };

  const submitReworkDraft = () => {
    addReworkRequest(reworkDraft, selectedPath);
    setReworkModalOpen(false);
  };

  useEffect(() => {
    if (gateId) loadChanges().then((data) => {
      if (data && !selectedPath) {
        const allChanges = data.git_changes || [];
        let best = allChanges[0]?.path || '';
        if (contextNodeIds) {
          const match = allChanges.find((c) => {
            const m = normalizePath(c.path).match(/^\.hgsdlc\/nodes\/([^/]+)\//);
            return m && contextNodeIds.has(m[1]);
          });
          if (match) best = match.path;
        }
        setSelectedPath(best);
      }
    });
  }, [gateId]);

  const selectedGitChange = useMemo(() => {
    const sel = normalizePath(selectedPath);
    return changes.find((item) => normalizePath(item.path) === sel) || null;
  }, [changes, selectedPath]);
  const isNewFile = ['added', 'untracked'].includes(String(selectedGitChange?.status || '').toLowerCase());
  const selectedLanguage = useMemo(() => detectLanguage(selectedPath), [selectedPath]);
  const isMarkdown = selectedLanguage === 'markdown';
  const showSideBySide = !isNewFile && diffMode === 'side-by-side';
  const showPreview = isMarkdown && diffMode === 'preview';
  const showPatch = diffMode === 'patch';
  const diffModeOptions = useMemo(() => {
    const opts = ['side-by-side', 'unified', 'patch'];
    if (isMarkdown) opts.push('preview');
    return opts;
  }, [isMarkdown]);
  const totalFiles = viewFiles.length;

  const reworkItems = reworkRequests.map((request, idx) => ({
    key: request.id,
    label: `#${idx + 1} • ${request.path} • ${request.scope}`,
    extra: (
      <Button type="text" danger size="small" icon={<DeleteOutlined />}
        onClick={(e) => { e.stopPropagation(); removeReworkRequest(request.id); }} />
    ),
    children: (
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Text><Text strong>Instruction:</Text> {request.instruction}</Text>
      </Space>
    ),
  }));

  return (
    <div className="gate-review-panel">
      <StageTracker gates={gates} currentGateId={gate?.gate_id} />

      {gate?.payload?.user_instructions && (
        <Collapse
          size="small"
          className="gate-review-instructions"
          defaultActiveKey={['instructions']}
          items={[{
            key: 'instructions',
            label: <><InfoCircleOutlined /> Review Instructions</>,
            children: <div className="gate-review-instructions-text">{gate.payload.user_instructions}</div>,
          }]}
        />
      )}

      <div className="gate-review-body">
        <div className="gate-review-tree">
          <div className="gate-review-summary">
            {summary.files_changed} files&nbsp;
            <span style={{ color: '#52c41a' }}>+{summary.added_lines}</span>&nbsp;
            <span style={{ color: '#ff4d4f' }}>-{summary.removed_lines}</span>
          </div>
          <Progress
            percent={totalFiles > 0 ? Math.round(viewedCount / totalFiles * 100) : 0}
            size="small" showInfo={false}
          />
          <Tree
            treeData={treeData}
            selectedKeys={[selectedPath]}
            onSelect={([key]) => key && setSelectedPath(key)}
            titleRender={(nodeData) => <FileTreeTitle nodeData={nodeData} />}
            showLine
          />
        </div>

        <div className="gate-review-diff">
          <div className="gate-review-diff-toolbar">
            <Segmented options={diffModeOptions} value={diffMode} onChange={setDiffMode} size="small" />
            <span className="mono" style={{ fontSize: 12, flex: 1 }}>{selectedPath}</span>
            <Button size="small" icon={<PlusOutlined />} onClick={openReworkModal} title="Add rework request for this file">
              Rework
            </Button>
            <Button size="small" onClick={() => toggleViewed(selectedPath)}>
              {viewedFiles.has(selectedPath) ? 'Unmark' : 'Mark viewed'}
            </Button>
          </div>
          {loadingDiff && <div className="gate-review-loading">Loading diff...</div>}
          {!loadingDiff && selectedPath && showPreview && (
            <div className="gate-review-preview">
              <MarkdownPreview markdown={modifiedContent} />
            </div>
          )}
          {!loadingDiff && selectedPath && showPatch && (
            <div className="gate-review-patch">
              <pre className="gate-review-patch-pre">{patchContent || 'No patch data available'}</pre>
            </div>
          )}
          {!loadingDiff && selectedPath && !showPreview && !showPatch && showSideBySide && (
            <DiffEditor
              height="100%"
              language={selectedLanguage}
              theme={monacoTheme}
              original={originalContent}
              modified={modifiedContent}
              options={{ readOnly: true, renderSideBySide: true, minimap: { enabled: false } }}
            />
          )}
          {!loadingDiff && selectedPath && !showPreview && !showPatch && !showSideBySide && (
            <Editor
              height="100%"
              language={selectedLanguage}
              theme={monacoTheme}
              value={modifiedContent}
              options={{ readOnly: true, minimap: { enabled: false }, wordWrap: 'on' }}
            />
          )}
        </div>
      </div>

      <div className="gate-review-actions">
        <div className="gate-review-actions-buttons">
          <Input
            placeholder="Comment (optional)"
            value={approveComment}
            onChange={(e) => setApproveComment(e.target.value)}
            size="small"
            style={{ flex: 1, minWidth: 0 }}
          />
          <Button type="primary" loading={submitting === 'approve'} onClick={approve}>
            Approve
          </Button>
          <div className="gate-review-actions-divider" />
          <Button
            loading={submitting === 'rework'}
            disabled={!canSubmitRework || reworkDiscardBlocked}
            onClick={requestRework}
            danger
          >
            Rework{reworkRequests.length > 0 ? ` (${reworkRequests.length})` : ''}
          </Button>
        </div>

        <Collapse
          size="small"
          className="gate-review-rework-details"
          items={[{
            key: 'rework',
            label: <Text type="secondary" style={{ fontSize: 12 }}>Rework details</Text>,
            children: (
              <Space direction="vertical" size={6} style={{ width: '100%' }}>
                {reworkRequests.length > 0 && <Collapse size="small" items={reworkItems} />}
                <Input.TextArea
                  placeholder="Rework instruction — describe what needs to change"
                  value={reworkInstruction}
                  onChange={(e) => setReworkInstruction(e.target.value)}
                  autoSize={{ minRows: 2, maxRows: 5 }}
                />
                <Input
                  placeholder="Rework comment (audit log)"
                  value={reworkComment}
                  onChange={(e) => setReworkComment(e.target.value)}
                  size="small"
                />
                {keepChangesSelectable && (
                  <div className="gate-review-keep-switch">
                    <Switch
                      checked={keepChanges}
                      onChange={handleKeepChangesToggle}
                      size="small"
                    />
                    <span>{keepChanges ? 'Keep changes' : 'Discard to checkpoint'}</span>
                  </div>
                )}
              </Space>
            ),
          }]}
        />
      </div>

      <Modal
        title="Add Rework Request"
        open={reworkModalOpen}
        onOk={submitReworkDraft}
        onCancel={() => setReworkModalOpen(false)}
        okText="Add"
        okButtonProps={{ disabled: !(reworkDraft.instruction || '').trim() }}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>File path</Text>
            <Input
              value={reworkDraft.path}
              onChange={(e) => setReworkDraft((d) => ({ ...d, path: e.target.value }))}
              placeholder="File path"
            />
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>Scope</Text>
            <div>
              <Radio.Group
                value={reworkDraft.scope}
                onChange={(e) => setReworkDraft((d) => ({ ...d, scope: e.target.value }))}
                size="small"
                optionType="button"
              >
                <Radio.Button value="file">File</Radio.Button>
                <Radio.Button value="selection">Selection</Radio.Button>
                <Radio.Button value="general">General</Radio.Button>
              </Radio.Group>
            </div>
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>Instruction</Text>
            <Input.TextArea
              value={reworkDraft.instruction}
              onChange={(e) => setReworkDraft((d) => ({ ...d, instruction: e.target.value }))}
              placeholder="Describe what needs to change"
              rows={3}
              autoFocus
            />
          </div>
        </Space>
      </Modal>
    </div>
  );
}
