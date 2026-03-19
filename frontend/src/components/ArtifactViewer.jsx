import React, { useCallback, useEffect, useState } from 'react';
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

function encodeBase64(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

export default function ArtifactViewer({ runId, artifact, onClose, onSubmitted, onSubmitReady }) {
  const [content, setContent] = useState(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (artifact?.humanInputEditable && !artifact?.artifact_version_id) {
      setContent('');
      setLoading(false);
      return;
    }
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
  const editable = artifact.humanInputEditable === true;

  const submitHumanInput = useCallback(async () => {
    if (!editable) {
      return;
    }
    if (!artifact.humanInputGateId) {
      message.error('Gate context missing for submit');
      return;
    }
    if (!content || !content.trim()) {
      message.warning('Введите ответ');
      return;
    }
    setSubmitting(true);
    try {
      await apiRequest(`/gates/${artifact.humanInputGateId}/submit-input`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: artifact.humanInputExpectedGateVersion,
          artifacts: [
            {
              artifact_key: artifact.artifact_key,
              path: artifact.path,
              scope: artifact.scope || 'run',
              content_base64: encodeBase64(content),
            },
          ],
          comment: artifact.humanInputComment || 'submitted from run console',
        }),
      });
      message.success('Input submitted');
      await onSubmitted?.();
    } catch (err) {
      if (err.status === 409) {
        message.warning('Gate was modified, reloading...');
        await onSubmitted?.();
        return;
      }
      message.error(err.message || 'Не удалось отправить input');
    } finally {
      setSubmitting(false);
    }
  }, [
    editable,
    artifact,
    content,
    onSubmitted,
  ]);

  useEffect(() => {
    if (!onSubmitReady) {
      return undefined;
    }
    onSubmitReady(editable ? submitHumanInput : null);
    return () => onSubmitReady(null);
  }, [onSubmitReady, editable, submitHumanInput]);

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
            onChange={(value) => {
              if (editable) {
                setContent(value || '');
              }
            }}
            options={{
              readOnly: !editable,
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
