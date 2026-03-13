import React from 'react';
import { Button, Card, Col, Input, Row, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';

const { Title, Text } = Typography;

export default function GateInput() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Human Input Gate</Title>
        <StatusTag value="awaiting_input" />
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card>
            <Title level={5}>Questions artifact</Title>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Artifact version</Text>
              <div className="mono">questions.md · v2</div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Node</Text>
              <div className="mono">intake-analysis</div>
            </div>
            <pre className="code-block" style={{ marginTop: 12 }}>
1. What refund policies apply?
2. Which services need updates?
            </pre>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card>
            <Title level={5}>Answers</Title>
            <Text type="secondary">Provide answers in markdown</Text>
            <Input.TextArea rows={8} style={{ marginTop: 12 }} />
            <div className="card-muted" style={{ marginTop: 12 }}>
              You are responding to artifact version v2.
            </div>
            <Button type="primary" style={{ marginTop: 12 }}>Submit answers</Button>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
