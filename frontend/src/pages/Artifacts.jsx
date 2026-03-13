import React from 'react';
import { Card, Col, List, Row, Typography } from 'antd';

const { Title } = Typography;

export default function Artifacts() {
  const artifacts = [
    { name: 'requirements-draft.md', detail: 'process-answers · v3' },
    { name: 'questions.md', detail: 'intake-analysis · v2' },
  ];

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Artifacts</Title>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={10}>
          <Card>
            <List
              dataSource={artifacts}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta title={item.name} description={item.detail} />
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card>
            <Title level={5}>Preview</Title>
            <pre className="code-block"># Requirements Draft
- Add partial refund support
- Store refund history</pre>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
