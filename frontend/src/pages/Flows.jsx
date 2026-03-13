import React from 'react';
import { Button, Card, Space, Table, Typography } from 'antd';
import { Link } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { flows } from '../data/mock.js';

const { Title, Text } = Typography;

export default function Flows() {
  const columns = [
    {
      title: 'Flow',
      dataIndex: 'name',
      key: 'name',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.name}</Text>
          <Text type="secondary">{record.description}</Text>
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (value) => <StatusTag value={value} />,
    },
    {
      title: 'Latest Version',
      dataIndex: 'version',
      key: 'version',
      render: (value) => <span className="mono">{value}</span>,
    },
    {
      title: 'Canonical Name',
      dataIndex: 'canonical',
      key: 'canonical',
      render: (value) => <span className="mono">{value}</span>,
    },
  ];

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Flows</Title>
        <Space>
          <Link to="/flow-editor">
            <Button>Open editor</Button>
          </Link>
          <Button type="primary">New flow</Button>
        </Space>
      </div>
      <Card>
        <Table columns={columns} dataSource={flows} pagination={false} rowKey="key" />
      </Card>
    </div>
  );
}
