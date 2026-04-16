import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Empty,
  Input,
  List,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import {
  BranchesOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloudUploadOutlined,
  CloseCircleOutlined,
  LinkOutlined,
  LoadingOutlined,
  MinusCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import StatusTag, { formatStatusLabel } from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;
const ACTIVE_RUN_STATUSES = ['created', 'running', 'waiting_gate', 'waiting_publish', 'publish_failed'];
const MAX_NOTIFIED_GATES = 20;
const NOTIFIED_GATES_STORAGE_KEY = 'runConsole.notifiedGates';

function readNotifiedGatesFromSession() {
  if (typeof window === 'undefined' || !window.sessionStorage) {
    return [];
  }
  try {
    const raw = window.sessionStorage.getItem(NOTIFIED_GATES_STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((value) => typeof value === 'string' && value.length > 0);
  } catch (_) {
    return [];
  }
}

function writeNotifiedGatesToSession(keys) {
  if (typeof window === 'undefined' || !window.sessionStorage) {
    return;
  }
  try {
    window.sessionStorage.setItem(NOTIFIED_GATES_STORAGE_KEY, JSON.stringify(keys));
  } catch (_) {
    // ignore session storage errors
  }
}

function hasGateBeenNotified(key) {
  return readNotifiedGatesFromSession().includes(key);
}

function markGateNotified(key) {
  const current = readNotifiedGatesFromSession().filter((item) => item !== key);
  current.push(key);
  const trimmed = current.slice(-MAX_NOTIFIED_GATES);
  writeNotifiedGatesToSession(trimmed);
}

function formatDate(value) {
  if (!value) {
    return '—';
  }
  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(new Date(value));
}

function shortId(value, length = 8) {
  if (!value) {
    return '—';
  }
  return String(value).slice(0, length);
}

function normalizeSearch(value) {
  return String(value || '').trim().toLowerCase();
}

function shortSha(value) {
  if (!value) {
    return '—';
  }
  return String(value).slice(0, 10);
}

function publishStageClass(status) {
  if (status === 'succeeded') return 'is-success';
  if (status === 'failed') return 'is-failed';
  if (status === 'running' || status === 'pending') return 'is-active';
  if (status === 'skipped') return 'is-muted';
  return 'is-default';
}

function publishStageIcon(stageKey, status) {
  if (status === 'running') return <LoadingOutlined />;
  if (status === 'pending') return <SyncOutlined />;
  if (status === 'succeeded') return <CheckCircleOutlined />;
  if (status === 'failed') return <CloseCircleOutlined />;
  if (status === 'skipped') return <MinusCircleOutlined />;
  if (stageKey === 'push') return <CloudUploadOutlined />;
  if (stageKey === 'pr') return <BranchesOutlined />;
  return <SyncOutlined />;
}

function isPublishPhaseVisible(run) {
  return ['waiting_publish', 'publish_failed', 'completed'].includes(run?.status);
}

function publishHeadline(run) {
  if (['created', 'running', 'waiting_gate'].includes(run.status)) {
    return 'Publish not started yet';
  }
  if (['failed', 'cancelled'].includes(run.status)) {
    return 'Publish skipped';
  }
  if (run.publish_status === 'failed') {
    return 'Publish failed';
  }
  if (run.publish_status === 'succeeded' && run.publish_mode === 'pr') {
    return 'Published with Pull Request';
  }
  if (run.publish_status === 'succeeded' && run.publish_mode === 'branch') {
    return 'Published to branch';
  }
  if (run.publish_status === 'succeeded' && run.publish_mode === 'local') {
    return 'Published locally (legacy mode)';
  }
  if (run.publish_status === 'running') {
    return 'Publishing in progress';
  }
  if (run.publish_status === 'pending') {
    return 'Awaiting publish execution';
  }
  return 'Publish pipeline';
}

function publishSubtitle(run) {
  if (['created', 'running', 'waiting_gate'].includes(run.status)) {
    return 'Workflow is still in progress. Publish starts after terminal node.';
  }
  if (['failed', 'cancelled'].includes(run.status)) {
    return 'Run ended before publish phase, so publish pipeline was skipped.';
  }
  if (run.publish_mode === 'branch') {
    return 'Runtime prepares final commit and pushes work branch without creating pull request.';
  }
  if (run.publish_mode === 'local') {
    return 'Legacy mode: runtime prepares a final local commit without push and without PR.';
  }
  if (run.publish_mode === 'pr') {
    return 'Runtime prepares final commit, pushes work branch and opens pull request.';
  }
  return 'Publish mode is not set for this run.';
}

function formatAuditValue(value) {
  if (value === null || value === undefined) {
    return '—';
  }
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false';
  }
  if (typeof value === 'number') {
    return String(value);
  }
  if (typeof value === 'string') {
    return value;
  }
  return JSON.stringify(value, null, 2);
}

function prettifyKey(key) {
  return String(key || '')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

function isPlainObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value);
}

function renderAuditData(value, path = 'root') {
  if (value === null || value === undefined) {
    return <Text type="secondary">—</Text>;
  }

  if (typeof value === 'string') {
    const multiline = value.includes('\n') || value.length > 160;
    return multiline ? <pre className="code-block">{value}</pre> : <span>{value}</span>;
  }

  if (typeof value === 'number' || typeof value === 'boolean') {
    return <span>{String(value)}</span>;
  }

  if (Array.isArray(value)) {
    if (value.length === 0) {
      return <Text type="secondary">—</Text>;
    }
    const allPrimitive = value.every((item) => item === null || ['string', 'number', 'boolean'].includes(typeof item));
    if (allPrimitive) {
      return (
        <List
          size="small"
          dataSource={value}
          renderItem={(item, index) => (
            <List.Item key={`${path}-${index}`}>
              <span>{formatAuditValue(item)}</span>
            </List.Item>
          )}
        />
      );
    }
    return <pre className="code-block">{JSON.stringify(value, null, 2)}</pre>;
  }

  if (isPlainObject(value)) {
    return (
      <Descriptions bordered size="small" column={1}>
        {Object.entries(value).map(([key, nestedValue]) => (
          <Descriptions.Item key={`${path}-${key}`} label={prettifyKey(key)}>
            {renderAuditData(nestedValue, `${path}-${key}`)}
          </Descriptions.Item>
        ))}
      </Descriptions>
    );
  }

  return <pre className="code-block">{JSON.stringify(value, null, 2)}</pre>;
}

