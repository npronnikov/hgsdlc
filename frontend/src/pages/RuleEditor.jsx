import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Dropdown, Input, Modal, Select, Space, Typography, message } from 'antd';
import { MoreOutlined } from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { apiRequest } from '../api/request.js';
import { toRussianError } from '../utils/errorMessages.js';
import { useLocation, useParams } from 'react-router-dom';
import { formatStatusLabel } from '../components/StatusTag.jsx';
import { useThemeMode } from '../theme/ThemeContext.jsx';
import { configureMonacoThemes, getMonacoThemeName } from '../utils/monacoTheme.js';

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
const getMaxPublishedMajor = (versions) => {
  let maxMajor = null;
  versions.forEach((item) => {
    if (item.status !== 'published') return;
    const parsed = parseMajorMinor(item.value);
    if (!parsed.valid) return;
    if (maxMajor === null || parsed.major > maxMajor) {
      maxMajor = parsed.major;
    }
  });
  return maxMajor;
};
const getMaxPublishedMinorForMajor = (versions, major) => {
  let maxMinor = null;
  versions.forEach((item) => {
    if (item.status !== 'published') return;
    const parsed = parseMajorMinor(item.value);
    if (!parsed.valid || parsed.major !== major) return;
    if (maxMinor === null || parsed.minor > maxMinor) {
      maxMinor = parsed.minor;
    }
  });
  return maxMinor;
};
const getDraftForMajor = (versions, major) => (
  versions.find((item) => {
    if (item.status !== 'draft') return false;
    const parsed = parseMajorMinor(item.value);
    return parsed.valid && parsed.major === major;
  })
);

