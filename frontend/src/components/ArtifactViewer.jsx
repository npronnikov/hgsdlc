import React, { useEffect, useState } from 'react';
import { Button, Card, Space, Typography, message } from 'antd';
import Editor from '@monaco-editor/react';
import { apiRequest } from '../api/request.js';

const { Text } = Typography;

function detectLanguage(path) {
  if (!path) {
    return 'plaintext';
  }
  const ext = path.split('.').pop().toLowerCase();
  const map = {
    md: 'markdown',
    json: 'json',
    yaml: 'yaml',
    yml: 'yaml',
    js: 'javascript',
    jsx: 'javascript',
    ts: 'typescript',
    tsx: 'typescript',
    java: 'java',
    py: 'python',
    sql: 'sql',
    xml: 'xml',
    html: 'html',
    css: 'css',
    sh: 'shell',
    bash: 'shell',
  };
  return map[ext] || 'plaintext';
}

export default function ArtifactViewer({ runId, artifact, onClose }) {
  const [content, setContent] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!artifact?.artifact_version_id) {
      return;
    }
    setLoading(true);
    setContent(null);
    apiRequest(`/runs/${runId}/artifacts/${artifact.artifact_version_id}/content`)
      .then((data) => setContent(data.content || ''))
      .catch((err) => {
        message.error(err.message || 'Failed to load artifact content');
        setContent(null);
      })
      .finally(() => setLoading(false));
  }, [runId, artifact?.artifact_version_id]);

  if (!artifact) {
    return null;
  }

  const language = detectLanguage(artifact.path || artifact.artifact_key);

  return (
    <Card
      size="small"
      title={<Text className="mono" style={{ fontSize: 13 }}>{artifact.artifact_key}</Text>}
      extra={<Button size="small" onClick={onClose}>Close</Button>}
      loading={loading}
    >
      <Space size={12} style={{ marginBottom: 8 }}>
        <Text type="secondary">Node: <span className="mono">{artifact.node_id}</span></Text>
        <Text type="secondary">Kind: <span className="mono">{artifact.kind}</span></Text>
        <Text type="secondary">Version: <span className="mono">v{artifact.version_no}</span></Text>
        <Text type="secondary">Size: {artifact.size_bytes ? `${artifact.size_bytes} B` : '—'}</Text>
      </Space>
      {content !== null && (
        <div style={{ border: '1px solid var(--border)', borderRadius: 6, overflow: 'hidden' }}>
          <Editor
            height="400px"
            defaultLanguage={language}
            value={content}
            options={{
              readOnly: true,
              minimap: { enabled: false },
              lineNumbers: 'on',
              scrollBeyondLastLine: false,
              fontSize: 12,
              wordWrap: 'on',
              automaticLayout: true,
              overviewRulerLanes: 0,
              hideCursorInOverviewRuler: true,
              scrollbar: { vertical: 'auto', horizontal: 'hidden' },
            }}
          />
        </div>
      )}
      {content === null && !loading && (
        <Text type="secondary">Content not available</Text>
      )}
    </Card>
  );
}
