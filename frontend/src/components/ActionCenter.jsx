import React, { useEffect, useState } from 'react';
import { Button, Card, Divider, Input, Modal, Radio, Select, Space, Typography, message } from 'antd';
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

function encodeBase64(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
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

function InputForm({ gate, onComplete }) {
  const ctx = extractGateContext(gate?.payload);
  const editMode = ctx.inputArtifactKey && ctx.outputArtifactKey && ctx.inputArtifactKey === ctx.outputArtifactKey;

  const [answers, setAnswers] = useState('');
  const [artifactKey, setArtifactKey] = useState('answers');
  const [artifactPath, setArtifactPath] = useState('answers.md');
  const [artifactScope, setArtifactScope] = useState('run');
  const [submitting, setSubmitting] = useState(false);
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    if (initialized || !gate) {
      return;
    }
    if (ctx.outputArtifactKey) {
      setArtifactKey(ctx.outputArtifactKey);
      setArtifactPath(ctx.outputArtifactKey + '.md');
    }
    if (editMode && ctx.inputArtifactContent) {
      setAnswers(ctx.inputArtifactContent);
    }
    setInitialized(true);
  }, [gate?.gate_id]);

  const submit = async () => {
    if (!answers.trim()) {
      message.warning('Введите ответ');
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
          comment: 'submitted from run console',
        }),
      });
      message.success('Input submitted');
      setAnswers('');
      setInitialized(false);
      onComplete();
    } catch (err) {
      if (err.status === 409) {
        message.warning('Gate was modified, reloading...');
        onComplete();
        return;
      }
      message.error(err.message || 'Не удалось отправить input');
    } finally {
      setSubmitting(false);
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

      {ctx.contextArtifacts.length > 0 && (
        <div>
          <Text className="muted">Context artifacts</Text>
          {ctx.contextArtifacts.map((artifact, idx) => (
            <div key={artifact.artifact_version_id || idx} style={{ marginTop: 4 }}>
              <Text strong className="mono" style={{ fontSize: 12 }}>{artifact.artifact_key}</Text>
              {artifact.content && (
                <pre className="code-block" style={{ fontSize: 11, maxHeight: 150, overflow: 'auto', marginTop: 2 }}>
                  {artifact.content}
                </pre>
              )}
            </div>
          ))}
        </div>
      )}

      {editMode && ctx.inputArtifactContent && (
        <div>
          <Text className="muted">Original: {ctx.inputArtifactKey}</Text>
          <pre className="code-block" style={{ fontSize: 11, maxHeight: 120, overflow: 'auto', marginTop: 4 }}>
            {ctx.inputArtifactContent}
          </pre>
        </div>
      )}

      <div>
        <Text className="muted">{editMode ? `Edit: ${ctx.outputArtifactKey}` : 'Ответ'}</Text>
        <Input.TextArea
          rows={6}
          style={{ marginTop: 4, fontFamily: 'monospace', fontSize: 12 }}
          value={answers}
          onChange={(e) => setAnswers(e.target.value)}
          placeholder={editMode ? 'Edit content and submit' : 'Markdown ответ'}
        />
      </div>

      <Divider style={{ margin: '8px 0' }} />

      <Space style={{ width: '100%' }} direction="vertical" size={4}>
        <Input size="small" addonBefore="key" value={artifactKey} onChange={(e) => setArtifactKey(e.target.value)} />
        <Input size="small" addonBefore="path" value={artifactPath} onChange={(e) => setArtifactPath(e.target.value)} />
        <Select
          size="small"
          value={artifactScope}
          onChange={setArtifactScope}
          style={{ width: '100%' }}
          options={[
            { value: 'run', label: 'run' },
            { value: 'project', label: 'project' },
          ]}
        />
      </Space>

      <Button type="primary" onClick={submit} loading={submitting} style={{ marginTop: 8 }}>
        {editMode ? 'Submit edited content' : 'Submit input'}
      </Button>
    </div>
  );
}

export default function ActionCenter({ run, onActionComplete }) {
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
      {isInput && <InputForm gate={gate} onComplete={onActionComplete} />}
      {!isApproval && !isInput && (
        <Text type="secondary">Unknown gate kind: {gate.gate_kind}</Text>
      )}
    </Card>
  );
}
