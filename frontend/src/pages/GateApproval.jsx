import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Input, Row, Tabs, Tag, Typography, message } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

function resolveDiscardUnavailableReason(reasonCode) {
  switch (reasonCode) {
    case 'flow_policy_keep_changes':
      return 'Flow policy is configured to keep changes.';
    case 'rework_target_missing':
      return 'Rework target node is missing.';
    case 'rework_target_kind_unsupported':
      return 'Rework target must be an AI/Command node.';
    case 'rework_target_checkpoint_disabled':
      return 'Target node must have checkpoint_before_run=true.';
    case 'target_checkpoint_not_found':
      return 'Checkpoint is not available yet for the rework target node.';
    default:
      return 'Discard to checkpoint is currently unavailable.';
  }
}

export default function GateApproval() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');
  const gateIdFromQuery = searchParams.get('gateId');
  const [run, setRun] = useState(null);
  const [gate, setGate] = useState(null);
  const [comment, setComment] = useState('');
  const [instruction, setInstruction] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const reworkMode = gate?.payload?.rework_mode
    || (gate?.payload?.rework_keep_changes === false ? 'discard' : 'keep');
  const isDiscardPolicy = reworkMode === 'discard';
  const reworkDiscardAvailable = gate?.payload?.rework_discard_available === true;
  const reworkDiscardBlocked = isDiscardPolicy && !reworkDiscardAvailable;
  const reworkDiscardUnavailableReason = gate?.payload?.rework_discard_unavailable_reason || '';

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
        message.warning('No active human_approval gate for current run');
      }
    } catch (err) {
      message.error(err.message || 'Failed to load gate');
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
      message.error(err.message || 'Failed to approve gate');
      await load();
    } finally {
      setSubmitting(false);
    }
  };

  const rework = async () => {
    if (!gate) {
      return;
    }
    if (reworkDiscardBlocked) {
      message.warning(resolveDiscardUnavailableReason(reworkDiscardUnavailableReason));
      return;
    }
    setSubmitting(true);
    try {
      await apiRequest(`/gates/${gate.gate_id}/request-rework`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          comment,
          instruction,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Rework requested');
      navigate(`/run-console?runId=${runId}`);
    } catch (err) {
      message.error(err.message || 'Failed to request rework');
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
            <Text className="muted" style={{ marginTop: 12, display: 'block' }}>Instruction</Text>
            <Input.TextArea rows={5} style={{ marginTop: 8 }} value={instruction} onChange={(e) => setInstruction(e.target.value)} />
            <Text className="muted" style={{ marginTop: 12, display: 'block' }}>Changes handling</Text>
            <Tag color={isDiscardPolicy ? 'orange' : 'blue'} style={{ marginTop: 8 }}>
              {isDiscardPolicy ? 'Discard to checkpoint' : 'Keep changes'}
            </Tag>
            {reworkDiscardBlocked && (
              <Text type="danger" style={{ marginTop: 12, display: 'block' }}>
                {resolveDiscardUnavailableReason(reworkDiscardUnavailableReason)}
              </Text>
            )}
            <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
              <Button type="default" onClick={approve} loading={submitting}>Approve</Button>
              <Button
                type="default"
                style={{ borderColor: '#d97706', color: '#d97706' }}
                onClick={rework}
                loading={submitting}
                disabled={reworkDiscardBlocked}
              >
                Rework
              </Button>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
