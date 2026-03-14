import React, { useEffect, useState } from 'react';
import { Button, Card, Space, Table, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function Flows() {
  const navigate = useNavigate();
  const [flows, setFlows] = useState([]);
  const [loading, setLoading] = useState(false);

  const loadFlows = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/flows');
      const mapped = data.map((flow) => ({
        key: flow.flow_id,
        name: flow.title || flow.flow_id,
        flowId: flow.flow_id,
        description: flow.description || '',
        status: flow.status,
        version: flow.version,
        canonical: flow.canonical_name,
      }));
      setFlows(mapped);
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить Flows');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFlows();
  }, []);
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
          loading={loading}
          onRow={(record) => ({
            onClick: () => navigate(`/flows/${record.flowId}`),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>
    </div>
  );
}