function renderAgentInputSection(agentInput) {
  if (!isPlainObject(agentInput)) {
    return null;
  }

  return (
    <Card size="small" title="Agent Input">
      <Descriptions bordered size="small" column={1}>
        <Descriptions.Item label="Start Node">
          {agentInput.startNode ? 'true' : 'false'}
        </Descriptions.Item>
        <Descriptions.Item label="Task">
          {agentInput.task ? <pre className="code-block">{agentInput.task}</pre> : <Text type="secondary">—</Text>}
        </Descriptions.Item>
        <Descriptions.Item label="Request clarification">
          {agentInput.requestClarification
            ? <pre className="code-block">{agentInput.requestClarification}</pre>
            : <Text type="secondary">—</Text>}
        </Descriptions.Item>
        <Descriptions.Item label="Node Instruction">
          {agentInput.nodeInstruction
            ? <pre className="code-block">{agentInput.nodeInstruction}</pre>
            : <Text type="secondary">—</Text>}
        </Descriptions.Item>
        <Descriptions.Item label="Inputs">
          {renderAuditData(agentInput.inputs, 'agent-inputs')}
        </Descriptions.Item>
        <Descriptions.Item label="Expected Results">
          {renderAuditData(agentInput.expectedResults, 'agent-expected')}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
}

function renderResolvedContextSection(resolvedContext) {
  if (!Array.isArray(resolvedContext) || resolvedContext.length === 0) {
    return <Text type="secondary">—</Text>;
  }
  return (
    <Space direction="vertical" size={8} style={{ width: '100%' }}>
      {resolvedContext.map((entry, index) => {
        if (!isPlainObject(entry)) {
          return (
            <Card key={`context-${index}`} size="small">
              {renderAuditData(entry, `resolved-context-${index}`)}
            </Card>
          );
        }
        const transferMode = entry.transfer_mode || 'by_ref';
        const inlineTokensApprox = typeof entry.size_tokens_approx === 'number'
          ? entry.size_tokens_approx
          : (typeof entry.size_bytes === 'number' && entry.size_bytes > 0
            ? Math.max(1, Math.round(entry.size_bytes / 4))
            : null);
        return (
          <Card key={`context-${index}`} size="small">
            <Space direction="vertical" size={6} style={{ width: '100%' }}>
              <Space size={8} wrap>
                <Text className="mono">{entry.type || 'unknown'}</Text>
                {'artifact_ref' === entry.type && (
                  <Tag color={transferMode === 'by_value' ? 'gold' : 'blue'}>
                    transfer: {transferMode}
                  </Tag>
                )}
                {entry.artifact_key && <Text className="mono">{entry.artifact_key}</Text>}
              </Space>
              {entry.path && <Text type="secondary">Path: <span className="mono">{entry.path}</span></Text>}
              {entry.source_node_id && <Text type="secondary">Source node: <span className="mono">{entry.source_node_id}</span></Text>}
              {transferMode === 'by_value' && inlineTokensApprox !== null && (
                <Text type="secondary">
                  Inline size: ~{inlineTokensApprox} tokens
                  {typeof entry.size_bytes === 'number' ? ` (${entry.size_bytes} B)` : ''}
                </Text>
              )}
            </Space>
          </Card>
        );
      })}
    </Space>
  );
}

function renderAuditSections(item) {
  const payload = item.payload || {};
  const sections = [];

  sections.push(
    <Card key="meta" size="small" title="Event">
      <Descriptions bordered size="small" column={1}>
        <Descriptions.Item label="Sequence No">{item.sequence_no}</Descriptions.Item>
        <Descriptions.Item label="Event Type">{item.event_type}</Descriptions.Item>
        <Descriptions.Item label="Time">{formatDate(item.event_time)}</Descriptions.Item>
        <Descriptions.Item label="Actor">{item.actor_id || item.actor_type}</Descriptions.Item>
        <Descriptions.Item label="Node Execution Id">{item.node_execution_id || '—'}</Descriptions.Item>
        <Descriptions.Item label="Gate Id">{item.gate_id || '—'}</Descriptions.Item>
      </Descriptions>
    </Card>,
  );

  if (payload.agent_input) {
    sections.push(<div key="agent-input">{renderAgentInputSection(payload.agent_input)}</div>);
  }

  if (payload.rendered_prompt) {
    sections.push(
      <Card key="prompt" size="small" title="Prompt Package">
        <pre className="code-block">{payload.rendered_prompt}</pre>
      </Card>,
    );
  }

  if (payload.resolved_context) {
    sections.push(
      <Card key="context" size="small" title="Resolved Context">
        {renderResolvedContextSection(payload.resolved_context)}
      </Card>,
    );
  }

  if (payload.runtime_files) {
    sections.push(
      <Card key="runtime-files" size="small" title="Runtime Files">
        {renderAuditData(payload.runtime_files, 'runtime-files')}
      </Card>,
    );
  }

  const remainingPayload = Object.fromEntries(
    Object.entries(payload).filter(([key]) => !['agent_input', 'rendered_prompt', 'resolved_context', 'runtime_files'].includes(key)),
  );

  if (Object.keys(remainingPayload).length > 0) {
    sections.push(
      <Card key="details" size="small" title="Details">
        {renderAuditData(remainingPayload, 'remaining-payload')}
      </Card>,
    );
  }

  return <Space direction="vertical" size={12} style={{ width: '100%' }}>{sections}</Space>;
}

function AuditTab({ runId, nodes, preFilterNodeId }) {
  const [events, setEvents] = useState([]);
  const [filterNodeId, setFilterNodeId] = useState(undefined);
  const [filterEventType, setFilterEventType] = useState(undefined);
  const [filterActorType, setFilterActorType] = useState(undefined);
  const [expandedEventKeys, setExpandedEventKeys] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);

  const nodeOptions = useMemo(() => {
    const seen = new Map();
    for (const n of nodes) {
      if (n.node_execution_id && !seen.has(n.node_execution_id)) {
        seen.set(n.node_execution_id, { value: n.node_execution_id, label: `${n.node_id} #${n.attempt_no}` });
      }
    }
    return Array.from(seen.values());
  }, [nodes]);

  useEffect(() => {
    setFilterNodeId(preFilterNodeId || undefined);
  }, [preFilterNodeId]);

  const fetchEvents = async (cursor) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (filterNodeId) params.set('nodeExecutionId', filterNodeId);
      if (filterEventType) params.set('eventType', filterEventType);
      if (filterActorType) params.set('actorType', filterActorType);
      if (cursor) params.set('cursor', String(cursor));
      params.set('limit', '50');
      const data = await apiRequest(`/runs/${runId}/audit/query?${params.toString()}`);
      if (cursor) {
        setEvents((prev) => [...prev, ...(data.events || [])]);
      } else {
        setEvents(data.events || []);
      }
      setNextCursor(data.next_cursor || null);
      setHasMore(data.has_more || false);
    } catch (err) {
      message.error(err.message || 'Failed to load audit');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvents(null);
  }, [runId, filterNodeId, filterEventType, filterActorType]);

  useEffect(() => {
    setExpandedEventKeys([]);
  }, [runId, filterNodeId, filterEventType, filterActorType]);

  const columns = [
    {
      title: 'Event',
      dataIndex: 'event_type',
      key: 'event_type',
      render: (value) => <Text strong>{value || '—'}</Text>,
    },
    {
      title: 'Status',
      dataIndex: 'actor_type',
      key: 'actor_type',
      width: 130,
      render: (value) => <StatusTag value={value || '—'} />,
    },
    {
      title: 'Sequence',
      dataIndex: 'sequence_no',
      key: 'sequence_no',
      width: 120,
      align: 'left',
      render: (value) => <span className="mono">{value ?? '—'}</span>,
    },
    {
      title: 'Time',
      dataIndex: 'event_time',
      key: 'event_time',
      width: 180,
      render: (value) => <span className="mono">{formatDate(value)}</span>,
    },
  ];

  return (
    <div>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          allowClear
          placeholder="Node execution"
          value={filterNodeId}
          onChange={setFilterNodeId}
          options={nodeOptions}
          style={{ minWidth: 180 }}
        />
        <Select
          allowClear
          placeholder="Event type"
          value={filterEventType}
          onChange={setFilterEventType}
          style={{ minWidth: 180 }}
          options={[
            { value: 'prompt_package_built', label: 'prompt_package_built' },
            { value: 'agent_invocation_started', label: 'agent_invocation_started' },
            { value: 'agent_invocation_finished', label: 'agent_invocation_finished' },
            { value: 'command_invocation_started', label: 'command_invocation_started' },
            { value: 'command_invocation_finished', label: 'command_invocation_finished' },
            { value: 'gate_opened', label: 'gate_opened' },
            { value: 'gate_approved', label: 'gate_approved' },
            { value: 'gate_rework_requested', label: 'gate_rework_requested' },
            { value: 'gate_input_submitted', label: 'gate_input_submitted' },
            { value: 'node_execution_started', label: 'node_execution_started' },
            { value: 'node_execution_succeeded', label: 'node_execution_succeeded' },
            { value: 'node_execution_failed', label: 'node_execution_failed' },
          ]}
        />
        <Select
          allowClear
          placeholder="Actor type"
          value={filterActorType}
          onChange={setFilterActorType}
          style={{ minWidth: 120 }}
          options={[
            { value: 'system', label: 'System' },
            { value: 'human', label: 'Human' },
            { value: 'agent', label: 'Agent' },
          ]}
        />
      </Space>
      {events.length === 0 && !loading ? (
        <Empty description="No audit events" />
      ) : (
        <Table
          size="small"
          rowKey="event_id"
          loading={loading}
          columns={columns}
          dataSource={events}
          pagination={false}
          expandable={{
            expandedRowRender: (record) => renderAuditSections(record),
            expandedRowKeys: expandedEventKeys,
            onExpand: (expanded, record) => {
              setExpandedEventKeys(expanded ? [record.event_id] : []);
            },
          }}
        />
      )}
      {hasMore && (
        <Button
          style={{ marginTop: 12 }}
          onClick={() => fetchEvents(nextCursor)}
          loading={loading}
        >
          Load more
        </Button>
      )}
    </div>
  );
}

