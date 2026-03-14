import React from 'react';
import { Button, Card, Space, Table, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { flows } from '../data/mock.js';

const { Title, Text } = Typography;

export default function Flows() {
  const navigate = useNavigate();
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
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
      render: (value) => <StatusTag value={value} />,
    },
    {
      title: 'Последняя версия',
      dataIndex: 'version',
      key: 'version',
      render: (value) => <span className="mono">{value}</span>,
    },
    {
      title: 'Каноническое имя',
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
          <Button type="default" onClick={() => navigate('/flows/create')}>Новый Flow</Button>
        </Space>
      </div>
      <Card>
        <Table
          columns={columns}
          dataSource={flows}
          pagination={false}
          rowKey="key"
          onRow={(record) => ({
            onClick: () => navigate(`/flows/${record.name}`),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>
    </div>
  );
}
