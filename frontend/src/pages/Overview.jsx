import React from 'react';
import { Button, Card, Col, List, Row, Space, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';
import { gates, recentRuns } from '../data/mock.js';

const { Title, Text } = Typography;

export default function Overview() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Overview</Title>
        <StatusTag value="System Health" />
      </div>

      <Row gutter={[16, 16]}>
        {[
          { label: 'Active Runs', value: '3', trend: '+1 today' },
          { label: 'Waiting Gates', value: '2', trend: '1 requires approval' },
          { label: 'Published Flows', value: '8', trend: '2 drafts' },
          { label: 'Audit Events', value: '1,248', trend: 'Last 24h' },
        ].map((metric) => (
          <Col key={metric.label} xs={24} sm={12} lg={6}>
            <Card className="metric-card">
              <Text type="secondary" className="card-label">{metric.label}</Text>
              <div className="metric-value">{metric.value}</div>
              <Text type="secondary">{metric.trend}</Text>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card
            title="Recent Runs"
            extra={<Button type="link">View all</Button>}
          >
            <List
              itemLayout="horizontal"
              dataSource={recentRuns}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={item.project}
                    description={item.flow}
                  />
                  <Space>
                    <StatusTag value={item.status} />
                    <Text className="mono">{item.key}</Text>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            title="Gate Inbox"
            extra={<Button type="link">Open inbox</Button>}
          >
            <List
              itemLayout="horizontal"
              dataSource={gates}
              renderItem={(gate) => (
                <List.Item>
                  <List.Item.Meta
                    title={gate.title}
                    description={`${gate.run} · ${gate.role}`}
                  />
                  <StatusTag value={gate.status} />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
