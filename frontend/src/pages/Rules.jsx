import React, { useEffect, useState } from 'react';
import { Button, Card, Space, Table, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function Rules() {
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const loadRules = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/rules');
      const mapped = data.map((rule) => ({
        key: rule.rule_id,
        name: rule.title || rule.rule_id,
        ruleId: rule.rule_id,
        provider: rule.provider,
        status: rule.status,
        version: rule.version,
        canonical: rule.canonical_name,
      }));
      setRules(mapped);
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить Rules');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRules();
  }, []);
  const columns = [
    {
      title: 'Rule',
      dataIndex: 'name',
      key: 'name',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.name}</Text>
          <Text type="secondary">{record.ruleId}</Text>
        </Space>
      ),
    },
    {
      title: 'Провайдер',
      dataIndex: 'provider',
      key: 'provider',
      render: (value) => <span className="mono">{value || '-'}</span>,
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
        <Title level={3} style={{ margin: 0 }}>Rules</Title>
        <Space>
          <Button type="default" onClick={() => navigate('/rules/create')}>Новый Rule</Button>
        </Space>
      </div>
      <Card>
        <Table
          columns={columns}
          dataSource={rules}
          pagination={false}
          rowKey="key"
          loading={loading}
          onRow={(record) => ({
            onClick: () => navigate(`/rules/${record.ruleId}`),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>
    </div>
  );
}
