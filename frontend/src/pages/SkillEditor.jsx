import React, { useEffect, useState } from 'react';
import { Button, Card, Input, List, Space, Tabs, Typography, message } from 'antd';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';
import { extractFrontmatterId } from '../utils/frontmatter.js';

const { Title, Text } = Typography;

const skillSample = `---
id: update-requirements
version: 1.0.0
canonical_name: update-requirements@1.0.0
name: Update requirements
description: Update or create requirements for a requested feature using current docs and human answers
---

# Purpose
Produce a clear requirements artifact.

# Instructions
1. Reuse existing business terminology.
2. Highlight unresolved constraints.`;

export default function SkillEditor() {
  const [skills, setSkills] = useState([]);
  const [loadingSkills, setLoadingSkills] = useState(false);
  const [selectedSkillId, setSelectedSkillId] = useState(null);
  const [editorValue, setEditorValue] = useState(skillSample);
  const [resourceVersion, setResourceVersion] = useState(0);

  const loadSkills = async () => {
    setLoadingSkills(true);
    try {
      const data = await apiRequest('/skills');
      const mapped = data.map((skill) => ({
        key: skill.skill_id,
        skillId: skill.skill_id,
        name: skill.skill_id,
        description: '',
        status: skill.status,
        version: skill.version,
        canonical: skill.canonical_name,
      }));
      setSkills(mapped);
      if (mapped.length > 0 && !selectedSkillId) {
        await loadSkill(mapped[0].skillId);
      }
    } catch (err) {
      message.error(err.message || 'Failed to load skills');
    } finally {
      setLoadingSkills(false);
    }
  };

  const loadSkill = async (skillId) => {
    try {
      const data = await apiRequest(`/skills/${skillId}`);
      setSelectedSkillId(skillId);
      setEditorValue(data.skill_markdown || '');
      setResourceVersion(data.resource_version ?? 0);
    } catch (err) {
      message.error(err.message || 'Failed to load skill');
    }
  };

  const saveSkill = async (publish) => {
    const skillId = extractFrontmatterId(editorValue);
    if (!skillId) {
      message.error('Frontmatter id is required');
      return;
    }
    const effectiveVersion = skillId === selectedSkillId ? (resourceVersion ?? 0) : 0;
    try {
      const response = await apiRequest(`/skills/${skillId}/save`, {
        method: 'POST',
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({
          skill_markdown: editorValue,
          publish,
          resource_version: effectiveVersion,
        }),
      });
      setEditorValue(response.skill_markdown || editorValue);
      setResourceVersion(response.resource_version ?? resourceVersion);
      setSelectedSkillId(skillId);
      await loadSkills();
      message.success(publish ? 'Skill published' : 'Draft saved');
    } catch (err) {
      message.error(err.message || 'Failed to save skill');
    }
  };

  useEffect(() => {
    loadSkills();
  }, []);

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Skill Editor</Title>
        <Space>
          <Button>Compare versions</Button>
          <Button type="primary" onClick={() => saveSkill(true)}>Publish version</Button>
        </Space>
      </div>
      <div className="split-layout">
        <Card className="side-panel">
          <Input placeholder="Search skills" style={{ marginBottom: 12 }} />
          <List
            dataSource={skills}
            loading={loadingSkills}
            renderItem={(item) => (
              <List.Item
                className={item.skillId === selectedSkillId ? 'card-muted' : ''}
                onClick={() => loadSkill(item.skillId)}
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
                <Button onClick={() => saveSkill(false)}>Save draft</Button>
                <Button type="primary" onClick={() => saveSkill(true)}>Publish</Button>
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
