import React, { useEffect, useState } from 'react';
import { Card, Empty, List, Typography, message } from 'antd';
import { useSearchParams } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title } = Typography;

export default function AuditReview() {
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');
  const [items, setItems] = useState([]);

  useEffect(() => {
    const load = async () => {
      if (!runId) {
        return;
      }
      try {
        const data = await apiRequest(`/runs/${runId}/audit`);
        const reviewEvents = (data || []).filter(
          (event) => event.event_type === 'gate_approved' || event.event_type === 'gate_rework_requested'
        );
        setItems(reviewEvents);
      } catch (err) {
        message.error(err.message || 'Failed to load review audit');
      }
    };
    load();
  }, [runId]);

  if (!runId) {
    return <Empty description="Add runId: /audit-review?runId=..." />;
  }

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Review Audit</Title>
      </div>
      <Card>
        <List
          dataSource={items}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={item.event_type}
                description={`${item.actor_id || 'unknown'} · ${item.event_time}`}
              />
              <StatusTag value={item.event_type === 'gate_approved' ? 'approved' : 'rework_requested'} />
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}
