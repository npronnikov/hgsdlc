import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Input, Modal, Select, Space, Typography, message } from 'antd';
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
];
const platformOptions = [
  { value: 'FRONT', label: 'FRONT' },
  { value: 'BACK', label: 'BACK' },
  { value: 'DATA', label: 'DATA' },
];
const environmentOptions = [
  { value: 'dev', label: 'dev' },
  { value: 'prod', label: 'prod' },
];
const visibilityOptions = [
  { value: 'internal', label: 'internal' },
  { value: 'restricted', label: 'restricted' },
  { value: 'public', label: 'public' },
];
const lifecycleOptions = [
  { value: 'active', label: 'active' },
  { value: 'deprecated', label: 'deprecated' },
  { value: 'retired', label: 'retired' },
];
const publicationTargetOptions = [
  { value: 'db_only', label: 'DB' },
  { value: 'db_and_git', label: 'DB + Git' },
];
const publishModeOptions = [
  { value: 'local', label: 'local (direct push)' },
  { value: 'pr', label: 'pr (create Pull Request)' },
];
const ruleKindOptions = [
  { value: 'architecture', label: 'architecture' },
  { value: 'coding-style', label: 'coding-style' },
  { value: 'security', label: 'security' },
  { value: 'governance', label: 'governance' },
];
const scopeOptions = [
  { value: 'global', label: 'global' },
  { value: 'project', label: 'project' },
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
const requiredLabel = (label) => `${label} *`;

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
  const [teamCode, setTeamCode] = useState('');
  const [platformCode, setPlatformCode] = useState('FRONT');
  const [tags, setTags] = useState([]);
  const [ruleKind, setRuleKind] = useState('');
  const [scope, setScope] = useState('');
  const [environment, setEnvironment] = useState('dev');
  const [visibility, setVisibility] = useState('internal');
  const [lifecycleStatus, setLifecycleStatus] = useState('active');
  const [approvalStatus, setApprovalStatus] = useState('');
  const [contentSource, setContentSource] = useState('');
  const [publicationStatus, setPublicationStatus] = useState('');
  const [publicationTarget, setPublicationTarget] = useState('db_and_git');
  const [publishMode, setPublishMode] = useState('pr');
  const [publishDialogOpen, setPublishDialogOpen] = useState(false);
  const [publishVariant, setPublishVariant] = useState('minor');
  const [publishDialogTarget, setPublishDialogTarget] = useState('db_and_git');
  const [publishDialogMode, setPublishDialogMode] = useState('pr');
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
      setTeamCode(data.team_code || '');
      setPlatformCode(data.platform_code || 'FRONT');
      setTags(data.tags || []);
      setRuleKind(data.rule_kind || '');
      setScope(data.scope || '');
      setEnvironment(data.environment || 'dev');
      setVisibility(data.visibility || 'internal');
      setLifecycleStatus(data.lifecycle_status || 'active');
      setApprovalStatus(data.approval_status || '');
      setContentSource(data.content_source || '');
      setPublicationStatus(data.publication_status || '');
      setPublicationTarget(data.publication_target || 'db_and_git');
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
      setTeamCode(data.team_code || '');
      setPlatformCode(data.platform_code || 'FRONT');
      setTags(data.tags || []);
      setRuleKind(data.rule_kind || '');
      setScope(data.scope || '');
      setEnvironment(data.environment || 'dev');
      setVisibility(data.visibility || 'internal');
      setLifecycleStatus(data.lifecycle_status || 'active');
      setApprovalStatus(data.approval_status || '');
      setContentSource(data.content_source || '');
      setPublicationStatus(data.publication_status || '');
      setPublicationTarget(data.publication_target || 'db_and_git');
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

  const saveRule = async ({ publish, release = false, publicationTargetOverride = null, publishModeOverride = null }) => {
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
          team_code: teamCode.trim(),
          platform_code: platformCode,
          tags,
          rule_kind: ruleKind,
          scope,
          environment,
          visibility,
          lifecycle_status: lifecycleStatus,
          rule_markdown: editorValue,
          publish,
          publication_target: publicationTargetOverride || publicationTarget,
          publish_mode: publishModeOverride || publishMode,
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
      setApprovalStatus(response.approval_status || approvalStatus);
      setContentSource(response.content_source || contentSource);
      setPublicationStatus(response.publication_status || publicationStatus);
      setPublicationTarget(response.publication_target || publicationTarget);
      setIsNewRule(false);
      setIsEditing(false);
      await loadVersions(response.rule_id || ruleId, response.version || ruleVersion);
      message.success(publish ? 'Publication requested' : 'Draft saved');
      return true;
    } catch (err) {
      message.error(toRussianError(err?.message, 'Failed to save Rule'));
      return false;
    }
  };

  const startNewRule = () => {
    setSelectedRuleId(null);
    setTitle('');
    setDescription('');
    setRuleId('');
    setCodingAgent('');
    setTeamCode('');
    setPlatformCode('FRONT');
    setTags([]);
    setRuleKind('');
    setScope('');
    setEnvironment('dev');
    setVisibility('internal');
    setLifecycleStatus('active');
    setApprovalStatus('');
    setContentSource('');
    setPublicationStatus('draft');
    setPublicationTarget('db_and_git');
    setPublishMode('pr');
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

  const openPublishDialog = () => {
    setPublishVariant('minor');
    setPublishDialogTarget(publicationTarget || 'db_and_git');
    setPublishDialogMode(publishMode || 'pr');
    setPublishDialogOpen(true);
  };

  const confirmPublish = async () => {
    const success = await saveRule({
      publish: true,
      release: publishVariant === 'major',
      publicationTargetOverride: publishDialogTarget,
      publishModeOverride: publishDialogMode,
    });
    if (success) {
      setPublishDialogOpen(false);
    }
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
                <Button type="default" onClick={openPublishDialog}>Request publication</Button>
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
              <Button type="default" onClick={openPublishDialog}>Request publication</Button>
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
            <Text className="muted">{requiredLabel('Coding agent')}</Text>
            <Select
              value={codingAgent || undefined}
              onChange={handleCodingAgentChange}
              options={codingAgentOptions}
              placeholder="Select coding agent"
              title="Для какого coding-agent будет выполняться правило."
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">{requiredLabel('Name')}</Text>
            <Input
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="Project rule"
              title="Короткое отображаемое имя правила."
              style={{ marginTop: 4 }}
              disabled={!isEditing || !!selectedRuleId}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">{requiredLabel('Description')}</Text>
            <Input.TextArea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Briefly describe the purpose of the rule"
              rows={2}
              title="Кратко объясняет, когда и зачем использовать правило."
              style={{ marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">{requiredLabel('ID Rule')}</Text>
            <Input
              value={ruleId}
              onChange={(event) => setRuleId(event.target.value)}
              placeholder="project-rule"
              title="Стабильный идентификатор правила для canonical_name и ссылок."
              style={{ marginTop: 4 }}
              disabled={!isEditing || !!selectedRuleId}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">{requiredLabel('Team code')}</Text>
            <Input
              value={teamCode}
              onChange={(event) => setTeamCode(event.target.value)}
              placeholder="platform-team"
              title="Код команды-владельца правила."
              style={{ marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">{requiredLabel('Platform')}</Text>
            <Select
              value={platformCode || undefined}
              onChange={setPlatformCode}
              options={platformOptions}
              placeholder="Select platform"
              title="Платформа применения правила: FRONT, BACK или DATA."
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Tags</Text>
            <Select
              mode="tags"
              value={tags}
              onChange={(nextTags) => setTags(nextTags)}
              placeholder="Add tags"
              title="Теги для поиска и фильтрации."
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Rule kind</Text>
            <Select
              value={ruleKind || undefined}
              onChange={setRuleKind}
              options={ruleKindOptions}
              placeholder="Select rule kind"
              title="Категория правила (архитектура, безопасность и т.п.)."
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Scope</Text>
            <Select
              value={scope || undefined}
              onChange={setScope}
              options={scopeOptions}
              placeholder="Select scope"
              title="Область действия правила: global или project."
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Environment</Text>
            <Select
              value={environment || undefined}
              onChange={setEnvironment}
              options={environmentOptions}
              placeholder="Select environment"
              title="Среда использования версии: dev или prod."
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Visibility</Text>
            <Select
              value={visibility || undefined}
              onChange={setVisibility}
              options={visibilityOptions}
              placeholder="Select visibility"
              title="Видимость версии внутри платформы."
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          <div style={{ marginTop: 12 }}>
            <Text className="muted">Lifecycle status</Text>
            <Select
              value={lifecycleStatus || undefined}
              onChange={setLifecycleStatus}
              options={lifecycleOptions}
              placeholder="Select lifecycle status"
              title="Состояние жизненного цикла: active/deprecated/retired."
              style={{ width: '100%', marginTop: 4 }}
              disabled={!isEditing}
            />
          </div>
          {!isCreateRoute && (
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Approval status</Text>
              <div className="mono" style={{ marginTop: 4 }}>{approvalStatus || 'draft'}</div>
            </div>
          )}
          {!isCreateRoute && (
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Publication status</Text>
              <div className="mono" style={{ marginTop: 4 }}>{publicationStatus || 'draft'}</div>
            </div>
          )}
          {!isCreateRoute && (
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Content source</Text>
              <div className="mono" style={{ marginTop: 4 }}>{contentSource || 'db'}</div>
            </div>
          )}
        </Card>
      </div>
      <Modal
        title="Request publication"
        open={publishDialogOpen}
        onCancel={() => setPublishDialogOpen(false)}
        onOk={confirmPublish}
        okText="Request"
        cancelText="Cancel"
      >
        <div style={{ display: 'grid', gap: 12 }}>
          <div>
            <Text className="muted">Version strategy</Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={publishVariant}
              onChange={setPublishVariant}
              options={[
                { value: 'minor', label: publishLabel },
                { value: 'major', label: releaseLabel },
              ]}
            />
          </div>
          <div>
            <Text className="muted">Publication target</Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={publishDialogTarget}
              onChange={setPublishDialogTarget}
              options={publicationTargetOptions}
            />
          </div>
          <div>
            <Text className="muted">Publish mode</Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={publishDialogMode}
              onChange={setPublishDialogMode}
              options={publishModeOptions}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
