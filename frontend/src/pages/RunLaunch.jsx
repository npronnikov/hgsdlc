import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Form, Input, Row, Select, Typography, message } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function RunLaunch() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm();
  const [projects, setProjects] = useState([]);
  const [flows, setFlows] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState(null);
  const [selectedFlowCanonical, setSelectedFlowCanonical] = useState(null);
  const [launching, setLaunching] = useState(false);

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

        const initialFlow = publishedFlows[0]?.canonical_name || null;
        setSelectedFlowCanonical(initialFlow);

        const initialProject = (projectData || []).find((project) => project.id === initialProjectId);
        form.setFieldsValue({
          project_id: initialProjectId || undefined,
          flow_canonical_name: initialFlow || undefined,
          target_branch: initialProject?.default_branch || 'main',
          context_root_dir: '.',
        });
      } catch (err) {
        message.error(err.message || 'Не удалось загрузить данные запуска');
      }
    };
    loadData();
  }, [searchParams, form]);

  const selectedProject = projects.find((project) => project.id === selectedProjectId);
  const selectedFlow = flows.find((flow) => flow.canonical_name === selectedFlowCanonical);

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
    () => flows.map((flow) => ({ value: flow.canonical_name, label: flow.canonical_name })),
    [flows]
  );

  const handleLaunch = async () => {
    try {
      const values = await form.validateFields();
      setLaunching(true);
      const response = await apiRequest('/runs', {
        method: 'POST',
        body: JSON.stringify({
          project_id: values.project_id,
          target_branch: values.target_branch,
          flow_canonical_name: values.flow_canonical_name,
          context_root_dir: values.context_root_dir,
          feature_request: values.feature_request,
          idempotency_key: crypto.randomUUID(),
        }),
      });
      localStorage.setItem('lastRunId', response.run_id);
      message.success(`Run создан: ${response.run_id}`);
      navigate(`/run-console?runId=${response.run_id}`);
    } catch (err) {
      if (err?.errorFields) {
        return;
      }
      message.error(err.message || 'Не удалось запустить run');
    } finally {
      setLaunching(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Launch Run</Title>
        <Button type="primary" onClick={handleLaunch} loading={launching}>Launch run</Button>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card>
            <Form layout="vertical" form={form}>
              <Form.Item
                label="Project"
                name="project_id"
                rules={[{ required: true, message: 'Выберите проект' }]}
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
              <Form.Item
                label="Flow version"
                name="flow_canonical_name"
                rules={[{ required: true, message: 'Выберите flow' }]}
              >
                <Select
                  options={flowOptions}
                  value={selectedFlowCanonical || undefined}
                  onChange={(value) => {
                    setSelectedFlowCanonical(value);
                    form.setFieldValue('flow_canonical_name', value);
                  }}
                />
              </Form.Item>
              <Form.Item
                label="Target branch"
                name="target_branch"
                rules={[{ required: true, message: 'Укажите target branch' }]}
              >
                <Input />
              </Form.Item>
              <Form.Item label="context_root_dir" name="context_root_dir">
                <Input placeholder="." />
              </Form.Item>
              <Form.Item
                label="Feature request"
                name="feature_request"
                rules={[{ required: true, message: 'Опишите feature request' }]}
              >
                <Input.TextArea
                  rows={6}
                  placeholder="Опишите требуемое изменение"
                />
              </Form.Item>
            </Form>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card>
            <Title level={5}>Selected configuration</Title>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Flow</Text>
              <div className="mono">{selectedFlow?.canonical_name || '—'}</div>
            </div>
            {selectedProject && (
              <div style={{ marginTop: 12 }}>
                <Text className="muted">Project</Text>
                <div className="mono">{selectedProject.name}</div>
                <div className="mono muted">{selectedProject.repo_url}</div>
              </div>
            )}
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Current start node</Text>
              <div className="mono">{selectedFlow?.start_node_id || '—'}</div>
            </div>
            <div style={{ marginTop: 12 }} className="card-muted">
              После запуска run стартует автоматически и остановится на `waiting_gate` либо завершится в terminal node.
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
