import React, { useEffect, useMemo, useState } from 'react';
import {
  Background,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  useNodesState,
} from 'reactflow';
import 'reactflow/dist/style.css';
import {
  Button,
  Card,
  Dropdown,
  Divider,
  Input,
  List,
  Select,
  Space,
  Typography,
  message,
} from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import StatusTag from '../components/StatusTag.jsx';
import { rules, skills } from '../data/mock.js';
import { useParams } from 'react-router-dom';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

function FlowNode({ data, selected }) {
  return (
    <div className={`flow-node ${data.variant} ${selected ? 'is-selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="flow-node-header">
        <div>
          <div className="flow-node-title">{data.title}</div>
          <div className="flow-node-meta">{data.typeLabel}</div>
        </div>
        {data.isStart && <span className="flow-node-start">Start</span>}
      </div>
      <div className="flow-node-id">{data.id}</div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

const nodeTypes = { flowNode: FlowNode };

const initialFlow = {
  title: 'Flow изменений',
  flowId: 'feature-change-flow',
  description: 'Flow реализации изменений с этапом согласования.',
  startRole: 'PRODUCT_OWNER',
  approverRole: 'TECH_APPROVER',
  startNodeId: 'intake-analysis',
  ruleRefs: ['project-rule@1.0.2'],
};

const emptyFlow = {
  title: '',
  flowId: '',
  description: '',
  startRole: '',
  approverRole: '',
  startNodeId: '',
  ruleRefs: [],
};

const initialNodes = [
  {
    id: 'intake-analysis',
    type: 'flowNode',
    position: { x: 40, y: 80 },
    data: {
      id: 'intake-analysis',
      title: 'Анализ запроса',
      typeLabel: 'AI-исполнитель',
      variant: 'executor ai',
      nodeType: 'executor',
      executorKind: 'AI',
      instruction: 'Проанализировать запрос и подготовить уточняющие вопросы.',
      inputs: ['feature-request'],
      outputs: ['feature-analysis', 'questions'],
      allowedOutcomes: ['need_more_input', 'ready_for_review'],
      outcomeRoutes: {
        need_more_input: 'collect-answers',
        ready_for_review: 'approve-requirements',
      },
      skillRefs: ['update-requirements@1.2.0'],
      isStart: true,
    },
  },
  {
    id: 'collect-answers',
    type: 'flowNode',
    position: { x: 360, y: 40 },
    data: {
      id: 'collect-answers',
      title: 'Сбор ответов',
      typeLabel: 'Гейт ввода',
      variant: 'gate input',
      nodeType: 'gate',
      gateKind: 'human_input',
      onSubmit: 'process-answers',
      inputs: ['questions.md'],
      outputs: ['answers.md'],
    },
  },
  {
    id: 'process-answers',
    type: 'flowNode',
    position: { x: 660, y: 80 },
    data: {
      id: 'process-answers',
      title: 'Обработать ответы',
      typeLabel: 'AI-исполнитель',
      variant: 'executor ai',
      nodeType: 'executor',
      executorKind: 'AI',
      instruction: 'Обновить требования на основе ответов.',
      inputs: ['answers.md'],
      outputs: ['requirements-draft.md'],
      onSuccess: 'approve-requirements',
      skillRefs: ['update-requirements@1.2.0'],
    },
  },
  {
    id: 'approve-requirements',
    type: 'flowNode',
    position: { x: 980, y: 80 },
    data: {
      id: 'approve-requirements',
      title: 'Согласовать требования',
      typeLabel: 'Гейт согласования',
      variant: 'gate approval',
      nodeType: 'gate',
      gateKind: 'human_approval',
      onApprove: 'publish-summary',
      onReject: 'close-run',
      onReworkRoutes: {
        keep_workspace: 'process-answers',
        discard_uncommitted: 'intake-analysis',
      },
      inputs: ['requirements-draft.md'],
      outputs: ['approval-comment.md'],
    },
  },
  {
    id: 'publish-summary',
    type: 'flowNode',
    position: { x: 1280, y: 80 },
    data: {
      id: 'publish-summary',
      title: 'Публикация итогов',
      typeLabel: 'Внешняя команда',
      variant: 'executor command',
      nodeType: 'executor',
      executorKind: 'External Command',
      commandSpec: 'maven_test',
      onSuccess: 'close-run',
    },
  },
  {
    id: 'close-run',
    type: 'flowNode',
    position: { x: 1560, y: 80 },
    data: {
      id: 'close-run',
      title: 'Завершить запуск',
      typeLabel: 'Внешняя команда',
      variant: 'executor command',
      nodeType: 'executor',
      executorKind: 'External Command',
      commandSpec: 'git_commit',
      onSuccess: null,
    },
  },
];

function buildEdges(nodes) {
  const edges = [];
  const nodeIds = new Set(nodes.map((node) => node.id));
  const addEdge = (source, target, label, routeType) => {
    if (!source || !target || !nodeIds.has(target)) {
      return;
    }
    edges.push({
      id: `${source}-${target}-${label}`,
      source,
      target,
      label,
      type: 'smoothstep',
      markerEnd: { type: MarkerType.ArrowClosed },
      className: `edge-${routeType}`,
      data: { routeType },
    });
  };

  nodes.forEach((node) => {
    const data = node.data || {};
    if (data.onSuccess) {
      addEdge(node.id, data.onSuccess, 'on_success', 'main');
    }
    if (data.onSubmit) {
      addEdge(node.id, data.onSubmit, 'on_submit', 'main');
    }
    if (data.onApprove) {
      addEdge(node.id, data.onApprove, 'on_approve', 'main');
    }
    if (data.onReject) {
      addEdge(node.id, data.onReject, 'on_reject', 'outcome');
    }
    if (data.outcomeRoutes) {
      Object.entries(data.outcomeRoutes).forEach(([outcome, target]) => {
        addEdge(node.id, target, `outcome: ${outcome}`, 'outcome');
      });
    }
    if (data.onReworkRoutes) {
      Object.entries(data.onReworkRoutes).forEach(([mode, target]) => {
        addEdge(node.id, target, `rework: ${mode}`, 'rework');
      });
    }
  });

  return edges;
}

function reorder(list, index, direction) {
  const next = [...list];
  const targetIndex = index + direction;
  if (targetIndex < 0 || targetIndex >= next.length) {
    return next;
  }
  const [item] = next.splice(index, 1);
  next.splice(targetIndex, 0, item);
  return next;
}

function validateFlow(nodes, meta) {
  const errors = [];
  const nodeIds = nodes.map((node) => node.id);
  const uniqueIds = new Set(nodeIds);
  if (nodes.length === 0) {
    errors.push('Flow не содержит нод.');
  }
  if (!meta.startNodeId) {
    errors.push('start_node_id не задан.');
  } else if (!uniqueIds.has(meta.startNodeId)) {
    errors.push(`start_node_id не найден: ${meta.startNodeId}`);
  }
  if (uniqueIds.size !== nodeIds.length) {
    const seen = new Set();
    nodeIds.forEach((id) => {
      if (seen.has(id)) {
        errors.push(`Дублирующийся ID ноды: ${id}`);
      }
      seen.add(id);
    });
  }

  const publishedRules = new Set(rules.filter((rule) => rule.status === 'published').map((rule) => rule.canonical));
  const publishedSkills = new Set(skills.filter((skill) => skill.status === 'published').map((skill) => skill.canonical));
  meta.ruleRefs.forEach((ref) => {
    if (!publishedRules.has(ref)) {
      errors.push(`Rule ref не опубликован: ${ref}`);
    }
  });

  nodes.forEach((node) => {
    const data = node.data || {};
    if (data.skillRefs && data.skillRefs.length > 0 && data.executorKind !== 'AI') {
      errors.push(`skill_refs разрешены только для AI нод: ${node.id}`);
    }
    if (data.skillRefs) {
      data.skillRefs.forEach((ref) => {
        if (!publishedSkills.has(ref)) {
        errors.push(`Skill ref не опубликован: ${ref}`);
        }
      });
    }

    const transitions = [];
    if (data.onSuccess) transitions.push(['on_success', data.onSuccess]);
    if (data.onSubmit) transitions.push(['on_submit', data.onSubmit]);
    if (data.onApprove) transitions.push(['on_approve', data.onApprove]);
    if (data.onReject) transitions.push(['on_reject', data.onReject]);
    if (data.outcomeRoutes) {
      Object.entries(data.outcomeRoutes).forEach(([key, value]) => transitions.push([`outcome:${key}`, value]));
    }
    if (data.onReworkRoutes) {
      Object.entries(data.onReworkRoutes).forEach(([key, value]) => transitions.push([`rework:${key}`, value]));
    }
    transitions.forEach(([label, target]) => {
      if (target && !uniqueIds.has(target)) {
        errors.push(`Невалидный переход ${label} из ${node.id} -> ${target}`);
      }
    });
  });

  if (meta.startNodeId && uniqueIds.has(meta.startNodeId)) {
    const edges = buildEdges(nodes);
    const adjacency = new Map();
    edges.forEach((edge) => {
      if (!adjacency.has(edge.source)) adjacency.set(edge.source, []);
      adjacency.get(edge.source).push(edge.target);
    });
    const visited = new Set([meta.startNodeId]);
    const queue = [meta.startNodeId];
    while (queue.length) {
      const current = queue.shift();
      const nextNodes = adjacency.get(current) || [];
      nextNodes.forEach((next) => {
        if (!visited.has(next)) {
          visited.add(next);
          queue.push(next);
        }
      });
    }
    nodeIds.forEach((id) => {
      if (!visited.has(id)) {
        errors.push(`Недостижимая нода: ${id}`);
      }
    });
  }

  return errors;
}

export default function FlowEditor() {
  const { flowId } = useParams();
  const [flowMeta, setFlowMeta] = useState(initialFlow);
  const [resourceVersion, setResourceVersion] = useState(0);
  const [flowVersion, setFlowVersion] = useState('0.1');
  const [currentStatus, setCurrentStatus] = useState('');
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [selectedNodeId, setSelectedNodeId] = useState(initialNodes[0].id);
  const isCreateMode = flowId === 'create';
  const flowVersionLabel = currentStatus === 'draft' || isCreateMode
    ? 'черновик'
    : (flowVersion || '0.0.0');
  const edges = useMemo(() => buildEdges(nodes), [nodes]);

  const selectedNode = nodes.find((node) => node.id === selectedNodeId);
  const validationErrors = validateFlow(nodes, flowMeta);

  const ruleOptions = rules.map((rule) => ({
    value: rule.canonical,
    label: `${rule.name} · ${rule.version}`,
    disabled: rule.status !== 'published',
  }));

  const skillOptions = skills.map((skill) => ({
    value: skill.canonical,
    label: `${skill.name} · ${skill.version}`,
    disabled: skill.status !== 'published',
  }));

  const updateSelectedNode = (updates) => {
    if (!selectedNodeId) {
      return;
    }
    setNodes((prev) =>
      prev.map((node) =>
        node.id === selectedNodeId
          ? { ...node, data: { ...node.data, ...updates } }
          : node
      )
    );
  };

  const updateFlowMeta = (updates) => {
    setFlowMeta((prev) => ({ ...prev, ...updates }));
  };

  const loadFlow = async (id) => {
    try {
      const data = await apiRequest(`/flows/${id}`);
      setFlowMeta((prev) => ({
        ...prev,
        flowId: data.flow_id || id,
        title: data.title || prev.title,
        description: data.description || prev.description,
        startRole: data.start_role || prev.startRole,
        approverRole: data.approver_role || prev.approverRole,
        startNodeId: data.start_node_id || prev.startNodeId,
        ruleRefs: data.rule_refs || [],
      }));
      setFlowVersion(data.version || flowVersion);
      setCurrentStatus(data.status || '');
      setResourceVersion(data.resource_version ?? 0);
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить Flow');
    }
  };

  const buildFlowYaml = () => {
    const version = flowVersion || '0.1';
    const canonicalName = `${flowMeta.flowId}@${version}`;
    const lines = [
      `id: ${flowMeta.flowId}`,
      `version: ${version}`,
      `canonical_name: ${canonicalName}`,
      `title: ${flowMeta.title}`,
      `description: ${flowMeta.description || ''}`,
      `start_role: ${flowMeta.startRole}`,
      `approver_role: ${flowMeta.approverRole}`,
      `start_node_id: ${flowMeta.startNodeId}`,
    ];
    if (flowMeta.ruleRefs.length === 0) {
      lines.push('rule_refs: []');
    } else {
      lines.push('rule_refs:');
      flowMeta.ruleRefs.forEach((ref) => lines.push(`  - ${ref}`));
    }
    lines.push('');
    lines.push('nodes:');
    nodes.forEach((node) => {
      const data = node.data || {};
      lines.push(`  - id: ${node.id}`);
      lines.push(`    type: ${data.nodeType || 'executor'}`);
      if (data.executorKind) {
        lines.push(`    executor_kind: ${data.executorKind}`);
      }
      if (data.gateKind) {
        lines.push(`    gate_kind: ${data.gateKind}`);
      }
      if (data.skillRefs && data.skillRefs.length > 0) {
        lines.push('    skill_refs:');
        data.skillRefs.forEach((ref) => lines.push(`      - ${ref}`));
      }
      if (data.instruction) {
        lines.push('    instruction: |');
        data.instruction.split('\n').forEach((line) => lines.push(`      ${line}`));
      }
      if (data.inputs && data.inputs.length > 0) {
        lines.push('    inputs:');
        data.inputs.forEach((input) => lines.push(`      - ${input}`));
      }
      if (data.outputs && data.outputs.length > 0) {
        lines.push('    outputs:');
        data.outputs.forEach((output) => lines.push(`      - ${output}`));
      }
      if (data.onSuccess) {
        lines.push(`    on_success: ${data.onSuccess}`);
      }
      if (data.allowedOutcomes && data.allowedOutcomes.length > 0) {
        lines.push('    allowed_outcomes:');
        data.allowedOutcomes.forEach((outcome) => lines.push(`      - ${outcome}`));
      }
      if (data.outcomeRoutes && Object.keys(data.outcomeRoutes).length > 0) {
        lines.push('    outcome_routes:');
        Object.entries(data.outcomeRoutes).forEach(([key, value]) => {
          lines.push(`      ${key}: ${value}`);
        });
      }
      if (data.onSubmit) {
        lines.push(`    on_submit: ${data.onSubmit}`);
      }
      if (data.onApprove) {
        lines.push(`    on_approve: ${data.onApprove}`);
      }
      if (data.onReject) {
        lines.push(`    on_reject: ${data.onReject}`);
      }
      if (data.onReworkRoutes && Object.keys(data.onReworkRoutes).length > 0) {
        lines.push('    on_rework_routes:');
        Object.entries(data.onReworkRoutes).forEach(([key, value]) => {
          lines.push(`      ${key}: ${value}`);
        });
      }
    });
    return lines.join('\n');
  };

  const saveFlow = async ({ publish, release = false }) => {
    if (!flowMeta.flowId) {
      message.error('Нужен ID Flow');
      return;
    }
    if (!flowMeta.title.trim()) {
      message.error('Нужно название');
      return;
    }
    if (!flowMeta.startRole.trim()) {
      message.error('Нужна стартовая роль');
      return;
    }
    if (!flowMeta.approverRole.trim()) {
      message.error('Нужна роль согласования');
      return;
    }
    if (!flowMeta.startNodeId.trim()) {
      message.error('Нужна стартовая нода');
      return;
    }
    const flowYaml = buildFlowYaml();
    try {
      const response = await apiRequest(`/flows/${flowMeta.flowId}/save`, {
        method: 'POST',
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({
          flow_id: flowMeta.flowId,
          flow_yaml: flowYaml,
          publish,
          release,
          resource_version: resourceVersion,
        }),
      });
      setFlowMeta((prev) => ({
        ...prev,
        flowId: response.flow_id || prev.flowId,
        title: response.title || prev.title,
        description: response.description || prev.description,
        startRole: response.start_role || prev.startRole,
        approverRole: response.approver_role || prev.approverRole,
        startNodeId: response.start_node_id || prev.startNodeId,
        ruleRefs: response.rule_refs || prev.ruleRefs,
      }));
      setFlowVersion(response.version || flowVersion);
      setCurrentStatus(response.status || currentStatus);
      setResourceVersion(response.resource_version ?? resourceVersion);
      message.success(publish ? 'Flow опубликован' : 'Черновик сохранён');
    } catch (err) {
      message.error(err.message || 'Не удалось сохранить Flow');
    }
  };

  useEffect(() => {
    if (!flowId) {
      return;
    }
    if (isCreateMode) {
      setFlowMeta(emptyFlow);
      setNodes([]);
      setSelectedNodeId(null);
      setFlowVersion('0.1');
      setCurrentStatus('draft');
      setResourceVersion(0);
      return;
    }
    setNodes(initialNodes);
    setSelectedNodeId(initialNodes[0]?.id || null);
    loadFlow(flowId);
  }, [flowId, isCreateMode, setNodes]);

  const handleStartNodeChange = (value) => {
    updateFlowMeta({ startNodeId: value });
    setNodes((prev) =>
      prev.map((node) => ({
        ...node,
        data: { ...node.data, isStart: node.id === value },
      }))
    );
  };

  return (
    <div className="flow-editor-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Редактор Flow</Title>
        <Space>
          <Dropdown
            menu={{
              items: [
                { key: 'publish', label: 'Опубликовать версию' },
                { key: 'release', label: 'Выпустить релиз' },
              ],
              onClick: ({ key }) => {
                saveFlow({ publish: true, release: key === 'release' });
              },
            }}
          >
            <Button type="default" icon={<MoreOutlined />}>Опубликовать</Button>
          </Dropdown>
        </Space>
      </div>

      <div className="split-layout flow-editor-layout">
        <Card className="flow-canvas-card">
          <div className="flow-canvas-header">
            <div>
              <Text className="muted">Канвас</Text>
              <div className="mono">{flowMeta.flowId || 'new-flow'}@{flowVersionLabel}</div>
            </div>
            <Space>
              <Button type="default">Вписать</Button>
              <Button type="default" onClick={() => saveFlow({ publish: false })}>Сохранить черновик</Button>
            </Space>
          </div>
          <div className="flow-canvas">
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              nodeTypes={nodeTypes}
              fitView
              onNodeClick={(_, node) => setSelectedNodeId(node.id)}
              onPaneClick={() => setSelectedNodeId(null)}
              proOptions={{ hideAttribution: true }}
            >
              <Background gap={20} color="#e2e8f0" />
              <Controls />
            </ReactFlow>
          </div>
        </Card>

        <div className="flow-right-panel">
          <Card className="flow-panel-card">
            <Title level={5}>Данные Flow</Title>
            <div className="form-stack">
              <div>
                <Text className="muted">Название</Text>
                <Input value={flowMeta.title} onChange={(event) => updateFlowMeta({ title: event.target.value })} />
              </div>
              <div>
                <Text className="muted">ID Flow</Text>
                <Input value={flowMeta.flowId} onChange={(event) => updateFlowMeta({ flowId: event.target.value })} />
              </div>
              <div>
                <Text className="muted">Описание</Text>
                <Input.TextArea
                  rows={3}
                  value={flowMeta.description}
                  onChange={(event) => updateFlowMeta({ description: event.target.value })}
                />
              </div>
              <div>
                <Text className="muted">Стартовая роль</Text>
                <Input value={flowMeta.startRole} onChange={(event) => updateFlowMeta({ startRole: event.target.value })} />
              </div>
              <div>
                <Text className="muted">Роль согласования</Text>
                <Input value={flowMeta.approverRole} onChange={(event) => updateFlowMeta({ approverRole: event.target.value })} />
              </div>
              <div>
                <Text className="muted">Стартовая нода</Text>
                <Select
                  value={flowMeta.startNodeId}
                  onChange={handleStartNodeChange}
                  options={nodes.map((node) => ({ value: node.id, label: node.id }))}
                />
              </div>
            </div>

            <Divider />

            <div className="linked-block">
              <div className="linked-header">
                <Title level={5}>Linked Rules</Title>
                <Text className="muted">Только опубликованные</Text>
              </div>
              <Select
                mode="multiple"
                options={ruleOptions}
                value={flowMeta.ruleRefs}
                onChange={(value) => updateFlowMeta({ ruleRefs: value })}
                placeholder="Выберите Rules"
              />
              <List
                dataSource={flowMeta.ruleRefs}
                locale={{ emptyText: 'Нет связанных Rules' }}
                renderItem={(ref, index) => {
                  const rule = rules.find((item) => item.canonical === ref);
                  return (
                    <List.Item
                      className="linked-item"
                      actions={[
                        <Button
                          key="up"
                          size="small"
                          icon={<ArrowUpOutlined />}
                          type="default"
                          onClick={() => updateFlowMeta({ ruleRefs: reorder(flowMeta.ruleRefs, index, -1) })}
                        />,
                        <Button
                          key="down"
                          size="small"
                          icon={<ArrowDownOutlined />}
                          type="default"
                          onClick={() => updateFlowMeta({ ruleRefs: reorder(flowMeta.ruleRefs, index, 1) })}
                        />,
                        <Button
                          key="delete"
                          size="small"
                          type="default"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => updateFlowMeta({ ruleRefs: flowMeta.ruleRefs.filter((item) => item !== ref) })}
                        />,
                      ]}
                    >
                      <List.Item.Meta
                        title={rule ? rule.name : ref}
                        description={
                          <div className="linked-meta">
                            <span className="mono">{ref}</span>
                            <span>{rule?.provider || 'неизвестно'}</span>
                          </div>
                        }
                      />
                      <StatusTag value={rule?.status || 'не найдено'} />
                    </List.Item>
                  );
                }}
              />
            </div>
          </Card>

          <Card className="flow-panel-card" style={{ marginTop: 16 }}>
            <Title level={5}>Выбранная нода</Title>
            {!selectedNode ? (
              <div className="card-muted">Выберите ноду на канвасе, чтобы редактировать её свойства.</div>
            ) : (
              <div className="form-stack">
                <div>
                  <Text className="muted">ID ноды</Text>
                  <Input value={selectedNode.data.id} disabled />
                </div>
                <div>
                  <Text className="muted">Название</Text>
                  <Input value={selectedNode.data.title} onChange={(event) => updateSelectedNode({ title: event.target.value })} />
                </div>
                <div>
                  <Text className="muted">Тип</Text>
                  <Input value={selectedNode.data.nodeType} disabled />
                </div>
                <div>
                  <Text className="muted">Вид исполнителя / гейта</Text>
                  <Input
                    value={selectedNode.data.executorKind || selectedNode.data.gateKind}
                    onChange={(event) =>
                      updateSelectedNode(
                        selectedNode.data.executorKind
                          ? { executorKind: event.target.value }
                          : { gateKind: event.target.value }
                      )
                    }
                  />
                </div>

                {selectedNode.data.executorKind === 'AI' && (
                  <div>
                    <Text className="muted">Инструкция</Text>
                    <Input.TextArea
                      rows={3}
                      value={selectedNode.data.instruction}
                      onChange={(event) => updateSelectedNode({ instruction: event.target.value })}
                    />
                  </div>
                )}

                {selectedNode.data.executorKind === 'External Command' && (
                  <div>
                    <Text className="muted">Команда</Text>
                    <Input
                      value={selectedNode.data.commandSpec}
                      onChange={(event) => updateSelectedNode({ commandSpec: event.target.value })}
                    />
                  </div>
                )}

                {selectedNode.data.gateKind && (
                  <div>
                    <Text className="muted">Настройки гейта</Text>
                    <Input placeholder="Роли, артефакты, политики" />
                  </div>
                )}

                <div>
                  <Text className="muted">Входы</Text>
                  <Select
                    mode="tags"
                    tokenSeparators={[',']}
                    value={selectedNode.data.inputs || []}
                    onChange={(value) => updateSelectedNode({ inputs: value })}
                    placeholder="Добавить входы"
                  />
                </div>
                <div>
                  <Text className="muted">Выходы</Text>
                  <Select
                    mode="tags"
                    tokenSeparators={[',']}
                    value={selectedNode.data.outputs || []}
                    onChange={(value) => updateSelectedNode({ outputs: value })}
                    placeholder="Добавить выходы"
                  />
                </div>

                <Divider />

                <div>
                  <Text className="muted">Переходы / исходы</Text>
                  {selectedNode.data.allowedOutcomes && selectedNode.data.allowedOutcomes.length > 0 && (
                    <div className="transition-list">
                      {selectedNode.data.allowedOutcomes.map((outcome) => (
                        <div key={outcome} className="transition-row">
                          <span className="mono">{outcome}</span>
                          <Input
                            value={selectedNode.data.outcomeRoutes?.[outcome]}
                            onChange={(event) => {
                              const nextRoutes = { ...(selectedNode.data.outcomeRoutes || {}) };
                              nextRoutes[outcome] = event.target.value;
                              updateSelectedNode({ outcomeRoutes: nextRoutes });
                            }}
                          />
                        </div>
                      ))}
                    </div>
                  )}
                  {selectedNode.data.onSuccess !== undefined && (
                    <div className="transition-row">
                      <span className="mono">on_success</span>
                      <Input
                        value={selectedNode.data.onSuccess || ''}
                        onChange={(event) => updateSelectedNode({ onSuccess: event.target.value })}
                      />
                    </div>
                  )}
                  {selectedNode.data.onSubmit && (
                    <div className="transition-row">
                      <span className="mono">on_submit</span>
                      <Input
                        value={selectedNode.data.onSubmit}
                        onChange={(event) => updateSelectedNode({ onSubmit: event.target.value })}
                      />
                    </div>
                  )}
                  {selectedNode.data.onApprove && (
                    <div className="transition-row">
                      <span className="mono">on_approve</span>
                      <Input
                        value={selectedNode.data.onApprove}
                        onChange={(event) => updateSelectedNode({ onApprove: event.target.value })}
                      />
                    </div>
                  )}
                  {selectedNode.data.onReject && (
                    <div className="transition-row">
                      <span className="mono">on_reject</span>
                      <Input
                        value={selectedNode.data.onReject}
                        onChange={(event) => updateSelectedNode({ onReject: event.target.value })}
                      />
                    </div>
                  )}
                  {selectedNode.data.onReworkRoutes && (
                    <div className="transition-list">
                      {Object.entries(selectedNode.data.onReworkRoutes).map(([mode, target]) => (
                        <div key={mode} className="transition-row">
                          <span className="mono">доработка:{mode}</span>
                          <Input
                            value={target}
                            onChange={(event) => {
                              const nextRoutes = { ...(selectedNode.data.onReworkRoutes || {}) };
                              nextRoutes[mode] = event.target.value;
                              updateSelectedNode({ onReworkRoutes: nextRoutes });
                            }}
                          />
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <Divider />

                <div className="linked-block">
                  <div className="linked-header">
                    <Title level={5}>Linked Skills</Title>
                    <Text className="muted">Только для AI</Text>
                  </div>
                  {selectedNode.data.executorKind !== 'AI' ? (
                    <div className="card-muted">Linked Skills доступны только для AI-исполнителей.</div>
                  ) : (
                    <>
                      <Select
                        mode="multiple"
                        options={skillOptions}
                        value={selectedNode.data.skillRefs || []}
                        onChange={(value) => updateSelectedNode({ skillRefs: value })}
                        placeholder="Выберите Skills"
                      />
                      <List
                        dataSource={selectedNode.data.skillRefs || []}
                        locale={{ emptyText: 'Нет связанных Skills' }}
                        renderItem={(ref, index) => {
                          const skill = skills.find((item) => item.canonical === ref);
                          const skillRefs = selectedNode.data.skillRefs || [];
                          return (
                            <List.Item
                              className="linked-item"
                              actions={[
                                <Button
                                  key="up"
                                  size="small"
                                  icon={<ArrowUpOutlined />}
                                  type="default"
                                  onClick={() => updateSelectedNode({ skillRefs: reorder(skillRefs, index, -1) })}
                                />,
                                <Button
                                  key="down"
                                  size="small"
                                  icon={<ArrowDownOutlined />}
                                  type="default"
                                  onClick={() => updateSelectedNode({ skillRefs: reorder(skillRefs, index, 1) })}
                                />,
                                <Button
                                  key="delete"
                                  size="small"
                                  type="default"
                                  danger
                                  icon={<DeleteOutlined />}
                                  onClick={() => updateSelectedNode({ skillRefs: skillRefs.filter((item) => item !== ref) })}
                                />,
                              ]}
                            >
                              <List.Item.Meta
                                title={skill ? skill.name : ref}
                                description={
                                  <div className="linked-meta">
                                    <span className="mono">{ref}</span>
                                    <span>{skill?.provider || 'неизвестно'}</span>
                                  </div>
                                }
                              />
                              <StatusTag value={skill?.status || 'не найдено'} />
                            </List.Item>
                          );
                        }}
                      />
                    </>
                  )}
                </div>
              </div>
            )}
          </Card>

          <Card className="flow-panel-card" style={{ marginTop: 16 }}>
            <Title level={5}>Валидация</Title>
            {validationErrors.length === 0 ? (
              <div className="card-muted">Ошибок валидации не найдено.</div>
            ) : (
              <List
                dataSource={validationErrors}
                renderItem={(item) => <List.Item className="card-muted">{item}</List.Item>}
              />
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
