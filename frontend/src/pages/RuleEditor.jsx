import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Dropdown, Input, Modal, Select, Space, Typography, message } from 'antd';
import { MoreOutlined } from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { apiRequest } from '../api/request.js';
import { toRussianError } from '../utils/errorMessages.js';
import { useLocation, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const splitFrontmatter = (markdown = '') => {
  const normalized = markdown.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const lines = normalized.split('\n');
  if (lines.length === 0 || lines[0].trim() !== '---') {
    return { frontmatter: null, body: markdown };
  }
  let endIndex = -1;
  for (let i = 1; i < lines.length; i += 1) {
    if (lines[i].trim() === '---') {
      endIndex = i;
      break;
    }
  }
  if (endIndex === -1) {
    return { frontmatter: null, body: markdown };
  }
  const frontmatter = lines.slice(1, endIndex).join('\n').trimEnd();
  const body = lines.slice(endIndex + 1).join('\n');
  return { frontmatter, body };
};

const codingAgentOptions = [
  { value: 'qwen', label: 'qwen' },
  { value: 'claude', label: 'claude' },
  { value: 'cursor', label: 'cursor' },
];

const DEFAULT_VERSION = '0.1';
const parseMajorMinor = (version) => {
  const normalized = (version || '').trim() || DEFAULT_VERSION;
  const match = normalized.match(/^(\d+)\.(\d+)(?:\.\d+)?$/);
  if (!match) {
    return { major: 0, minor: 0, valid: false };
  }
  return { major: Number(match[1]), minor: Number(match[2]), valid: true };
};
const compareVersions = (a, b) => {
  if (a.major !== b.major) return a.major - b.major;
  return a.minor - b.minor;
};
const nextMajorVersion = (version) => {
  const parsed = parseMajorMinor(version);
  const major = parsed.valid ? parsed.major : 0;
  return `${major + 1}.0`;
};
const nextMinorVersion = (version) => {
  const parsed = parseMajorMinor(version);
  if (!parsed.valid) {
    return DEFAULT_VERSION;
  }
  return `${parsed.major}.${parsed.minor + 1}`;
};
const getLatestVersion = (versions, status) => {
  const candidates = versions.filter((item) => !status || item.status === status);
  let best = null;
  candidates.forEach((item) => {
    const parsed = parseMajorMinor(item.value);
    if (!parsed.valid) {
      return;
    }
    if (!best || compareVersions(parsed, best.parsed) > 0) {
      best = { value: item.value, parsed };
    }
  });
  return best ? best.value : '';
};

export default function RuleEditor() {
  const { ruleId: ruleIdParam } = useParams();
  const location = useLocation();
  const isCreateRoute = location.pathname.endsWith('/rules/create');
  const [selectedRuleId, setSelectedRuleId] = useState(null);
  const [editorValue, setEditorValue] = useState('');
  const [resourceVersion, setResourceVersion] = useState(0);
  const [ruleVersion, setRuleVersion] = useState('');
  const [versionOptions, setVersionOptions] = useState([]);
  const [hasDraft, setHasDraft] = useState(false);
  const [currentStatus, setCurrentStatus] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [ruleId, setRuleId] = useState('');
  const [codingAgent, setCodingAgent] = useState('');
  const [frontmatterSummary, setFrontmatterSummary] = useState([]);
  const [isNewRule, setIsNewRule] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const editorRef = useRef(null);
  const previewRef = useRef(null);
  const isSyncingScroll = useRef(false);
  const previewContent = useMemo(() => splitFrontmatter(editorValue), [editorValue]);

  const loadRule = async (ruleId) => {
    try {
      const data = await apiRequest(`/rules/${ruleId}`);
      setSelectedRuleId(ruleId);
      setEditorValue(data.rule_markdown || '');
      setResourceVersion(data.resource_version ?? 0);
      setRuleVersion(data.version || '');
      setCurrentStatus(data.status || '');
      setTitle(data.title || '');
      setDescription(data.description || '');
      setRuleId(data.rule_id || '');
      setCodingAgent(data.coding_agent || '');
      setIsNewRule(false);
      setIsEditing(false);
      await loadVersions(ruleId, data.version);
      if (data.coding_agent) {
        await loadTemplate(data.coding_agent, { replaceMarkdown: false });
      } else {
        setFrontmatterSummary([]);
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить Rule');
    }
  };

  const loadVersions = async (ruleId, currentVersion) => {
    try {
      const versions = await apiRequest(`/rules/${ruleId}/versions`);
      const mapped = versions.map((item) => ({
        label: `v${item.version} · ${item.status === 'draft' ? 'черновик' : item.status === 'published' ? 'опубликовано' : item.status}`,
        value: item.version,
        canonical: item.canonical_name,
        ruleId: item.rule_id,
        status: item.status,
      }));
      setVersionOptions(mapped);
      setHasDraft(mapped.some((item) => item.status === 'draft'));
      if (currentVersion) {
        setRuleVersion(currentVersion);
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить версии Rule');
    }
  };

  const handleVersionSelect = async (value, { keepEditing = false } = {}) => {
    if (!selectedRuleId) return;
    const selected = versionOptions.find((option) => option.value === value);
    if (!selected) return;
    try {
      const data = await apiRequest(`/rules/${selected.ruleId}/versions/${value}`);
      setSelectedRuleId(selected.ruleId);
      setEditorValue(data.rule_markdown || '');
      setResourceVersion(data.resource_version ?? 0);
      setRuleVersion(data.version || value);
      setCurrentStatus(data.status || '');
      setTitle(data.title || '');
      setDescription(data.description || '');
      setRuleId(data.rule_id || '');
      setCodingAgent(data.coding_agent || '');
      setIsNewRule(false);
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
      const template = await apiRequest(`/rule-templates/${codingAgentValue}`);
      setFrontmatterSummary(template.frontmatterSummary || []);
      if (replaceMarkdown) {
        setEditorValue(template.template || '');
      }
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить шаблон Rule');
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
        cancelText: 'Оставить текущий',
        onOk: () => applyChange(true),
        onCancel: () => applyChange(false),
      });
      return;
    }
    await applyChange(isNewRule || !hasContent);
  };

  const saveRule = async ({ publish, release = false }) => {
    if (!ruleId) {
      message.error('Нужен ID Rule');
      return;
    }
    if (!title.trim()) {
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
    const effectiveVersion = ruleId === selectedRuleId ? (resourceVersion ?? 0) : 0;
    try {
      const response = await apiRequest(`/rules/${ruleId}/save`, {
        method: 'POST',
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({
          title: title.trim(),
          description: description.trim(),
          rule_id: ruleId.trim(),
          coding_agent: codingAgent,
          rule_markdown: editorValue,
          publish,
          release,
          resource_version: effectiveVersion,
        }),
      });
      setEditorValue(response.rule_markdown || editorValue);
      setResourceVersion(response.resource_version ?? resourceVersion);
      setRuleVersion(response.version || ruleVersion);
      setCurrentStatus(response.status || currentStatus);
      setSelectedRuleId(response.rule_id || ruleId);
      setIsNewRule(false);
      setIsEditing(false);
      await loadVersions(response.rule_id || ruleId, response.version || ruleVersion);
      message.success(publish ? 'Rule опубликован' : 'Черновик сохранён');
    } catch (err) {
      message.error(toRussianError(err?.message, 'Не удалось сохранить Rule'));
    }
  };

  const startNewRule = () => {
    setSelectedRuleId(null);
    setTitle('');
    setDescription('');
    setRuleId('');
    setCodingAgent('');
    setEditorValue('');
    setResourceVersion(0);
    setRuleVersion('');
    setCurrentStatus('');
    setVersionOptions([]);
    setFrontmatterSummary([]);
    setIsNewRule(true);
    setIsEditing(true);
    setHasDraft(false);
  };

  useEffect(() => {
    if (isCreateRoute) {
      startNewRule();
      return;
    }
    if (ruleIdParam) {
      loadRule(ruleIdParam);
    }
  }, [ruleIdParam, isCreateRoute]);

  const latestDraftVersion = getLatestVersion(versionOptions, 'draft');
  const latestPublishedVersion = getLatestVersion(versionOptions, 'published');
  const publishVersion = latestDraftVersion
    || (latestPublishedVersion ? nextMinorVersion(latestPublishedVersion) : (ruleVersion || DEFAULT_VERSION));
  const publishLabel = `Опубликовать версию → ${publishVersion}`;
  const releaseLabel = `Несовместимое обновление (major) → ${nextMajorVersion(publishVersion)}`;

  return (
    <div className="rule-editor-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Редактор Rule</Title>
        <Space>
          {!isEditing && (
            currentStatus === 'draft' ? (
              <>
                <Button type="default" onClick={beginEdit}>Редактировать</Button>
                <Dropdown
                  menu={{
                    items: [
                      { key: 'publish', label: publishLabel },
                      { key: 'release', label: releaseLabel },
                    ],
                    onClick: ({ key }) => {
                      if (key === 'release') {
                        Modal.confirm({
                          title: 'Подтвердить major-обновление?',
                          content: `Будет выпущена несовместимая версия → ${nextMajorVersion(publishVersion)}.`,
                          okText: 'Выпустить',
                          cancelText: 'Отмена',
                          onOk: () => saveRule({ publish: true, release: true }),
                        });
                        return;
                      }
                      saveRule({ publish: true, release: false });
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
              <Button type="default" onClick={() => saveRule({ publish: false })}>Сохранить</Button>
              <Dropdown
                menu={{
                  items: [
                    { key: 'publish', label: publishLabel },
                    { key: 'release', label: releaseLabel },
                  ],
                  onClick: ({ key }) => {
                    if (key === 'release') {
                      Modal.confirm({
                        title: 'Подтвердить major-обновление?',
                        content: `Будет выпущена несовместимая версия → ${nextMajorVersion(publishVersion)}.`,
                        okText: 'Выпустить',
                        cancelText: 'Отмена',
                        onOk: () => saveRule({ publish: true, release: true }),
                      });
                      return;
                    }
                    saveRule({ publish: true, release: false });
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
                  {previewContent.frontmatter && (
                    <pre className="frontmatter-block">{`---\n${previewContent.frontmatter}\n---`}</pre>
                  )}
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {previewContent.body || ''}
                  </ReactMarkdown>
                </div>
              </div>
            </div>
          ) : (
            <div className="editor-pane">
              <Text className="muted">Предпросмотр</Text>
              <div className="editor-pane-body markdown-preview">
                {previewContent.frontmatter && (
                  <pre className="frontmatter-block">{`---\n${previewContent.frontmatter}\n---`}</pre>
                )}
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {previewContent.body || ''}
                </ReactMarkdown>
              </div>
            </div>
          )}
        </Card>
        <Card>
          <div className="rule-fields-header">
            <Title level={5} style={{ margin: 0 }}>Поля Rule</Title>
            {selectedRuleId ? (
              <Select
                value={ruleVersion || undefined}
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
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="Rule проекта"
              style={{ marginTop: 4 }}
              disabled={!isEditing || !!selectedRuleId}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Описание</Text>
            <Input.TextArea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Кратко опишите назначение правила"
              rows={2}
              style={{ marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">ID Rule</Text>
            <Input
              value={ruleId}
              onChange={(event) => setRuleId(event.target.value)}
              placeholder="project-rule"
              style={{ marginTop: 4 }}
              disabled={!isEditing || !!selectedRuleId}
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
