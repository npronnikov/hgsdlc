import { useEffect, useMemo, useState } from 'react';
import { Button, Collapse, Input, Progress, Segmented, Space, Switch, Tree, Typography, message } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import Editor, { DiffEditor } from '@monaco-editor/react';
import { useGateReview, EMPTY_REQUEST_DRAFT } from '../../hooks/useGateReview.js';
import { useGateChanges, detectLanguage, matchesAnyEditablePath, normalizePath } from '../../hooks/useGateChanges.js';
import { useThemeMode } from '../../theme/ThemeContext.jsx';
import { getMonacoThemeName } from '../../utils/monacoTheme.js';
import { StageTracker } from './StageTracker.jsx';

const { Text } = Typography;

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

  const reviewHook = useGateReview(gate, { onDecision, onRefresh });
  const {
    approveComment, setApproveComment,
    reworkInstruction, setReworkInstruction,
    submitting,
    reworkRequests, addReworkRequest, removeReworkRequest,
    keepChanges, handleKeepChangesToggle, keepChangesSelectable,
    effectiveDiscardAvailable, reworkDiscardBlocked, reworkDiscardBlockedReason,
    canSubmitRework,
    approve, requestRework,
  } = reviewHook;

  useEffect(() => {
    if (gateId) loadChanges().then((data) => {
      if (data && !selectedPath) {
        const first = (data.git_changes || [])[0]?.path || '';
        setSelectedPath(first);
      }
    });
  }, [gateId]);

  const selectedGitChange = useMemo(() => {
    const sel = normalizePath(selectedPath);
    return changes.find((item) => normalizePath(item.path) === sel) || null;
  }, [changes, selectedPath]);
  const isNewFile = ['added', 'untracked'].includes(String(selectedGitChange?.status || '').toLowerCase());
  const showSideBySide = !isNewFile && diffMode === 'side-by-side';
  const selectedLanguage = useMemo(() => detectLanguage(selectedPath), [selectedPath]);
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
            showLine
          />
        </div>

        <div className="gate-review-diff">
          <div className="gate-review-diff-toolbar">
            <Segmented options={['side-by-side', 'unified']} value={diffMode} onChange={setDiffMode} size="small" />
            <span className="mono" style={{ fontSize: 12, flex: 1 }}>{selectedPath}</span>
            <Button size="small" onClick={() => toggleViewed(selectedPath)}>
              {viewedFiles.has(selectedPath) ? 'Unmark' : 'Mark viewed'}
            </Button>
          </div>
          {loadingDiff && <div className="gate-review-loading">Loading diff...</div>}
          {!loadingDiff && selectedPath && showSideBySide && (
            <DiffEditor
              height="100%"
              language={selectedLanguage}
              theme={monacoTheme}
              original={originalContent}
              modified={modifiedContent}
              options={{ readOnly: true, renderSideBySide: true, minimap: { enabled: false } }}
            />
          )}
          {!loadingDiff && selectedPath && !showSideBySide && (
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
        <div className="gate-review-action-approve">
          <Input.TextArea
            placeholder="Approve comment (optional)"
            value={approveComment}
            onChange={(e) => setApproveComment(e.target.value)}
            rows={2}
          />
          <Button type="primary" loading={submitting === 'approve'} onClick={approve}>
            Approve
          </Button>
        </div>
        <div className="gate-review-action-rework">
          {reworkRequests.length > 0 && <Collapse size="small" items={reworkItems} />}
          <Input.TextArea
            placeholder="General rework instruction"
            value={reworkInstruction}
            onChange={(e) => setReworkInstruction(e.target.value)}
            rows={2}
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
          <Button
            loading={submitting === 'rework'}
            disabled={!canSubmitRework || reworkDiscardBlocked}
            onClick={requestRework}
          >
            Request Rework{reworkRequests.length > 0 ? ` (${reworkRequests.length})` : ''}
          </Button>
        </div>
      </div>
    </div>
  );
}
