import React, { useEffect, useRef, useState } from 'react';
import { Button, Card, Dropdown, Input, Modal, Select, Space, Typography, message } from 'antd';
import Editor from '@monaco-editor/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { apiRequest } from '../api/request.js';
import { useLocation, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const providerOptions = [
  { value: 'qwen', label: 'qwen' },
  { value: 'claude', label: 'claude' },
  { value: 'cursor', label: 'cursor' },
  { value: 'platform-native', label: 'platform-native' },
];

export default function SkillEditor() {
  const { skillId: skillIdParam } = useParams();
  const location = useLocation();
  const isCreateRoute = location.pathname.endsWith('/skills/create');
  const [selectedSkillId, setSelectedSkillId] = useState(null);
  const [editorValue, setEditorValue] = useState('');
  const [resourceVersion, setResourceVersion] = useState(0);
  const [skillVersion, setSkillVersion] = useState('');
  const [versionOptions, setVersionOptions] = useState([]);
  const [hasDraft, setHasDraft] = useState(false);
  const [currentStatus, setCurrentStatus] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [skillId, setSkillId] = useState('');
  const [provider, setProvider] = useState('');
  const [frontmatterSummary, setFrontmatterSummary] = useState([]);
  const [isNewSkill, setIsNewSkill] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const editorRef = useRef(null);
  const previewRef = useRef(null);
  const isSyncingScroll = useRef(false);

  const loadSkill = async (skillId) => {
    try {
      const data = await apiRequest(`/skills/${skillId}`);
      setSelectedSkillId(skillId);
      setEditorValue(data.skill_markdown || '');
      setResourceVersion(data.resource_version ?? 0);
      setSkillVersion(data.version || '');
      setCurrentStatus(data.status || '');
      setName(data.name || '');
      setDescription(data.description || '');
      setSkillId(data.skill_id || '');
      setProvider(data.provider || '');
      setIsNewSkill(false);
      setIsEditing(false);
      await loadVersions(skillId, data.version);
      if (data.provider) {
        await loadTemplate(data.provider, { replaceMarkdown: false });
      } else {
        setFrontmatterSummary([]);
      }
    } catch (err) {
      message.error(err.message || 'Failed to load skill');
    }
  };

  const loadVersions = async (skillIdValue, currentVersion) => {
    try {
      const versions = await apiRequest(`/skills/${skillIdValue}/versions`);
      const mapped = versions.map((item) => ({
        label: `v${item.version} · ${item.status}`,
        value: item.version,
        canonical: item.canonical_name,
        skillId: item.skill_id,
        status: item.status,
      }));
      setVersionOptions(mapped);
      setHasDraft(mapped.some((item) => item.status === 'draft'));
      if (currentVersion) {
        setSkillVersion(currentVersion);
      }
    } catch (err) {
      message.error(err.message || 'Failed to load skill versions');
    }
  };

  const handleVersionSelect = async (value, { keepEditing = false } = {}) => {
    if (!selectedSkillId) return;
    const selected = versionOptions.find((option) => option.value === value);
    if (!selected) return;
    try {
      const data = await apiRequest(`/skills/${selected.skillId}/versions/${value}`);
      setSelectedSkillId(selected.skillId);
      setEditorValue(data.skill_markdown || '');
      setResourceVersion(data.resource_version ?? 0);
      setSkillVersion(data.version || value);
      setCurrentStatus(data.status || '');
      setName(data.name || '');
      setDescription(data.description || '');
      setSkillId(data.skill_id || '');
      setProvider(data.provider || '');
      setIsNewSkill(false);
      setIsEditing(keepEditing);
      if (data.provider) {
        await loadTemplate(data.provider, { replaceMarkdown: false });
      } else {
        setFrontmatterSummary([]);
      }
    } catch (err) {
      message.error(err.message || 'Failed to load selected version');
    }
  };

  const beginEdit = async () => {
    const draft = versionOptions.find((item) => item.status === 'draft');
    if (draft) {
      await handleVersionSelect(draft.value, { keepEditing: true });
      return;
    }
    setResourceVersion(0);
    setIsEditing(true);
  };

  const loadTemplate = async (providerValue, { replaceMarkdown }) => {
    if (!providerValue) {
      setFrontmatterSummary([]);
      return;
    }
    try {
      const template = await apiRequest(`/skill-templates/${providerValue}`);
      setFrontmatterSummary(template.frontmatterSummary || []);
      if (replaceMarkdown) {
        setEditorValue(template.template || '');
      }
    } catch (err) {
      message.error(err.message || 'Failed to load skill template');
      setFrontmatterSummary([]);
    }
  };

  const handleProviderChange = async (nextProvider) => {
    const hasContent = editorValue.trim().length > 0;
    const isChange = provider && provider !== nextProvider;
    const applyChange = async (replaceMarkdown) => {
      setProvider(nextProvider);
      await loadTemplate(nextProvider, { replaceMarkdown });
    };
    if (hasContent && isChange) {
      Modal.confirm({
        title: 'Change provider?',
        content: 'Template/frontmatter expectations will change. Replace markdown with the new template?',
        okText: 'Replace template',
        cancelText: 'Keep current markdown',
        onOk: () => applyChange(true),
        onCancel: () => applyChange(false),
      });
      return;
    }
    await applyChange(isNewSkill || !hasContent);
  };

  const saveSkill = async ({ publish, release = false }) => {
    if (!skillId) {
      message.error('Skill ID is required');
      return;
    }
    if (!name.trim()) {
      message.error('Name is required');
      return;
    }
    if (!description.trim()) {
      message.error('Description is required');
      return;
    }
    if (!provider) {
      message.error('Provider is required');
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
          name: name.trim(),
          description: description.trim(),
          skill_id: skillId.trim(),
          provider,
          skill_markdown: editorValue,
          publish,
          release,
          resource_version: effectiveVersion,
        }),
      });
      setEditorValue(response.skill_markdown || editorValue);
      setResourceVersion(response.resource_version ?? resourceVersion);
      setSkillVersion(response.version || skillVersion);
      setCurrentStatus(response.status || currentStatus);
      setSelectedSkillId(response.skill_id || skillId);
      setIsNewSkill(false);
      setIsEditing(false);
      await loadVersions(response.skill_id || skillId, response.version || skillVersion);
      message.success(publish ? 'Skill published' : 'Draft saved');
    } catch (err) {
      message.error(err.message || 'Failed to save skill');
    }
  };

  const startNewSkill = () => {
    setSelectedSkillId(null);
    setName('');
    setDescription('');
    setSkillId('');
    setProvider('');
    setEditorValue('');
    setResourceVersion(0);
    setSkillVersion('');
    setVersionOptions([]);
    setFrontmatterSummary([]);
    setIsNewSkill(true);
    setIsEditing(true);
    setHasDraft(false);
    setCurrentStatus('');
  };

  useEffect(() => {
    if (isCreateRoute) {
      startNewSkill();
      return;
    }
    if (skillIdParam) {
      loadSkill(skillIdParam);
    }
  }, [skillIdParam, isCreateRoute]);

  return (
    <div className="rule-editor-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Skill Editor</Title>
        <Space>
          {!isEditing && (
            currentStatus === 'draft' ? (
              <>
                <Button onClick={beginEdit}>Edit</Button>
                <Dropdown
                  menu={{
                    items: [
                      { key: 'publish', label: 'Publish version' },
                      { key: 'release', label: 'Release version' },
                    ],
                    onClick: ({ key }) => {
                      saveSkill({ publish: true, release: key === 'release' });
                    },
                  }}
                >
                  <Button type="primary">Publish</Button>
                </Dropdown>
              </>
            ) : (
              <Button type="primary" onClick={beginEdit}>Edit</Button>
            )
          )}
          {isEditing && (
            <>
              <Button onClick={() => saveSkill({ publish: false })}>Save</Button>
              <Dropdown
                menu={{
                  items: [
                    { key: 'publish', label: 'Publish version' },
                    { key: 'release', label: 'Release version' },
                  ],
                  onClick: ({ key }) => {
                    saveSkill({ publish: true, release: key === 'release' });
                  },
                }}
              >
                <Button type="primary">Publish</Button>
              </Dropdown>
            </>
          )}
        </Space>
      </div>
      <div className="split-layout rule-editor-layout">
        <Card className="editor-panel">
          {isEditing ? (
            <div className="editor-split">
              <div className="editor-pane">
                <Text className="muted">Markdown</Text>
                <div className="editor-pane-body">
                  <Editor
                    height="100%"
                    defaultLanguage="markdown"
                    value={editorValue}
                    onChange={(value) => setEditorValue(value ?? '')}
                    onMount={(editor) => {
                      editorRef.current = editor;
                      editor.onDidScrollChange(() => {
                        if (isSyncingScroll.current) return;
                        const previewEl = previewRef.current;
                        if (!previewEl) return;
                        const layout = editor.getLayoutInfo();
                        const editorScrollRange = editor.getScrollHeight() - layout.height;
                        const editorRatio = editorScrollRange > 0
                          ? editor.getScrollTop() / editorScrollRange
                          : 0;
                        const previewScrollRange = previewEl.scrollHeight - previewEl.clientHeight;
                        isSyncingScroll.current = true;
                        previewEl.scrollTop = previewScrollRange * editorRatio;
                        requestAnimationFrame(() => {
                          isSyncingScroll.current = false;
                        });
                      });
                    }}
                    options={{
                      minimap: { enabled: false },
                      fontSize: 13,
                      wordWrap: 'on',
                      scrollBeyondLastLine: false,
                    }}
                  />
                </div>
              </div>
              <div className="editor-pane">
                <Text className="muted">Preview</Text>
                <div
                  className="editor-pane-body markdown-preview"
                  ref={previewRef}
                  onScroll={(event) => {
                    if (isSyncingScroll.current) return;
                    const editor = editorRef.current;
                    if (!editor) return;
                    const target = event.currentTarget;
                    const previewScrollRange = target.scrollHeight - target.clientHeight;
                    const ratio = previewScrollRange > 0 ? target.scrollTop / previewScrollRange : 0;
                    const scrollHeight = editor.getScrollHeight();
                    const layout = editor.getLayoutInfo();
                    const editorScrollRange = scrollHeight - layout.height;
                    isSyncingScroll.current = true;
                    editor.setScrollTop(editorScrollRange * ratio);
                    requestAnimationFrame(() => {
                      isSyncingScroll.current = false;
                    });
                  }}
                >
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {editorValue || ''}
                  </ReactMarkdown>
                </div>
              </div>
            </div>
          ) : (
            <div className="editor-pane">
              <Text className="muted">Preview</Text>
              <div className="editor-pane-body markdown-preview">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {editorValue || ''}
                </ReactMarkdown>
              </div>
            </div>
          )}
        </Card>
        <Card>
          <div className="rule-fields-header">
            <Title level={5} style={{ margin: 0 }}>Skill Fields</Title>
            {selectedSkillId ? (
              <Select
                value={skillVersion || undefined}
                options={versionOptions}
                onChange={(value) => handleVersionSelect(value)}
                className="rule-version-select"
                placeholder="Version"
                disabled={isEditing}
              />
            ) : (
              <span className="rule-version-pill">new</span>
            )}
          </div>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">Name</Text>
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Update requirements"
              style={{ marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Skill ID</Text>
            <Input
              value={skillId}
              onChange={(event) => setSkillId(event.target.value)}
              placeholder="update-requirements"
              style={{ marginTop: 4 }}
              disabled={!isEditing || !!selectedSkillId}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Description</Text>
            <Input.TextArea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={4}
              placeholder="Brief description of what this skill does"
              style={{ marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Provider</Text>
            <Select
              value={provider || undefined}
              onChange={handleProviderChange}
              options={providerOptions}
              placeholder="Select provider"
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 16 }}>
            <Title level={5}>Frontmatter Help</Title>
            {frontmatterSummary.length === 0 ? (
              <Text type="secondary">Select a provider to see expected frontmatter fields.</Text>
            ) : (
              <Space direction="vertical" size={8}>
                {frontmatterSummary.map((item) => (
                  <div key={item.field}>
                    <Text className="muted">{item.field}</Text>
                    <div className="mono">{item.meaning}</div>
                  </div>
                ))}
              </Space>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}
