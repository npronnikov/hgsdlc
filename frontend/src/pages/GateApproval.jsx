import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Input, Row, Select, Tabs, Typography, message } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function GateApproval() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');
  const gateIdFromQuery = searchParams.get('gateId');
  const [run, setRun] = useState(null);
  const [gate, setGate] = useState(null);
  const [comment, setComment] = useState('');
  const [mode, setMode] = useState('keep_current_changes');
  const [submitting, setSubmitting] = useState(false);

  const load = async () => {
    if (!runId) {
      return;
    }
    try {
      const runData = await apiRequest(`/runs/${runId}`);
      const currentGate = runData?.current_gate || null;
      if (currentGate && currentGate.gate_kind === 'human_approval') {
        setRun(runData);
        setGate(currentGate);
      } else {
        message.warning('Для текущего run нет active human_approval gate');
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить gate');
    }
  };

  useEffect(() => {
    load();
  }, [runId, gateIdFromQuery]);

  const approve = async () => {
    if (!gate) {
      return;
    }
    setSubmitting(true);
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
      navigate(`/run-console?runId=${runId}`);
    } catch (err) {
      message.error(err.message || 'Не удалось approve gate');
      await load();
    } finally {
      setSubmitting(false);
    }
  };

  const rework = async () => {
    if (!gate) {
      return;
    }
    setSubmitting(true);
    try {
      await apiRequest(`/gates/${gate.gate_id}/request-rework`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          mode,
          comment,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Rework requested');
      navigate(`/run-console?runId=${runId}`);
    } catch (err) {
      message.error(err.message || 'Не удалось запросить rework');
      await load();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Human Approval Gate</Title>
        <StatusTag value={gate?.status || 'awaiting_decision'} />
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={6}>
          <Card title="Summary">
            <div>
              <Text className="muted">Reviewed artifacts</Text>
              <div className="mono">{JSON.stringify(gate?.payload?.reviewed_artifact_version_ids || [])}</div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Run</Text>
              <div className="mono">{runId || '—'}</div>
            </div>
            <div className="card-muted" style={{ marginTop: 12 }}>
              Node: <span className="mono">{gate?.node_id || '—'}</span>
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card>
            <Tabs
              defaultActiveKey="payload"
              items={[
                {
                  key: 'payload',
                  label: 'Gate payload',
                  children: (
                    <pre className="code-block">{JSON.stringify(gate?.payload || {}, null, 2)}</pre>
                  ),
                },
                {
                  key: 'run',
                  label: 'Run',
                  children: (
                    <pre className="code-block">{JSON.stringify(run || {}, null, 2)}</pre>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="Decision">
            <Text className="muted">Comment</Text>
            <Input.TextArea rows={5} style={{ marginTop: 8 }} value={comment} onChange={(e) => setComment(e.target.value)} />
            <Text className="muted" style={{ marginTop: 12, display: 'block' }}>Rework mode</Text>
            <Select
              style={{ width: '100%', marginTop: 8 }}
              value={mode}
              onChange={setMode}
              options={[
                { value: 'keep_current_changes', label: 'keep_current_changes' },
                { value: 'discard_current_changes', label: 'discard_current_changes' },
                { value: 'requirements', label: 'requirements' },
              ]}
            />
            <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
              <Button type="primary" onClick={approve} loading={submitting}>Approve</Button>
              <Button type="default" style={{ borderColor: '#d97706', color: '#d97706' }} onClick={rework} loading={submitting}>Rework</Button>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
