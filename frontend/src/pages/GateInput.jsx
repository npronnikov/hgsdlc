import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Input, Row, Select, Space, Typography, message, Divider } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

function extractGateContext(payload) {
  if (!payload) {
    return { contextArtifacts: [], inputArtifactContent: null, inputArtifactKey: null, outputArtifactKey: null, userInstructions: null };
  }
  return {
    contextArtifacts: payload.execution_context_artifacts || [],
    inputArtifactContent: payload.input_artifact_content || null,
    inputArtifactKey: payload.input_artifact_key || null,
    outputArtifactKey: payload.output_artifact_key || null,
    userInstructions: payload.user_instructions || null,
  };
}

function isEditInPlace(inputArtifactKey, outputArtifactKey) {
  return inputArtifactKey && outputArtifactKey && inputArtifactKey === outputArtifactKey;
}

export default function GateInput() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');
  const gateIdFromQuery = searchParams.get('gateId');
  const [run, setRun] = useState(null);
  const [gate, setGate] = useState(null);
  const [answers, setAnswers] = useState('');
  const [artifactKey, setArtifactKey] = useState('answers');
  const [artifactPath, setArtifactPath] = useState('answers.md');
  const [artifactScope, setArtifactScope] = useState('run');
  const [submitting, setSubmitting] = useState(false);
  const [initialized, setInitialized] = useState(false);

  const load = async () => {
    if (!runId) {
      return;
    }
    try {
      const runData = await apiRequest(`/runs/${runId}`);
      const currentGate = runData?.current_gate || null;
      if (currentGate && currentGate.gate_kind === 'human_input') {
        setRun(runData);
        setGate(currentGate);
        if (!initialized) {
          applyPayloadDefaults(currentGate.payload);
          setInitialized(true);
        }
      } else {
        message.warning('Для текущего run нет active human_input gate');
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить gate');
    }
  };

  const applyPayloadDefaults = (payload) => {
    const ctx = extractGateContext(payload);
    if (ctx.outputArtifactKey) {
      setArtifactKey(ctx.outputArtifactKey);
      setArtifactPath(ctx.outputArtifactKey + '.md');
    }
    if (isEditInPlace(ctx.inputArtifactKey, ctx.outputArtifactKey) && ctx.inputArtifactContent) {
      setAnswers(ctx.inputArtifactContent);
    }
  };

  useEffect(() => {
    load();
  }, [runId, gateIdFromQuery]);

  const encodeBase64 = (value) => {
    const bytes = new TextEncoder().encode(value);
    let binary = '';
    bytes.forEach((byte) => {
      binary += String.fromCharCode(byte);
    });
    return btoa(binary);
  };

  const submit = async () => {
    if (!gate) {
      return;
    }
    if (!answers.trim()) {
      message.warning('Введите ответы');
      return;
    }
    setSubmitting(true);
    try {
      await apiRequest(`/gates/${gate.gate_id}/submit-input`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          artifacts: [
            {
              artifact_key: artifactKey,
              path: artifactPath,
              scope: artifactScope,
              content_base64: encodeBase64(answers),
            },
          ],
          comment: 'submitted from gate input page',
        }),
      });
      message.success('Input submitted');
      navigate(`/run-console?runId=${runId}`);
    } catch (err) {
      message.error(err.message || 'Не удалось отправить input');
      await load();
    } finally {
      setSubmitting(false);
    }
  };

  const ctx = extractGateContext(gate?.payload);
  const editMode = isEditInPlace(ctx.inputArtifactKey, ctx.outputArtifactKey);

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Human Input Gate</Title>
        <StatusTag value={gate?.status || 'awaiting_input'} />
      </div>

      {ctx.userInstructions && (
        <Card style={{ marginBottom: 16 }}>
          <Title level={5}>Instructions</Title>
          <pre className="code-block">{ctx.userInstructions}</pre>
        </Card>
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          {ctx.contextArtifacts.length > 0 ? (
            ctx.contextArtifacts.map((artifact, idx) => (
              <Card key={artifact.artifact_version_id || idx} style={{ marginBottom: 16 }}>
                <Title level={5}>
                  Context: {artifact.artifact_key}
                </Title>
                {artifact.content ? (
                  <pre className="code-block" style={{ maxHeight: 500, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
                    {artifact.content}
                  </pre>
                ) : (
                  <Text type="secondary">Content not available (file too large or missing)</Text>
                )}
              </Card>
            ))
          ) : (
            <Card>
              <Title level={5}>Gate context</Title>
              <div style={{ marginTop: 12 }}>
                <Text className="muted">Run</Text>
                <div className="mono">{runId || '—'}</div>
              </div>
              <div style={{ marginTop: 12 }}>
                <Text className="muted">Node</Text>
                <div className="mono">{gate?.node_id || '—'}</div>
              </div>
              {gate?.payload && !ctx.inputArtifactContent && (
                <pre className="code-block" style={{ marginTop: 12 }}>
                  {JSON.stringify(gate.payload, null, 2)}
                </pre>
              )}
            </Card>
          )}

          {editMode && ctx.inputArtifactContent && (
            <Card style={{ marginTop: 16 }}>
              <Title level={5}>Original: {ctx.inputArtifactKey}</Title>
              <Text type="secondary">This is the original content you are editing</Text>
              <pre className="code-block" style={{ marginTop: 8, maxHeight: 400, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
                {ctx.inputArtifactContent}
              </pre>
            </Card>
          )}
        </Col>

        <Col xs={24} lg={12}>
          <Card>
            <Title level={5}>
              {editMode ? `Edit: ${ctx.outputArtifactKey}` : 'Answers'}
            </Title>
            <Text type="secondary">
              {editMode
                ? 'Edit the content below and submit'
                : 'Provide answers in markdown'}
            </Text>
            <Input.TextArea
              rows={12}
              style={{ marginTop: 12, fontFamily: 'monospace' }}
              value={answers}
              onChange={(e) => setAnswers(e.target.value)}
            />
            <Divider style={{ margin: '12px 0' }} />
            <Space style={{ width: '100%' }} direction="vertical" size={8}>
              <Input addonBefore="artifact_key" value={artifactKey} onChange={(e) => setArtifactKey(e.target.value)} />
              <Input addonBefore="path" value={artifactPath} onChange={(e) => setArtifactPath(e.target.value)} />
              <Select
                value={artifactScope}
                onChange={setArtifactScope}
                style={{ width: '100%' }}
                options={[
                  { value: 'run', label: 'run' },
                  { value: 'project', label: 'project' },
                ]}
              />
            </Space>
            <div className="card-muted" style={{ marginTop: 12 }}>
              Gate id: <span className="mono">{gate?.gate_id || '—'}</span>
            </div>
            <Button type="primary" style={{ marginTop: 12 }} onClick={submit} loading={submitting}>
              {editMode ? 'Submit edited content' : 'Submit answers'}
            </Button>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
