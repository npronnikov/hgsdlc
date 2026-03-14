import React, { useEffect, useState } from 'react';
import { Button, Card, Space, Table, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function Skills() {
  const [skills, setSkills] = useState([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const loadSkills = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/skills');
      const mapped = data.map((skill) => ({
        key: skill.skill_id,
        name: skill.name || skill.skill_id,
        skillId: skill.skill_id,
        description: skill.description || '',
        codingAgent: skill.coding_agent,
        status: skill.status,
        version: skill.version,
        canonical: skill.canonical_name,
      }));
      setSkills(mapped);
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить Skills');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSkills();
  }, []);
  const columns = [
    {
      title: 'Skill',
      dataIndex: 'name',
      key: 'name',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.name}</Text>
          <Text type="secondary">{record.skillId}</Text>
          {record.description && <Text type="secondary">{record.description}</Text>}
        </Space>
      ),
    },
    {
      title: 'Кодинг-агент',
      dataIndex: 'codingAgent',
      key: 'codingAgent',
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
        <Title level={3} style={{ margin: 0 }}>Skills</Title>
        <Space>
          <Button type="default" onClick={() => navigate('/skills/create')}>Новый Skill</Button>
        </Space>
      </div>
      <Card>
        <Table
          columns={columns}
          dataSource={skills}
          pagination={false}
          rowKey="key"
          loading={loading}
          onRow={(record) => ({
            onClick: () => navigate(`/skills/${record.skillId}`),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>
    </div>
  );
}
