import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Empty,
  List,
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
import { CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined, LoadingOutlined, MinusCircleOutlined, SyncOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import StatusTag, { formatStatusLabel } from '../components/StatusTag.jsx';
import ActionCenter from '../components/ActionCenter.jsx';
import ArtifactViewer from '../components/ArtifactViewer.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;
const ACTIVE_RUN_STATUSES = ['created', 'running', 'waiting_gate'];

function formatDate(value) {
  if (!value) {
    return '—';
  }
  return new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(new Date(value));
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
              {transferMode === 'by_value' && typeof entry.size_bytes === 'number' && (
                <Text type="secondary">Inline size: {entry.size_bytes} B</Text>
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

  const items = events.map((item) => ({
    key: item.event_id,
    label: (
      <Space size={12} wrap>
        <Text strong>{item.event_type}</Text>
        <StatusTag value={item.actor_type} />
        <Text type="secondary">seq {item.sequence_no}</Text>
        <Text type="secondary">{formatDate(item.event_time)}</Text>
      </Space>
    ),
    children: renderAuditSections(item),
  }));

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
      {items.length === 0 && !loading ? (
        <Empty description="No audit events" />
      ) : (
        <Collapse items={items} accordion />
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

  const load = async () => {
    setLoading(true);
    try {
      const [runsData, projectsData] = await Promise.all([
        apiRequest('/runs?limit=20'),
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

  const columns = [
    {
      title: 'Started',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 180,
      render: (value) => <span className="mono">{formatDate(value)}</span>,
    },
    {
      title: 'Project',
      dataIndex: 'project_id',
      key: 'project_id',
      render: (value) => projectNames.get(value) || value,
    },
    {
      title: 'Branch',
      dataIndex: 'target_branch',
      key: 'target_branch',
      width: 140,
      render: (value) => <span className="mono">{value}</span>,
    },
    {
      title: 'Flow',
      dataIndex: 'flow_canonical_name',
      key: 'flow_canonical_name',
      render: (value) => <span className="mono">{value}</span>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (value) => <StatusTag value={value} />,
    },
    {
      title: 'Run ID',
      dataIndex: 'run_id',
      key: 'run_id',
      render: (value) => <span className="mono">{value}</span>,
    },
  ];

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Recent Runs</Title>
      </div>
      <Card>
        {runs.length === 0 ? (
          <Empty description="No runs yet" />
        ) : (
          <Table
            rowKey="run_id"
            loading={loading}
            columns={columns}
            dataSource={runs}
            pagination={false}
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
  const [artifacts, setArtifacts] = useState([]);
  const [runtimeSettings, setRuntimeSettings] = useState(null);
  const [loading, setLoading] = useState(false);
  const [selectedArtifact, setSelectedArtifact] = useState(null);
  const [artifactSubmitHandler, setArtifactSubmitHandler] = useState(null);
  const [selectedNodeId, setSelectedNodeId] = useState(null);
  const timelineEndRef = useRef(null);
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
      const [runResult, nodeResult, artifactResult, settingsResult] = await Promise.allSettled([
        apiRequest(`/runs/${runId}`),
        apiRequest(`/runs/${runId}/nodes`),
        apiRequest(`/runs/${runId}/artifacts`),
        apiRequest('/settings/runtime'),
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

      if (artifactResult.status === 'fulfilled') {
        setArtifacts(artifactResult.value || []);
      } else {
        errors.push(artifactResult.reason);
      }

      if (settingsResult.status === 'fulfilled') {
        setRuntimeSettings(settingsResult.value || null);
      } else {
        errors.push(settingsResult.reason);
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
    load();
  }, [runId]);

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

  const latestArtifacts = useMemo(() => artifacts.slice(0, 8), [artifacts]);

  const selectedNodeIdRef = nodes.find((n) => n.node_execution_id === selectedNodeId);
  const selectedNodeIdStr = selectedNodeIdRef?.node_id || null;

  const logNodeExecutionId = useMemo(() => {
    if (selectedNodeId) return selectedNodeId;
    const active = nodes.find((n) => n.status === 'running');
    return active?.node_execution_id || null;
  }, [selectedNodeId, nodes]);

  const filteredArtifacts = useMemo(() => {
    if (!selectedNodeIdStr) return artifacts;
    return artifacts.filter((a) => a.node_id === selectedNodeIdStr);
  }, [artifacts, selectedNodeIdStr]);

  const toRelativeRunScopePath = (rawPath) => {
    if (!rawPath) {
      return null;
    }
    if (!rawPath.startsWith('/')) {
      return rawPath;
    }
    const workspaceRoot = run?.workspace_root || '';
    if (workspaceRoot && rawPath.startsWith(workspaceRoot + '/.hgsdlc/')) {
      return rawPath.slice((workspaceRoot + '/.hgsdlc/').length);
    }
    const runScopeMarker = '/.hgsdlc/';
    const runScopeIndex = rawPath.indexOf(runScopeMarker);
    if (runScopeIndex >= 0) {
      return rawPath.slice(runScopeIndex + runScopeMarker.length);
    }
    return rawPath;
  };

  const humanInputArtifactsFromGate = useMemo(() => {
    const gate = run?.current_gate;
    if (!gate || gate.gate_kind !== 'human_input') {
      return [];
    }
    const entries = Array.isArray(gate.payload?.human_input_artifacts) ? gate.payload.human_input_artifacts : [];
    return entries
      .filter((entry) => entry && entry.path)
      .map((entry) => ({
        artifact_key: entry.artifact_key || 'artifact',
        path: toRelativeRunScopePath(entry.path),
        scope: 'run',
        node_id: gate.node_id,
        required: entry.required !== false,
      }));
  }, [run]);

  const artifactsTableRows = useMemo(() => {
    const expectedByPath = new Map(humanInputArtifactsFromGate.map((expected) => [expected.path, expected]));
    const isExpectedMatch = (expected, artifact, relativePath) => {
      if (!expected || !artifact) {
        return false;
      }
      if (expected.node_id && artifact.node_id && expected.node_id !== artifact.node_id) {
        return false;
      }
      if (expected.artifact_key && artifact.artifact_key && expected.artifact_key !== artifact.artifact_key) {
        return false;
      }
      const expectedPath = expected.path || '';
      const artifactPath = relativePath || artifact.path || '';
      if (!expectedPath) {
        return true;
      }
      return artifactPath === expectedPath || artifactPath.endsWith(`/${expectedPath}`);
    };

    const rows = filteredArtifacts.map((artifact) => {
      const relativePath = toRelativeRunScopePath(artifact.path);
      let expected = expectedByPath.get(relativePath);
      if (!expected) {
        expected = humanInputArtifactsFromGate.find((candidate) => isExpectedMatch(candidate, artifact, relativePath)) || null;
      }
      return {
        ...artifact,
        path: expected ? expected.path : artifact.path,
        scope: expected ? 'run' : artifact.scope,
        humanInputEditable: Boolean(expected),
        humanInputGateId: expected ? run?.current_gate?.gate_id : null,
        humanInputExpectedGateVersion: expected ? run?.current_gate?.resource_version : null,
        humanInputComment: expected ? 'submitted from run console' : null,
      };
    });

    return rows;
  }, [humanInputArtifactsFromGate, filteredArtifacts, run]);

  const handleArtifactSubmitReady = useCallback((handler) => {
    setArtifactSubmitHandler(() => handler);
  }, []);

  const openHumanInputArtifactEditor = async () => {
    if (selectedArtifact?.humanInputEditable && typeof artifactSubmitHandler === 'function') {
      await artifactSubmitHandler();
      return;
    }
    const editableRows = artifactsTableRows.filter((row) => row.humanInputEditable);
    if (editableRows.length === 0) {
      message.warning('No editable artifacts found for human input');
      return;
    }
    setSelectedArtifact(editableRows[0]);
    setActiveTab('artifacts');
  };

  const cancelRun = async () => {
    try {
      await apiRequest(`/runs/${runId}/cancel`, { method: 'POST' });
      await load();
    } catch (err) {
      message.error(err.message || 'Failed to cancel run');
    }
  };

  if (!run) {
    return <Card loading={loading} />;
  }

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Run Console</Title>
        <Space>
          <Button
            type="default"
            className="btn-danger-common"
            onClick={cancelRun}
            icon={<MinusCircleOutlined />}
            disabled={['completed', 'failed', 'cancelled'].includes(run.status)}
          >
            Stop
          </Button>
        </Space>
      </div>
      <div className="summary-bar">
        <div>
          <Text className="muted">Status</Text>
          <div><StatusTag value={run.status} /></div>
        </div>
        <div>
          <Text className="muted">Current node</Text>
          <div className="mono">{run.current_node_id}</div>
        </div>
        <div>
          <Text className="muted">Current gate</Text>
          <div>{run.current_gate ? <StatusTag value={run.current_gate.status} /> : '—'}</div>
        </div>
        <div>
          <Text className="muted">Workflow</Text>
          <div className="mono">{run.flow_canonical_name}</div>
        </div>
        <div>
          <Text className="muted">Target branch</Text>
          <div className="mono">{run.target_branch}</div>
        </div>
        <div>
          <Text className="muted">Working directory</Text>
          <div className="mono">{runtimeSettings?.workspace_root || '—'}</div>
        </div>
        <div>
          <Text className="muted">Coding agent</Text>
          <div className="mono">{runtimeSettings?.coding_agent || '—'}</div>
        </div>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={5}>
          <Card
            title={<span style={{ fontSize: 13 }}>Node Timeline</span>}
            className="timeline-card"
            size="small"
            style={{ maxHeight: 600, overflow: 'auto' }}
            extra={selectedNodeId && (
              <Button type="default" size="small" onClick={() => setSelectedNodeId(null)} style={{ padding: 0, fontSize: 11 }}>
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

        <Col xs={24} lg={13}>
          <Card>
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
                          <div className="metric-value">{filteredArtifacts.length}</div>
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
                          <div className="metric-value">—</div>
                        </Card>
                      </Col>
                      <Col span={24}>
                        <Card>
                          <Title level={5}>{selectedNodeId ? 'Node artifacts' : 'Latest artifacts'}</Title>
                          <List
                            dataSource={selectedNodeId ? filteredArtifacts : latestArtifacts}
                            renderItem={(item) => (
                              <List.Item>
                                <Text className="mono">{item.artifact_key}</Text>
                                <StatusTag value={item.kind} />
                              </List.Item>
                            )}
                          />
                        </Card>
                      </Col>
                    </Row>
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
                  key: 'artifacts',
                  label: 'Artifacts',
                  children: selectedArtifact ? (
                    <ArtifactViewer
                      runId={runId}
                      artifact={selectedArtifact}
                      onClose={() => {
                        setSelectedArtifact(null);
                        setArtifactSubmitHandler(null);
                      }}
                      onSubmitted={async () => {
                        setSelectedArtifact(null);
                        setArtifactSubmitHandler(null);
                        await load();
                      }}
                      onSubmitReady={handleArtifactSubmitReady}
                    />
                  ) : (
                    <Table
                      rowKey={(record) => record.artifact_version_id || `${record.node_id}:${record.path}`}
                      dataSource={artifactsTableRows}
                      pagination={false}
                      size="small"
                      onRow={(record) => ({
                        onClick: () => setSelectedArtifact({
                          ...record,
                          humanInputEditable: record.humanInputEditable === true,
                          humanInputGateId: record.humanInputGateId || run?.current_gate?.gate_id,
                          humanInputExpectedGateVersion: record.humanInputExpectedGateVersion || run?.current_gate?.resource_version,
                          humanInputComment: 'submitted from run console',
                        }),
                        style: { cursor: 'pointer' },
                      })}
                      columns={[
                        { title: 'Key', dataIndex: 'artifact_key', key: 'artifact_key', render: (v) => <span className="mono">{v}</span> },
                        { title: 'Node', dataIndex: 'node_id', key: 'node_id', render: (v) => <span className="mono">{v}</span> },
                        { title: 'Kind', dataIndex: 'kind', key: 'kind', render: (v) => <StatusTag value={v} /> },
                        { title: 'Ver', dataIndex: 'version_no', key: 'version_no', width: 50, render: (v) => <span className="mono">v{v || 0}</span> },
                        { title: 'Size', dataIndex: 'size_bytes', key: 'size_bytes', width: 80, render: (v) => v ? `${v} B` : '—' },
                      ]}
                    />
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

        <Col xs={24} lg={6}>
          <ActionCenter run={run} onActionComplete={load} onOpenArtifactEditor={openHumanInputArtifactEditor} />
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
