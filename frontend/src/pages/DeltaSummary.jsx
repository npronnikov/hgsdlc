import React from 'react';
import { Card, Col, Row, Typography } from 'antd';

const { Title, Text } = Typography;

export default function DeltaSummary() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Delta Summary</Title>
      </div>
      <Row gutter={[16, 16]}>
        {[
          { label: 'changed_files_count', value: '8' },
          { label: 'added_lines', value: '186' },
          { label: 'removed_lines', value: '41' },
          { label: 'created_files_count', value: '1' },
        ].map((item) => (
          <Col xs={24} sm={12} lg={6} key={item.label}>
            <Card className="metric-card">
              <Text className="card-label" type="secondary">{item.label}</Text>
              <div className="metric-value">{item.value}</div>
            </Card>
          </Col>
        ))}
      </Row>
      <Card style={{ marginTop: 16 }}>
        <div>
          <Text className="muted">agent_summary</Text>
          <div>Implemented partial refund support and updated tests.</div>
        </div>
        <div style={{ marginTop: 12 }}>
          <Text className="muted">touched_areas</Text>
          <div>src/main/java, src/test/java, docs</div>
        </div>
      </Card>
    </div>
  );
}
