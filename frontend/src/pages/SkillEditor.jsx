import React, { useEffect, useRef, useState } from 'react';
import { Button, Card, Dropdown, Input, Modal, Select, Space, Typography, message } from 'antd';
import { MoreOutlined } from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { apiRequest } from '../api/request.js';
import { useLocation, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const codingAgentOptions = [
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
  const [codingAgent, setCodingAgent] = useState('');
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
      setCodingAgent(data.coding_agent || '');
      setIsNewSkill(false);
      setIsEditing(false);
      await loadVersions(skillId, data.version);
      if (data.coding_agent) {
        await loadTemplate(data.coding_agent, { replaceMarkdown: false });
      } else {
        setFrontmatterSummary([]);
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить Skill');
    }
  };

  const loadVersions = async (skillIdValue, currentVersion) => {
    try {
      const versions = await apiRequest(`/skills/${skillIdValue}/versions`);
      const mapped = versions.map((item) => ({
        label: `v${item.version} · ${item.status === 'draft' ? 'черновик' : item.status === 'published' ? 'опубликовано' : item.status}`,
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
      message.error(err.message || 'Не удалось загрузить версии Skill');
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
      setCodingAgent(data.coding_agent || '');
      setIsNewSkill(false);
      setIsEditing(keepEditing);
      if (data.coding_agent) {
        await loadTemplate(data.coding_agent, { replaceMarkdown: false });
      } else {
        setFrontmatterSummary([]);
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить выбранную версию');
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

  const loadTemplate = async (codingAgentValue, { replaceMarkdown }) => {
    if (!codingAgentValue) {
      setFrontmatterSummary([]);
      return;
    }
    try {
      const template = await apiRequest(`/skill-templates/${codingAgentValue}`);
      setFrontmatterSummary(template.frontmatterSummary || []);
      if (replaceMarkdown) {
        setEditorValue(template.template || '');
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить шаблон Skill');
      setFrontmatterSummary([]);
    }
  };

  const handleCodingAgentChange = async (nextAgent) => {
    const hasContent = editorValue.trim().length > 0;
    const isChange = codingAgent && codingAgent !== nextAgent;
    const applyChange = async (replaceMarkdown) => {
      setCodingAgent(nextAgent);
      await loadTemplate(nextAgent, { replaceMarkdown });
    };
    if (hasContent && isChange) {
      Modal.confirm({
        title: 'Сменить кодинг-агент?',
        content: 'Требования к шаблону и frontmatter изменятся. Заменить markdown новым шаблоном?',
        okText: 'Заменить шаблон',
        cancelText: 'Оставить текущий markdown',
        onOk: () => applyChange(true),
        onCancel: () => applyChange(false),
      });
      return;
    }
    await applyChange(isNewSkill || !hasContent);
  };

  const saveSkill = async ({ publish, release = false }) => {
    if (!skillId) {
      message.error('Нужен ID Skill');
      return;
    }
    if (!name.trim()) {
      message.error('Нужно название');
      return;
    }
    if (!description.trim()) {
      message.error('Нужно описание');
      return;
    }
    if (!codingAgent) {
      message.error('Нужен Кодинг-агент');
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
          coding_agent: codingAgent,
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
      message.success(publish ? 'Skill опубликован' : 'Черновик сохранён');
    } catch (err) {
      message.error(err.message || 'Не удалось сохранить Skill');
    }
  };

  const startNewSkill = () => {
    setSelectedSkillId(null);
    setName('');
    setDescription('');
    setSkillId('');
    setCodingAgent('');
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
        <Title level={3} style={{ margin: 0 }}>Редактор Skill</Title>
        <Space>
          {!isEditing && (
            currentStatus === 'draft' ? (
              <>
                <Button type="default" onClick={beginEdit}>Редактировать</Button>
                <Dropdown
                  menu={{
                    items: [
                      { key: 'publish', label: 'Опубликовать версию' },
                      { key: 'release', label: 'Выпустить релиз' },
                    ],
                    onClick: ({ key }) => {
                      saveSkill({ publish: true, release: key === 'release' });
                    },
                  }}
                >
                  <Button type="default" icon={<MoreOutlined />}>Опубликовать</Button>
                </Dropdown>
              </>
            ) : (
              <Button type="default" onClick={beginEdit}>Редактировать</Button>
            )
          )}
          {isEditing && (
            <>
              <Button type="default" onClick={() => saveSkill({ publish: false })}>Сохранить</Button>
              <Dropdown
                menu={{
                  items: [
                    { key: 'publish', label: 'Опубликовать версию' },
                    { key: 'release', label: 'Выпустить релиз' },
                  ],
                  onClick: ({ key }) => {
                    saveSkill({ publish: true, release: key === 'release' });
                  },
                }}
              >
                <Button type="default" icon={<MoreOutlined />}>Опубликовать</Button>
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
                    onMount={(editor, monaco) => {
                      editorRef.current = editor;
                      if (monaco) {
                        monaco.editor.defineTheme('hg-light', {
                          base: 'vs',
                          inherit: true,
                          rules: [
                            { token: 'comment', foreground: '6e7781' },
                            { token: 'string', foreground: '0a3069' },
                            { token: 'keyword', foreground: '8250df' },
                          ],
                          colors: {
                            'editor.background': '#ffffff',
                            'editor.foreground': '#333333',
                            'editorCursor.foreground': '#000000',
                            'editorLineNumber.foreground': '#8c959f',
                            'editorLineNumber.activeForeground': '#1677ff',
                            'editorLineNumber.dimmedForeground': '#b0b7c3',
                            'editor.selectionBackground': '#cfe3ff',
                            'editor.inactiveSelectionBackground': '#e8f0ff',
                            'editor.lineHighlightBackground': '#f5f7fa',
                            'editorGutter.background': '#ffffff',
                            'editorIndentGuide.background': '#e6e8eb',
                            'editorIndentGuide.activeBackground': '#d0d7de',
                            'editorWhitespace.foreground': '#d0d7de',
                            'editor.wordHighlightBorder': '#00000000',
                            'editor.wordHighlightStrongBorder': '#00000000',
                            'editor.wordHighlightBackground': '#00000000',
                            'editor.wordHighlightStrongBackground': '#00000000',
                            'editor.selectionHighlightBorder': '#00000000',
                            'editor.selectionHighlightBackground': '#00000000',
                            'editor.findMatchBorder': '#00000000',
                            'editor.findMatchHighlightBorder': '#00000000',
                            'editor.findMatchHighlightBackground': '#00000000',
                            'scrollbarSlider.background': '#c1c1c1',
                            'scrollbarSlider.hoverBackground': '#a8a8a8',
                            'scrollbarSlider.activeBackground': '#909090',
                          },
                        });
                        monaco.editor.setTheme('hg-light');
                      }
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
                      selectionHighlight: false,
                      occurrencesHighlight: 'off',
                      wordHighlight: 'off',
                      wordHighlightDelay: 0,
                      renderLineHighlight: 'none',
                      fontFamily: "'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace",
                      fontSize: 14,
                      lineHeight: 25,
                      wordWrap: 'on',
                      scrollBeyondLastLine: false,
                    }}
                    theme="hg-light"
                  />
                </div>
              </div>
              <div className="editor-pane">
                <Text className="muted">Предпросмотр</Text>
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
              <Text className="muted">Предпросмотр</Text>
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
            <Title level={5} style={{ margin: 0 }}>Поля Skill</Title>
            {selectedSkillId ? (
              <Select
                value={skillVersion || undefined}
                options={versionOptions}
                onChange={(value) => handleVersionSelect(value)}
                className="rule-version-select"
                placeholder="Версия"
                disabled={isEditing}
              />
            ) : (
              <span className="rule-version-pill">новый</span>
            )}
          </div>
          <div style={{ marginTop: 8 }}>
            <Text className="muted">Название</Text>
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Обновить требования"
              style={{ marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">ID Skill</Text>
            <Input
              value={skillId}
              onChange={(event) => setSkillId(event.target.value)}
              placeholder="update-requirements"
              style={{ marginTop: 4 }}
              disabled={!isEditing || !!selectedSkillId}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Описание</Text>
            <Input.TextArea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={4}
              placeholder="Краткое описание назначения Skill"
              style={{ marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Кодинг-агент</Text>
            <Select
              value={codingAgent || undefined}
              onChange={handleCodingAgentChange}
              options={codingAgentOptions}
              placeholder="Выберите кодинг-агент"
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 16 }}>
            <Title level={5}>Подсказка по frontmatter</Title>
            {frontmatterSummary.length === 0 ? (
              <Text type="secondary">Выберите кодинг-агент, чтобы увидеть ожидаемые поля frontmatter.</Text>
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
