import React, { useEffect, useMemo, useState } from 'react';
import { Card, Empty, Input, List, Space, Typography, message } from 'antd';
import { useSearchParams } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title } = Typography;

export default function AuditRuntime() {
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId') || localStorage.getItem('lastRunId');
  const [events, setEvents] = useState([]);
  const [filter, setFilter] = useState('');

  useEffect(() => {
    if (runId) {
      localStorage.setItem('lastRunId', runId);
    }
    const load = async () => {
      if (!runId) {
        return;
      }
      try {
        const data = await apiRequest(`/runs/${runId}/audit`);
        setEvents(data || []);
      } catch (err) {
        message.error(err.message || 'Не удалось загрузить runtime audit');
      }
    };
    load();
  }, [runId]);

  const filteredEvents = useMemo(() => {
    if (!filter.trim()) {
      return events;
    }
    const query = filter.trim().toLowerCase();
    return events.filter((event) => event.event_type?.toLowerCase().includes(query));
  }, [events, filter]);

  if (!runId) {
    return <Empty description="Добавьте runId: /audit-runtime?runId=..." />;
  }

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Runtime Audit</Title>
        <Space>
          <Input placeholder="Filter by event" value={filter} onChange={(e) => setFilter(e.target.value)} />
        </Space>
      </div>
      <Card>
        <List
          dataSource={filteredEvents}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={item.event_type}
                description={`${item.actor_type} · seq ${item.sequence_no}`}
              />
              <StatusTag value={item.actor_type} />
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}
