import React, { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  List,
  Row,
  Space,
  Table,
  Tabs,
  Typography,
  message,
} from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
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
      message.error(err.message || 'Не удалось загрузить последние запуски');
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
        <Space>
          <Button onClick={load} loading={loading}>Refresh</Button>
          <Button type="primary" onClick={() => navigate('/run-launch')}>Launch run</Button>
        </Space>
      </div>
      <Card>
        {runs.length === 0 ? (
          <Empty description="Запусков пока нет" />
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

function RunDetailView({ navigate, runId }) {
  const [run, setRun] = useState(null);
  const [nodes, setNodes] = useState([]);
  const [artifacts, setArtifacts] = useState([]);
  const [audit, setAudit] = useState([]);
  const [loading, setLoading] = useState(false);

  const load = async ({ silent = false } = {}) => {
    if (!runId) {
      return;
    }
    setLoading(true);
    try {
      const [runResult, nodeResult, artifactResult, auditResult] = await Promise.allSettled([
        apiRequest(`/runs/${runId}`),
        apiRequest(`/runs/${runId}/nodes`),
        apiRequest(`/runs/${runId}/artifacts`),
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

      if (artifactResult.status === 'fulfilled') {
        setArtifacts(artifactResult.value || []);
      } else {
        errors.push(artifactResult.reason);
      }

      if (auditResult.status === 'fulfilled') {
        setAudit(auditResult.value || []);
      } else {
        errors.push(auditResult.reason);
      }

      if (!silent && errors.length === 4) {
        message.error(errors[0]?.message || 'Не удалось загрузить run');
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

  const latestArtifacts = useMemo(() => {
    const map = new Map();
    for (const artifact of artifacts) {
      if (!map.has(artifact.artifact_key)) {
        map.set(artifact.artifact_key, artifact);
      }
    }
    return Array.from(map.values()).slice(0, 8);
  }, [artifacts]);

  const openCurrentGate = () => {
    if (!run?.current_gate) {
      return;
    }
    const gateKind = run.current_gate.gate_kind;
    const gatePath = gateKind === 'human_input' ? '/gate-input' : '/gate-approval';
    navigate(`${gatePath}?runId=${runId}&gateId=${run.current_gate.gate_id}`);
  };

  const resumeRun = async () => {
    try {
      await apiRequest(`/runs/${runId}/resume`, { method: 'POST' });
      await load();
    } catch (err) {
      message.error(err.message || 'Не удалось продолжить run');
    }
  };

  const cancelRun = async () => {
    try {
      await apiRequest(`/runs/${runId}/cancel`, { method: 'POST' });
      await load();
    } catch (err) {
      message.error(err.message || 'Не удалось отменить run');
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
          <Button onClick={() => navigate('/run-console')}>All runs</Button>
          <Button onClick={load} loading={loading}>Refresh</Button>
          <Button onClick={resumeRun}>Resume</Button>
          <Button danger onClick={cancelRun}>Cancel</Button>
        </Space>
      </div>
      <div className="summary-bar">
        <div>
          <Text className="muted">run status</Text>
          <div><StatusTag value={run.status} /></div>
        </div>
        <div>
          <Text className="muted">current node</Text>
          <div className="mono">{run.current_node_id}</div>
        </div>
        <div>
          <Text className="muted">current gate</Text>
          <div>{run.current_gate ? <StatusTag value={run.current_gate.status} /> : '—'}</div>
        </div>
        <div>
          <Text className="muted">flow_canonical_name</Text>
          <div className="mono">{run.flow_canonical_name}</div>
        </div>
        <div>
          <Text className="muted">target branch</Text>
          <div className="mono">{run.target_branch}</div>
        </div>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={6}>
          <Card title="Node Timeline" className="timeline-card">
            <List
              dataSource={nodes}
              renderItem={(item) => (
                <List.Item>
                  <Space direction="vertical" size={0}>
                    <Text strong>{item.node_id}</Text>
                    <Text type="secondary">{item.status} · attempt {item.attempt_no}</Text>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card>
            <Tabs
              defaultActiveKey="overview"
              items={[
                {
                  key: 'overview',
                  label: 'Overview',
                  children: (
                    <Row gutter={[12, 12]}>
                      <Col span={8}>
                        <Card className="metric-card">
                          <Text className="card-label" type="secondary">Changed files</Text>
                          <div className="metric-value">{latestArtifacts.length}</div>
                        </Card>
                      </Col>
                      <Col span={8}>
                        <Card className="metric-card">
                          <Text className="card-label" type="secondary">Node executions</Text>
                          <div className="metric-value">{nodes.length}</div>
                        </Card>
                      </Col>
                      <Col span={8}>
                        <Card className="metric-card">
                          <Text className="card-label" type="secondary">Audit events</Text>
                          <div className="metric-value">{audit.length}</div>
                        </Card>
                      </Col>
                      <Col span={24}>
                        <Card>
                          <Title level={5}>Latest artifacts</Title>
                          <List
                            dataSource={latestArtifacts}
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
                  key: 'timeline',
                  label: 'Timeline',
                  children: (
                    <List
                      dataSource={audit}
                      renderItem={(item) => (
                        <List.Item>
                          <Space direction="vertical" size={0}>
                            <Text strong>{item.event_type}</Text>
                            <Text type="secondary">{item.actor_type} · seq {item.sequence_no}</Text>
                          </Space>
                          <StatusTag value={item.actor_type} />
                        </List.Item>
                      )}
                    />
                  ),
                },
                {
                  key: 'artifacts',
                  label: 'Artifacts',
                  children: (
                    <Descriptions bordered size="small" column={1}>
                      {artifacts.map((item) => (
                        <Descriptions.Item key={item.artifact_version_id} label={item.artifact_key}>
                          Node: {item.node_id} · Kind: {item.kind} · Checksum: {item.checksum || '—'}
                        </Descriptions.Item>
                      ))}
                    </Descriptions>
                  ),
                },
                {
                  key: 'audit',
                  label: 'Audit JSON',
                  children: (
                    <pre className="code-block">{JSON.stringify(audit, null, 2)}</pre>
                  ),
                },
              ]}
            />
          </Card>
        </Col>

        <Col xs={24} lg={6}>
          <Card title="Current Gate">
            <div className="card-muted">{run.current_gate?.node_id || 'No active gate'}</div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Gate status</Text>
              <div>{run.current_gate ? <StatusTag value={run.current_gate.status} /> : '—'}</div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Gate id</Text>
              <div className="mono">{run.current_gate?.gate_id || '—'}</div>
            </div>
            <div style={{ marginTop: 16 }}>
              <Title level={5}>Latest artifacts</Title>
              <List
                dataSource={latestArtifacts.slice(0, 4)}
                renderItem={(item) => (
                  <List.Item>
                    <Text className="mono">{item.artifact_key}</Text>
                    <StatusTag value={item.kind} />
                  </List.Item>
                )}
              />
            </div>
            <Button
              type="primary"
              style={{ marginTop: 12 }}
              onClick={openCurrentGate}
              disabled={!run.current_gate}
            >
              Open Gate
            </Button>
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default function RunConsole() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');

  if (!runId) {
    return <RunListView navigate={navigate} />;
  }

  return <RunDetailView navigate={navigate} runId={runId} />;
}
