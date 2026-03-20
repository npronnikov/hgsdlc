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
      const data = await apiRequest('/gates/inbox');
      setGates(data || []);
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
    { title: 'Gate', dataIndex: 'node_id', key: 'node_id' },
    { title: 'Run', dataIndex: 'run_id', key: 'run_id', render: (value) => <span className="mono">{value}</span> },
    { title: 'Status', dataIndex: 'status', key: 'status', render: (value) => <StatusTag value={value} /> },
    { title: 'Role', dataIndex: 'assignee_role', key: 'assignee_role', render: (value) => value || '—' },
    {
      title: 'Action',
      key: 'action',
      render: (_, record) => (
        <Button
          type="link"
          onClick={() => {
            const gatePath = record.gate_kind === 'human_input' ? '/gate-input' : '/gate-approval';
            navigate(`${gatePath}?runId=${record.run_id}&gateId=${record.gate_id}`);
          }}
        >
          Open
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Gates Inbox</Title>
        <Space>
          <Button type="primary" onClick={load} loading={loading}>Refresh</Button>
        </Space>
      </div>
      <Card>
        <Table columns={columns} dataSource={gates} pagination={false} rowKey="gate_id" loading={loading} />
      </Card>
    </div>
  );
}
