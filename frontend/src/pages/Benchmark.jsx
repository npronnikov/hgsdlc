import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Card, Col, Empty, Form, Input, Modal, Popconfirm,
  Row, Select, Space, Table, Tag, Typography, message,
} from 'antd';
import { DeleteOutlined, ExperimentOutlined, PlayCircleOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text, Paragraph } = Typography;
const CODING_AGENT_OPTIONS = [
  { value: 'qwen', label: 'qwen' },
  { value: 'gigacode', label: 'gigacode' },
  { value: 'claude', label: 'claude' },
];

function normalizeCodingAgent(value) {
  if (value == null) return null;
  const normalized = String(value).trim().toLowerCase().replace(/_/g, '-');
  return normalized || null;
}

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

function formatWithoutArtifactLabel(artifactType) {
  if (artifactType === 'SKILL') return 'without skill';
  if (artifactType === 'RULE') return 'without rule';
  return 'without artifact';
}

export default function Benchmark() {
  const navigate = useNavigate();

  const [cases, setCases] = useState([]);
  const [runs, setRuns] = useState([]);
  const [projects, setProjects] = useState([]);
  const [skills, setSkills] = useState([]);
  const [rules, setRules] = useState([]);
  const [runtimeCodingAgent, setRuntimeCodingAgent] = useState(null);
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
  const caseFormCodingAgent = Form.useWatch('coding_agent', caseForm);
  const runFormCodingAgent = Form.useWatch('coding_agent', runForm);

  const resolveArtifactCodingAgent = (type, artifactId) => {
    if (!type || !artifactId) return null;
    if (type === 'SKILL') {
      const skill = skills.find((item) => item.skill_id === artifactId || item.canonical_name === artifactId);
      return normalizeCodingAgent(skill?.coding_agent);
    }
    if (type === 'RULE') {
      const rule = rules.find((item) => item.rule_id === artifactId || item.canonical_name === artifactId);
      return normalizeCodingAgent(rule?.coding_agent);
    }
    return null;
  };

  const inferCaseCodingAgent = (benchCase) => {
    if (!benchCase?.artifact_type) return null;
    const artifactAgentA = resolveArtifactCodingAgent(benchCase.artifact_type, benchCase.artifact_id);
    const artifactAgentB = resolveArtifactCodingAgent(benchCase.artifact_type, benchCase.artifact_id_b);
    return artifactAgentA || artifactAgentB || null;
  };

  const load = async () => {
    setLoading(true);
    try {
      const [casesData, runsData, projectsData, settingsData, skillsData, rulesData] = await Promise.all([
        apiRequest('/benchmark/cases'),
        apiRequest('/benchmark/runs'),
        apiRequest('/projects'),
        apiRequest('/settings/runtime'),
        apiRequest('/skills'),
        apiRequest('/rules'),
      ]);
      setCases(Array.isArray(casesData) ? casesData : []);
      setRuns(Array.isArray(runsData) ? runsData : []);
      setProjects(Array.isArray(projectsData) ? projectsData : []);
      setRuntimeCodingAgent(normalizeCodingAgent(settingsData?.coding_agent));
      setSkills(Array.isArray(skillsData) ? skillsData : []);
      setRules(Array.isArray(rulesData) ? rulesData : []);
    } catch (err) {
      message.error(err.message || 'Failed to load benchmark data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const artifactOptions = useMemo(() => {
    const selectedAgent = normalizeCodingAgent(caseFormCodingAgent);
    if (artifactType === 'SKILL') {
      return skills
        .filter((s) => !selectedAgent || normalizeCodingAgent(s.coding_agent) === selectedAgent)
        .map((s) => ({
          value: s.skill_id,
          label: `${s.skill_id}${s.name ? ` — ${s.name}` : ''}${s.coding_agent ? ` [${s.coding_agent}]` : ''}`,
        }));
    }
    return rules
      .filter((r) => !selectedAgent || normalizeCodingAgent(r.coding_agent) === selectedAgent)
      .map((r) => ({
        value: r.rule_id,
        label: `${r.rule_id}${r.title ? ` — ${r.title}` : ''}${r.coding_agent ? ` [${r.coding_agent}]` : ''}`,
      }));
  }, [artifactType, skills, rules, caseFormCodingAgent]);

  const handleCreateCase = async (values) => {
    setCreating(true);
    try {
      const selectedAgent = normalizeCodingAgent(values.coding_agent);
      await apiRequest('/benchmark/cases', {
        method: 'POST',
        body: JSON.stringify({
          instruction: values.instruction,
          project_id: values.project_id,
          name: values.name || null,
          artifact_type: values.artifact_type || null,
          artifact_id: values.artifact_id || null,
          artifact_type_b: values.artifact_id_b ? (values.artifact_type || null) : null,
          artifact_id_b: values.artifact_id_b || null,
        }),
      });
      message.success('Benchmark case created');
      if (values.artifact_type && selectedAgent && runtimeCodingAgent && selectedAgent !== runtimeCodingAgent) {
        message.warning(
          `Runtime coding_agent is "${runtimeCodingAgent}". Benchmark run with "${selectedAgent}" will fail (CODING_AGENT_MISMATCH).`,
          6,
        );
      }
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
    const inferredCodingAgent = inferCaseCodingAgent(bc);
    runForm.setFieldsValue({
      instruction: bc.instruction,
      project_id: bc.project_id,
      repo_url: proj?.repo_url || '',
      coding_agent: inferredCodingAgent || runtimeCodingAgent || undefined,
    });
    setRunModalOpen(true);
  };

  const handleStartRun = async (values) => {
    setStartingRun(true);
    try {
      const selectedAgent = normalizeCodingAgent(values.coding_agent);
      const run = await apiRequest('/benchmark/runs', {
        method: 'POST',
        body: JSON.stringify({
          case_id: runModalCase.id,
          instruction_override: values.instruction !== runModalCase.instruction ? values.instruction : null,
          project_id_override: values.project_id !== runModalCase.project_id ? values.project_id : null,
          coding_agent: selectedAgent,
        }),
      });
      if (selectedAgent && runtimeCodingAgent && selectedAgent !== runtimeCodingAgent) {
        message.warning(
          `Runtime coding_agent is "${runtimeCodingAgent}". Run with "${selectedAgent}" may fail (CODING_AGENT_MISMATCH).`,
          6,
        );
      }
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
      return [bc.name, bc.instruction, bc.artifact_id, bc.artifact_id_b, bc.artifact_type, projectName(bc.project_id)]
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
      width: 220,
      render: (_, bc) => (
        <div className="benchmark-table-case">
          <div className="benchmark-table-case-main">{bc.name || bc.instruction.slice(0, 60)}</div>
          <div className="benchmark-table-case-meta">
            <span>{projectName(bc.project_id)}</span>
            {bc.artifact_type && (
              <div className="benchmark-table-artifact-compact">
                <div className="benchmark-table-artifact-line">
                  <span className="benchmark-table-artifact-label">Run A:</span>
                  <span className="benchmark-table-artifact-value">{bc.artifact_id}</span>
                </div>
                <div className="benchmark-table-artifact-line">
                  <span className="benchmark-table-artifact-label">Run B:</span>
                  <span className="benchmark-table-artifact-value">{bc.artifact_id_b || formatWithoutArtifactLabel(bc.artifact_type)}</span>
                </div>
              </div>
            )}
          </div>
        </div>
      ),
    },
    {
      title: 'Instruction',
      key: 'instruction',
      width: 420,
      render: (_, bc) => (
        <Paragraph className="benchmark-table-instruction" ellipsis={{ rows: 2, tooltip: bc.instruction }}>
          {bc.instruction}
        </Paragraph>
      ),
    },
    {
      title: 'Runs',
      key: 'runs',
      width: 340,
      render: (_, bc) => {
        const caseRuns = [...(runsByCaseId[bc.id] || [])]
          .sort((a, b) => String(b.created_at || '').localeCompare(String(a.created_at || '')));
        if (caseRuns.length === 0) return <Text type="secondary" className="benchmark-table-empty-runs">No runs</Text>;
        return (
          <div className="benchmark-table-runs">
            {caseRuns.map((run) => (
              <button
                key={run.id}
                type="button"
                className="benchmark-table-run-chip"
                onClick={(e) => { e.stopPropagation(); navigate(`/benchmark/${run.id}`); }}
              >
                <div className="benchmark-table-run-title">
                  {run.artifact_id || run.artifact_type || formatStatus(run.status)}
                  {' vs '}
                  {run.artifact_id_b || formatWithoutArtifactLabel(run.artifact_type)}
                </div>
                <div className="benchmark-table-run-meta">
                  <StatusTag value={String(run.status || '').toLowerCase()} />
                  {run.human_verdict && <Tag>{formatStatus(run.human_verdict)}</Tag>}
                </div>
              </button>
            ))}
          </div>
        );
      },
    },
    {
      title: 'Created',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 140,
      responsive: ['xl'],
      render: (v) => <span className="mono benchmark-table-created">{formatDate(v)}</span>,
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
            disabled={!bc.artifact_type || !bc.artifact_id}
            title={(!bc.artifact_type || !bc.artifact_id) ? 'No artifact A configured' : 'Start run'}
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

      <Card className="gates-inbox-table-card benchmark-table-card">
        <Table
          columns={columns}
          dataSource={rows}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 15, showSizeChanger: false }}
          tableLayout="fixed"
          scroll={{ x: 800 }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No benchmark cases" /> }}
          rowClassName={() => 'benchmark-table-row'}
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
              caseForm.setFieldValue('artifact_id_b', undefined);
            }
            if (changed.coding_agent !== undefined) {
              caseForm.setFieldValue('artifact_id', undefined);
              caseForm.setFieldValue('artifact_id_b', undefined);
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
          <Form.Item
            label="Coding agent"
            name="coding_agent"
            rules={[
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (getFieldValue('artifact_type') && !value) {
                    return Promise.reject(new Error('Coding agent is required when artifact type is selected'));
                  }
                  return Promise.resolve();
                },
              }),
            ]}
          >
            <Select allowClear placeholder="Select coding agent" options={CODING_AGENT_OPTIONS} />
          </Form.Item>
          {normalizeCodingAgent(caseFormCodingAgent)
            && runtimeCodingAgent
            && normalizeCodingAgent(caseFormCodingAgent) !== runtimeCodingAgent && (
              <Alert
                showIcon
                type="warning"
                style={{ marginBottom: 16 }}
                message={`Runtime coding_agent is "${runtimeCodingAgent}". Benchmark run with "${normalizeCodingAgent(caseFormCodingAgent)}" will fail (CODING_AGENT_MISMATCH).`}
              />
          )}
          {artifactType && (
            <Form.Item label={artifactType === 'SKILL' ? 'Skill A' : 'Rule A'} name="artifact_id">
              <Select
                showSearch
                placeholder={`Select ${artifactType.toLowerCase()}`}
                filterOption={(input, opt) => (opt?.label ?? '').toLowerCase().includes(input.toLowerCase())}
                options={artifactOptions}
              />
            </Form.Item>
          )}
          {artifactType && (
            <Form.Item label={artifactType === 'SKILL' ? 'Skill B (optional)' : 'Rule B (optional)'} name="artifact_id_b">
              <Select
                allowClear
                showSearch
                placeholder={`Select ${artifactType.toLowerCase()} for B (optional)`}
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
        title={runModalCase
          ? `Start Run — ${runModalCase.artifact_type}: ${runModalCase.artifact_id} vs ${runModalCase.artifact_id_b || formatWithoutArtifactLabel(runModalCase.artifact_type)}`
          : 'Start Run'}
        onCancel={() => { setRunModalOpen(false); runForm.resetFields(); }}
        footer={null}
        destroyOnClose
      >
        <Form form={runForm} layout="vertical" onFinish={handleStartRun} style={{ marginTop: 16 }}>
          <Form.Item
            label="Coding agent"
            name="coding_agent"
            rules={[{ required: true, message: 'Coding agent is required' }]}
          >
            <Select options={CODING_AGENT_OPTIONS} />
          </Form.Item>
          {normalizeCodingAgent(runFormCodingAgent)
            && runtimeCodingAgent
            && normalizeCodingAgent(runFormCodingAgent) !== runtimeCodingAgent && (
              <Alert
                showIcon
                type="warning"
                style={{ marginBottom: 16 }}
                message={`Runtime coding_agent is "${runtimeCodingAgent}". Run with "${normalizeCodingAgent(runFormCodingAgent)}" will fail (CODING_AGENT_MISMATCH).`}
              />
          )}
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
