import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Input, Modal, Select, Space, Tree, Typography, message } from 'antd';
import Editor from '@monaco-editor/react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { DeleteOutlined, FileAddOutlined, FileOutlined, FolderAddOutlined, FolderOpenOutlined } from '@ant-design/icons';
import { apiRequest } from '../api/request.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { toRussianError } from '../utils/errorMessages.js';
import { useLocation, useParams } from 'react-router-dom';
import StatusTag, { formatStatusLabel } from '../components/StatusTag.jsx';
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
  { value: 'FRONT', label: 'Frontend' },
  { value: 'BACK', label: 'Backend' },
  { value: 'DATA', label: 'Data' },
];
const skillKindOptions = [
  { value: 'analysis', label: 'Analysis' },
  { value: 'code', label: 'Code' },
  { value: 'review', label: 'Review' },
  { value: 'refactor', label: 'Refactor' },
  { value: 'qa', label: 'Qa' },
  { value: 'ops', label: 'Ops' },
];
const scopeOptions = [
  { value: 'organization', label: 'Organization' },
  { value: 'team', label: 'Team' },
];
const LOCKED_PUBLICATION_STATUSES = new Set(['pending_approval', 'approved', 'publishing', 'published']);

const DEFAULT_VERSION = '0.1';
const parseMajorMinor = (version) => {
  const normalized = (version || '').trim() || DEFAULT_VERSION;
  const match = normalized.match(/^(\d+)\.(\d+)$/);
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
const DEFAULT_SKILL_FILE = { path: 'SKILL.md', text_content: '', is_executable: false };
const MAX_FILES = 6;
const ROOT_DIRS = ['scripts', 'templates', 'assets'];
const normalizePath = (raw = '') => raw.trim().replace(/\\/g, '/').replace(/\/+/g, '/').replace(/\/$/, '');
const getParentPath = (path) => {
  const index = path.lastIndexOf('/');
  return index > 0 ? path.slice(0, index) : '';
};
const getBaseName = (path) => {
  const index = path.lastIndexOf('/');
  return index >= 0 ? path.slice(index + 1) : path;
};
const pathSegments = (path) => path.split('/').filter(Boolean);
const hasForbiddenSegment = (path) => {
  const segments = pathSegments(path);
  return segments.some((segment) => segment === '.' || segment === '..' || segment === '.git' || segment === '.svn');
};
const isAllowedFilePath = (path) => (
  path === 'SKILL.md'
  || path.startsWith('scripts/')
  || path.startsWith('templates/')
  || path.startsWith('assets/')
);
const isAllowedFolderPath = (path) => ROOT_DIRS.some((root) => path === root || path.startsWith(`${root}/`));
const isValidPath = (path) => (
  !!path
  && !path.startsWith('/')
  && !path.includes('\\')
  && !hasForbiddenSegment(path)
);
const collectFoldersFromFiles = (files) => {
  const folders = new Set();
  files.forEach((item) => {
    let parent = getParentPath(item.path);
    while (parent) {
      folders.add(parent);
      parent = getParentPath(parent);
    }
  });
  return Array.from(folders).sort((a, b) => a.localeCompare(b));
};

export default function SkillEditor() {
  const { user } = useAuth();
  const canManageCatalog = user?.roles?.includes('ADMIN') || user?.roles?.includes('FLOW_CONFIGURATOR');
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);
  const { skillId: skillIdParam } = useParams();
  const location = useLocation();
  const isCreateRoute = location.pathname.endsWith('/skills/create');
  const [selectedSkillId, setSelectedSkillId] = useState(null);
  const [editorValue, setEditorValue] = useState('');
  const [packageFiles, setPackageFiles] = useState([DEFAULT_SKILL_FILE]);
  const [folderPaths, setFolderPaths] = useState([]);
  const [selectedFilePath, setSelectedFilePath] = useState('SKILL.md');
  const [selectedTreeKey, setSelectedTreeKey] = useState('file:SKILL.md');
  const [resourceVersion, setResourceVersion] = useState(0);
  const [skillVersion, setSkillVersion] = useState('');
  const [baseVersion, setBaseVersion] = useState('');
  const [versionOptions, setVersionOptions] = useState([]);
  const [hasDraft, setHasDraft] = useState(false);
  const [currentStatus, setCurrentStatus] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [skillId, setSkillId] = useState('');
  const [codingAgent, setCodingAgent] = useState('');
  const [frontmatterSummary, setFrontmatterSummary] = useState([]);
  const [teamCode, setTeamCode] = useState('');
  const [scope, setScope] = useState('organization');
  const [platformCode, setPlatformCode] = useState('FRONT');
  const [tags, setTags] = useState([]);
  const [tagOptions, setTagOptions] = useState([]);
  const [skillKind, setSkillKind] = useState('');
  const [lifecycleStatus, setLifecycleStatus] = useState('active');
  const [publicationStatus, setPublicationStatus] = useState('');
  const [publishDialogOpen, setPublishDialogOpen] = useState(false);
  const [publishVariant, setPublishVariant] = useState('minor');
  const [createFolderModalOpen, setCreateFolderModalOpen] = useState(false);
  const [createFileModalOpen, setCreateFileModalOpen] = useState(false);
  const [newFolderPath, setNewFolderPath] = useState('');
  const [newFilePath, setNewFilePath] = useState('');
  const [forkedFrom, setForkedFrom] = useState('');
  const [isNewSkill, setIsNewSkill] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const editorRef = useRef(null);
  const previewRef = useRef(null);
  const isSyncingScroll = useRef(false);
  const previewContent = useMemo(() => splitFrontmatter(editorValue), [editorValue]);
  const normalizePublicationStatus = (publication, status) => (
    publication || (status === 'published' ? 'published' : '')
  );

  const syncEditorWithSelectedFile = (files, preferredPath = null) => {
    const sorted = [...files].sort((a, b) => a.path.localeCompare(b.path));
    const selected = preferredPath
      || (sorted.find((item) => item.path === 'SKILL.md')?.path)
      || sorted[0]?.path
      || 'SKILL.md';
    const selectedFile = sorted.find((item) => item.path === selected);
    setPackageFiles(sorted);
    setSelectedFilePath(selected);
    setSelectedTreeKey(`file:${selected}`);
    setEditorValue(selectedFile?.text_content || '');
  };

  const loadPackageFiles = async (skillIdValue, versionValue, fallbackMarkdown = '') => {
    try {
      const meta = await apiRequest(`/skills/${skillIdValue}/versions/${versionValue}/files`);
      const files = await Promise.all((meta || []).map(async (item) => {
        const contentResponse = await apiRequest(`/skills/${skillIdValue}/versions/${versionValue}/files/content`, {
          method: 'POST',
          body: JSON.stringify({ path: item.path }),
        });
        return {
          path: item.path,
          text_content: contentResponse?.text_content || '',
          is_executable: !!item.is_executable,
        };
      }));
      if (files.length > 0) {
        setFolderPaths(collectFoldersFromFiles(files));
        syncEditorWithSelectedFile(files);
        return;
      }
    } catch (err) {
      // fallback below
    }
    setFolderPaths([]);
    syncEditorWithSelectedFile([{ path: 'SKILL.md', text_content: fallbackMarkdown || '', is_executable: false }]);
  };

  const loadSkill = async (skillId) => {
    try {
      const data = await apiRequest(`/skills/${skillId}`);
      setSelectedSkillId(skillId);
      await loadPackageFiles(skillId, data.version, data.skill_markdown || '');
      setResourceVersion(data.resource_version ?? 0);
      setSkillVersion(data.version || '');
      setBaseVersion(data.version || '');
      setCurrentStatus(data.status || '');
      setName(data.name || '');
      setDescription(data.description || '');
      setSkillId(data.skill_id || '');
      setCodingAgent(data.coding_agent || '');
      setTeamCode(data.team_code || '');
      setScope(data.scope || 'organization');
      setPlatformCode(data.platform_code || 'FRONT');
      setTags(data.tags || []);
      setSkillKind(data.skill_kind || '');
      setLifecycleStatus(data.lifecycle_status || 'active');
      setPublicationStatus(normalizePublicationStatus(data.publication_status, data.status));
      setForkedFrom(data.forked_from || '');
      setIsNewSkill(false);
      setIsEditing(false);
      await loadVersions(skillId, data.version);
      if (data.coding_agent) {
        await loadTemplate(data.coding_agent, { replaceMarkdown: false });
      } else {
        setFrontmatterSummary([]);
      }
    } catch (err) {
      const rawMessage = String(err?.message || '');
      if (rawMessage.includes('Skill not found:')) {
        startNewSkill();
        setSkillId(skillId);
        if (skillId.startsWith('team-')) {
          setScope('team');
        }
        return;
      }
      message.error(err.message || 'Failed to load Skill');
    }
  };

  const loadTags = async () => {
    try {
      const data = await apiRequest('/skills/tags');
      setTagOptions((data || []).map((tag) => ({ value: tag, label: tag })));
    } catch (err) {
      // ignore tags dictionary loading errors
    }
  };

  const loadVersions = async (skillIdValue, currentVersion) => {
    try {
      const versions = await apiRequest(`/skills/${skillIdValue}/versions`);
      const mapped = versions.map((item) => ({
        label: `v${item.version} · ${formatStatusLabel(item.status)}`,
        value: item.version,
        canonical: item.canonical_name,
        skillId: item.skill_id,
        status: item.status,
        resourceVersion: item.resource_version,
      }));
      setVersionOptions(mapped);
      setHasDraft(mapped.some((item) => item.status === 'draft'));
      if (currentVersion) {
        setSkillVersion(currentVersion);
      }
    } catch (err) {
      message.error(err.message || 'Failed to load Skill versions');
    }
  };

  const handleVersionSelect = async (value, { keepEditing = false } = {}) => {
    if (!selectedSkillId) return;
    const selected = versionOptions.find((option) => option.value === value);
    if (!selected) return;
    try {
      const data = await apiRequest(`/skills/${selected.skillId}/versions/${value}`);
      setSelectedSkillId(selected.skillId);
      await loadPackageFiles(selected.skillId, value, data.skill_markdown || '');
      setResourceVersion(data.resource_version ?? 0);
      setSkillVersion(data.version || value);
      setBaseVersion(data.version || value);
      setCurrentStatus(data.status || '');
      setName(data.name || '');
      setDescription(data.description || '');
      setSkillId(data.skill_id || '');
      setCodingAgent(data.coding_agent || '');
      setTeamCode(data.team_code || '');
      setScope(data.scope || 'organization');
      setPlatformCode(data.platform_code || 'FRONT');
      setTags(data.tags || []);
      setSkillKind(data.skill_kind || '');
      setLifecycleStatus(data.lifecycle_status || 'active');
      setPublicationStatus(normalizePublicationStatus(data.publication_status, data.status));
      setForkedFrom(data.forked_from || '');
      setIsNewSkill(false);
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
    if (!canManageCatalog) {
      return;
    }
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
        const templateText = template.template || '';
        const nextFiles = packageFiles.some((item) => item.path === 'SKILL.md')
          ? packageFiles.map((item) => (item.path === 'SKILL.md' ? { ...item, text_content: templateText } : item))
          : [{ path: 'SKILL.md', text_content: templateText, is_executable: false }, ...packageFiles];
        syncEditorWithSelectedFile(nextFiles, 'SKILL.md');
      }
    } catch (err) {
      message.error(err.message || 'Failed to load Skill template');
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
    await applyChange(isNewSkill || !hasContent);
  };

  const handleSkillIdChange = (value) => {
    if (scope === 'team') {
      const trimmed = value.trim();
      if (trimmed && !trimmed.startsWith('team-')) {
        setSkillId(`team-${trimmed}`);
        return;
      }
    }
    setSkillId(value);
  };

  const handleScopeChange = (value) => {
    setScope(value);
    if (!isEditing) {
      return;
    }
    if (value === 'team' && skillId && !skillId.startsWith('team-')) {
      setSkillId(`team-${skillId.trim()}`);
      return;
    }
    if (value === 'organization' && skillId && skillId.startsWith('team-')) {
      setSkillId(skillId.replace(/^team-/, ''));
    }
  };

  const updateSelectedFileContent = (value) => {
    const nextText = value ?? '';
    setEditorValue(nextText);
    setPackageFiles((prev) => prev.map((item) => (
      item.path === selectedFilePath
        ? { ...item, text_content: nextText }
        : item
    )));
  };

  const handleSelectFile = (path) => {
    const file = packageFiles.find((item) => item.path === path);
    setSelectedFilePath(path);
    setSelectedTreeKey(`file:${path}`);
    setEditorValue(file?.text_content || '');
  };

  const resolveSelectedBaseFolder = () => (
    selectedTreeKey.startsWith('folder:')
      ? selectedTreeKey.slice('folder:'.length)
      : getParentPath(selectedFilePath)
  );

  const openCreateFolderDialog = () => {
    if (!isEditing) return;
    const baseFolder = resolveSelectedBaseFolder();
    setNewFolderPath(baseFolder || 'scripts');
    setCreateFolderModalOpen(true);
  };

  const confirmCreateFolder = () => {
    const folderPath = normalizePath(newFolderPath);
    if (!isValidPath(folderPath) || !isAllowedFolderPath(folderPath)) {
      message.error('Invalid folder path');
      return;
    }
    if (packageFiles.some((item) => item.path === folderPath)) {
      message.error('Folder conflicts with an existing file');
      return;
    }
    const next = new Set(folderPaths);
    let current = folderPath;
    while (current) {
      next.add(current);
      current = getParentPath(current);
    }
    setFolderPaths(Array.from(next).sort((a, b) => a.localeCompare(b)));
    setSelectedTreeKey(`folder:${folderPath}`);
    setCreateFolderModalOpen(false);
    setNewFolderPath('');
  };

  const openCreateFileDialog = () => {
    if (!isEditing) return;
    const baseFolder = resolveSelectedBaseFolder();
    const suggestion = baseFolder ? `${baseFolder}/new-file.md` : 'scripts/new-file.md';
    setNewFilePath(suggestion);
    setCreateFileModalOpen(true);
  };

  const confirmCreateFile = () => {
    const path = normalizePath(newFilePath);
    if (!isValidPath(path) || !isAllowedFilePath(path)) {
      message.error('Invalid file path');
      return;
    }
    if (path === 'SKILL.md') {
      message.error('SKILL.md already exists at the root');
      return;
    }
    if (packageFiles.some((item) => item.path === path)) {
      message.error('File already exists');
      return;
    }
    if (folderPaths.includes(path)) {
      message.error('File conflicts with an existing folder');
      return;
    }
    if (packageFiles.length >= MAX_FILES) {
      message.error(`File limit exceeded (max ${MAX_FILES})`);
      return;
    }
    const parent = getParentPath(path);
    if (parent) {
      const nextFolders = new Set(folderPaths);
      let current = parent;
      while (current) {
        nextFolders.add(current);
        current = getParentPath(current);
      }
      setFolderPaths(Array.from(nextFolders).sort((a, b) => a.localeCompare(b)));
    }
    const nextFile = { path, text_content: '', is_executable: path.startsWith('scripts/') };
    syncEditorWithSelectedFile([...packageFiles, nextFile], path);
    setCreateFileModalOpen(false);
    setNewFilePath('');
  };

  const removeSelectedNode = () => {
    if (!isEditing || !selectedTreeKey) return;
    if (selectedTreeKey.startsWith('file:')) {
      const filePath = selectedTreeKey.slice('file:'.length);
      if (filePath === 'SKILL.md') {
        message.error('SKILL.md cannot be deleted');
        return;
      }
      const nextFiles = packageFiles.filter((item) => item.path !== filePath);
      setFolderPaths((prev) => {
        const merged = new Set([...prev, ...collectFoldersFromFiles(nextFiles)]);
        return Array.from(merged).sort((a, b) => a.localeCompare(b));
      });
      syncEditorWithSelectedFile(nextFiles, 'SKILL.md');
      return;
    }
    if (selectedTreeKey.startsWith('folder:')) {
      const folderPath = selectedTreeKey.slice('folder:'.length);
      const prefix = `${folderPath}/`;
      const nextFiles = packageFiles.filter((item) => !item.path.startsWith(prefix));
      const nextFolders = folderPaths.filter((item) => item !== folderPath && !item.startsWith(prefix));
      setFolderPaths(nextFolders);
      syncEditorWithSelectedFile(nextFiles, 'SKILL.md');
    }
  };

  const saveSkill = async ({ publish, release = false }) => {
    if (!canManageCatalog) {
      message.error('Only ADMIN and FLOW_CONFIGURATOR can edit skills');
      return false;
    }
    const publication = (publicationStatus || '').toLowerCase();
    const isLockedAfterPublicationRequest = LOCKED_PUBLICATION_STATUSES.has(publication);
    if (selectedSkillId && isLockedAfterPublicationRequest) {
      message.error('Editing is locked after publication request');
      return false;
    }
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
    if (!codingAgent) {
      message.error('Coding agent is required');
      return;
    }
    if (!teamCode.trim()) {
      message.error('Team code is required');
      return;
    }
    if (!platformCode) {
      message.error('Platform is required');
      return;
    }
    if (!skillKind) {
      message.error('Skill kind is required');
      return;
    }
    if (!packageFiles.some((item) => item.path === 'SKILL.md')) {
      message.error('SKILL.md is required');
      return;
    }
    const normalizedSkillId = skillId.trim();
    const normalizedSelectedSkillId = (selectedSkillId || '').trim();
    let effectiveVersion = normalizedSkillId === normalizedSelectedSkillId ? (resourceVersion ?? 0) : 0;
    const canLookupExistingDraft = !!normalizedSelectedSkillId && normalizedSkillId === normalizedSelectedSkillId;
    if (effectiveVersion === 0 && normalizedSkillId && canLookupExistingDraft) {
      try {
        const versions = await apiRequest(`/skills/${normalizedSkillId}/versions`);
        const baseMajor = parseMajorMinor(baseVersion || skillVersion || DEFAULT_VERSION).major;
        const matchingDraft = versions.find((item) => {
          if (item.status !== 'draft') return false;
          const parsed = parseMajorMinor(item.version);
          return parsed.valid && parsed.major === baseMajor;
        });
        if (matchingDraft) {
          effectiveVersion = matchingDraft.resource_version ?? 0;
        }
      } catch (err) {
        // If skill does not exist yet, we keep resource_version = 0 for first save.
      }
    }
    try {
      const response = await apiRequest(`/skills/${normalizedSkillId}/save`, {
        method: 'POST',
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({
          name: name.trim(),
          description: description.trim(),
          skill_id: normalizedSkillId,
          coding_agent: codingAgent,
          team_code: teamCode.trim(),
          scope,
          platform_code: platformCode,
          tags,
          skill_kind: skillKind,
          lifecycle_status: lifecycleStatus,
          forked_from: forkedFrom || undefined,
          files: packageFiles.map((item) => ({
            path: item.path,
            text_content: item.text_content ?? '',
            is_executable: !!item.is_executable,
          })),
          publish,
          release,
          base_version: baseVersion || undefined,
          resource_version: effectiveVersion,
        }),
      });
      await loadPackageFiles(response.skill_id || normalizedSkillId, response.version || skillVersion, response.skill_markdown || editorValue);
      setResourceVersion(response.resource_version ?? resourceVersion);
      setSkillVersion(response.version || skillVersion);
      setBaseVersion(response.version || baseVersion);
      const nextStatus = response.status || currentStatus;
      setCurrentStatus(nextStatus);
      setPublicationStatus(normalizePublicationStatus(response.publication_status, nextStatus) || publicationStatus);
      setScope(response.scope || scope);
      setForkedFrom(response.forked_from || forkedFrom);
      setSelectedSkillId(response.skill_id || normalizedSkillId);
      setIsNewSkill(false);
      setIsEditing(false);
      await loadVersions(response.skill_id || normalizedSkillId, response.version || skillVersion);
      if (publish) {
        message.success('Publication requested');
      } else {
        message.success('Draft saved');
      }
      return true;
    } catch (err) {
      message.error(toRussianError(err?.message, 'Failed to save Skill'));
      return false;
    }
  };

  const startNewSkill = () => {
    setSelectedSkillId(null);
    setName('');
    setDescription('');
    setSkillId('');
    setCodingAgent('');
    setTeamCode('');
    setScope('organization');
    setPlatformCode('FRONT');
    setTags([]);
    setSkillKind('');
    setLifecycleStatus('active');
    setPublicationStatus('draft');
    setCreateFolderModalOpen(false);
    setCreateFileModalOpen(false);
    setNewFolderPath('');
    setNewFilePath('');
    setForkedFrom('');
    setPackageFiles([{ ...DEFAULT_SKILL_FILE }]);
    setFolderPaths([]);
    setSelectedFilePath('SKILL.md');
    setSelectedTreeKey('file:SKILL.md');
    setEditorValue('');
    setResourceVersion(0);
    setSkillVersion('');
    setBaseVersion('');
    setVersionOptions([]);
    setFrontmatterSummary([]);
    setIsNewSkill(true);
    setIsEditing(canManageCatalog);
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

  useEffect(() => {
    loadTags();
  }, []);

  const fileTreeData = useMemo(() => {
    const folderMap = new Map();
    const roots = [];

    const ensureFolderNode = (path) => {
      if (!path) return null;
      if (folderMap.has(path)) return folderMap.get(path);
      const node = {
        key: `folder:${path}`,
        title: getBaseName(path),
        isLeaf: false,
        icon: <FolderOpenOutlined />,
        children: [],
      };
      folderMap.set(path, node);
      const parent = getParentPath(path);
      if (parent) {
        ensureFolderNode(parent)?.children.push(node);
      } else {
        roots.push(node);
      }
      return node;
    };

    folderPaths.forEach((path) => ensureFolderNode(path));

    packageFiles.forEach((item) => {
      const node = {
        key: `file:${item.path}`,
        title: getBaseName(item.path),
        isLeaf: true,
        icon: <FileOutlined />,
      };
      const parent = getParentPath(item.path);
      if (parent) {
        ensureFolderNode(parent)?.children.push(node);
      } else {
        roots.push(node);
      }
    });

    const sortNodes = (nodes) => {
      nodes.sort((a, b) => {
        if (!!a.isLeaf !== !!b.isLeaf) {
          return a.isLeaf ? 1 : -1;
        }
        return String(a.title).localeCompare(String(b.title), undefined, { sensitivity: 'base' });
      });
      nodes.forEach((node) => {
        if (Array.isArray(node.children)) {
          sortNodes(node.children);
        }
      });
    };

    sortNodes(roots);
    return roots;
  }, [folderPaths, packageFiles]);

  const latestPublishedVersion = getLatestVersion(versionOptions, 'published');
  const currentParsed = parseMajorMinor(skillVersion || baseVersion || latestPublishedVersion || DEFAULT_VERSION);
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
  const publicationStatusValue = (publicationStatus || '').toLowerCase();
  const hasPublicationRequest = LOCKED_PUBLICATION_STATUSES.has(publicationStatusValue);
  const canEditCurrentDraft = canManageCatalog && currentStatus === 'draft' && !hasPublicationRequest;
  const canDeleteDraft = canManageCatalog
    && !!selectedSkillId
    && !!skillVersion
    && currentStatus === 'draft'
    && !hasPublicationRequest;

  const startDraftFromPublished = () => {
    if (!canManageCatalog) {
      return;
    }
    if (!latestPublishedVersion) {
      message.error('A published skill is required to create a new version');
      return;
    }
    const sourceVersion = latestPublishedVersion;
    const sourceId = selectedSkillId || skillId;
    if (sourceId && sourceVersion) {
      setForkedFrom(`${sourceId}@${sourceVersion}`);
    }
    setBaseVersion(sourceVersion);
    setCurrentStatus('draft');
    setPublicationStatus('draft');
    setIsEditing(true);
    if (draftForMajor) {
      setResourceVersion(draftForMajor.resourceVersion ?? 0);
      setSkillVersion(draftForMajor.value || nextDraftVersion);
      return;
    }
    setResourceVersion(0);
    setSkillVersion(nextDraftVersion);
  };

  const deleteCurrentDraft = async () => {
    if (!canDeleteDraft) {
      return;
    }
    Modal.confirm({
      title: 'Delete draft skill?',
      content: `Skill ${selectedSkillId}@${skillVersion} will be removed.`,
      okText: 'Delete',
      okButtonProps: { danger: true },
      cancelText: 'Cancel',
      onOk: async () => {
        try {
          await apiRequest(`/skills/${selectedSkillId}/versions/${skillVersion}/draft`, {
            method: 'DELETE',
          });
          message.success('Draft deleted');
          if (selectedSkillId) {
            try {
              await loadSkill(selectedSkillId);
            } catch (reloadErr) {
              startNewSkill();
              setSkillId(selectedSkillId);
            }
          } else {
            startNewSkill();
          }
        } catch (err) {
          message.error(toRussianError(err?.message, 'Failed to delete Skill draft'));
        }
      },
    });
  };

  const openPublishDialog = () => {
    setPublishVariant('minor');
    setPublishDialogOpen(true);
  };

  const confirmPublish = async () => {
    const success = await saveSkill({
      publish: true,
      release: publishVariant === 'major',
    });
    if (success) {
      setPublishDialogOpen(false);
    }
  };

  return (
    <div className="rule-editor-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Skill editor</Title>
        <Space>
          {canManageCatalog && !isEditing && (
            currentStatus === 'draft' ? (
              <>
                <Button type="default" onClick={beginEditDraft} disabled={!canEditCurrentDraft}>Edit</Button>
                <Button type="default" onClick={openPublishDialog} disabled={!canEditCurrentDraft}>Request publication</Button>
                {canDeleteDraft && (
                  <Button danger type="default" onClick={deleteCurrentDraft}>Delete draft</Button>
                )}
              </>
            ) : (
              <Button type="default" onClick={startDraftFromPublished}>
                {`Create new version ${nextDraftVersion}`}
              </Button>
            )
          )}
          {canManageCatalog && isEditing && (
            <>
              <Button type="default" onClick={() => saveSkill({ publish: false })}>Save</Button>
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
                <Text className="muted">{selectedFilePath}</Text>
                <div className="editor-pane-body">
                  <Editor
                    height="100%"
                    defaultLanguage="markdown"
                    value={editorValue}
                    onChange={updateSelectedFileContent}
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
                  {selectedFilePath === 'SKILL.md' && previewContent.frontmatter && (
                    <pre className="frontmatter-block">{`---\n${previewContent.frontmatter}\n---`}</pre>
                  )}
                  {selectedFilePath === 'SKILL.md' ? (
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>
                      {previewContent.body || ''}
                    </ReactMarkdown>
                  ) : (
                    <pre className="frontmatter-block">{editorValue || ''}</pre>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div className="editor-pane">
              <Text className="muted">Preview</Text>
              <div className="editor-pane-body markdown-preview">
                {selectedFilePath === 'SKILL.md' && previewContent.frontmatter && (
                  <pre className="frontmatter-block">{`---\n${previewContent.frontmatter}\n---`}</pre>
                )}
                {selectedFilePath === 'SKILL.md' ? (
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {previewContent.body || ''}
                  </ReactMarkdown>
                ) : (
                  <pre className="frontmatter-block">{editorValue || ''}</pre>
                )}
              </div>
            </div>
          )}
        </Card>
        <div className="side-panel skill-side-stack">
          <Card className="skill-files-panel">
            <div className="skill-files-card-header">
              <Title level={5} style={{ margin: 0 }}>Files</Title>
              <Space size={6}>
                <Button
                  size="small"
                  icon={<FolderAddOutlined />}
                  onClick={openCreateFolderDialog}
                  disabled={!isEditing}
                  title="Create folder"
                />
                <Button
                  size="small"
                  icon={<FileAddOutlined />}
                  onClick={openCreateFileDialog}
                  disabled={!isEditing}
                  title="Create file"
                />
                <Button
                  size="small"
                  icon={<DeleteOutlined />}
                  onClick={removeSelectedNode}
                  disabled={!isEditing || selectedTreeKey === 'file:SKILL.md'}
                  title="Delete selected file or folder"
                />
              </Space>
            </div>
            <div className="skill-files-tree">
              <Tree
                showIcon
                blockNode
                defaultExpandAll
                treeData={fileTreeData}
                selectedKeys={selectedTreeKey ? [selectedTreeKey] : []}
                onSelect={(keys, info) => {
                  const key = String(keys?.[0] || info?.node?.key || '');
                  if (!key) return;
                  setSelectedTreeKey(key);
                  if (key.startsWith('file:')) {
                    handleSelectFile(key.slice('file:'.length));
                  }
                }}
              />
            </div>
          </Card>
          <Card className="skill-fields-panel">
            <div className="rule-fields-header">
              <Title level={5} style={{ margin: 0 }}>Skill fields</Title>
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
              <Text className="muted">{requiredLabel('Coding agent')}</Text>
              <Select
                value={codingAgent || undefined}
                onChange={handleCodingAgentChange}
                options={codingAgentOptions}
                placeholder="Select coding agent"
                title="Which coding agent this skill is executed with."
                style={{ width: '100%', marginTop: 4 }}
                disabled={!isEditing}
              />
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">{requiredLabel('Name')}</Text>
              <Input
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Update requirements"
                title="Short display name of the skill in the catalog."
                style={{ marginTop: 4 }}
                disabled={!isEditing}
              />
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">{requiredLabel('ID Skill')}</Text>
              <Input
                value={skillId}
                onChange={(event) => handleSkillIdChange(event.target.value)}
                placeholder="update-requirements"
                title="Stable skill identifier used in canonical_name and references."
                style={{ marginTop: 4 }}
                disabled={!isEditing || !!selectedSkillId}
              />
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">{requiredLabel('Description')}</Text>
              <Input.TextArea
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                rows={4}
                placeholder="Brief description of the Skill purpose"
                title="Short skill purpose description for search and cards."
                style={{ marginTop: 4 }}
                disabled={!isEditing}
              />
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">{requiredLabel('Team code')}</Text>
              <Input
                value={teamCode}
                onChange={(event) => setTeamCode(event.target.value)}
                placeholder="platform-team"
                title="Owner team code for this skill."
                style={{ marginTop: 4 }}
                disabled={!isEditing}
              />
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">{requiredLabel('Scope')}</Text>
              <Select
                value={scope || undefined}
                onChange={handleScopeChange}
                options={scopeOptions}
                placeholder="Select scope"
                style={{ width: '100%', marginTop: 4 }}
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
                title="Skill target platform: FRONT, BACK, or DATA."
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
                options={tagOptions}
                placeholder="Add tags"
                title="Tags used for filtering and search."
                style={{ width: '100%', marginTop: 4 }}
                disabled={!isEditing}
              />
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">{requiredLabel('Skill kind')}</Text>
              <Select
                value={skillKind || undefined}
                onChange={setSkillKind}
                options={skillKindOptions}
                placeholder="Select skill kind"
                title="Skill type (analysis/code/review/refactor/qa/ops)."
                style={{ width: '100%', marginTop: 4 }}
                disabled={!isEditing}
              />
            </div>
          {!isCreateRoute && (
            <div style={{ marginTop: 12 }}>
                <Text className="muted">Publication status</Text>
                <div style={{ marginTop: 4 }}>
                  <StatusTag value={publicationStatus || (currentStatus === 'published' ? 'published' : 'draft')} />
                </div>
              </div>
            )}
          </Card>
        </div>
      </div>
      <Modal
        title="Create folder"
        open={createFolderModalOpen}
        onCancel={() => {
          setCreateFolderModalOpen(false);
          setNewFolderPath('');
        }}
        onOk={confirmCreateFolder}
        okText="Create"
        cancelText="Cancel"
      >
        <Text className="muted">Folder path (only `scripts/*`, `templates/*`, `assets/*`)</Text>
        <Input
          value={newFolderPath}
          onChange={(event) => setNewFolderPath(event.target.value)}
          placeholder="scripts/helpers"
          style={{ marginTop: 8 }}
          autoFocus
        />
      </Modal>
      <Modal
        title="Create file"
        open={createFileModalOpen}
        onCancel={() => {
          setCreateFileModalOpen(false);
          setNewFilePath('');
        }}
        onOk={confirmCreateFile}
        okText="Create"
        cancelText="Cancel"
      >
        <Text className="muted">File path (`SKILL.md` already exists at root)</Text>
        <Input
          value={newFilePath}
          onChange={(event) => setNewFilePath(event.target.value)}
          placeholder="scripts/my-script.sh"
          style={{ marginTop: 8 }}
          autoFocus
        />
      </Modal>
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
        </div>
      </Modal>
    </div>
  );
}
