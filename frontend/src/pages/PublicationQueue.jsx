import React, { useEffect, useState } from 'react';
import { Button, Card, Select, Space, Table, Typography, message } from 'antd';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function PublicationQueue() {
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState(null);
  const [jobs, setJobs] = useState([]);

  const load = async () => {
    setLoading(true);
    try {
      const query = status ? `?status=${encodeURIComponent(status)}` : '';
      const data = await apiRequest(`/publications/jobs${query}`);
      setJobs(Array.isArray(data) ? data : []);
    } catch (err) {
      message.error(err.message || 'Failed to load publication queue');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [status]);

  const retryPublication = async (row) => {
    try {
      await apiRequest(`/publications/${row.entity_type}s/${row.entity_id}/versions/${row.version}/retry`, { method: 'POST' });
      message.success('Retry started');
      await load();
    } catch (err) {
      message.error(err.message || 'Retry failed');
    }
  };

  const columns = [
    { title: 'Entity', dataIndex: 'entity_type', key: 'entity_type', width: 80 },
    { title: 'ID', dataIndex: 'entity_id', key: 'entity_id' },
    { title: 'Version', dataIndex: 'version', key: 'version', width: 90 },
    { title: 'Status', dataIndex: 'status', key: 'status', width: 120 },
    { title: 'Step', dataIndex: 'step', key: 'step', width: 120 },
    { title: 'Attempt', dataIndex: 'attempt_no', key: 'attempt_no', width: 90 },
    {
      title: 'PR',
      dataIndex: 'pr_url',
      key: 'pr_url',
      render: (value) => (value ? <a href={value} target="_blank" rel="noreferrer">open</a> : <Text type="secondary">—</Text>),
      width: 90,
    },
    {
      title: 'Error',
      dataIndex: 'error',
      key: 'error',
      render: (value) => (value ? <span title={value}>{value.slice(0, 80)}</span> : <Text type="secondary">—</Text>),
    },
    {
      title: 'Action',
      key: 'action',
      width: 100,
      render: (_, row) => (
        row.status === 'failed'
          ? <Button size="small" onClick={() => retryPublication(row)}>Retry</Button>
          : <Text type="secondary">—</Text>
      ),
    },
  ];

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Publication Queue</Title>
        <Space>
          <Select
            allowClear
            style={{ width: 180 }}
            value={status}
            onChange={(value) => setStatus(value || null)}
            placeholder="Status"
            options={[
              { value: 'pending', label: 'pending' },
              { value: 'running', label: 'running' },
              { value: 'completed', label: 'completed' },
              { value: 'failed', label: 'failed' },
            ]}
          />
          <Button onClick={load} loading={loading}>Refresh</Button>
        </Space>
      </div>
      <Card>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={jobs}
          pagination={{ pageSize: 20 }}
        />
      </Card>
    </div>
  );
}
