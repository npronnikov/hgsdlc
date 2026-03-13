import React from 'react';
import { Button, Card, Col, Form, Input, Row, Select, Typography } from 'antd';

const { Title, Text } = Typography;

export default function RunLaunch() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Launch Run</Title>
        <Button type="primary">Launch run</Button>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card>
            <Form layout="vertical">
              <Form.Item label="Project">
                <Select
                  options={[
                    { value: 'checkout-service', label: 'checkout-service' },
                    { value: 'billing-core', label: 'billing-core' },
                  ]}
                />
              </Form.Item>
              <Form.Item label="Flow version">
                <Select
                  options={[
                    { value: 'feature-change-flow@1.0.3', label: 'feature-change-flow@1.0.3' },
                    { value: 'refund-flow@0.9.2', label: 'refund-flow@0.9.2' },
                  ]}
                />
              </Form.Item>
              <Form.Item label="Target branch">
                <Input value="main" />
              </Form.Item>
              <Form.Item label="context_root_dir">
                <Input value="src/main/java/ru/company/checkout" />
              </Form.Item>
              <Form.Item label="Feature request">
                <Input.TextArea
                  rows={6}
                  value="Add partial refund support with refund history and remaining balance checks."
                />
              </Form.Item>
            </Form>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card>
            <Title level={5}>Selected configuration</Title>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Flow</Text>
              <div className="mono">feature-change-flow@1.0.3</div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Rule</Text>
              <div className="mono">project-rule@1.0.2</div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Expected skills</Text>
              <div className="mono">feature-intake@1.0.0, update-requirements@1.2.0</div>
            </div>
            <div style={{ marginTop: 16 }} className="card-muted">
              Node sequence preview: intake-analysis → collect-answers → process-answers → approve-requirements
            </div>
            <div style={{ marginTop: 12 }} className="card-muted">
              Warning: Another active run exists on branch <span className="mono">main</span>.
            </div>
            <div style={{ marginTop: 12 }} className="card-muted">
              Run branch will be <span className="mono">feature/hgsdlc/run-0042</span>.
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