function RunListView({ navigate }) {
  const [runs, setRuns] = useState([]);
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [statusFilter, setStatusFilter] = useState(null);
  const [projectFilter, setProjectFilter] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      const [runsData, projectsData] = await Promise.all([
        apiRequest('/runs?limit=200'),
        apiRequest('/projects'),
      ]);
      setRuns(runsData || []);
      setProjects(projectsData || []);
    } catch (err) {
      message.error(err.message || 'Failed to load latest runs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const projectNames = useMemo(() => {
    const next = new Map();
    for (const project of projects) {
      next.set(project.id, project.name);
    }
    return next;
  }, [projects]);

  const rows = useMemo(() => {
    const query = normalizeSearch(searchText);
    return runs
      .map((run) => ({
        ...run,
        project_name: projectNames.get(run.project_id) || run.project_id || '—',
      }))
      .filter((run) => {
        if (statusFilter && run.status !== statusFilter) {
          return false;
        }
        if (projectFilter && run.project_id !== projectFilter) {
          return false;
        }
        if (!query) {
          return true;
        }
        const searchable = [
          run.run_id,
          run.project_name,
          run.target_branch,
          run.flow_canonical_name,
          run.status,
          run.publish_status,
          run.current_node_id,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        return searchable.includes(query);
      });
  }, [projectFilter, projectNames, runs, searchText, statusFilter]);

  const statusOptions = useMemo(() => {
    const set = new Set(runs.map((run) => run.status).filter(Boolean));
    return Array.from(set).map((value) => ({ value, label: formatStatusLabel(value) }));
  }, [runs]);

  const projectOptions = useMemo(() => projects.map((project) => ({
    value: project.id,
    label: project.name,
  })), [projects]);

  const stats = useMemo(() => {
    const total = rows.length;
    const active = rows.filter((run) => ACTIVE_RUN_STATUSES.includes(run.status)).length;
    const waitingGate = rows.filter((run) => run.status === 'waiting_gate').length;
    const publishFailed = rows.filter((run) => run.status === 'publish_failed').length;
    const completed = rows.filter((run) => run.status === 'completed').length;
    return {
      total,
      active,
      waitingGate,
      publishFailed,
      completed,
    };
  }, [rows]);

  const columns = [
    {
      title: 'Run',
      key: 'run',
      render: (_, run) => (
        <div className="run-console-run-cell">
          <div className="run-console-run-main mono">{shortId(run.run_id, 12)}</div>
          <div className="run-console-run-meta">
            <span className="mono">{run.flow_canonical_name || '—'}</span>
            <span>{run.project_name}</span>
          </div>
        </div>
      ),
    },
    {
      title: 'Started',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 180,
      responsive: ['md'],
      render: (value) => <span className="mono">{formatDate(value)}</span>,
    },
    {
      title: 'Project',
      dataIndex: 'project_name',
      key: 'project_name',
      width: 220,
      responsive: ['xl'],
    },
    {
      title: 'Branch',
      dataIndex: 'target_branch',
      key: 'target_branch',
      width: 140,
      responsive: ['lg'],
      render: (value) => <span className="mono">{value || '—'}</span>,
    },
    {
      title: 'Node',
      dataIndex: 'current_node_id',
      key: 'current_node_id',
      width: 170,
      responsive: ['xl'],
      render: (value) => <span className="mono">{value || '—'}</span>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (value) => <StatusTag value={value} />,
    },
    {
      title: 'Publish',
      dataIndex: 'publish_status',
      key: 'publish_status',
      width: 140,
      responsive: ['lg'],
      render: (value) => value ? <StatusTag value={value} /> : '—',
    },
  ];

  return (
    <div className="cards-page run-console-page">
      <div className="page-header run-console-header">
        <div className="run-console-title-block">
          <Title level={3} style={{ margin: 0 }}>Run Console</Title>
          <Text type="secondary">Monitor execution progress, gates, and publication state across runs.</Text>
        </div>
        <Space wrap className="run-console-list-controls">
          <Input
            allowClear
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="Search run id, flow, branch, node"
            prefix={<SearchOutlined />}
            style={{ width: 320 }}
          />
          <Select
            allowClear
            value={statusFilter}
            onChange={(value) => setStatusFilter(value || null)}
            options={statusOptions}
            placeholder="Status"
            optionFilterProp="label"
            style={{ width: 180 }}
          />
          <Select
            allowClear
            value={projectFilter}
            onChange={(value) => setProjectFilter(value || null)}
            options={projectOptions}
            placeholder="Project"
            optionFilterProp="label"
            style={{ width: 220 }}
          />
          <Button onClick={load} loading={loading} icon={<ReloadOutlined />}>Refresh</Button>
        </Space>
      </div>

      <div className="run-console-summary-grid">
        <Card className="run-console-summary-card">
          <div className="card-label">Total Runs</div>
          <div className="run-console-summary-value">{stats.total}</div>
        </Card>
        <Card className="run-console-summary-card run-console-summary-card-active">
          <div className="card-label">Active</div>
          <div className="run-console-summary-value">{stats.active}</div>
        </Card>
        <Card className="run-console-summary-card run-console-summary-card-waiting">
          <div className="card-label">Waiting Gate</div>
          <div className="run-console-summary-value">{stats.waitingGate}</div>
        </Card>
        <Card className="run-console-summary-card run-console-summary-card-failed">
          <div className="card-label">Publish Failed</div>
          <div className="run-console-summary-value">{stats.publishFailed}</div>
        </Card>
        <Card className="run-console-summary-card run-console-summary-card-success">
          <div className="card-label">Completed</div>
          <div className="run-console-summary-value">{stats.completed}</div>
        </Card>
      </div>

      <Card className="run-console-list-table-card">
        {rows.length === 0 ? (
          <Empty description="No runs match current filters" />
        ) : (
          <Table
            rowKey="run_id"
            loading={loading}
            columns={columns}
            dataSource={rows}
            tableLayout="fixed"
            sticky
            scroll={{ x: 1100, y: 'calc(100vh - 420px)' }}
            pagination={{ pageSize: 20, showSizeChanger: false }}
            rowClassName={(record) => `run-console-row run-console-row-${record.status || 'unknown'}`}
            onRow={(record) => ({
              onClick: () => navigate(`/run-console?runId=${record.run_id}`),
              style: { cursor: 'pointer' },
            })}
          />
        )}
      </Card>
    </div>
  );
}

