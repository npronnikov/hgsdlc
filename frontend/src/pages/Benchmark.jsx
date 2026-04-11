import React, { useEffect, useMemo, useState } from 'react';
import {
  Button, Card, Col, Empty, Form, Input, Modal, Popconfirm,
  Row, Select, Space, Table, Tag, Typography, message,
} from 'antd';
import { DeleteOutlined, ExperimentOutlined, PlayCircleOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { apiRequest } from '../api/request.js';

const { Title, Text, Paragraph } = Typography;

const STATUS_COLOR = {
  RUNNING: 'processing',
  WAITING_COMPARISON: 'warning',
  COMPLETED: 'success',
  FAILED: 'error',
};

const VERDICT_COLOR = {
  SKILL_USEFUL: 'green',
  SKILL_NOT_HELPFUL: 'red',
  NEUTRAL: 'default',
};

function formatDate(value) {
  if (!value) return '—';
  try {
    return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
  } catch (_) {
    return String(value);
  }
}

function formatStatus(s) {
  return (s || '').replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function Benchmark() {
  const navigate = useNavigate();

  const [cases, setCases] = useState([]);
  const [runs, setRuns] = useState([]);
  const [projects, setProjects] = useState([]);
  const [skills, setSkills] = useState([]);
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [statusFilter, setStatusFilter] = useState(null);

  // New Case modal
  const [caseModalOpen, setCaseModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [caseForm] = Form.useForm();
  const [artifactType, setArtifactType] = useState(null);

  // Start Run modal
  const [runModalOpen, setRunModalOpen] = useState(false);
  const [runModalCase, setRunModalCase] = useState(null);
  const [startingRun, setStartingRun] = useState(false);
  const [runForm] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const [casesData, runsData, projectsData] = await Promise.all([
        apiRequest('/benchmark/cases'),
        apiRequest('/benchmark/runs'),
        apiRequest('/projects'),
      ]);
      setCases(Array.isArray(casesData) ? casesData : []);
      setRuns(Array.isArray(runsData) ? runsData : []);
      setProjects(Array.isArray(projectsData) ? projectsData : []);
    } catch (err) {
      message.error(err.message || 'Failed to load benchmark data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  // Load artifacts when artifact type changes in New Case form
  const loadArtifacts = async (type) => {
    if (!type) { setSkills([]); setRules([]); return; }
    try {
      const data = await apiRequest(type === 'SKILL' ? '/skills' : '/rules');
      if (type === 'SKILL') setSkills(Array.isArray(data) ? data : []);
      else setRules(Array.isArray(data) ? data : []);
    } catch (_) {}
  };

  const artifactOptions = artifactType === 'SKILL'
    ? skills.map((s) => ({ value: s.skill_id, label: `${s.skill_id}${s.name ? ` — ${s.name}` : ''}` }))
    : rules.map((r) => ({ value: r.rule_id, label: `${r.rule_id}${r.title ? ` — ${r.title}` : ''}` }));

  const handleCreateCase = async (values) => {
    setCreating(true);
    try {
      await apiRequest('/benchmark/cases', {
        method: 'POST',
        body: JSON.stringify({
          instruction: values.instruction,
          project_id: values.project_id,
          name: values.name || null,
          artifact_type: values.artifact_type || null,
          artifact_id: values.artifact_id || null,
        }),
      });
      message.success('Benchmark case created');
      setCaseModalOpen(false);
      caseForm.resetFields();
      setArtifactType(null);
      load();
    } catch (err) {
      message.error(err.message || 'Failed to create benchmark case');
    } finally {
      setCreating(false);
    }
  };

  const handleDeleteCase = async (caseId) => {
    try {
      await apiRequest(`/benchmark/cases/${caseId}`, { method: 'DELETE' });
      message.success('Case deleted');
      load();
    } catch (err) {
      message.error(err.message || 'Failed to delete case');
    }
  };

  const openRunModal = (bc) => {
    setRunModalCase(bc);
    const proj = projects.find((p) => p.id === bc.project_id);
    runForm.setFieldsValue({
      instruction: bc.instruction,
      project_id: bc.project_id,
      repo_url: proj?.repo_url || '',
    });
    setRunModalOpen(true);
  };

  const handleStartRun = async (values) => {
    setStartingRun(true);
    try {
      const run = await apiRequest('/benchmark/runs', {
        method: 'POST',
        body: JSON.stringify({
          case_id: runModalCase.id,
          instruction_override: values.instruction !== runModalCase.instruction ? values.instruction : null,
          project_id_override: values.project_id !== runModalCase.project_id ? values.project_id : null,
          coding_agent: null,
        }),
      });
      message.success('Benchmark run started');
      setRunModalOpen(false);
      navigate(`/benchmark/${run.id}`);
    } catch (err) {
      message.error(err.message || 'Failed to start run');
    } finally {
      setStartingRun(false);
    }
  };

  const projectName = (id) => projects.find((p) => p.id === id)?.name || id;

  // Runs per case for summary column
  const runsByCaseId = useMemo(() => {
    const map = {};
    for (const r of runs) {
      if (!map[r.case_id]) map[r.case_id] = [];
      map[r.case_id].push(r);
    }
    return map;
  }, [runs]);

  // Table rows = cases
  const rows = useMemo(() => {
    const q = searchText.trim().toLowerCase();
    return cases.filter((bc) => {
      if (statusFilter) {
        const caseRuns = runsByCaseId[bc.id] || [];
        if (!caseRuns.some((r) => r.status === statusFilter)) return false;
      }
      if (!q) return true;
      return [bc.name, bc.instruction, bc.artifact_id, bc.artifact_type, projectName(bc.project_id)]
        .filter(Boolean).join(' ').toLowerCase().includes(q);
    });
  }, [cases, searchText, statusFilter, runsByCaseId]);

  const statusOptions = useMemo(() => {
    const set = new Set(runs.map((r) => r.status).filter(Boolean));
    return Array.from(set).map((v) => ({ value: v, label: formatStatus(v) }));
  }, [runs]);

  // Stats
  const stats = useMemo(() => ({
    cases: cases.length,
    running: runs.filter((r) => r.status === 'RUNNING').length,
    waiting: runs.filter((r) => r.status === 'WAITING_COMPARISON').length,
    completed: runs.filter((r) => r.status === 'COMPLETED').length,
  }), [cases, runs]);

  const columns = [
    {
      title: 'Case',
      key: 'case',
      render: (_, bc) => (
        <div>
          <div style={{ fontWeight: 500 }}>{bc.name || bc.instruction.slice(0, 60)}</div>
          <div style={{ fontSize: 12, color: 'var(--color-text-secondary, #888)', marginTop: 2 }}>
            <span style={{ marginRight: 8 }}>{projectName(bc.project_id)}</span>
            {bc.artifact_type && (
              <Tag style={{ margin: 0 }}>{bc.artifact_type}: {bc.artifact_id}</Tag>
            )}
          </div>
        </div>
      ),
    },
    {
      title: 'Instruction',
      key: 'instruction',
      width: 280,
      render: (_, bc) => (
        <Paragraph ellipsis={{ rows: 2, tooltip: bc.instruction }} style={{ margin: 0, fontSize: 13 }}>
          {bc.instruction}
        </Paragraph>
      ),
    },
    {
      title: 'Runs',
      key: 'runs',
      width: 220,
      render: (_, bc) => {
        const caseRuns = runsByCaseId[bc.id] || [];
        if (caseRuns.length === 0) return <Text type="secondary" style={{ fontSize: 12 }}>No runs</Text>;
        return (
          <Space wrap size={4}>
            {caseRuns.map((run) => (
              <Tag
                key={run.id}
                color={STATUS_COLOR[run.status] || 'default'}
                style={{ cursor: 'pointer', margin: 0 }}
                onClick={(e) => { e.stopPropagation(); navigate(`/benchmark/${run.id}`); }}
              >
                {run.artifact_id || run.artifact_type || formatStatus(run.status)}
                {run.human_verdict && (
                  <span style={{ marginLeft: 4 }}>·{formatStatus(run.human_verdict)}</span>
                )}
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: 'Created',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 140,
      responsive: ['xl'],
      render: (v) => <span className="mono" style={{ fontSize: 12 }}>{formatDate(v)}</span>,
    },
    {
      title: '',
      key: 'actions',
      width: 100,
      render: (_, bc) => (
        <Space size={4} onClick={(e) => e.stopPropagation()}>
          <Button
            size="small"
            icon={<PlayCircleOutlined />}
            onClick={() => openRunModal(bc)}
            disabled={!bc.artifact_type}
            title={!bc.artifact_type ? 'No artifact configured' : 'Start run'}
          />
          <Popconfirm
            title="Delete this benchmark case?"
            onConfirm={() => handleDeleteCase(bc.id)}
            okText="Delete"
            okButtonProps={{ danger: true }}
          >
            <Button size="small" icon={<DeleteOutlined />} danger />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="cards-page gates-inbox-page">
      <div className="page-header gates-inbox-header">
        <div className="gates-inbox-title">
          <Title level={3} style={{ margin: 0 }}>
            <ExperimentOutlined style={{ marginRight: 8 }} />
            Benchmark
          </Title>
          <Text type="secondary">A/B comparison of skill and rule effectiveness.</Text>
        </div>
        <Space className="gates-inbox-controls" wrap>
          <Input
            allowClear
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            placeholder="Search case, artifact, project"
            prefix={<SearchOutlined />}
            style={{ width: 260 }}
          />
          <Select
            allowClear
            placeholder="Run status"
            value={statusFilter}
            onChange={(v) => setStatusFilter(v || null)}
            options={statusOptions}
            style={{ width: 160 }}
          />
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>Refresh</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCaseModalOpen(true)}>
            New Case
          </Button>
        </Space>
      </div>

      <Row gutter={[10, 10]} className="gates-inbox-summary-grid">
        <Col xs={24} sm={12} lg={6}>
          <Card className="gates-inbox-summary-card">
            <div className="card-label">Cases</div>
            <div className="gates-inbox-summary-value">{stats.cases}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="gates-inbox-summary-card gates-inbox-summary-card-awaiting">
            <div className="card-label">Running</div>
            <div className="gates-inbox-summary-value">{stats.running}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="gates-inbox-summary-card gates-inbox-summary-card-approval">
            <div className="card-label">Waiting Comparison</div>
            <div className="gates-inbox-summary-value">{stats.waiting}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="gates-inbox-summary-card gates-inbox-summary-card-input">
            <div className="card-label">Completed</div>
            <div className="gates-inbox-summary-value">{stats.completed}</div>
          </Card>
        </Col>
      </Row>

      <Card className="gates-inbox-table-card">
        <Table
          columns={columns}
          dataSource={rows}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 15, showSizeChanger: false }}
          tableLayout="fixed"
          scroll={{ x: 800 }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No benchmark cases" /> }}
        />
      </Card>

      {/* New Case Modal */}
      <Modal
        open={caseModalOpen}
        title="New Benchmark Case"
        onCancel={() => { setCaseModalOpen(false); caseForm.resetFields(); setArtifactType(null); }}
        footer={null}
        destroyOnClose
      >
        <Form
          form={caseForm}
          layout="vertical"
          onFinish={handleCreateCase}
          style={{ marginTop: 16 }}
          onValuesChange={(changed) => {
            if (changed.artifact_type !== undefined) {
              setArtifactType(changed.artifact_type || null);
              caseForm.setFieldValue('artifact_id', undefined);
              loadArtifacts(changed.artifact_type);
            }
          }}
        >
          <Form.Item label="Name (optional)" name="name">
            <Input placeholder="Error handling benchmark" />
          </Form.Item>
          <Form.Item label="Artifact type" name="artifact_type">
            <Select
              allowClear
              placeholder="Skill or Rule (optional)"
              options={[
                { value: 'SKILL', label: 'Skill' },
                { value: 'RULE', label: 'Rule' },
              ]}
            />
          </Form.Item>
          {artifactType && (
            <Form.Item label={artifactType === 'SKILL' ? 'Skill' : 'Rule'} name="artifact_id">
              <Select
                showSearch
                placeholder={`Select ${artifactType.toLowerCase()}`}
                filterOption={(input, opt) => (opt?.label ?? '').toLowerCase().includes(input.toLowerCase())}
                options={artifactOptions}
              />
            </Form.Item>
          )}
          <Form.Item
            label="Project"
            name="project_id"
            rules={[{ required: true, message: 'Project is required' }]}
          >
            <Select
              placeholder="Select project"
              options={projects.map((p) => ({ value: p.id, label: p.name }))}
            />
          </Form.Item>
          <Form.Item
            label="Instruction (task for the agent)"
            name="instruction"
            rules={[{ required: true, message: 'Instruction is required' }]}
          >
            <Input.TextArea rows={4} placeholder="Add proper error handling to the main API endpoints" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => { setCaseModalOpen(false); caseForm.resetFields(); setArtifactType(null); }}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={creating}>Create</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Start Run Modal */}
      <Modal
        open={runModalOpen}
        title={runModalCase ? `Start Run — ${runModalCase.artifact_type}: ${runModalCase.artifact_id}` : 'Start Run'}
        onCancel={() => { setRunModalOpen(false); runForm.resetFields(); }}
        footer={null}
        destroyOnClose
      >
        <Form form={runForm} layout="vertical" onFinish={handleStartRun} style={{ marginTop: 16 }}>
          <Form.Item label="Project" name="project_id">
            <Select
              options={projects.map((p) => ({ value: p.id, label: p.name }))}
            />
          </Form.Item>
          <Form.Item label="Instruction" name="instruction">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => { setRunModalOpen(false); runForm.resetFields(); }}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={startingRun} icon={<PlayCircleOutlined />}>
                Start
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
