import React from 'react';
import { Button, Card, Input, List, Space, Tabs, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';
import { rules } from '../data/mock.js';

const { Title, Text } = Typography;

const ruleSample = `---
id: project-rule
version: 1.0.2
canonical_name: project-rule@1.0.2
response_schema_id: agent-response-v1
allowed_paths:
  - src/main/java/**
  - src/test/java/**
forbidden_paths:
  - .git/**
require_structured_response: true
allowed_commands:
  - git_commit
---

# Project structure
- Main code under src/main/java
- Tests under src/test/java`;

export default function RuleEditor() {
  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Rule Editor</Title>
        <Space>
          <Button>Compare versions</Button>
          <Button type="primary">Publish version</Button>
        </Space>
      </div>
      <div className="split-layout">
        <Card className="side-panel">
          <Input placeholder="Search rules" style={{ marginBottom: 12 }} />
          <List
            dataSource={rules}
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
              { key: 'markdown', label: 'Markdown', children: <pre className="code-block">{ruleSample}</pre> },
              {
                key: 'preview',
                label: 'Preview',
                children: (
                  <div>
                    <Title level={5}>Project structure</Title>
                    <ul>
                      <li>Main code under src/main/java</li>
                      <li>Tests under src/test/java</li>
                    </ul>
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
          <Title level={5}>Frontmatter Summary</Title>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">response_schema_id</Text>
            <div className="mono">agent-response-v1</div>
          </div>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">allowed_paths</Text>
            <div className="mono">src/main/java/**</div>
          </div>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">forbidden_paths</Text>
            <div className="mono">.git/**</div>
          </div>
          <div className="card-muted" style={{ marginTop: 16 }}>
            allowed_commands is advisory only
          </div>
        </Card>
      </div>
    </div>
  );
}