const FINISHED_STATUSES = ['succeeded', 'failed', 'cancelled'];
const ACTIVE_STATUSES = ['running', 'waiting_gate'];

function nodeTimelineColor(status) {
  if (status === 'succeeded') return '#16a34a';
  if (status === 'failed') return '#dc2626';
  if (ACTIVE_STATUSES.includes(status)) return '#2563eb';
  if (status === 'created') return '#94a3b8';
  if (status === 'cancelled') return '#64748b';
  return '#d4d4d8';
}

function nodeTimelineIcon(status) {
  if (status === 'succeeded') return <CheckCircleOutlined style={{ fontSize: 12 }} />;
  if (status === 'failed') return <CloseCircleOutlined style={{ fontSize: 12 }} />;
  if (status === 'running') return <LoadingOutlined style={{ fontSize: 12 }} />;
  if (status === 'waiting_gate') return <SyncOutlined style={{ fontSize: 12 }} />;
  if (status === 'cancelled') return <MinusCircleOutlined style={{ fontSize: 12 }} />;
  return <ClockCircleOutlined style={{ fontSize: 12 }} />;
}

function NodeLogTab({ runId, nodeExecutionId }) {
  const [logContent, setLogContent] = useState('');
  const [offset, setOffset] = useState(0);
  const [running, setRunning] = useState(true);
  const preRef = useRef(null);
  const autoScroll = useRef(true);

  const handleScroll = () => {
    const el = preRef.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
    autoScroll.current = atBottom;
  };

  useEffect(() => {
    setLogContent('');
    setOffset(0);
    setRunning(true);
    autoScroll.current = true;
  }, [nodeExecutionId]);

  useEffect(() => {
    if (!nodeExecutionId) return undefined;
    let cancelled = false;
    const poll = async () => {
      if (cancelled) return;
      try {
        const data = await apiRequest(
          `/runs/${runId}/nodes/${nodeExecutionId}/log?offset=${offset}`
        );
        if (cancelled) return;
        if (data.content) {
          setLogContent((prev) => prev + data.content);
        }
        setOffset(data.offset);
        setRunning(data.running);
      } catch (_) { /* ignore polling errors */ }
    };
    poll();
    const timerId = window.setInterval(poll, running ? 1500 : 5000);
    return () => { cancelled = true; window.clearInterval(timerId); };
  }, [runId, nodeExecutionId, offset, running]);

  useEffect(() => {
    if (autoScroll.current && preRef.current) {
      preRef.current.scrollTop = preRef.current.scrollHeight;
    }
  }, [logContent]);

  if (!nodeExecutionId) {
    return <Empty description="Select a node in the timeline" />;
  }

  return (
    <pre
      ref={preRef}
      onScroll={handleScroll}
      className="node-log-pre"
    >
      {logContent || (running ? 'Waiting for output...' : 'Log is empty.')}
    </pre>
  );
}

