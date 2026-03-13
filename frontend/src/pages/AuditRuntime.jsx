import React from 'react';
import { Card, Input, List, Space, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';
import { auditEvents } from '../data/mock.js';

const { Title } = Typography;

export default function AuditRuntime() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Runtime Audit</Title>
        <Space>
          <Input placeholder="Filter by event" />
        </Space>
      </div>
      <Card>
        <List
          dataSource={auditEvents}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta title={item.event} description={item.detail} />
              <StatusTag value={item.type} />
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}
