import React from 'react';
import { Button, Card, Input, List, Space, Tabs, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';
import { skills } from '../data/mock.js';

const { Title, Text } = Typography;

const skillSample = `---
id: update-requirements
version: 1.2.0
canonical_name: update-requirements@1.2.0
name: Update requirements
---

# Purpose
Produce a clear requirements artifact.

# Instructions
1. Reuse existing business terminology.
2. Highlight unresolved constraints.`;

export default function SkillEditor() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Skill Editor</Title>
        <Space>
          <Button>Compare versions</Button>
          <Button type="primary">Publish version</Button>
        </Space>
      </div>
      <div className="split-layout">
        <Card className="side-panel">
          <Input placeholder="Search skills" style={{ marginBottom: 12 }} />
          <List
            dataSource={skills}
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
            defaultActiveKey="markdown"
            items={[
              { key: 'markdown', label: 'Markdown', children: <pre className="code-block">{skillSample}</pre> },
              {
                key: 'preview',
                label: 'Preview',
                children: (
                  <div>
                    <Title level={5}>Purpose</Title>
                    <Text>Produce a clear requirements artifact.</Text>
                    <Title level={5} style={{ marginTop: 12 }}>Instructions</Title>
                    <ol>
                      <li>Reuse existing business terminology.</li>
                      <li>Highlight unresolved constraints.</li>
                    </ol>
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
          <Title level={5}>Metadata</Title>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">Used by nodes</Text>
            <div>intake-analysis, process-answers</div>
          </div>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">Compatible node types</Text>
            <div>AI executor</div>
          </div>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">Usage frequency</Text>
            <StatusTag value="High" />
          </div>
        </Card>
      </div>
    </div>
  );
}
