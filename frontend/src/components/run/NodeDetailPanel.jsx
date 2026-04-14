import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Segmented, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CodeOutlined,
  FileTextOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import { LiveLogViewer } from './LiveLogViewer.jsx';
import { apiRequest } from '../../api/request.js';
import { useThemeMode } from '../../theme/ThemeContext.jsx';
import { getMonacoThemeName } from '../../utils/monacoTheme.js';
import { detectLanguage } from '../../hooks/useGateChanges.js';

const { Text } = Typography;

function NodeConfig({ nodeModel, nodeExecution }) {
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);

  if (!nodeModel) {
    return <div className="node-detail-empty">Select a node to view config</div>;
  }

  return (
    <div className="node-detail-config">
      {nodeModel.instruction && (
        <div className="node-detail-section">
          <div className="node-detail-section-title">Instruction</div>
          <Editor
            height={Math.min(300, Math.max(80, (nodeModel.instruction.split('\n').length + 1) * 18))}
            language="markdown"
            theme={monacoTheme}
            value={nodeModel.instruction}
            options={{ readOnly: true, minimap: { enabled: false }, wordWrap: 'on', lineNumbers: 'off', scrollBeyondLastLine: false, folding: false }}
          />
        </div>
      )}

      {nodeModel.execution_context?.length > 0 && (
        <div className="node-detail-section">
          <div className="node-detail-section-title">Execution Context</div>
          <div className="node-detail-ctx-list">
            {nodeModel.execution_context.map((entry, i) => (
              <div key={i} className="node-detail-ctx-entry">
                <Tag>{entry.type || 'artifact'}</Tag>
                <span className="mono">{entry.path}</span>
                {entry.node_id && <Text type="secondary"> from {entry.node_id}</Text>}
                {entry.modifiable && <Tag color="blue">editable</Tag>}
              </div>
            ))}
          </div>
        </div>
      )}

      {nodeModel.produced_artifacts?.length > 0 && (
        <div className="node-detail-section">
          <div className="node-detail-section-title">Produced Artifacts</div>
          <div className="node-detail-ctx-list">
            {nodeModel.produced_artifacts.map((art, i) => (
              <div key={i} className="node-detail-ctx-entry">
                <span className="mono">{art.path}</span>
                {art.scope && <Tag>{art.scope}</Tag>}
              </div>
            ))}
          </div>
        </div>
      )}

      {nodeExecution && (
        <div className="node-detail-section">
          <div className="node-detail-section-title">Runtime Info</div>
          <div className="node-detail-ctx-list">
            <div className="node-detail-ctx-entry">
              <Text type="secondary">Status:</Text>
              <Tag color={nodeExecution.status === 'succeeded' ? 'success' : nodeExecution.status === 'failed' ? 'error' : 'processing'}>
                {nodeExecution.status}
              </Tag>
            </div>
            {nodeExecution.checkpoint_enabled && (
              <div className="node-detail-ctx-entry">
                <SaveOutlined style={{ color: '#52c41a' }} />
                <Text type="secondary">Checkpoint:</Text>
                <span className="mono">{nodeExecution.checkpoint_commit_sha?.slice(0, 8) || 'pending'}</span>
              </div>
            )}
            {nodeExecution.error_code && (
              <div className="node-detail-ctx-entry">
                <Text type="secondary">Error:</Text>
                <Tag color="error">{nodeExecution.error_code}</Tag>
                <Text type="secondary">{nodeExecution.error_message}</Text>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function NodeArtifacts({ runId, nodeId }) {
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);
  const [allArtifacts, setAllArtifacts] = useState([]);
  const [showAll, setShowAll] = useState(false);
  const [selected, setSelected] = useState(null);
  const [content, setContent] = useState('');
  const [loadingContent, setLoadingContent] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!runId) return;
    setLoading(true);
    apiRequest(`/runs/${runId}/artifacts`).then((list) => {
      const arr = Array.isArray(list) ? list : [];
      setAllArtifacts(arr);
      setSelected(null);
      setContent('');
      const nodeArts = nodeId ? arr.filter((a) => a.node_id === nodeId) : [];
      if (nodeArts.length === 0 && arr.length > 0) setShowAll(true);
    }).catch(() => {
      setAllArtifacts([]);
    }).finally(() => setLoading(false));
  }, [runId, nodeId]);

  const nodeArtifacts = useMemo(
    () => nodeId ? allArtifacts.filter((a) => a.node_id === nodeId) : [],
    [allArtifacts, nodeId],
  );

  const displayArtifacts = showAll ? allArtifacts : (nodeArtifacts.length > 0 ? nodeArtifacts : allArtifacts);

  const loadContent = useCallback(async (art) => {
    setSelected(art.artifact_version_id);
    setLoadingContent(true);
    try {
      const data = await apiRequest(`/runs/${runId}/artifacts/${art.artifact_version_id}/content`);
      setContent(data.content || '');
    } catch {
      setContent('Failed to load content');
    } finally {
      setLoadingContent(false);
    }
  }, [runId]);

  const selectedArtifact = displayArtifacts.find((a) => a.artifact_version_id === selected);
  const language = useMemo(() => detectLanguage(selectedArtifact?.path || ''), [selectedArtifact?.path]);

  if (loading) {
    return <div className="node-detail-empty">Loading artifacts...</div>;
  }

  if (allArtifacts.length === 0) {
    return <div className="node-detail-empty">No artifacts for this run</div>;
  }

  return (
    <div className="node-detail-artifacts">
      <div className="node-detail-art-toolbar">
        <Text type="secondary" style={{ fontSize: 11 }}>
          {nodeArtifacts.length > 0
            ? `${nodeArtifacts.length} for ${nodeId}`
            : `No artifacts for ${nodeId}`}
          {' · '}{allArtifacts.length} total
        </Text>
        {allArtifacts.length !== nodeArtifacts.length && (
          <Button type="link" size="small" onClick={() => setShowAll((v) => !v)}>
            {showAll ? 'Show node only' : 'Show all'}
          </Button>
        )}
      </div>
      <div className="node-detail-art-list">
        {displayArtifacts.map((art) => (
          <div
            key={art.artifact_version_id}
            className={`node-detail-art-item ${selected === art.artifact_version_id ? 'is-selected' : ''} ${art.node_id !== nodeId ? 'is-other-node' : ''}`}
            onClick={() => loadContent(art)}
          >
            <FileTextOutlined />
            <span className="mono node-detail-art-path">{art.path || art.artifact_key}</span>
            {art.node_id !== nodeId && <Tag style={{ fontSize: 10 }}>{art.node_id}</Tag>}
            <Tag>{art.kind}</Tag>
            <Text type="secondary">v{art.version_no}</Text>
          </div>
        ))}
      </div>
      {selected && (
        <div className="node-detail-art-content">
          {loadingContent ? (
            <div className="node-detail-empty">Loading...</div>
          ) : (
            <Editor
              height="100%"
              language={language}
              theme={monacoTheme}
              value={content}
              options={{ readOnly: true, minimap: { enabled: false }, wordWrap: 'on' }}
            />
          )}
        </div>
      )}
    </div>
  );
}

export function NodeDetailPanel({ runId, nodeExecutionId, flowSnapshot, nodeExecutions }) {
  const [tab, setTab] = useState('logs');

  const selectedExec = useMemo(() =>
    nodeExecutions?.find((e) => e.node_execution_id === nodeExecutionId) || null,
  [nodeExecutions, nodeExecutionId]);

  const nodeModel = useMemo(() => {
    if (!selectedExec || !flowSnapshot?.nodes) return null;
    return flowSnapshot.nodes.find((n) => n.id === selectedExec.node_id) || null;
  }, [selectedExec, flowSnapshot]);

  return (
    <div className="node-detail-panel">
      <div className="node-detail-tabs">
        <Segmented
          size="small"
          value={tab}
          onChange={setTab}
          options={[
            { label: 'Logs', value: 'logs', icon: <CodeOutlined /> },
            { label: 'Config', value: 'config', icon: <FileTextOutlined /> },
            { label: 'Artifacts', value: 'artifacts', icon: <CheckCircleOutlined /> },
          ]}
        />
        {selectedExec && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {selectedExec.node_id}
            {selectedExec.attempt_no > 1 ? ` #${selectedExec.attempt_no}` : ''}
          </Text>
        )}
      </div>
      <div className="node-detail-content">
        {tab === 'logs' && (
          <LiveLogViewer runId={runId} nodeExecutionId={nodeExecutionId} />
        )}
        {tab === 'config' && (
          <NodeConfig nodeModel={nodeModel} nodeExecution={selectedExec} />
        )}
        {tab === 'artifacts' && (
          <NodeArtifacts runId={runId} nodeId={selectedExec?.node_id} />
        )}
      </div>
    </div>
  );
}
