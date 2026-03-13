import React from 'react';
import { Button, Card, Table, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';

const { Title } = Typography;

export default function Versions() {
  const data = [
    {
      key: 'v1',
      entity: 'Flow',
      canonical: 'feature-change-flow@1.0.3',
      status: 'published',
      savedAt: '2026-03-12 17:40',
    },
    {
      key: 'v2',
      entity: 'Rule',
      canonical: 'project-rule@1.0.2',
      status: 'published',
      savedAt: '2026-03-12 16:58',
    },
  ];

  const columns = [
    { title: 'Entity', dataIndex: 'entity', key: 'entity' },
    {
      title: 'Canonical Name',
      dataIndex: 'canonical',
      key: 'canonical',
      render: (value) => <span className="mono">{value}</span>,
    },
    { title: 'Status', dataIndex: 'status', key: 'status', render: (value) => <StatusTag value={value} /> },
    { title: 'Saved At', dataIndex: 'savedAt', key: 'savedAt' },
  ];

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Versions / Snapshots</Title>
        <Button>Export snapshot</Button>
      </div>
      <Card>
        <Table columns={columns} dataSource={data} pagination={false} />
      </Card>
    </div>
  );
}