export default function RuleEditor() {
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);
  const { ruleId: ruleIdParam } = useParams();
  const location = useLocation();
  const isCreateRoute = location.pathname.endsWith('/rules/create');
  const [selectedRuleId, setSelectedRuleId] = useState(null);
  const [editorValue, setEditorValue] = useState('');
  const [resourceVersion, setResourceVersion] = useState(0);
  const [ruleVersion, setRuleVersion] = useState('');
  const [baseVersion, setBaseVersion] = useState('');
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
      setBaseVersion(data.version || '');
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
      message.error(err.message || 'Failed to load Rule');
    }
  };

  const loadVersions = async (ruleId, currentVersion) => {
    try {
      const versions = await apiRequest(`/rules/${ruleId}/versions`);
      const mapped = versions.map((item) => ({
        label: `v${item.version} · ${formatStatusLabel(item.status)}`,
        value: item.version,
        canonical: item.canonical_name,
        ruleId: item.rule_id,
        status: item.status,
        resourceVersion: item.resource_version,
      }));
      setVersionOptions(mapped);
      setHasDraft(mapped.some((item) => item.status === 'draft'));
      if (currentVersion) {
        setRuleVersion(currentVersion);
      }
    } catch (err) {
      message.error(err.message || 'Failed to load Rule versions');
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
      setBaseVersion(data.version || value);
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
      message.error(err.message || 'Failed to load selected version');
    }
  };

  const beginEditDraft = () => {
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
      message.error(err.message || 'Failed to load Rule template');
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
        title: 'Change coding agent?',
        content: 'Template and frontmatter requirements will change. Replace markdown with a new template?',
        okText: 'Replace template',
        cancelText: 'Keep current',
        onOk: () => applyChange(true),
        onCancel: () => applyChange(false),
      });
      return;
    }
    await applyChange(isNewRule || !hasContent);
  };

  const saveRule = async ({ publish, release = false }) => {
    if (!ruleId) {
      message.error('Rule ID is required');
      return;
    }
    if (!title.trim()) {
      message.error('Name is required');
      return;
    }
    if (!description.trim()) {
      message.error('Description is required');
      return;
    }
    if (!codingAgent) {
      message.error('Coding agent is required');
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
          base_version: baseVersion || undefined,
          resource_version: effectiveVersion,
        }),
      });
      setEditorValue(response.rule_markdown || editorValue);
      setResourceVersion(response.resource_version ?? resourceVersion);
      setRuleVersion(response.version || ruleVersion);
      setBaseVersion(response.version || baseVersion);
      setCurrentStatus(response.status || currentStatus);
      setSelectedRuleId(response.rule_id || ruleId);
      setIsNewRule(false);
      setIsEditing(false);
      await loadVersions(response.rule_id || ruleId, response.version || ruleVersion);
      message.success(publish ? 'Rule published' : 'Draft saved');
    } catch (err) {
      message.error(toRussianError(err?.message, 'Failed to save Rule'));
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
    setBaseVersion('');
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

  const latestPublishedVersion = getLatestVersion(versionOptions, 'published');
  const currentParsed = parseMajorMinor(ruleVersion || baseVersion || latestPublishedVersion || DEFAULT_VERSION);
  const currentMajor = currentParsed.valid ? currentParsed.major : parseMajorMinor(DEFAULT_VERSION).major;
  const maxMinorForMajor = getMaxPublishedMinorForMajor(versionOptions, currentMajor);
  const nextDraftVersion = maxMinorForMajor !== null
    ? `${currentMajor}.${maxMinorForMajor + 1}`
    : (currentMajor === parseMajorMinor(DEFAULT_VERSION).major && !latestPublishedVersion
      ? DEFAULT_VERSION
      : `${currentMajor}.0`);
  const draftForMajor = getDraftForMajor(versionOptions, currentMajor);
  const publishVersion = draftForMajor ? draftForMajor.value : nextDraftVersion;
  const publishLabel = `Publish version -> ${publishVersion}`;
  const maxPublishedMajor = getMaxPublishedMajor(versionOptions);
  const releaseMajor = maxPublishedMajor === null ? 1 : maxPublishedMajor + 1;
  const releaseVersion = `${releaseMajor}.0`;
  const releaseLabel = `Breaking update (major) -> ${releaseVersion}`;

  const startDraftFromPublished = () => {
    const sourceVersion = ruleVersion || baseVersion || latestPublishedVersion || DEFAULT_VERSION;
    setBaseVersion(sourceVersion);
    setCurrentStatus('draft');
    setIsEditing(true);
    if (draftForMajor) {
      setResourceVersion(draftForMajor.resourceVersion ?? 0);
      setRuleVersion(draftForMajor.value || nextDraftVersion);
      return;
    }
    setResourceVersion(0);
    setRuleVersion(nextDraftVersion);
  };

  return (
    <div className="rule-editor-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Rule editor</Title>
        <Space>
          {!isEditing && (
            currentStatus === 'draft' ? (
              <>
                <Button type="default" onClick={beginEditDraft}>Edit</Button>
                <Dropdown
                  menu={{
                    items: [
                      { key: 'publish', label: publishLabel },
                      { key: 'release', label: releaseLabel },
                    ],
                    onClick: ({ key }) => {
                      if (key === 'release') {
                        Modal.confirm({
                          title: 'Confirm major update?',
                          content: `A breaking version will be released -> ${releaseVersion}. This is the next available major after ${maxPublishedMajor === null ? 'no published versions' : `${maxPublishedMajor}.x`}.`,
                          okText: 'Release',
                          cancelText: 'Cancel',
                          onOk: () => saveRule({ publish: true, release: true }),
                        });
                        return;
                      }
                      saveRule({ publish: true, release: false });
                    },
                  }}
                >
                  <Button type="default" icon={<MoreOutlined />}>Publish</Button>
                </Dropdown>
              </>
            ) : (
              <Button type="default" onClick={startDraftFromPublished}>
                {`Create new version ${nextDraftVersion}`}
              </Button>
            )
          )}
          {isEditing && (
            <>
              <Button type="default" onClick={() => saveRule({ publish: false })}>Save</Button>
              <Dropdown
                menu={{
                  items: [
                    { key: 'publish', label: publishLabel },
                    { key: 'release', label: releaseLabel },
                  ],
                  onClick: ({ key }) => {
                    if (key === 'release') {
                      Modal.confirm({
                        title: 'Confirm major update?',
                        content: `A breaking version will be released -> ${releaseVersion}. This is the next available major after ${maxPublishedMajor === null ? 'no published versions' : `${maxPublishedMajor}.x`}.`,
                        okText: 'Release',
                        cancelText: 'Cancel',
                        onOk: () => saveRule({ publish: true, release: true }),
                      });
                      return;
                    }
                    saveRule({ publish: true, release: false });
                  },
                }}
              >
                <Button type="default" icon={<MoreOutlined />}>Publish</Button>
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
                        configureMonacoThemes(monaco);
                        monaco.editor.setTheme(monacoTheme);
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
                    theme={monacoTheme}
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
              <Text className="muted">Preview</Text>
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
            <Title level={5} style={{ margin: 0 }}>Rule fields</Title>
            {selectedRuleId ? (
              <Select
                value={ruleVersion || undefined}
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
            <Text className="muted">Coding agent</Text>
            <Select
              value={codingAgent || undefined}
              onChange={handleCodingAgentChange}
              options={codingAgentOptions}
              placeholder="Select coding agent"
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Name</Text>
            <Input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="Project rule"
              style={{ marginTop: 4 }}
              disabled={!isEditing || !!selectedRuleId}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Description</Text>
            <Input.TextArea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Briefly describe the purpose of the rule"
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
        </Card>
      </div>
    </div>
  );
}
