import React from 'react';
import { Button, Card, Table, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';
import { gates } from '../data/mock.js';

const { Title } = Typography;

export default function GatesInbox() {
  const columns = [
    { title: 'Gate', dataIndex: 'title', key: 'title' },
    { title: 'Run', dataIndex: 'run', key: 'run', render: (value) => <span className="mono">{value}</span> },
    { title: 'Status', dataIndex: 'status', key: 'status', render: (value) => <StatusTag value={value} /> },
    { title: 'Role', dataIndex: 'role', key: 'role', render: (value) => <StatusTag value={value} /> },
  ];

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Gates Inbox</Title>
        <Button type="primary">Refresh</Button>
      </div>
      <Card>
        <Table columns={columns} dataSource={gates} pagination={false} rowKey="key" />
      </Card>
    </div>
  );
}
