import React, { useEffect, useState } from 'react';
import { Button, Card, Input, Switch, Tag, Typography, message } from 'antd';
import { apiRequest } from '../api/request.js';

const { Text } = Typography;

function resolveDiscardUnavailableReason(reasonCode) {
  switch (reasonCode) {
    case 'rework_target_missing':
      return 'Rework target node is missing.';
    case 'rework_target_kind_unsupported':
      return 'Rework target must be an AI/Command node.';
    case 'rework_target_checkpoint_disabled':
      return 'Rollback before rework is unavailable because checkpoint creation is disabled on the target node.';
    case 'target_checkpoint_not_found':
      return 'Checkpoint is not available yet for the rework target node.';
    default:
      return 'Discard to checkpoint is currently unavailable.';
  }
}

function extractGateContext(payload) {
  if (!payload) {
    return { inputArtifactKey: null, outputArtifactKey: null, userInstructions: null, humanInputArtifacts: [] };
  }
  return {
    inputArtifactKey: payload.input_artifact_key || null,
    outputArtifactKey: payload.output_artifact_key || null,
    userInstructions: payload.user_instructions || null,
    humanInputArtifacts: payload.human_input_artifacts || [],
  };
}

function ApprovalForm({ gate, onComplete }) {
  const [comment, setComment] = useState('');
  const [instruction, setInstruction] = useState('');
  const [activeAction, setActiveAction] = useState(null);
  const [keepChanges, setKeepChanges] = useState(gate?.payload?.rework_keep_changes !== false);
  const keepChangesSelectable = gate?.payload?.rework_keep_changes_selectable === true;
  const isDiscardPolicy = !keepChanges;
  const reworkDiscardAvailable = gate?.payload?.rework_discard_available === true;
  const reworkDiscardBlocked = isDiscardPolicy && !reworkDiscardAvailable;
  const reworkDiscardUnavailableReason = gate?.payload?.rework_discard_unavailable_reason || '';

  useEffect(() => {
    setKeepChanges(gate?.payload?.rework_keep_changes !== false);
  }, [gate?.gate_id, gate?.resource_version, gate?.payload?.rework_keep_changes]);

  const approve = async () => {
    setActiveAction('approve');
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
      setComment('');
      setInstruction('');
      onComplete();
    } catch (err) {
      if (err.status === 409) {
        message.warning('Gate was modified, reloading...');
        onComplete();
        return;
      }
      message.error(err.message || 'Failed to approve');
    } finally {
      setActiveAction(null);
    }
  };

  const doRework = async () => {
    if (reworkDiscardBlocked) {
      message.warning(resolveDiscardUnavailableReason(reworkDiscardUnavailableReason));
      return;
    }
    setActiveAction('rework');
    try {
      await apiRequest(`/gates/${gate.gate_id}/request-rework`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          comment,
          instruction,
          keep_changes: keepChanges,
          session_policy: 'new_session',
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Rework requested');
      setComment('');
      setInstruction('');
      onComplete();
    } catch (err) {
      if (err.status === 409) {
        message.warning('Gate was modified, reloading...');
        onComplete();
        return;
      }
      message.error(err.message || 'Failed to request rework');
    } finally {
      setActiveAction(null);
    }
  };

  const rework = () => {
    doRework();
  };

  return (
    <div className="action-center-form">
      <div>
        <Text className="muted">Comment</Text>
        <Input.TextArea
          rows={3}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="Optional comment"
          style={{ marginTop: 4 }}
        />
      </div>
      <div>
        <Text className="muted">Rework instruction</Text>
        <Input.TextArea
          rows={3}
          value={instruction}
          onChange={(e) => setInstruction(e.target.value)}
          placeholder="What exactly needs to be revised"
          style={{ marginTop: 4 }}
        />
      </div>
      <div>
        <Text className="muted">Changes handling</Text>
        <div style={{ marginTop: 4 }}>
          <Tag color={isDiscardPolicy ? 'orange' : 'blue'}>
            {isDiscardPolicy ? 'Discard to checkpoint' : 'Keep changes'}
          </Tag>
        </div>
        <div style={{ marginTop: 6 }}>
          <Switch
            checked={keepChanges}
            onChange={setKeepChanges}
            disabled={!keepChangesSelectable || activeAction !== null}
            checkedChildren="Keep"
            unCheckedChildren="Discard"
          />
        </div>
        {reworkDiscardBlocked && (
          <Text type="danger">{resolveDiscardUnavailableReason(reworkDiscardUnavailableReason)}</Text>
        )}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 4 }}>
        <Button style={{ borderColor: '#16a34a', color: '#16a34a' }} onClick={approve} loading={activeAction === 'approve'} disabled={activeAction !== null}>Approve</Button>
        <Button
          type="default"
          danger
          onClick={rework}
          loading={activeAction === 'rework'}
          disabled={activeAction !== null || !instruction.trim() || reworkDiscardBlocked}
        >
          Request rework
        </Button>
      </div>
    </div>
  );
}

function InputForm({ gate, onOpenArtifactEditor }) {
  const ctx = extractGateContext(gate?.payload);
  const [opening, setOpening] = useState(false);

  const openEditor = async () => {
    if (!gate) {
      return;
    }
    setOpening(true);
    try {
      await onOpenArtifactEditor?.({
        gateId: gate.gate_id,
        expectedGateVersion: gate.resource_version,
        nodeId: gate.node_id,
        humanInputArtifacts: ctx.humanInputArtifacts,
      });
    } finally {
      setOpening(false);
    }
  };

  return (
    <div className="action-center-form">
      {ctx.userInstructions && (
        <div>
          <Text className="muted">Instructions</Text>
          <pre className="code-block" style={{ fontSize: 12, maxHeight: 120, overflow: 'auto', marginTop: 4 }}>
            {ctx.userInstructions}
          </pre>
        </div>
      )}
      <Button type="default" onClick={openEditor} loading={opening} style={{ marginTop: 8 }}>
        Reply
      </Button>
    </div>
  );
}

export default function ActionCenter({ run, onActionComplete, onOpenArtifactEditor }) {
  const gate = run?.current_gate;

  if (!gate) {
    return (
      <Card size="small" className="action-center">
        <Text type="secondary">No active gate</Text>
      </Card>
    );
  }

  const isApproval = gate.gate_kind === 'human_approval';
  const isInput = gate.gate_kind === 'human_input';

  return (
    <Card
      size="small"
      className="action-center"
      title={isApproval ? 'Approval' : isInput ? 'Human Input' : gate.gate_kind}
      extra={<span className="mono" style={{ fontSize: 11 }}>{gate.node_id}</span>}
    >
      {isApproval && <ApprovalForm gate={gate} onComplete={onActionComplete} />}
      {isInput && <InputForm gate={gate} onOpenArtifactEditor={onOpenArtifactEditor} />}
      {!isApproval && !isInput && (
        <Text type="secondary">Unknown gate kind: {gate.gate_kind}</Text>
      )}
    </Card>
  );
}
