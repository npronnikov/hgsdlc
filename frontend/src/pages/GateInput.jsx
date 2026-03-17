import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Input, Row, Select, Space, Typography, message } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

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
      } else {
        message.warning('Для текущего run нет active human_input gate');
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить gate');
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

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Human Input Gate</Title>
        <StatusTag value={gate?.status || 'awaiting_input'} />
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card>
            <Title level={5}>Questions artifact</Title>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Run</Text>
              <div className="mono">{runId || '—'}</div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Node</Text>
              <div className="mono">{gate?.node_id || '—'}</div>
            </div>
            <pre className="code-block" style={{ marginTop: 12 }}>
{JSON.stringify(gate?.payload || {}, null, 2)}
            </pre>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card>
            <Title level={5}>Answers</Title>
            <Text type="secondary">Provide answers in markdown</Text>
            <Input.TextArea rows={8} style={{ marginTop: 12 }} value={answers} onChange={(e) => setAnswers(e.target.value)} />
            <Space style={{ marginTop: 12 }} direction="vertical" size={8}>
              <Input addonBefore="artifact_key" value={artifactKey} onChange={(e) => setArtifactKey(e.target.value)} />
              <Input addonBefore="path" value={artifactPath} onChange={(e) => setArtifactPath(e.target.value)} />
              <Select
                value={artifactScope}
                onChange={setArtifactScope}
                options={[
                  { value: 'run', label: 'run' },
                  { value: 'project', label: 'project' },
                ]}
              />
            </Space>
            <div className="card-muted" style={{ marginTop: 12 }}>
              Gate id: <span className="mono">{gate?.gate_id || '—'}</span>
            </div>
            <Button type="primary" style={{ marginTop: 12 }} onClick={submit} loading={submitting}>Submit answers</Button>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
