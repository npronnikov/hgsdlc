import React from 'react';
import { Button, Card, Col, Input, Row, Select, Tabs, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';

const { Title, Text } = Typography;

export default function GateApproval() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Human Approval Gate</Title>
        <StatusTag value="awaiting_decision" />
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={6}>
          <Card title="Summary">
            <div>
              <Text className="muted">Reviewed artifacts</Text>
              <div className="mono">requirements-draft.md v3</div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Prompt checksum</Text>
              <div className="mono">9bc12d...1af</div>
            </div>
            <div className="card-muted" style={{ marginTop: 12 }}>
              Agent summary: Requirements updated with refund limits.
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card>
            <Tabs
              defaultActiveKey="requirements"
              items={[
                {
                  key: 'requirements',
                  label: 'Requirements',
                  children: (
                    <pre className="code-block"># Refund Requirements
- Support partial refunds
- Track refund history</pre>
                  ),
                },
                {
                  key: 'plan',
                  label: 'Plan',
                  children: (
                    <pre className="code-block">1. Add refund table
2. Update API
3. Add tests</pre>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="Decision">
            <Text className="muted">Comment</Text>
            <Input.TextArea rows={5} style={{ marginTop: 8 }} />
            <Text className="muted" style={{ marginTop: 12, display: 'block' }}>Rework mode</Text>
            <Select
              style={{ width: '100%', marginTop: 8 }}
              defaultValue="keep_workspace"
              options={[
                { value: 'keep_workspace', label: 'keep_workspace' },
                { value: 'discard_uncommitted', label: 'discard_uncommitted' },
              ]}
            />
            <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
              <Button type="primary">Approve</Button>
              <Button danger>Reject</Button>
              <Button type="default" style={{ borderColor: '#d97706', color: '#d97706' }}>Rework</Button>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