function RunDetailView({ navigate, runId, searchParams, setSearchParams }) {
  const [run, setRun] = useState(null);
  const [nodes, setNodes] = useState([]);
  const [runtimeSettings, setRuntimeSettings] = useState(null);
  const [auditEventsCount, setAuditEventsCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState(null);
  const timelineEndRef = useRef(null);
  const notificationPermissionRequestedRef = useRef(false);
  const activeTab = searchParams.get('tab') || 'overview';
  const setActiveTab = (tab) => {
    const next = new URLSearchParams(searchParams);
    next.set('tab', tab);
    setSearchParams(next, { replace: true });
  };

  const load = async ({ silent = false } = {}) => {
    if (!runId) {
      return;
    }
    setLoading(true);
    try {
      const [runResult, nodeResult, settingsResult, auditResult] = await Promise.allSettled([
        apiRequest(`/runs/${runId}`),
        apiRequest(`/runs/${runId}/nodes`),
        apiRequest('/settings/runtime'),
        apiRequest(`/runs/${runId}/audit`),
      ]);

      const errors = [];
      if (runResult.status === 'fulfilled') {
        setRun(runResult.value);
      } else {
        errors.push(runResult.reason);
      }

      if (nodeResult.status === 'fulfilled') {
        setNodes(nodeResult.value || []);
      } else {
        errors.push(nodeResult.reason);
      }

      if (settingsResult.status === 'fulfilled') {
        setRuntimeSettings(settingsResult.value || null);
      } else {
        errors.push(settingsResult.reason);
      }

      if (auditResult.status === 'fulfilled') {
        setAuditEventsCount(Array.isArray(auditResult.value) ? auditResult.value.length : 0);
      } else {
        errors.push(auditResult.reason);
      }

      if (!silent && errors.length === 4) {
        message.error(errors[0]?.message || 'Failed to load run');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (runId) {
      localStorage.setItem('lastRunId', runId);
    }
    notificationPermissionRequestedRef.current = false;
    load();
  }, [runId]);

  useEffect(() => {
    const gate = run?.current_gate;
    const gateId = gate?.gate_id;
    if (!runId || !gateId) {
      return;
    }
    const gateNotificationKey = `${runId}:${gateId}`;
    if (hasGateBeenNotified(gateNotificationKey)) {
      return;
    }
    markGateNotified(gateNotificationKey);
    const title = gate.gate_kind === 'human_input' ? 'Human Input Gate opened' : 'Human Approval Gate opened';
    const body = gate.payload?.user_instructions
      ? String(gate.payload.user_instructions).slice(0, 160)
      : `Node: ${gate.node_id || 'unknown'}`;
    message.info({
      key: `run-${runId}-gate-${gateId}`,
      content: `${title}. ${body}`,
      duration: 6,
    });
    if (typeof window === 'undefined' || !('Notification' in window)) {
      return;
    }
    const notify = () => {
      try {
        new Notification(title, { body, tag: `run-${runId}-gate-${gateId}` });
      } catch (_) {
        // ignore browser notification errors
      }
    };
    if (window.Notification.permission === 'granted') {
      notify();
      return;
    }
    if (window.Notification.permission === 'default' && !notificationPermissionRequestedRef.current) {
      notificationPermissionRequestedRef.current = true;
      window.Notification.requestPermission()
        .then((permission) => {
          if (permission === 'granted') {
            notify();
          }
        })
        .catch(() => {});
    }
  }, [run?.current_gate, runId]);

  useEffect(() => {
    if (!runId) {
      return undefined;
    }
    if (run && !ACTIVE_RUN_STATUSES.includes(run.status)) {
      return undefined;
    }
    const timerId = window.setInterval(() => {
      load({ silent: true });
    }, 2000);
    return () => window.clearInterval(timerId);
  }, [runId, run?.status]);

  useEffect(() => {
    if (timelineEndRef.current) {
      timelineEndRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  }, [nodes.length]);

  const logNodeExecutionId = useMemo(() => {
    if (selectedNodeId) return selectedNodeId;
    const active = nodes.find((n) => n.status === 'running');
    return active?.node_execution_id || null;
  }, [selectedNodeId, nodes]);
  const currentGate = run?.current_gate || null;
  const gateGitSummary = currentGate?.payload?.git_summary || null;
  const gateGitChanges = Array.isArray(currentGate?.payload?.git_changes)
    ? currentGate.payload.git_changes
    : [];

  const cancelRun = async () => {
    try {
      await apiRequest(`/runs/${runId}/cancel`, { method: 'POST' });
      await load();
    } catch (err) {
      message.error(err.message || 'Failed to cancel run');
    }
  };

  const retryPublish = async () => {
    try {
      await apiRequest(`/runs/${runId}/publish/retry`, { method: 'POST' });
      message.success('Publish retry requested');
      await load();
    } catch (err) {
      message.error(err.message || 'Failed to retry publish');
    }
  };

  const latestFailedAiNode = nodes
    .filter((n) => n.status === 'failed' && n.node_kind === 'ai' && n.error_code === 'NODE_VALIDATION_FAILED')
    .sort((a, b) => b.attempt_no - a.attempt_no)[0] || null;

  const canRetryAiValidation = run != null
    && run.status === 'failed'
    && run.error_code === 'NODE_VALIDATION_FAILED'
    && latestFailedAiNode !== null;

  const retryAiValidation = () => {
    Modal.confirm({
      title: 'Retry AI node attempt?',
      content: 'Runtime will start a new attempt on the same AI node with the same instruction and inputs.',
      okText: 'Retry',
      cancelText: 'Cancel',
      onOk: async () => {
        try {
          await apiRequest(`/runs/${runId}/retry`, { method: 'POST' });
          message.success('Retry requested');
          await load();
        } catch (err) {
          message.error(err.message || 'Failed to retry');
        }
      },
    });
  };

  const giveUpAiValidation = () => {
    Modal.confirm({
      title: 'Give up and continue via on_failure path?',
      content: 'Runtime will skip the AI node and continue the flow through the on_failure transition.',
      okText: 'Give up',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk: async () => {
        try {
          await apiRequest(`/runs/${runId}/give-up`, { method: 'POST' });
          message.success('Continuing via on_failure path');
          await load();
        } catch (err) {
          message.error(err.message || 'Failed to give up');
        }
      },
    });
  };

  if (!run) {
    return <Card loading={loading} />;
  }

  const publishPhaseVisible = isPublishPhaseVisible(run);
  const isBenchmarkRun = String(run.flow_canonical_name || '').startsWith('benchmark:');
  const benchmarkGateBlocked = isBenchmarkRun && Boolean(run.current_gate);
  const publishStages = [
    {
      key: 'publish',
      label: 'Publish',
      status: publishPhaseVisible ? run.publish_status : null,
      hint: run.publish_mode === 'pr' ? 'Prepare final release commit' : 'Prepare final branch commit',
    },
    {
      key: 'push',
      label: 'Push',
      status: publishPhaseVisible ? run.push_status : null,
      hint: 'Push work branch to remote',
    },
    {
      key: 'pr',
      label: 'Pull Request',
      status: publishPhaseVisible ? run.pr_status : null,
      hint: 'Open pull request to target branch',
    },
  ];

  return (
    <div className="run-console-page run-console-detail-page">
      <div className="page-header run-console-header">
        <div className="run-console-title-block">
          <Title level={3} style={{ margin: 0 }}>Run Console</Title>
          <Text type="secondary">
            Run <span className="mono">{shortId(run.run_id, 12)}</span> · started {formatDate(run.created_at)}
          </Text>
        </div>
        <Space wrap>
          <Button onClick={() => load()} loading={loading} icon={<ReloadOutlined />}>Refresh</Button>
          {run.status === 'publish_failed' && (
            <Button type="primary" onClick={retryPublish}>
              Retry publish
            </Button>
          )}
          {canRetryAiValidation && (
            <Button type="primary" onClick={retryAiValidation} icon={<ReloadOutlined />}>
              Retry AI node
            </Button>
          )}
          {canRetryAiValidation && (
            <Button danger onClick={giveUpAiValidation}>
              Give up
            </Button>
          )}
          <Button
            type="default"
            danger
            className="btn-danger-common"
            onClick={cancelRun}
            icon={<MinusCircleOutlined />}
            disabled={['completed', 'failed', 'cancelled'].includes(run.status)}
          >
            Stop
          </Button>
        </Space>
      </div>

      <div className="run-console-summary-grid run-console-detail-summary-grid">
        <Card className="run-console-summary-card run-console-summary-card-active">
          <div className="card-label">Run Status</div>
          <div className="run-console-summary-value"><StatusTag value={run.status} /></div>
        </Card>
        <Card className="run-console-summary-card">
          <div className="card-label">Current Node</div>
          <div className="run-console-summary-value mono">{run.current_node_id || '—'}</div>
        </Card>
        <Card className="run-console-summary-card run-console-summary-card-waiting">
          <div className="card-label">Current Gate</div>
          <div className="run-console-summary-value">{run.current_gate ? <StatusTag value={run.current_gate.status} /> : '—'}</div>
        </Card>
        <Card className="run-console-summary-card">
          <div className="card-label">Workflow</div>
          <div className="run-console-summary-value mono">{run.flow_canonical_name || '—'}</div>
        </Card>
        <Card className="run-console-summary-card">
          <div className="card-label">Target Branch</div>
          <div className="run-console-summary-value mono">{run.target_branch || '—'}</div>
        </Card>
        <Card className="run-console-summary-card">
          <div className="card-label">Workspace</div>
          <div className="run-console-summary-value mono">{run.workspace_root || runtimeSettings?.workspace_root || '—'}</div>
        </Card>
        <Card className="run-console-summary-card">
          <div className="card-label">Coding Agent</div>
          <div className="run-console-summary-value mono">{runtimeSettings?.coding_agent || '—'}</div>
        </Card>
        <Card className="run-console-summary-card">
          <div className="card-label">Audit Events</div>
          <div className="run-console-summary-value">{auditEventsCount}</div>
        </Card>
      </div>

      <Row gutter={[16, 16]} className="run-console-detail-grid">
        <Col xs={24} lg={5} className="run-console-detail-col">
          <Card
            title={<span style={{ fontSize: 13 }}>Node Timeline</span>}
            className="timeline-card run-console-timeline-card"
            size="small"
            style={{ maxHeight: 640, overflow: 'auto' }}
            extra={selectedNodeId && (
              <Button type="default" size="small" onClick={() => setSelectedNodeId(null)} className="run-console-clear-btn">
                Clear
              </Button>
            )}
          >
            {nodes.length === 0 ? (
              <Empty description="Wait for node to be started" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <Timeline
                items={nodes.map((item) => {
                  const isActive = item.node_execution_id === selectedNodeId;
                  return {
                    color: nodeTimelineColor(item.status),
                    dot: nodeTimelineIcon(item.status),
                    children: (
                      <div
                        className={`node-timeline-item${isActive ? ' is-active' : ''}`}
                        onClick={() => setSelectedNodeId(isActive ? null : item.node_execution_id)}
                      >
                        <div className="node-timeline-name">{item.node_id}</div>
                        <div className="node-timeline-meta">
                          <Tag color={nodeTimelineColor(item.status)} style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>{formatStatusLabel(item.status)}</Tag>
                          {item.attempt_no > 1 && <span className="mono" style={{ fontSize: 10 }}>#{item.attempt_no}</span>}
                        </div>
                      </div>
                    ),
                  };
                })}
              />
            )}
            <div ref={timelineEndRef} />
          </Card>
        </Col>

        <Col xs={24} lg={13} className="run-console-detail-col">
          <Card className="run-console-main-card">
            <Tabs
              activeKey={activeTab}
              onChange={setActiveTab}
              items={[
                {
                  key: 'overview',
                  label: 'Overview',
                  children: (
                    <Row gutter={[12, 12]}>
                      <Col span={8}>
                        <Card className="metric-card">
                          <Text className="card-label" type="secondary">Changed files</Text>
                          <div className="metric-value">{run.current_gate?.payload?.git_summary?.files_changed ?? '—'}</div>
                        </Card>
                      </Col>
                      <Col span={8}>
                        <Card className="metric-card">
                          <Text className="card-label" type="secondary">Node executions</Text>
                          <div className="metric-value">{selectedNodeId ? 1 : nodes.length}</div>
                        </Card>
                      </Col>
                      <Col span={8}>
                        <Card className="metric-card">
                          <Text className="card-label" type="secondary">Audit events</Text>
                          <div className="metric-value">{auditEventsCount}</div>
                        </Card>
                      </Col>
                      <Col span={24}>
                        <Card>
                          <Title level={5}>Git summary for active gate</Title>
                          {run.current_gate ? (
                            <Space direction="vertical" size={10} style={{ width: '100%' }}>
                              <Space size={16} wrap>
                                <Text><Text strong>Files:</Text> {gateGitSummary?.files_changed ?? 0}</Text>
                                <Text style={{ color: '#16a34a' }}><Text strong style={{ color: '#16a34a' }}>+</Text>{gateGitSummary?.added_lines ?? 0}</Text>
                                <Text style={{ color: '#dc2626' }}><Text strong style={{ color: '#dc2626' }}>-</Text>{gateGitSummary?.removed_lines ?? 0}</Text>
                                <Tag>{gateGitSummary?.status_label || '—'}</Tag>
                              </Space>
                              <Collapse
                                size="small"
                                items={[
                                  {
                                    key: 'files',
                                    label: `Changed files (${gateGitChanges.length})`,
                                    children: (
                                      <Table
                                        size="small"
                                        pagination={false}
                                        rowKey={(record) => record.path}
                                        dataSource={gateGitChanges}
                                        columns={[
                                          { title: 'Path', dataIndex: 'path', key: 'path', render: (v) => <span className="mono">{v}</span> },
                                          { title: 'Status', dataIndex: 'status', key: 'status', width: 110 },
                                          { title: '+', dataIndex: 'added', key: 'added', width: 70, render: (v) => <span style={{ color: '#16a34a' }}>{v || 0}</span> },
                                          { title: '-', dataIndex: 'removed', key: 'removed', width: 70, render: (v) => <span style={{ color: '#dc2626' }}>{v || 0}</span> },
                                        ]}
                                      />
                                    ),
                                  },
                                ]}
                              />
                            </Space>
                          ) : (
                            <Text type="secondary">No active gate</Text>
                          )}
                        </Card>
                      </Col>
                    </Row>
                  ),
                },
                {
                  key: 'publish',
                  label: 'Publish',
                  children: (
                    <div className="publish-tab-layout">
                      <Card className="publish-hero-card" bordered={false}>
                        <div className="publish-hero-head">
                          <div>
                            <Text className="publish-eyebrow">Delivery</Text>
                            <Title level={4} className="publish-hero-title">{publishHeadline(run)}</Title>
                            <Text type="secondary">{publishSubtitle(run)}</Text>
                          </div>
                          <Space wrap size={[8, 8]} className="publish-hero-tags">
                            <StatusTag value={run.publish_mode} />
                            {publishPhaseVisible && <StatusTag value={run.publish_status} />}
                          </Space>
                        </div>
                        <Row gutter={[10, 10]} className="publish-meta-grid">
                          <Col xs={24} md={12} xl={6}>
                            <div className="publish-meta-card">
                              <Text className="publish-meta-label">Work branch</Text>
                              <div className="publish-meta-value mono">{run.work_branch || '—'}</div>
                            </div>
                          </Col>
                          <Col xs={24} md={12} xl={6}>
                            <div className="publish-meta-card">
                              <Text className="publish-meta-label">PR strategy</Text>
                              <div className="publish-meta-value">{run.pr_commit_strategy || '—'}</div>
                            </div>
                          </Col>
                          <Col xs={24} md={12} xl={6}>
                            <div className="publish-meta-card">
                              <Text className="publish-meta-label">Final commit</Text>
                              <div className="publish-meta-value mono">{shortSha(run.publish_commit_sha)}</div>
                            </div>
                          </Col>
                          <Col xs={24} md={12} xl={6}>
                            <div className="publish-meta-card">
                              <Text className="publish-meta-label">PR</Text>
                              <div className="publish-meta-value">
                                {run.pr_number ? `#${run.pr_number}` : '—'}
                              </div>
                            </div>
                          </Col>
                        </Row>
                      </Card>

                      <Row gutter={[10, 10]}>
                        {publishStages.map((stage) => (
                          <Col xs={24} lg={8} key={stage.key}>
                            <div className={`publish-stage-card ${publishStageClass(stage.status)}`}>
                              <div className="publish-stage-head">
                                <span className="publish-stage-icon">{publishStageIcon(stage.key, stage.status)}</span>
                                <div>
                                  <div className="publish-stage-label">{stage.label}</div>
                                  <Text className="publish-stage-hint">{stage.hint}</Text>
                                </div>
                              </div>
                              <div className="publish-stage-footer">
                                {stage.status ? <StatusTag value={stage.status} /> : <Text type="secondary">Not started</Text>}
                              </div>
                            </div>
                          </Col>
                        ))}
                      </Row>

                      {run.pr_url && (
                        <Card size="small" className="publish-link-card">
                          <Space size={8} wrap>
                            <LinkOutlined />
                            <Text strong>Pull request:</Text>
                            <a href={run.pr_url} target="_blank" rel="noreferrer" className="publish-pr-link">{run.pr_url}</a>
                          </Space>
                        </Card>
                      )}

                      {(run.publish_error_step || run.error_message) && (
                        <Card size="small" className="publish-error-card">
                          <Space direction="vertical" size={6} style={{ width: '100%' }}>
                            <Text strong className="publish-error-title">Publish diagnostics</Text>
                            <Text>
                              <Text strong>Step:</Text> {run.publish_error_step || '—'}
                            </Text>
                            <pre className="code-block publish-error-message">{run.error_message || 'No error message'}</pre>
                          </Space>
                        </Card>
                      )}

                      <Collapse
                        size="small"
                        items={[
                          {
                            key: 'technical',
                            label: 'Technical details',
                            children: (
                              <Descriptions bordered size="small" column={1}>
                                <Descriptions.Item label="Work branch">{run.work_branch || '—'}</Descriptions.Item>
                                <Descriptions.Item label="Publish mode">{run.publish_mode || '—'}</Descriptions.Item>
                                <Descriptions.Item label="PR commit strategy">{run.pr_commit_strategy || '—'}</Descriptions.Item>
                                <Descriptions.Item label="Publish error step">{run.publish_error_step || '—'}</Descriptions.Item>
                                <Descriptions.Item label="Final commit SHA">{run.publish_commit_sha || '—'}</Descriptions.Item>
                                <Descriptions.Item label="PR URL">
                                  {run.pr_url ? (
                                    <a href={run.pr_url} target="_blank" rel="noreferrer">{run.pr_url}</a>
                                  ) : '—'}
                                </Descriptions.Item>
                                <Descriptions.Item label="PR number">{run.pr_number ?? '—'}</Descriptions.Item>
                              </Descriptions>
                            ),
                          },
                        ]}
                      />
                    </div>
                  ),
                },
                {
                  key: 'audit',
                  label: 'Audit',
                  children: (
                    <AuditTab runId={runId} nodes={nodes} preFilterNodeId={selectedNodeId} />
                  ),
                },
                {
                  key: 'log',
                  label: 'Log',
                  children: (
                    <NodeLogTab runId={runId} nodeExecutionId={logNodeExecutionId} />
                  ),
                },
              ]}
            />
          </Card>
        </Col>

        <Col xs={24} lg={6} className="run-console-detail-col">
          <Card size="small" title="Human Gate" className="run-console-gate-card">
            {run.current_gate ? (
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                <Text>
                  You are in <Text strong>{run.current_gate.gate_kind}</Text>
                </Text>
                {benchmarkGateBlocked && (
                  <Text type="secondary">
                    Gate completion is blocked for benchmark runs. Run will be completed after verdict submission in Benchmark.
                  </Text>
                )}
                <Text className="muted">Instruction</Text>
                <pre className="code-block" style={{ maxHeight: 200, overflow: 'auto' }}>
                  {run.current_gate.payload?.user_instructions || '—'}
                </pre>
                <div style={{ display: 'flex', justifyContent: 'flex-end', width: '100%' }}>
                  <Button
                    type="default"
                    disabled={benchmarkGateBlocked}
                    onClick={() => navigate(`/human-gate?runId=${runId}&gateId=${run.current_gate.gate_id}&gateKind=${run.current_gate.gate_kind}`)}
                  >
                    Go to Gate
                  </Button>
                </div>
              </Space>
            ) : (
              <Text type="secondary">No active gate</Text>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default function RunConsole() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const runId = searchParams.get('runId');

  if (!runId) {
    return <RunListView navigate={navigate} />;
  }

  return <RunDetailView navigate={navigate} runId={runId} searchParams={searchParams} setSearchParams={setSearchParams} />;
}
