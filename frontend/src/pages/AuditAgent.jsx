import React, { useEffect, useMemo, useState } from 'react';
import { Card, Empty, Input, List, Space, Typography, message } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { apiRequest } from '../api/request.js';

const { Title } = Typography;

export default function AuditAgent() {
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');
  const [events, setEvents] = useState([]);
  const [nodeFilter, setNodeFilter] = useState('');

  useEffect(() => {
    const load = async () => {
      if (!runId) {
        return;
      }
      try {
        const data = await apiRequest(`/runs/${runId}/audit`);
        setEvents((data || []).filter((item) => item.actor_type === 'agent'));
      } catch (err) {
        message.error(err.message || 'Не удалось загрузить agent audit');
      }
    };
    load();
  }, [runId]);

  const filtered = useMemo(() => {
    if (!nodeFilter.trim()) {
      return events;
    }
    const query = nodeFilter.trim().toLowerCase();
    return events.filter((event) => String(event.payload?.node_id || '').toLowerCase().includes(query));
  }, [events, nodeFilter]);

  if (!runId) {
    return <Empty description="Добавьте runId: /audit-agent?runId=..." />;
  }

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Agent Audit</Title>
        <Space>
          <Input placeholder="Node id" value={nodeFilter} onChange={(e) => setNodeFilter(e.target.value)} />
        </Space>
      </div>
      <Card>
        <List
          dataSource={filtered}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={item.event_type}
                description={`seq ${item.sequence_no} · ${item.event_time}`}
              />
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}
