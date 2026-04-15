import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Form, Input, Radio, Row, Select, Switch, Tag, Typography, message } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { apiRequest } from '../api/request.js';
import {
  Background,
  Controls,
  Handle,
  Position,
  ReactFlow,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { buildFlowPreview, NODE_KIND_META } from '../utils/flowLayout.js';

const { Title } = Typography;

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

function generateIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `run-${Date.now()}-${Math.random().toString(16).slice(2)}`;
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
  const publishModeValue = Form.useWatch('publish_mode', form);
  const aiSessionModeValue = Form.useWatch('ai_session_mode', form);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [projectData, flowData] = await Promise.all([
          apiRequest('/projects'),
          apiRequest('/flows'),
        ]);
        const activeProjects = (projectData || []).filter(
          (project) => (project?.status || '').toLowerCase() === 'active'
        );
        setProjects(activeProjects);
        const publishedFlows = (flowData || []).filter(
          (flow) => flow.status === 'published' && (!flow.lifecycle_status || flow.lifecycle_status === 'active')
        );
        setFlows(publishedFlows);

        const paramProjectId = searchParams.get('projectId');
        const paramProjectIsActive = activeProjects.some((project) => project.id === paramProjectId);
        const initialProjectId = paramProjectIsActive ? paramProjectId : activeProjects?.[0]?.id || null;
        setSelectedProjectId(initialProjectId);

        setSelectedFlowId(null);
        setSelectedFlowCanonical(null);

        const initialProject = activeProjects.find((project) => project.id === initialProjectId);
        form.setFieldsValue({
          project_id: initialProjectId || undefined,
          target_branch: initialProject?.default_branch || 'main',
          ai_session_mode: 'isolated_attempt_sessions',
          publish_mode: 'branch',
          pr_commit_strategy: 'squash',
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
          ai_session_mode: values.ai_session_mode,
          publish_mode: values.publish_mode,
          work_branch: values.work_branch?.trim() || undefined,
          pr_commit_strategy: values.publish_mode === 'pr' ? (values.pr_commit_strategy || 'squash') : undefined,
          debug_mode: values.debug_mode || false,
          idempotency_key: generateIdempotencyKey(),
        }),
      });
      localStorage.setItem('lastRunId', response.run_id);
      message.success(`Flow started: ${response.run_id}`);
      if (values.debug_mode) {
        navigate(`/run-workspace?runId=${response.run_id}`);
      } else {
        navigate(`/run-console?runId=${response.run_id}`);
      }
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
    && aiSessionModeValue
    && publishModeValue
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
              <Form.Item name="ai_session_mode" hidden initialValue="isolated_attempt_sessions">
                <Input />
              </Form.Item>
              <Row gutter={12}>
                <Col xs={24} md={12}>
                  <Form.Item
                    label="Publish mode"
                    name="publish_mode"
                    rules={[{ required: true, message: 'Select publish mode' }]}
                    initialValue="branch"
                  >
                    <Select
                      options={[
                        { value: 'branch', label: 'Branch' },
                        { value: 'pr', label: 'Pull Request' },
                      ]}
                      onChange={(value) => {
                        if (value === 'pr' && !form.getFieldValue('pr_commit_strategy')) {
                          form.setFieldValue('pr_commit_strategy', 'squash');
                        }
                      }}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    label="Work branch"
                    name="work_branch"
                  >
                    <Input placeholder="run/&lt;runId&gt; (default)" />
                  </Form.Item>
                </Col>
              </Row>
              {publishModeValue === 'pr' && (
                <Form.Item
                  label="PR commit strategy"
                  name="pr_commit_strategy"
                  initialValue="squash"
                  rules={[{ required: true, message: 'Select commit strategy' }]}
                >
                  <Select
                    options={[
                      { value: 'squash', label: 'squash' },
                    ]}
                  />
                </Form.Item>
              )}
              <Form.Item
                label="Debug mode"
                name="debug_mode"
                valuePropName="checked"
                initialValue={false}
              >
                <Switch checkedChildren="Debug" unCheckedChildren="Normal" />
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
