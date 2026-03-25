import React, { useEffect, useState } from 'react';
import { Button, Card, Col, List, Row, Space, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;
const numberFormat = new Intl.NumberFormat('ru-RU');

function formatNumber(value) {
  return numberFormat.format(value || 0);
}

function shortId(value) {
  if (!value) {
    return '—';
  }
  return String(value).slice(0, 8);
}

export default function Overview() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [overview, setOverview] = useState({
    metrics: {
      active_runs: 0,
      waiting_gates: 0,
      awaiting_decision_gates: 0,
      published_flows: 0,
      draft_flows: 0,
      audit_events_24h: 0,
    },
    recent_runs: [],
    gate_inbox: [],
  });

  const load = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/overview');
      setOverview({
        metrics: data?.metrics || {},
        recent_runs: data?.recent_runs || [],
        gate_inbox: data?.gate_inbox || [],
      });
    } catch (err) {
      message.error(err.message || 'Failed to load overview');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const metrics = [
    {
      label: 'Active Runs',
      value: formatNumber(overview.metrics.active_runs),
      trend: 'Currently active',
    },
    {
      label: 'Waiting Gates',
      value: formatNumber(overview.metrics.waiting_gates),
      trend: `${formatNumber(overview.metrics.awaiting_decision_gates)} require approval`,
    },
    {
      label: 'Published Flows',
      value: formatNumber(overview.metrics.published_flows),
      trend: `${formatNumber(overview.metrics.draft_flows)} drafts`,
    },
    {
      label: 'Audit Events',
      value: formatNumber(overview.metrics.audit_events_24h),
      trend: 'Last 24h',
    },
  ];

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Overview</Title>
        <StatusTag value="System Health" />
      </div>

      <Row gutter={[16, 16]}>
        {metrics.map((metric) => (
          <Col key={metric.label} xs={24} sm={12} lg={6}>
            <Card className="metric-card" loading={loading}>
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
            extra={<Button type="default" onClick={() => navigate('/run-console')}>View all</Button>}
            loading={loading}
          >
            <List
              itemLayout="horizontal"
              dataSource={overview.recent_runs}
              locale={{ emptyText: 'No runs yet' }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={item.project}
                    description={item.flow}
                  />
                  <Space>
                    <StatusTag value={item.status} />
                    <Text className="mono">{shortId(item.run_id)}</Text>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            title="Gate Inbox"
            extra={<Button type="default" onClick={() => navigate('/gates-inbox')}>Open inbox</Button>}
            loading={loading}
          >
            <List
              itemLayout="horizontal"
              dataSource={overview.gate_inbox}
              locale={{ emptyText: 'No open gates' }}
              renderItem={(gate) => (
                <List.Item>
                  <List.Item.Meta
                    title={gate.title}
                    description={`${shortId(gate.run_id)} · ${gate.role || '—'}`}
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
