import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Input, Row, Select, Space, Typography, message, Divider } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

function extractGateContext(payload) {
  if (!payload) {
    return {
      contextArtifacts: [],
      humanInputArtifacts: [],
      inputArtifactContent: null,
      inputArtifactKey: null,
      outputArtifactKey: null,
      userInstructions: null,
    };
  }
  return {
    contextArtifacts: payload.execution_context_artifacts || [],
    humanInputArtifacts: payload.human_input_artifacts || [],
    inputArtifactContent: payload.input_artifact_content || null,
    inputArtifactKey: payload.input_artifact_key || null,
    outputArtifactKey: payload.output_artifact_key || null,
    userInstructions: payload.user_instructions || null,
  };
}

export default function GateInput() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');
  const gateIdFromQuery = searchParams.get('gateId');
  const [run, setRun] = useState(null);
  const [gate, setGate] = useState(null);
  const [answers, setAnswers] = useState('');
  const [artifactKey, setArtifactKey] = useState('');
  const [artifactPath, setArtifactPath] = useState('');
  const [artifactScope, setArtifactScope] = useState('run');
  const [selectedArtifactPath, setSelectedArtifactPath] = useState('');
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
        message.warning('No active human_input gate for current run');
      }
    } catch (err) {
      message.error(err.message || 'Failed to load gate');
    }
  };

  const applyPayloadDefaults = (payload) => {
    const ctx = extractGateContext(payload);
    const firstEditable = ctx.humanInputArtifacts?.[0] || null;
    if (firstEditable) {
      setArtifactKey(firstEditable.artifact_key || '');
      setArtifactPath(firstEditable.path || '');
      setArtifactScope(firstEditable.scope || 'run');
      setSelectedArtifactPath(firstEditable.path || '');
      setAnswers(firstEditable.content || '');
      return;
    }
    if (ctx.outputArtifactKey) {
      setArtifactKey(ctx.outputArtifactKey);
      setArtifactPath(ctx.outputArtifactKey + '.md');
      setArtifactScope('run');
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
      message.warning('Enter answers');
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
      message.error(err.message || 'Failed to submit input');
      await load();
    } finally {
      setSubmitting(false);
    }
  };

  const ctx = extractGateContext(gate?.payload);
  const editableArtifacts = ctx.humanInputArtifacts || [];
  const selectedEditable = editableArtifacts.find((item) => item.path === selectedArtifactPath) || editableArtifacts[0] || null;
  const editMode = true;

  useEffect(() => {
    if (!selectedEditable) {
      return;
    }
    setArtifactKey(selectedEditable.artifact_key || '');
    setArtifactPath(selectedEditable.path || '');
    setArtifactScope(selectedEditable.scope || 'run');
    setAnswers(selectedEditable.content || '');
  }, [selectedEditable?.path, gate?.gate_id]);

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
          {editableArtifacts.length > 0 ? (
            editableArtifacts.map((artifact, idx) => (
              <Card key={artifact.artifact_version_id || idx} style={{ marginBottom: 16 }}>
                <Title level={5}>
                  Context: {artifact.artifact_key}
                </Title>
                <Text type="secondary">source: <span className="mono">{artifact.source_node_id || '—'}</span></Text>
                {artifact.content ? (
                  <pre className="code-block" style={{ maxHeight: 500, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
                    {artifact.content}
                  </pre>
                ) : (
                  <Text type="secondary">Content not available</Text>
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

          {ctx.contextArtifacts.length > 0 && (
            <Card style={{ marginTop: 16 }}>
              <Title level={5}>Additional context artifacts</Title>
              <pre className="code-block" style={{ marginTop: 8, maxHeight: 400, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
                {JSON.stringify(ctx.contextArtifacts, null, 2)}
              </pre>
            </Card>
          )}
        </Col>

        <Col xs={24} lg={12}>
          <Card>
            <Title level={5}>
              {editMode ? `Edit: ${artifactKey || 'artifact'}` : 'Answers'}
            </Title>
            <Text type="secondary">
              {editMode
                ? 'Edit the content below and submit'
                : 'Provide answers in markdown'}
            </Text>
            {editableArtifacts.length > 1 && (
              <Select
                style={{ width: '100%', marginTop: 12 }}
                value={selectedEditable?.path}
                onChange={setSelectedArtifactPath}
                options={editableArtifacts.map((artifact) => ({
                  value: artifact.path,
                  label: `${artifact.artifact_key} (${artifact.path})`,
                }))}
              />
            )}
            <Input.TextArea
              rows={12}
              style={{ marginTop: 12, fontFamily: 'monospace' }}
              value={answers}
              onChange={(e) => setAnswers(e.target.value)}
            />
            <Divider style={{ margin: '12px 0' }} />
            <Text type="secondary">artifact_key: <span className="mono">{artifactKey || '—'}</span></Text>
            <br />
            <Text type="secondary">path: <span className="mono">{artifactPath || '—'}</span></Text>
            <br />
            <Text type="secondary">scope: <span className="mono">{artifactScope || '—'}</span></Text>
            <div className="card-muted" style={{ marginTop: 12 }}>
              Gate id: <span className="mono">{gate?.gate_id || '—'}</span>
            </div>
            <Button type="default" style={{ marginTop: 12 }} onClick={submit} loading={submitting}>
              {editMode ? 'Submit edited content' : 'Submit answers'}
            </Button>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
