import React, { useEffect, useState } from 'react';
import { Button, Card, Input, List, Space, Tabs, Typography, message } from 'antd';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';
import { extractFrontmatterId } from '../utils/frontmatter.js';

const { Title, Text } = Typography;

const ruleSample = `---
id: project-rule
version: 1.0.0
canonical_name: project-rule@1.0.0
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
  const [rules, setRules] = useState([]);
  const [loadingRules, setLoadingRules] = useState(false);
  const [selectedRuleId, setSelectedRuleId] = useState(null);
  const [editorValue, setEditorValue] = useState(ruleSample);
  const [resourceVersion, setResourceVersion] = useState(0);

  const loadRules = async () => {
    setLoadingRules(true);
    try {
      const data = await apiRequest('/rules');
      const mapped = data.map((rule) => ({
        key: rule.rule_id,
        ruleId: rule.rule_id,
        name: rule.rule_id,
        description: '',
        status: rule.status,
        version: rule.version,
        canonical: rule.canonical_name,
      }));
      setRules(mapped);
      if (mapped.length > 0 && !selectedRuleId) {
        await loadRule(mapped[0].ruleId);
      }
    } catch (err) {
      message.error(err.message || 'Failed to load rules');
    } finally {
      setLoadingRules(false);
    }
  };

  const loadRule = async (ruleId) => {
    try {
      const data = await apiRequest(`/rules/${ruleId}`);
      setSelectedRuleId(ruleId);
      setEditorValue(data.rule_markdown || '');
      setResourceVersion(data.resource_version ?? 0);
    } catch (err) {
      message.error(err.message || 'Failed to load rule');
    }
  };

  const saveRule = async (publish) => {
    const ruleId = extractFrontmatterId(editorValue);
    if (!ruleId) {
      message.error('Frontmatter id is required');
      return;
    }
    const effectiveVersion = ruleId === selectedRuleId ? (resourceVersion ?? 0) : 0;
    try {
      const response = await apiRequest(`/rules/${ruleId}/save`, {
        method: 'POST',
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({
          rule_markdown: editorValue,
          publish,
          resource_version: effectiveVersion,
        }),
      });
      setEditorValue(response.rule_markdown || editorValue);
      setResourceVersion(response.resource_version ?? resourceVersion);
      setSelectedRuleId(ruleId);
      await loadRules();
      message.success(publish ? 'Rule published' : 'Draft saved');
    } catch (err) {
      message.error(err.message || 'Failed to save rule');
    }
  };

  useEffect(() => {
    loadRules();
  }, []);

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Rule Editor</Title>
        <Space>
          <Button>Compare versions</Button>
          <Button type="primary" onClick={() => saveRule(true)}>Publish version</Button>
        </Space>
      </div>
      <div className="split-layout">
        <Card className="side-panel">
          <Input placeholder="Search rules" style={{ marginBottom: 12 }} />
          <List
            dataSource={rules}
            loading={loadingRules}
            renderItem={(item) => (
              <List.Item
                className={item.ruleId === selectedRuleId ? 'card-muted' : ''}
                onClick={() => loadRule(item.ruleId)}
                style={{ cursor: 'pointer' }}
              >
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
              {
                key: 'markdown',
                label: 'Markdown',
                children: (
                  <Input.TextArea
                    value={editorValue}
                    onChange={(event) => setEditorValue(event.target.value)}
                    rows={20}
                    style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }}
                  />
                ),
              },
              {
                key: 'preview',
                label: 'Preview',
                children: (
                  <pre className="code-block">{editorValue}</pre>
                ),
              },
            ]}
            tabBarExtraContent={
              <Space>
                <Button onClick={() => saveRule(false)}>Save draft</Button>
                <Button type="primary" onClick={() => saveRule(true)}>Publish</Button>
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
