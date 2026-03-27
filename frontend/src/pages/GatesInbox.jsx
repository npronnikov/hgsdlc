import React, { useEffect, useState } from 'react';
import { Button, Card, Space, Table, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title } = Typography;

export default function GatesInbox() {
  const navigate = useNavigate();
  const [gates, setGates] = useState([]);
  const [loading, setLoading] = useState(false);

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

      const enriched = (gatesResult.value || []).map((gate) => {
        const projectId = runProjectByRunId.get(gate.run_id);
        const projectName = projectId ? projectNameById.get(projectId) : null;
        return {
          ...gate,
          project_name: projectName || '—',
          instruction: gate?.payload?.user_instructions || '—',
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

  const columns = [
    { title: 'Project', dataIndex: 'project_name', key: 'project_name' },
    { title: 'Gate', dataIndex: 'node_id', key: 'node_id' },
    {
      title: 'Instruction',
      dataIndex: 'instruction',
      key: 'instruction',
      render: (value) => (
        <Typography.Text ellipsis={{ tooltip: value || '—' }}>
          {value || '—'}
        </Typography.Text>
      ),
    },
    { title: 'Run', dataIndex: 'run_id', key: 'run_id', render: (value) => <span className="mono">{value}</span> },
    { title: 'Status', dataIndex: 'status', key: 'status', render: (value) => <StatusTag value={value} /> },
    { title: 'Role', dataIndex: 'assignee_role', key: 'assignee_role', render: (value) => value || '—' },
  ];

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Gates Inbox</Title>
        <Space>
          <Button type="default" onClick={load} loading={loading}>Refresh</Button>
        </Space>
      </div>
      <Card>
        <Table
          columns={columns}
          dataSource={gates}
          pagination={false}
          rowKey="gate_id"
          loading={loading}
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
