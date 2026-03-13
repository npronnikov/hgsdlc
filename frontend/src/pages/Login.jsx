import React from 'react';
import { Button, Card, Input, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';

const { Title, Text } = Typography;

export default function Login() {
  const navigate = useNavigate();

  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="brand">
          <div className="brand-mark">HG</div>
          <div>
            <Title level={2} style={{ margin: 0 }}>HGSDLC Platform</Title>
            <Text type="secondary">Managed flow execution for Git-based engineering work</Text>
          </div>
        </div>
        <Card>
          <div style={{ display: 'grid', gap: 12 }}>
            <Input placeholder="Username" />
            <Input.Password placeholder="Password" />
            <Button type="primary" onClick={() => navigate('/overview')}>
              Sign in
            </Button>
          </div>
        </Card>
        <div style={{ display: 'grid', gap: 12 }}>
          <Card size="small">
            <Tag color="#0891b2">FLOW_CONFIGURATOR</Tag>
            <Text type="secondary">Configure flows, rules and skills.</Text>
          </Card>
          <Card size="small">
            <Tag color="#d97706">PRODUCT_OWNER</Tag>
            <Text type="secondary">Launch runs and respond to input gates.</Text>
          </Card>
          <Card size="small">
            <Tag color="#16a34a">TECH_APPROVER</Tag>
            <Text type="secondary">Approve, reject or rework gates.</Text>
          </Card>
        </div>
      </div>
      <div className="login-bg">
        <div className="login-grid"></div>
      </div>
    </div>
  );
}
