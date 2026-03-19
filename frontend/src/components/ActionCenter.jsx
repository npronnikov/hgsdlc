import React, { useState } from 'react';
import { Button, Card, Input, Radio, Typography, message } from 'antd';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

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
  const [reworkMode, setReworkMode] = useState('discard');
  const [activeAction, setActiveAction] = useState(null);
  const reworkDiscardAvailable = gate?.payload?.rework_discard_available !== false;

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
      message.error(err.message || 'Не удалось approve');
    } finally {
      setActiveAction(null);
    }
  };

  const doRework = async () => {
    setActiveAction('rework');
    try {
      await apiRequest(`/gates/${gate.gate_id}/request-rework`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          mode: reworkDiscardAvailable ? reworkMode : 'keep',
          comment,
          instruction,
          reviewed_artifact_version_ids: [],
        }),
      });
      message.success('Rework requested');
      setComment('');
      setInstruction('');
      setReworkMode('discard');
      onComplete();
    } catch (err) {
      if (err.status === 409) {
        message.warning('Gate was modified, reloading...');
        onComplete();
        return;
      }
      message.error(err.message || 'Не удалось запросить rework');
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
        <Text className="muted">Комментарий</Text>
        <Input.TextArea
          rows={3}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="Необязательный комментарий"
          style={{ marginTop: 4 }}
        />
      </div>
      <div>
        <Text className="muted">Инструкция для доработки</Text>
        <Input.TextArea
          rows={3}
          value={instruction}
          onChange={(e) => setInstruction(e.target.value)}
          placeholder="Что именно нужно переделать"
          style={{ marginTop: 4 }}
        />
      </div>
      {reworkDiscardAvailable ? (
        <div>
          <Text className="muted">Changes handling</Text>
          <Radio.Group
            value={reworkMode}
            onChange={(event) => setReworkMode(event.target.value)}
            optionType="button"
            buttonStyle="solid"
            style={{ display: 'block', marginTop: 4 }}
          >
            <Radio.Button value="keep">Keep changes</Radio.Button>
            <Radio.Button value="discard">Discard changes</Radio.Button>
          </Radio.Group>
        </div>
      ) : (
        <Text type="secondary">Текущие изменения будут сохранены</Text>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 4 }}>
        <Button style={{ borderColor: '#16a34a', color: '#16a34a' }} onClick={approve} loading={activeAction === 'approve'} disabled={activeAction !== null}>Принять</Button>
        <Button
          style={{ borderColor: '#d97706', color: '#d97706' }}
          onClick={rework}
          loading={activeAction === 'rework'}
          disabled={activeAction !== null || !instruction.trim()}
        >
          Доработать
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
        Ответить
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
