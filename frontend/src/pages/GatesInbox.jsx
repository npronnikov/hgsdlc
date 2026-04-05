import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Empty, Input, Row, Select, Space, Table, Typography, message } from 'antd';
import { ArrowRightOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

function formatDate(value) {
  if (!value) {
    return '—';
  }
  try {
    return new Intl.DateTimeFormat('ru-RU', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value));
  } catch (_err) {
    return String(value);
  }
}

function normalizeSearch(value) {
  return String(value || '').trim().toLowerCase();
}

function pickInstruction(gate) {
  if (!gate?.payload || typeof gate.payload !== 'object') {
    return '—';
  }
  return gate.payload.user_instructions
    || gate.payload.instruction
    || gate.payload.comment
    || '—';
}

export default function GatesInbox() {
  const navigate = useNavigate();
  const [gates, setGates] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [statusFilter, setStatusFilter] = useState(null);
  const [kindFilter, setKindFilter] = useState(null);
  const [roleFilter, setRoleFilter] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      const [gatesResult, runsResult, projectsResult] = await Promise.allSettled([
        apiRequest('/gates/inbox'),
        apiRequest('/runs?limit=500'),
        apiRequest('/projects'),
      ]);

      if (gatesResult.status !== 'fulfilled') {
        throw gatesResult.reason;
      }

      const runs = runsResult.status === 'fulfilled' ? (runsResult.value || []) : [];
      const projects = projectsResult.status === 'fulfilled' ? (projectsResult.value || []) : [];

      const projectNameById = new Map(projects.map((item) => [item.id, item.name]));
      const runProjectByRunId = new Map(runs.map((item) => [item.run_id, item.project_id]));
      const runByRunId = new Map(runs.map((item) => [item.run_id, item]));

      const enriched = (gatesResult.value || []).map((gate) => {
        const projectId = runProjectByRunId.get(gate.run_id) || gate.project_id;
        const projectName = projectId ? projectNameById.get(projectId) : null;
        const run = runByRunId.get(gate.run_id);
        return {
          ...gate,
          project_name: projectName || '—',
          instruction: pickInstruction(gate),
          run_status: run?.status || null,
          flow_canonical_name: run?.flow_canonical_name || null,
        };
      });

      setGates(enriched);
    } catch (err) {
      message.error(err.message || 'Failed to load inbox');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const statusOptions = useMemo(() => {
    const set = new Set(gates.map((item) => item.status).filter(Boolean));
    return Array.from(set).map((value) => ({ value, label: value.replace(/_/g, ' ') }));
  }, [gates]);

  const gateKindOptions = useMemo(() => {
    const set = new Set(gates.map((item) => item.gate_kind).filter(Boolean));
    return Array.from(set).map((value) => ({ value, label: value.replace(/_/g, ' ') }));
  }, [gates]);

  const roleOptions = useMemo(() => {
    const set = new Set(gates.map((item) => item.assignee_role).filter(Boolean));
    return Array.from(set).map((value) => ({ value, label: value }));
  }, [gates]);

  const rows = useMemo(() => {
    const query = normalizeSearch(searchText);
    return gates.filter((gate) => {
      if (statusFilter && gate.status !== statusFilter) {
        return false;
      }
      if (kindFilter && gate.gate_kind !== kindFilter) {
        return false;
      }
      if (roleFilter && gate.assignee_role !== roleFilter) {
        return false;
      }
      if (!query) {
        return true;
      }
      const searchable = [
        gate.project_name,
        gate.node_id,
        gate.instruction,
        gate.run_id,
        gate.status,
        gate.assignee_role,
        gate.gate_kind,
        gate.flow_canonical_name,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return searchable.includes(query);
    });
  }, [gates, kindFilter, roleFilter, searchText, statusFilter]);

  const stats = useMemo(() => {
    const base = rows;
    return {
      total: base.length,
      approvals: base.filter((item) => item.gate_kind === 'human_approval').length,
      inputs: base.filter((item) => item.gate_kind === 'human_input').length,
      awaitingDecision: base.filter((item) => item.status === 'awaiting_decision').length,
      awaitingInput: base.filter((item) => item.status === 'awaiting_input').length,
    };
  }, [rows]);

  const columns = [
    {
      title: 'Queue',
      key: 'queue',
      render: (_, gate) => (
        <div className="gates-inbox-project-cell">
          <div className="gates-inbox-project-main">{gate.project_name || '—'}</div>
          <div className="gates-inbox-project-meta">
            <span className="mono">{gate.node_id}</span>
            <span>{gate.flow_canonical_name || 'Flow is unavailable'}</span>
          </div>
        </div>
      ),
    },
    {
      title: 'Instruction',
      dataIndex: 'instruction',
      key: 'instruction',
      width: 320,
      render: (value) => (
        <Typography.Paragraph
          className="gates-inbox-instruction"
          ellipsis={{ rows: 2, tooltip: value || '—' }}
        >
          {value || '—'}
        </Typography.Paragraph>
      ),
    },
    {
      title: 'Gate',
      key: 'gate_kind',
      width: 170,
      render: (_, gate) => <StatusTag value={gate.gate_kind || 'unknown'} />,
    },
    {
      title: 'Status',
      key: 'status',
      width: 170,
      render: (_, gate) => <StatusTag value={gate.status} />,
    },
    {
      title: 'Run',
      dataIndex: 'run_id',
      key: 'run_id',
      width: 180,
      responsive: ['lg'],
      render: (value) => <span className="mono">{value || '—'}</span>,
    },
    {
      title: 'Opened',
      dataIndex: 'opened_at',
      key: 'opened_at',
      width: 160,
      responsive: ['xl'],
      render: (value) => <span className="mono">{formatDate(value)}</span>,
    },
    {
      title: 'Role',
      dataIndex: 'assignee_role',
      key: 'assignee_role',
      width: 160,
      responsive: ['md'],
      render: (value) => value || '—',
    },
    {
      title: '',
      key: 'action',
      width: 120,
      responsive: ['sm'],
      render: (_, record) => (
        <Button
          size="small"
          type="default"
          icon={<ArrowRightOutlined />}
          className="gates-inbox-open-btn"
          onClick={(event) => {
            event.stopPropagation();
            const gatePath = record.gate_kind === 'human_input' ? '/gate-input' : '/gate-approval';
            navigate(`${gatePath}?runId=${record.run_id}&gateId=${record.gate_id}&gateKind=${record.gate_kind}`);
          }}
        >
          Open
        </Button>
      ),
    },
  ];

  return (
    <div className="cards-page gates-inbox-page">
      <div className="page-header gates-inbox-header">
        <div className="gates-inbox-title">
          <Title level={3} style={{ margin: 0 }}>Gates Inbox</Title>
          <Text type="secondary">Operational queue for human input and approval checkpoints.</Text>
        </div>
        <Space className="gates-inbox-controls" wrap>
          <Input
            allowClear
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="Search project, flow, run, instruction"
            prefix={<SearchOutlined />}
            style={{ width: 300 }}
          />
          <Select
            allowClear
            placeholder="Status"
            value={statusFilter}
            onChange={(value) => setStatusFilter(value || null)}
            options={statusOptions}
            optionFilterProp="label"
            style={{ width: 170 }}
          />
          <Select
            allowClear
            placeholder="Gate type"
            value={kindFilter}
            onChange={(value) => setKindFilter(value || null)}
            options={gateKindOptions}
            optionFilterProp="label"
            style={{ width: 170 }}
          />
          <Select
            allowClear
            placeholder="Role"
            value={roleFilter}
            onChange={(value) => setRoleFilter(value || null)}
            options={roleOptions}
            optionFilterProp="label"
            style={{ width: 170 }}
          />
          <Button type="default" onClick={load} loading={loading} icon={<ReloadOutlined />}>Refresh</Button>
        </Space>
      </div>

      <Row gutter={[10, 10]} className="gates-inbox-summary-grid">
        <Col xs={24} sm={12} lg={5}>
          <Card className="gates-inbox-summary-card">
            <div className="card-label">Total</div>
            <div className="gates-inbox-summary-value">{stats.total}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={5}>
          <Card className="gates-inbox-summary-card gates-inbox-summary-card-approval">
            <div className="card-label">Approval Gates</div>
            <div className="gates-inbox-summary-value">{stats.approvals}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={5}>
          <Card className="gates-inbox-summary-card gates-inbox-summary-card-input">
            <div className="card-label">Input Gates</div>
            <div className="gates-inbox-summary-value">{stats.inputs}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={5}>
          <Card className="gates-inbox-summary-card gates-inbox-summary-card-awaiting">
            <div className="card-label">Awaiting Decision</div>
            <div className="gates-inbox-summary-value">{stats.awaitingDecision}</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={4}>
          <Card className="gates-inbox-summary-card gates-inbox-summary-card-awaiting">
            <div className="card-label">Awaiting Input</div>
            <div className="gates-inbox-summary-value">{stats.awaitingInput}</div>
          </Card>
        </Col>
      </Row>

      <Card className="gates-inbox-table-card">
        <Table
          columns={columns}
          dataSource={rows}
          pagination={{ pageSize: 15, showSizeChanger: false }}
          rowKey="gate_id"
          loading={loading}
          tableLayout="fixed"
          scroll={{ x: 980 }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No gates match current filters" /> }}
          rowClassName={(record) => `gates-inbox-row gates-inbox-row-${record.gate_kind || 'unknown'}`}
          onRow={(record) => ({
            onClick: () => {
              const gatePath = record.gate_kind === 'human_input' ? '/gate-input' : '/gate-approval';
              navigate(`${gatePath}?runId=${record.run_id}&gateId=${record.gate_id}&gateKind=${record.gate_kind}`);
            },
            style: { cursor: 'pointer' },
          })}
        />
      </Card>
    </div>
  );
}
