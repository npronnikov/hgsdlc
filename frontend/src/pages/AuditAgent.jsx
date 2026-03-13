import React from 'react';
import { Card, Input, Space, Typography } from 'antd';

const { Title, Text } = Typography;

export default function AuditAgent() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Agent Audit</Title>
        <Space>
          <Input placeholder="Node id" />
        </Space>
      </div>
      <Card>
        <div>
          <Text className="muted">Node id</Text>
          <div className="mono">process-answers</div>
        </div>
        <div style={{ marginTop: 12 }}>
          <Text className="muted">Attempt</Text>
          <div className="mono">1</div>
        </div>
        <div style={{ marginTop: 12 }}>
          <Text className="muted">Prompt checksum</Text>
          <div className="mono">d0a1f...c92</div>
        </div>
        <div style={{ marginTop: 16 }} className="card-muted">
          Declared/injected/used skills: feature-intake@1.0.0, update-requirements@1.2.0
        </div>
      </Card>
    </div>
  );
}
