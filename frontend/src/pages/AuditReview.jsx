import React from 'react';
import { Card, List, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';

const { Title } = Typography;

export default function AuditReview() {
  const items = [
    { event: 'GATE_APPROVED', detail: 'approve-plan · tech_approver · 11:42', status: 'approved' },
    { event: 'GATE_REWORK_REQUESTED', detail: 'approve-code · tech_approver · 12:10', status: 'rework_requested' },
  ];

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
              <List.Item.Meta title={item.event} description={item.detail} />
              <StatusTag value={item.status} />
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}
