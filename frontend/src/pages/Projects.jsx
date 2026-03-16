import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Dropdown, Form, Input, Modal, Row, Segmented, Select, Typography, message } from 'antd';
import { EditOutlined, EyeOutlined, InboxOutlined, MoreOutlined, PlayCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';
import { Handle, MarkerType, Position, ReactFlow } from 'reactflow';
import 'reactflow/dist/style.css';
import { parse as parseYaml } from 'yaml';
import dagre from 'dagre';

const { Title, Text } = Typography;

function PreviewNode({ data, sourcePosition, targetPosition }) {
  return (
    <div className="flow-preview-node">
      <Handle
        type="target"
        position={targetPosition || Position.Top}
        style={{ opacity: 0, pointerEvents: 'none' }}
      />
      {data?.label || ''}
      <Handle
        type="source"
        position={sourcePosition || Position.Bottom}
        style={{ opacity: 0, pointerEvents: 'none' }}
      />
    </div>
  );
}

export default function Projects() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingProject, setEditingProject] = useState(null);
  const [viewingProject, setViewingProject] = useState(null);
  const [launchProject, setLaunchProject] = useState(null);
  const [launchConfig, setLaunchConfig] = useState(null);
  const [flowOptions, setFlowOptions] = useState([]);
  const [selectedFlowId, setSelectedFlowId] = useState(null);
  const [layoutDirection, setLayoutDirection] = useState('TB');
  const [form] = Form.useForm();

  const loadProjects = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/projects');
      setProjects(data || []);
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить проекты');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProjects();
  }, []);

  const openCreate = () => {
    setEditingProject(null);
    form.resetFields();
    setIsModalOpen(true);
  };

  const openEdit = (project) => {
    setEditingProject(project);
    form.setFieldsValue({
      name: project.name,
      repo_url: project.repo_url,
      default_branch: project.default_branch,
    });
    setIsModalOpen(true);
  };

  const openView = (project) => {
    setViewingProject(project);
  };

  const closeView = () => {
    setViewingProject(null);
  };

  const submitForm = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload = {
        name: values.name?.trim(),
        repo_url: values.repo_url?.trim(),
      };
      const trimmedBranch = values.default_branch?.trim();
      if (trimmedBranch) {
        payload.default_branch = trimmedBranch;
      }

      if (editingProject) {
        const response = await apiRequest(`/projects/${editingProject.id}`, {
          method: 'PATCH',
          body: JSON.stringify({
            ...payload,
            resource_version: editingProject.resource_version,
          }),
        });
        setProjects((prev) => prev.map((item) => (item.id === response.id ? response : item)));
        message.success('Проект обновлён');
      } else {
        const response = await apiRequest('/projects', {
          method: 'POST',
          body: JSON.stringify(payload),
        });
        setProjects((prev) => [response, ...prev]);
        message.success('Проект создан');
      }
      setIsModalOpen(false);
      setEditingProject(null);
      form.resetFields();
    } catch (err) {
      if (err?.errorFields) {
        return;
      }
      message.error(err.message || 'Не удалось сохранить проект');
    } finally {
      setSubmitting(false);
    }
  };

  const archiveProject = async (project) => {
    try {
      const response = await apiRequest(`/projects/${project.id}/archive`, {
        method: 'POST',
      });
      setProjects((prev) => prev.map((item) => (item.id === response.id ? response : item)));
      message.success('Проект архивирован');
    } catch (err) {
      message.error(err.message || 'Не удалось архивировать проект');
    }
  };

  const projectMenuItems = (project) => ([
    { key: 'open', label: 'Открыть', icon: <EyeOutlined /> },
    {
      key: 'launch',
      label: 'Запустить',
      icon: <PlayCircleOutlined />,
      disabled: project.status === 'archived',
    },
    { key: 'edit', label: 'Редактировать', icon: <EditOutlined /> },
    {
      key: 'archive',
      label: 'Архивировать',
      icon: <InboxOutlined />,
      disabled: project.status === 'archived',
    },
  ]);

  const handleMenuClick = (project, key) => {
    if (key === 'open') {
      openView(project);
      return;
    }
    if (key === 'launch') {
      setLaunchProject(project);
      setLaunchConfig({ loading: true, flow: null, rules: [], skills: [], nodes: [], edges: [] });
      return;
    }
    if (key === 'edit') {
      openEdit(project);
      return;
    }
    if (key === 'archive') {
      Modal.confirm({
        title: 'Архивировать проект?',
        okText: 'Архивировать',
        cancelText: 'Отмена',
        onOk: () => archiveProject(project),
      });
    }
  };

  const buildFlowPreview = (flowYaml, direction = 'TB') => {
    if (!flowYaml) {
      return { nodes: [], edges: [], rawNodes: [] };
    }
    try {
      const parsed = parseYaml(flowYaml);
      const rawNodes = Array.isArray(parsed?.nodes) ? parsed.nodes : [];
      const nodeIds = new Set(rawNodes.map((node) => node.id).filter(Boolean));
      const nodes = rawNodes.map((node, index) => ({
        id: node.id || `node-${index + 1}`,
        type: 'preview',
        position: { x: index * 220, y: 0 },
        data: { label: node.title || node.id || `node-${index + 1}` },
      }));
      const edges = [];
      rawNodes.forEach((node) => {
        const source = node.id;
        if (!source) return;
        const addEdge = (target, suffix) => {
          if (!target || !nodeIds.has(target)) return;
          edges.push({
            id: `${source}-${target}-${suffix}`,
            source,
            target,
            type: 'smoothstep',
            style: { stroke: '#94a3b8', strokeWidth: 2 },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8' },
          });
        };
        addEdge(node.on_success, 'success');
        addEdge(node.on_failure, 'failure');
        addEdge(node.on_submit, 'submit');
        addEdge(node.on_approve, 'approve');
        if (node.on_rework_routes) {
          Object.values(node.on_rework_routes).forEach((target) => addEdge(target, 'rework'));
        }
      });
      if (nodes.length === 0) {
        return { nodes, edges, rawNodes };
      }
      const NODE_WIDTH = 180;
      const NODE_HEIGHT = 56;
      const graph = new dagre.graphlib.Graph();
      graph.setDefaultEdgeLabel(() => ({}));
      graph.setGraph({ rankdir: direction, nodesep: 50, ranksep: 80 });
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
  };

  const loadLaunchConfig = async (flowIdOverride) => {
    try {
      let flowId = flowIdOverride || selectedFlowId;
      if (!flowId) {
        setLaunchConfig({ loading: false, flow: null, rules: [], skills: [], nodes: [], edges: [] });
        return;
      }
      const flow = await apiRequest(`/flows/${flowId}`);
      const flowPreview = buildFlowPreview(flow.flow_yaml, layoutDirection);
      const nodes = flowPreview.rawNodes || [];
      const skills = Array.from(new Set(
        nodes.flatMap((node) => node.skill_refs || [])
      ));
      setLaunchConfig({
        loading: false,
        flow,
        rules: flow.rule_refs || [],
        skills,
        nodes: flowPreview.nodes,
        edges: flowPreview.edges,
        nodeList: nodes,
      });
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить конфигурацию запуска');
      setLaunchConfig({ loading: false, flow: null, rules: [], skills: [], nodes: [], edges: [], nodeList: [] });
    }
  };

  const loadFlowOptions = async () => {
    try {
      const flows = await apiRequest('/flows');
      const options = (flows || []).map((flow) => ({
        value: flow.flow_id,
        label: flow.canonical_name || flow.flow_id,
        status: flow.status,
      }));
      setFlowOptions(options);
      if (!selectedFlowId && options.length > 0) {
        const published = options.find((flow) => flow.status === 'published');
        setSelectedFlowId((published || options[0]).value);
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить список Flow');
    }
  };

  useEffect(() => {
    if (launchProject) {
      loadFlowOptions();
    }
  }, [launchProject]);

  useEffect(() => {
    if (launchProject && selectedFlowId) {
      setLaunchConfig((prev) => ({ ...(prev || {}), loading: true }));
      loadLaunchConfig(selectedFlowId);
    }
  }, [launchProject, selectedFlowId]);

  useEffect(() => {
    if (launchProject && launchConfig?.flow?.flow_yaml) {
      const flowPreview = buildFlowPreview(launchConfig.flow.flow_yaml, layoutDirection);
      setLaunchConfig((prev) => ({
        ...(prev || {}),
        nodes: flowPreview.nodes,
        edges: flowPreview.edges,
      }));
    }
  }, [launchProject, layoutDirection]);

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Projects</Title>
        <Button type="default" icon={<PlusOutlined />} onClick={openCreate}>Новый проект</Button>
      </div>
      <div className="cards-fullscreen">
        {loading ? (
          <div className="card-muted">Загрузка...</div>
        ) : (
          <div className="cards-grid">
            {projects.map((project) => (
              <Card
                key={project.id}
                className={`resource-card project-card status-${(project.status || 'unknown').toLowerCase()}`}
                hoverable
              >
                <div className="resource-card-header">
                  <div className="resource-card-title">
                    <span className="resource-card-name">{project.name}</span>
                  </div>
                  <div className="resource-card-actions">
                    <StatusTag value={project.status} />
                    <Dropdown
                      trigger={['click']}
                      menu={{
                        items: projectMenuItems(project),
                        onClick: ({ key }) => handleMenuClick(project, key),
                      }}
                    >
                      <Button
                        type="text"
                        size="small"
                        icon={<MoreOutlined />}
                        className="resource-card-menu"
                      />
                    </Dropdown>
                  </div>
                </div>
                <div className="resource-card-description mono">{project.repo_url}</div>
                <div className="resource-card-footer resource-card-footer-stack">
                  <span className="resource-canonical mono">branch: {project.default_branch}</span>
                  <span className="resource-canonical mono">last run: {project.last_run_id || '—'}</span>
                </div>
              </Card>
            ))}
            {projects.length === 0 && (
              <div className="card-muted">Проекты не найдены.</div>
            )}
          </div>
        )}
      </div>

      <Modal
        title={editingProject ? 'Редактировать проект' : 'Добавить проект'}
        open={isModalOpen}
        onOk={submitForm}
        confirmLoading={submitting}
        onCancel={() => {
          setIsModalOpen(false);
          setEditingProject(null);
        }}
        okText={editingProject ? 'Сохранить' : 'Создать'}
        cancelText="Отмена"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="Name"
            name="name"
            rules={[{ required: true, message: 'Введите название проекта' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="Repository URL"
            name="repo_url"
            rules={[{ required: true, message: 'Введите ссылку на репозиторий' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item label="Default branch" name="default_branch">
            <Input placeholder="main" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Детали проекта"
        open={!!viewingProject}
        onCancel={closeView}
        footer={null}
      >
        {viewingProject && (
          <div className="form-stack">
            <div>
              <div className="muted">Name</div>
              <div>{viewingProject.name}</div>
            </div>
            <div>
              <div className="muted">Repository</div>
              <div className="mono">{viewingProject.repo_url}</div>
            </div>
            <div>
              <div className="muted">Default branch</div>
              <div className="mono">{viewingProject.default_branch}</div>
            </div>
            <div>
              <div className="muted">Status</div>
              <StatusTag value={viewingProject.status} />
            </div>
            <div>
              <div className="muted">Last run</div>
              <div className="mono">{viewingProject.last_run_id || '—'}</div>
            </div>
          </div>
        )}
      </Modal>

      <Modal
        title={launchProject ? `Запуск флоу для проекта ${launchProject.name}` : 'Запуск флоу'}
        open={!!launchProject}
        onOk={() => {
          setLaunchProject(null);
          setLaunchConfig(null);
          setSelectedFlowId(null);
          setFlowOptions([]);
          setLayoutDirection('TB');
        }}
        onCancel={() => {
          setLaunchProject(null);
          setLaunchConfig(null);
          setSelectedFlowId(null);
          setFlowOptions([]);
          setLayoutDirection('TB');
        }}
        okText="Запустить"
        cancelText="Отмена"
        width={980}
        bodyStyle={{ height: '65vh', overflow: 'hidden' }}
        className="flow-launch-modal"
      >
        {launchProject && (
          <div className="flow-launch-dialog">
            <div className="flow-launch-toolbar">
              <div className="flow-launch-select">
                <Text className="muted">Выберите Flow для запуска</Text>
                <Select
                  value={selectedFlowId || undefined}
                  options={flowOptions}
                  placeholder="Выберите Flow для запуска"
                  onChange={(value) => setSelectedFlowId(value)}
                />
              </div>
              <Segmented
                value={layoutDirection}
                onChange={(value) => setLayoutDirection(value)}
                options={[
                  { value: 'TB', label: 'Вертикально' },
                  { value: 'LR', label: 'Горизонтально' },
                ]}
                disabled={!selectedFlowId}
              />
            </div>
            {launchConfig?.loading && <div className="card-muted">Загрузка конфигурации...</div>}
            {!launchConfig?.loading && !selectedFlowId && (
              <div className="flow-preview-placeholder card-muted">Выберите Flow для запуска.</div>
            )}
            {!launchConfig?.loading && selectedFlowId && !launchConfig?.flow && (
              <div className="flow-preview-placeholder card-muted">Нет доступных Flow для запуска.</div>
            )}
            {!launchConfig?.loading && launchConfig?.flow && (
              <div className="flow-preview-canvas card-muted">
                <div className="flow-preview-info">
                  <div className="muted">Rules</div>
                  <div className="mono flow-preview-list">
                    {(launchConfig.rules || []).length > 0
                      ? launchConfig.rules.map((rule) => `> ${rule}`).join('\n')
                      : '—'}
                  </div>
                  <div className="muted">Expected skills</div>
                  <div className="mono flow-preview-list">
                    {(launchConfig.skills || []).length > 0
                      ? launchConfig.skills.map((skill) => `> ${skill}`).join('\n')
                      : '—'}
                  </div>
                </div>
                <ReactFlow
                  style={{ width: '100%', height: '100%' }}
                  nodes={launchConfig.nodes}
                  edges={launchConfig.edges}
                  nodeTypes={{ preview: PreviewNode }}
                  nodesDraggable={false}
                  nodesConnectable={false}
                  zoomOnScroll={false}
                  panOnScroll={false}
                  fitView
                  fitViewOptions={{ padding: 0.2 }}
                  proOptions={{ hideAttribution: true }}
                />
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
