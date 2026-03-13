import React from 'react';
import { Button, Card, Input, List, Space, Tabs, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';
import { flows } from '../data/mock.js';

const { Title, Text } = Typography;

const yamlSample = `id: feature-change-flow
version: 1.0.3
canonical_name: feature-change-flow@1.0.3
start_node_id: intake-analysis
rule_ref: project-rule@1.0.2
nodes:
  - id: intake-analysis
    type: executor
    executor_kind: AI
    skill_refs: [feature-intake@1.0.0]
    outputs: [feature-analysis, questions]
    on_success: collect-answers
  - id: collect-answers
    type: gate
    gate_kind: human_input
    on_submit: process-answers`;

export default function FlowEditor() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Flow Editor</Title>
        <Space>
          <Button>Compare versions</Button>
          <Button type="primary">Publish version</Button>
        </Space>
      </div>
      <div className="split-layout">
        <Card className="side-panel">
          <Input placeholder="Search flows" style={{ marginBottom: 12 }} />
          <List
            dataSource={flows}
            renderItem={(item, index) => (
              <List.Item className={index === 0 ? 'card-muted' : ''}>
                <Space direction="vertical" size={0}>
                  <Text strong>{item.name}</Text>
                  <Text type="secondary">{item.version} · {item.status}</Text>
                </Space>
                <StatusTag value={item.status} />
              </List.Item>
            )}
          />
        </Card>
        <Card className="editor-panel">
          <Tabs
            defaultActiveKey="yaml"
            items={[
              { key: 'yaml', label: 'YAML', children: <pre className="code-block">{yamlSample}</pre> },
              {
                key: 'preview',
                label: 'Preview',
                children: (
                  <div>
                    <div className="prompt-title">Graph preview</div>
                    <div className="card-muted" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <span className="mono">intake-analysis</span>
                      <span className="mono">collect-answers</span>
                      <span className="mono">process-answers</span>
                      <span className="mono">approve-requirements</span>
                    </div>
                  </div>
                ),
              },
            ]}
            tabBarExtraContent={
              <Space>
                <Button>Save draft</Button>
                <Button type="primary">Publish</Button>
              </Space>
            }
          />
        </Card>
        <Card>
          <Title level={5}>Validation</Title>
          <div className="card-muted">Start node resolved</div>
          <div className="card-muted" style={{ marginTop: 8 }}>1 unreachable node detected</div>
          <div className="card-muted" style={{ marginTop: 8 }}>Duplicate node id: approve-plan</div>
          <div style={{ marginTop: 16 }}>
            <Text className="muted">Rule ref</Text>
            <div className="mono">project-rule@1.0.2</div>
          </div>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">Nodes</Text>
            <div>10</div>
          </div>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">Start node</Text>
            <div className="mono">intake-analysis</div>
          </div>
        </Card>
      </div>
    </div>
  );
}
