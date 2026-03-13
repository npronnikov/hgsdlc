import React from 'react';
import { Button, Card, Table, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';
import { projects } from '../data/mock.js';

const { Title } = Typography;

export default function Projects() {
  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (value) => <span>{value}</span>,
    },
    {
      title: 'Repository',
      dataIndex: 'repo',
      key: 'repo',
      render: (value) => <span className="mono">{value}</span>,
    },
    {
      title: 'Default Branch',
      dataIndex: 'branch',
      key: 'branch',
      render: (value) => <span className="mono">{value}</span>,
    },
    {
      title: 'Last Run',
      dataIndex: 'status',
      key: 'status',
      render: (value) => <StatusTag value={value} />,
    },
  ];

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Projects</Title>
        <Button type="primary">Register project</Button>
      </div>
      <Card>
        <Table
          columns={columns}
          dataSource={projects}
          pagination={false}
          rowKey="key"
        />
      </Card>
    </div>
  );
}
