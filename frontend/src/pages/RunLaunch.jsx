import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Form, Input, Radio, Row, Select, Tag, Typography, message } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { apiRequest } from '../api/request.js';
import {
  Background,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { parse as parseYaml } from 'yaml';
import dagre from 'dagre';

const { Title } = Typography;

const NODE_KIND_META = {
  ai: {
    label: 'AI Executor',
    variant: 'executor ai',
  },
  command: {
    label: 'Command Executor',
    variant: 'executor command',
  },
  human_input: {
    label: 'Human Input Gate',
    variant: 'gate input',
  },
  human_approval: {
    label: 'Human Approval Gate',
    variant: 'gate approval',
  },
  terminal: {
    label: 'Terminal',
    variant: 'terminal',
  },
};

function FlowNode({ data, selected }) {
  const visuals = NODE_KIND_META[data.nodeKind] || {};
  return (
    <div className={`flow-node ${visuals.variant || data.variant || ''} ${selected ? 'is-selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="flow-node-header">
        <div>
          <div className="flow-node-title">{data.title}</div>
          <div className="flow-node-meta">{visuals.label || data.typeLabel}</div>
        </div>
        {data.isStart && <span className="flow-node-start">Start</span>}
      </div>
      <div className="flow-node-id">{data.id}</div>
      {data.isTerminal && <span className="flow-node-stop">Stop</span>}
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

const RUN_LAUNCH_NODE_TYPES = { flowNode: FlowNode };

function normalizeNodeKind(node) {
  const raw = node?.node_kind || node?.nodeKind || node?.type || node?.kind || '';
  const normalized = String(raw || '').trim().toLowerCase();
  return normalized === 'external_command' ? 'command' : normalized;
}

function toNodeData(node, isStart) {
  const kind = normalizeNodeKind(node);
  const rawRework = node.on_rework || node.onRework || null;
  let onRework = null;
  if (rawRework) {
    onRework = {
      keepChanges: rawRework.keep_changes ?? rawRework.keepChanges ?? false,
      nextNode: rawRework.next_node || rawRework.nextNode || '',
    };
  }
  const onReworkRoutes = node.on_rework_routes || node.onReworkRoutes || null;
  return {
    id: node.id || '',
    title: node.title || node.id || '',
    nodeKind: kind,
    typeLabel: kind ? kind.replace(/_/g, ' ') : 'node',
    onSuccess: node.on_success || node.onSuccess || '',
    onFailure: node.on_failure || node.onFailure || '',
    onSubmit: node.on_submit || node.onSubmit || '',
    onApprove: node.on_approve || node.onApprove || '',
    onRework: onRework || null,
    onReworkRoutes,
    isStart: !!isStart,
    isTerminal: kind === 'terminal',
  };
}

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
    if (data.onFailure) {
      addEdge(node.id, data.onFailure, 'on_failure', 'outcome');
    }
    if (data.onSubmit) {
      addEdge(node.id, data.onSubmit, 'on_submit', 'main');
    }
    if (data.onApprove) {
      addEdge(node.id, data.onApprove, 'on_approve', 'main');
    }
    if (data.onRework && data.onRework.nextNode) {
      const keepChanges = !!data.onRework.keepChanges;
      addEdge(node.id, data.onRework.nextNode, `rework: keep_changes=${keepChanges}`, 'rework');
    }
    if (data.onReworkRoutes && typeof data.onReworkRoutes === 'object') {
      Object.entries(data.onReworkRoutes).forEach(([mode, target]) => {
        addEdge(node.id, target, `rework: ${mode}`, 'rework');
      });
    }
  });

  return edges;
}

function buildFlowPreview(flowYaml, direction = 'TB') {
  if (!flowYaml) {
    return { nodes: [], edges: [], rawNodes: [] };
  }
  try {
    const parsed = parseYaml(flowYaml);
    const rawNodes = Array.isArray(parsed?.nodes) ? parsed.nodes : [];
    const startNodeId = parsed?.start_node_id || parsed?.startNodeId || '';
    const nodes = rawNodes.map((node, index) => ({
      id: node.id || `node-${index + 1}`,
      type: 'flowNode',
      position: { x: index * 220, y: 0 },
      data: toNodeData(node, node.id === startNodeId),
    }));
    const edges = buildEdges(nodes);
    if (nodes.length === 0) {
      return { nodes, edges, rawNodes };
    }
    const NODE_WIDTH = 220;
    const NODE_HEIGHT = 90;
    const graph = new dagre.graphlib.Graph();
    graph.setDefaultEdgeLabel(() => ({}));
    graph.setGraph({ rankdir: direction, nodesep: 80, ranksep: 140 });
    nodes.forEach((node) => {
      graph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
    });
    edges.forEach((edge) => {
      graph.setEdge(edge.source, edge.target);
    });
    dagre.layout(graph);
    const layoutedNodes = nodes.map((node) => {
      const layout = graph.node(node.id);
      const isHorizontal = direction === 'LR';
      return {
        ...node,
        targetPosition: isHorizontal ? Position.Left : Position.Top,
        sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
        position: {
          x: layout.x - NODE_WIDTH / 2,
          y: layout.y - NODE_HEIGHT / 2,
        },
      };
    });
    return { nodes: layoutedNodes, edges, rawNodes };
  } catch (err) {
    return { nodes: [], edges: [], rawNodes: [] };
  }
}

export default function RunLaunch() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm();
  const [projects, setProjects] = useState([]);
  const [flows, setFlows] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState(null);
  const [selectedFlowId, setSelectedFlowId] = useState(null);
  const [selectedFlowCanonical, setSelectedFlowCanonical] = useState(null);
  const [layoutDirection, setLayoutDirection] = useState('TB');
  const [launchConfig, setLaunchConfig] = useState(null);
  const [launching, setLaunching] = useState(false);
  const projectIdValue = Form.useWatch('project_id', form);
  const targetBranchValue = Form.useWatch('target_branch', form);
  const featureRequestValue = Form.useWatch('feature_request', form);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [projectData, flowData] = await Promise.all([
          apiRequest('/projects'),
          apiRequest('/flows'),
        ]);
        setProjects(projectData || []);
        const publishedFlows = (flowData || []).filter((flow) => flow.status === 'published');
        setFlows(publishedFlows);

        const paramProjectId = searchParams.get('projectId');
        const initialProjectId = paramProjectId || projectData?.[0]?.id || null;
        setSelectedProjectId(initialProjectId);

        setSelectedFlowId(null);
        setSelectedFlowCanonical(null);

        const initialProject = (projectData || []).find((project) => project.id === initialProjectId);
        form.setFieldsValue({
          project_id: initialProjectId || undefined,
          target_branch: initialProject?.default_branch || 'main',
        });
      } catch (err) {
        message.error(err.message || 'Failed to load launch data');
      }
    };
    loadData();
  }, [searchParams, form]);


  useEffect(() => {
    if (!selectedProjectId) {
      return;
    }
    const project = projects.find((item) => item.id === selectedProjectId);
    if (project) {
      form.setFieldValue('target_branch', project.default_branch || 'main');
    }
  }, [selectedProjectId, projects, form]);

  const projectOptions = useMemo(
    () => projects.map((project) => ({ value: project.id, label: project.name })),
    [projects]
  );

  const flowOptions = useMemo(
    () => flows.map((flow) => ({
      value: flow.flow_id,
      label: flow.canonical_name || flow.flow_id,
    })),
    [flows]
  );

  useEffect(() => {
    if (!selectedFlowId) {
      setLaunchConfig({ loading: false, flow: null, rules: [], skills: [], nodes: [], edges: [] });
      return;
    }
    const flow = flows.find((item) => item.flow_id === selectedFlowId);
    if (flow?.canonical_name) {
      setSelectedFlowCanonical(flow.canonical_name);
    }
    const loadLaunchConfig = async () => {
      try {
        setLaunchConfig((prev) => ({ ...(prev || {}), loading: true }));
        const flowDetails = await apiRequest(`/flows/${selectedFlowId}`);
        const flowPreview = buildFlowPreview(flowDetails.flow_yaml, layoutDirection);
        const nodes = flowPreview.rawNodes || [];
        const skills = Array.from(new Set(
          nodes.flatMap((node) => node.skill_refs || [])
        ));
        setLaunchConfig({
          loading: false,
          flow: flowDetails,
          rules: flowDetails.rule_refs || [],
          skills,
          nodes: flowPreview.nodes,
          edges: flowPreview.edges,
          nodeList: nodes,
        });
      } catch (err) {
        message.error(err.message || 'Failed to load launch configuration');
        setLaunchConfig({ loading: false, flow: null, rules: [], skills: [], nodes: [], edges: [], nodeList: [] });
      }
    };
    loadLaunchConfig();
  }, [selectedFlowId, flows]);

  useEffect(() => {
    if (!launchConfig?.flow?.flow_yaml) {
      return;
    }
    const flowPreview = buildFlowPreview(launchConfig.flow.flow_yaml, layoutDirection);
    setLaunchConfig((prev) => ({
      ...(prev || {}),
      nodes: flowPreview.nodes,
      edges: flowPreview.edges,
    }));
  }, [layoutDirection, launchConfig?.flow?.flow_yaml]);

  const handleLaunch = async () => {
    try {
      const values = await form.validateFields();
      if (!selectedFlowCanonical) {
        message.error('Select a flow to launch');
        return;
      }
      setLaunching(true);
      const response = await apiRequest('/runs', {
        method: 'POST',
        body: JSON.stringify({
          project_id: values.project_id,
          target_branch: values.target_branch,
          flow_canonical_name: selectedFlowCanonical,
          feature_request: values.feature_request,
          idempotency_key: crypto.randomUUID(),
        }),
      });
      localStorage.setItem('lastRunId', response.run_id);
      message.success(`Flow started: ${response.run_id}`);
      navigate(`/run-console?runId=${response.run_id}`);
    } catch (err) {
      if (err?.errorFields) {
        return;
      }
      message.error(err.message || 'Failed to start run');
    } finally {
      setLaunching(false);
    }
  };

  const canLaunch = Boolean(
    projectIdValue
    && selectedFlowId
    && targetBranchValue?.trim()
    && featureRequestValue?.trim()
  );

  return (
    <div className="run-launch-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Launch Run</Title>
        <Button
          type="default"
          icon={<PlayCircleOutlined />}
          onClick={handleLaunch}
          loading={launching}
          disabled={!canLaunch}
        >
          Launch
        </Button>
      </div>
      <Row gutter={[16, 16]} className="run-launch-grid">
        <Col xs={24} lg={12} className="run-launch-left">
          <div className="run-launch-left-column">
            <Card className="run-launch-form-card">
            <Form layout="vertical" form={form}>
              <Row gutter={12}>
                <Col xs={24} md={12}>
                  <Form.Item
                    label="Project"
                    name="project_id"
                    rules={[{ required: true, message: 'Select project' }]}
                  >
                    <Select
                      options={projectOptions}
                      value={selectedProjectId || undefined}
                      onChange={(value) => {
                        setSelectedProjectId(value);
                        form.setFieldValue('project_id', value);
                      }}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    label="Target branch"
                    name="target_branch"
                    rules={[{ required: true, message: 'Specify target branch' }]}
                  >
                    <Input />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item
                label="Flow"
                name="flow_id"
                rules={[{ required: true, message: 'Select flow' }]}
              >
                <Select
                  options={flowOptions}
                  value={selectedFlowId || undefined}
                  placeholder="Select a Flow to launch"
                  onChange={(value) => {
                    setSelectedFlowId(value);
                    const flow = flows.find((item) => item.flow_id === value);
                    setSelectedFlowCanonical(flow?.canonical_name || null);
                    form.setFieldValue('flow_id', value);
                  }}
                />
              </Form.Item>
              <Form.Item
                label="Feature request"
                name="feature_request"
                rules={[{ required: true, message: 'Describe feature request' }]}
              >
                <Input.TextArea
                  rows={6}
                  placeholder="Describe the requested change"
                />
              </Form.Item>
            </Form>
            </Card>
            {selectedFlowId && (
              <Card className="run-launch-params-card">
                <div className="run-launch-params-title">Launch Parameters</div>
                <div className="run-launch-meta">
                  <div className="run-launch-param-section">
                    <div className="run-launch-param-header">
                      <div>Rules</div>
                      <Tag>{(launchConfig?.rules || []).length}</Tag>
                    </div>
                    <div className="mono flow-preview-list">
                      {(launchConfig?.rules || []).length > 0
                        ? launchConfig.rules.map((rule) => `> ${rule}`).join('\n')
                        : '—'}
                    </div>
                  </div>
                  <div className="run-launch-param-section">
                    <div className="run-launch-param-header">
                      <div>Expected skills</div>
                      <Tag>{(launchConfig?.skills || []).length}</Tag>
                    </div>
                    <div className="mono flow-preview-list">
                      {(launchConfig?.skills || []).length > 0
                        ? launchConfig.skills.map((skill) => `> ${skill}`).join('\n')
                        : '—'}
                    </div>
                  </div>
                </div>
              </Card>
            )}
          </div>
        </Col>
        <Col xs={24} lg={12} className="run-launch-right">
          <Card className="run-launch-preview-card">
            <div className="flow-launch-dialog">
              <div className="flow-launch-toolbar">
                <Radio.Group
                  value={layoutDirection}
                  onChange={(event) => setLayoutDirection(event.target.value)}
                  optionType="button"
                  buttonStyle="solid"
                  disabled={!selectedFlowId}
                >
                  <Radio.Button value="TB">Vertical</Radio.Button>
                  <Radio.Button value="LR">Horizontal</Radio.Button>
                </Radio.Group>
              </div>
              {launchConfig?.loading && <div className="card-muted">Loading configuration...</div>}
              {!launchConfig?.loading && !selectedFlowId && (
                <div className="flow-preview-placeholder card-muted">Select a Flow to launch.</div>
              )}
              {!launchConfig?.loading && selectedFlowId && !launchConfig?.flow && (
                <div className="flow-preview-placeholder card-muted">No Flows available for launch.</div>
              )}
              {!launchConfig?.loading && launchConfig?.flow && (
                <div className="flow-preview-canvas flow-canvas">
                  <ReactFlow
                    style={{ width: '100%', height: '100%' }}
                    nodes={launchConfig.nodes}
                    edges={launchConfig.edges}
                    nodeTypes={RUN_LAUNCH_NODE_TYPES}
                    nodesDraggable={false}
                    nodesConnectable={false}
                    zoomOnScroll={false}
                    panOnScroll
                    fitView
                    fitViewOptions={{ padding: 0.2 }}
                    proOptions={{ hideAttribution: true }}
                  >
                    <Background gap={20} color="#e2e8f0" />
                    <Controls />
                  </ReactFlow>
                </div>
              )}
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
