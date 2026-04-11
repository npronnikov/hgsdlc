import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Drawer,
  Empty,
  Input,
  List,
  Modal,
  Row,
  Segmented,
  Space,
  Spin,
  Tag,
  Tree,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DownloadOutlined,
  MinusCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import { apiRequest } from '../api/request.js';
import StatusTag from '../components/StatusTag.jsx';
import MarkdownPreview from '../components/MarkdownPreview.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import { useThemeMode } from '../theme/ThemeContext.jsx';
import { configureMonacoThemes, getMonacoThemeName } from '../utils/monacoTheme.js';

const { Title, Text } = Typography;

const FILE_FILTER_OPTIONS = [
  { label: 'Different', value: 'different' },
  { label: 'All', value: 'all' },
  { label: 'Same', value: 'same' },
];

function formatWithoutArtifactLabel(artifactType) {
  if (artifactType === 'SKILL') return 'without skill';
  if (artifactType === 'RULE') return 'without rule';
  return 'without artifact';
}

function shortArtifactId(value, max = 25) {
  const text = String(value || '');
  if (!text || text.length <= max) return text;
  return `${text.slice(0, max)}...`;
}

function formatVerdictLabel(verdict, run) {
  const hasArtifactB = Boolean(run?.artifact_id_b);
  if (verdict === 'SKILL_USEFUL') {
    if (hasArtifactB) {
      return `Run A better (${run?.artifact_id || 'A'})`;
    }
    return run?.artifact_type === 'RULE' ? 'Rule was useful' : 'Skill was useful';
  }
  if (verdict === 'SKILL_NOT_HELPFUL') {
    if (hasArtifactB) {
      return `Run B better (${run?.artifact_id_b || 'B'})`;
    }
    return run?.artifact_type === 'RULE' ? 'Rule did not help' : 'Skill did not help';
  }
  if (verdict === 'NEUTRAL') {
    return hasArtifactB ? 'Tie / Skip' : 'Neutral / Skip';
  }
  return verdict || '—';
}

function detectLanguage(path) {
  if (!path) return 'plaintext';
  const lower = path.toLowerCase();
  if (lower.endsWith('.md') || lower.endsWith('.markdown')) return 'markdown';
  if (lower.endsWith('.java')) return 'java';
  if (lower.endsWith('.kt')) return 'kotlin';
  if (lower.endsWith('.js') || lower.endsWith('.jsx')) return 'javascript';
  if (lower.endsWith('.ts') || lower.endsWith('.tsx')) return 'typescript';
  if (lower.endsWith('.json')) return 'json';
  if (lower.endsWith('.yml') || lower.endsWith('.yaml')) return 'yaml';
  if (lower.endsWith('.xml')) return 'xml';
  if (lower.endsWith('.html')) return 'html';
  if (lower.endsWith('.css') || lower.endsWith('.scss')) return 'css';
  if (lower.endsWith('.sql')) return 'sql';
  if (lower.endsWith('.sh')) return 'shell';
  if (lower.endsWith('.py')) return 'python';
  return 'plaintext';
}

function toTreeData(files) {
  const root = {};
  files.forEach((file) => {
    const parts = String(file.path || '').split('/').filter(Boolean);
    if (parts.length === 0) return;
    let node = root;
    parts.forEach((part, index) => {
      if (!node[part]) node[part] = { __children: {}, __path: null };
      if (index === parts.length - 1) node[part].__path = file.path;
      node = node[part].__children;
    });
  });

  const buildNodes = (obj, prefix = '') => Object.keys(obj)
    .sort((a, b) => a.localeCompare(b))
    .map((name) => {
      const item = obj[name];
      const key = item.__path || `${prefix}/${name}`;
      const children = buildNodes(item.__children, key);
      return {
        key,
        title: name,
        children: children.length > 0 ? children : undefined,
        isLeaf: !!item.__path,
      };
    });

  return buildNodes(root);
}

function formatBytes(value) {
  const size = Number(value || 0);
  if (!Number.isFinite(size) || size <= 0) {
    return '0 B';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  const units = ['KB', 'MB', 'GB'];
  let current = size / 1024;
  let idx = 0;
  while (current >= 1024 && idx < units.length - 1) {
    current /= 1024;
    idx += 1;
  }
  return `${current.toFixed(current >= 10 || idx === 0 ? 1 : 2)} ${units[idx]}`;
}

function isFileChangedOnSide(file, side) {
  const rawStatus = side === 'a' ? file?.status_a : file?.status_b;
  const status = String(rawStatus || 'unchanged').toLowerCase();
  return status !== 'unchanged';
}

function isFileDifferent(file) {
  if (!file) return false;
  const existsA = Boolean(file.exists_a);
  const existsB = Boolean(file.exists_b);
  if (existsA !== existsB) return true;
  if (!existsA && !existsB) return false;

  const binaryA = Boolean(file.binary_a);
  const binaryB = Boolean(file.binary_b);
  if (binaryA !== binaryB) return true;
  if (binaryA && binaryB) {
    return Number(file.size_bytes_a || 0) !== Number(file.size_bytes_b || 0);
  }

  const contentA = String(file.content_a || '');
  const contentB = String(file.content_b || '');
  return contentA !== contentB;
}

function normalizeLineComments(value) {
  if (!Array.isArray(value)) return [];
  return value.reduce((acc, item) => {
    const path = String(item?.path || '').trim();
    const side = String(item?.side || '').toUpperCase();
    const benchmarkRunId = String(item?.benchmark_run_id || '').trim();
    const runId = String(item?.run_id || '').trim();
    const line = Number(item?.line || 0);
    const text = String(item?.text || '').trim();
    if (!path || (side !== 'A' && side !== 'B') || !Number.isInteger(line) || line < 1 || !text) {
      return acc;
    }
    acc.push({
      id: item?.id ? String(item.id) : `${benchmarkRunId || 'benchmark'}:${runId || side}:${path}:${line}`,
      benchmark_run_id: benchmarkRunId || null,
      run_id: runId || null,
      path,
      side,
      line,
      text,
      created_at: item?.created_at || new Date().toISOString(),
    });
    return acc;
  }, []);
}

function parseLineCommentsJson(raw) {
  if (typeof raw !== 'string' || raw.trim() === '') {
    return [];
  }
  try {
    return normalizeLineComments(JSON.parse(raw));
  } catch {
    return [];
  }
}

function toLineCommentsJson(comments) {
  return JSON.stringify(normalizeLineComments(comments));
}

function getDraftStorageKey(runId) {
  return `benchmark-run-comments:${runId}`;
}

function loadDraft(runId) {
  if (typeof window === 'undefined' || !window.localStorage || !runId) {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(getDraftStorageKey(runId));
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return {
      reviewComment: String(parsed?.reviewComment || ''),
      lineComments: normalizeLineComments(parsed?.lineComments),
    };
  } catch {
    return null;
  }
}

function saveDraft(runId, reviewComment, lineComments) {
  if (typeof window === 'undefined' || !window.localStorage || !runId) {
    return;
  }
  try {
    window.localStorage.setItem(getDraftStorageKey(runId), JSON.stringify({
      reviewComment,
      lineComments: normalizeLineComments(lineComments),
      savedAt: new Date().toISOString(),
    }));
  } catch {
    // ignore
  }
}

function clearDraft(runId) {
  if (typeof window === 'undefined' || !window.localStorage || !runId) {
    return;
  }
  try {
    window.localStorage.removeItem(getDraftStorageKey(runId));
  } catch {
    // ignore
  }
}

function normalizeInlineText(value, fallback = '—') {
  const normalized = String(value || '').replace(/\s+/g, ' ').trim();
  return normalized || fallback;
}

function formatReportDate(value) {
  if (!value) return '—';
  try {
    return new Intl.DateTimeFormat('ru-RU', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(value));
  } catch {
    return String(value);
  }
}

function resolveComparedLabel(artifactType, artifactId, fallbackLabel = 'control') {
  if (!artifactId) {
    return fallbackLabel;
  }
  const type = artifactType ? String(artifactType).toLowerCase() : 'artifact';
  return `${type}:${artifactId}`;
}

function resolveDecisionVerdictLabel(verdict) {
  if (verdict === 'SKILL_USEFUL') return 'A better';
  if (verdict === 'SKILL_NOT_HELPFUL') return 'B better';
  return 'tie';
}

function extractPrimaryReason(reviewComment) {
  if (!reviewComment) return null;
  const firstLine = String(reviewComment)
    .split(/\r?\n/)
    .map((item) => item.trim())
    .find(Boolean);
  if (!firstLine) return null;
  return firstLine.length > 220 ? `${firstLine.slice(0, 217)}...` : firstLine;
}

function stripFirstMarkdownHeading(markdown) {
  const lines = String(markdown || '').split('\n');
  if (!lines.length || !lines[0].startsWith('# ')) {
    return markdown || '';
  }
  let start = 1;
  while (start < lines.length && lines[start].trim() === '') {
    start += 1;
  }
  return lines.slice(start).join('\n');
}

export default function BenchmarkRun() {
  const navigate = useNavigate();
  const { runId } = useParams();
  const { user } = useAuth();
  const { isDark } = useThemeMode();

  const [run, setRun] = useState(null);
  const [benchmarkCase, setBenchmarkCase] = useState(null);
  const [projectNameById, setProjectNameById] = useState({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [fileComparison, setFileComparison] = useState([]);
  const [fileComparisonLoading, setFileComparisonLoading] = useState(false);
  const [selectedFilePath, setSelectedFilePath] = useState(null);
  const [verdictOpen, setVerdictOpen] = useState(false);
  const [reportPreviewOpen, setReportPreviewOpen] = useState(false);
  const [fileFilter, setFileFilter] = useState('different');
  const [reviewComment, setReviewComment] = useState('');
  const [lineComments, setLineComments] = useState([]);
  const [lineCommentDraft, setLineCommentDraft] = useState(null);
  const [draftInitialized, setDraftInitialized] = useState(false);
  const [contextMenuState, setContextMenuState] = useState({
    open: false,
    x: 0,
    y: 0,
    side: null,
    line: null,
    path: null,
  });

  const pollRef = useRef(null);
  const runStatusRef = useRef(null);
  const selectedFilePathRef = useRef(null);
  const editorRefs = useRef({ A: null, B: null });
  const monacoRefs = useRef({ A: null, B: null });
  const decorationIdsRef = useRef({ A: [], B: [] });
  const contextMenuListenerRef = useRef({ A: null, B: null });
  const mouseDownListenerRef = useRef({ A: null, B: null });

  const load = useCallback(async () => {
    try {
      const data = await apiRequest(`/benchmark/runs/${runId}`);
      setRun(data);
      return data;
    } catch (err) {
      message.error(err.message || 'Failed to load benchmark run');
      return null;
    } finally {
      setLoading(false);
    }
  }, [runId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    runStatusRef.current = run?.status || null;
  }, [run?.status]);

  const loadFileComparison = useCallback(async () => {
    setFileComparisonLoading(true);
    try {
      const data = await apiRequest(`/benchmark/runs/${runId}/file-comparison`);
      const files = Array.isArray(data?.files) ? data.files : [];
      setFileComparison(files);
    } catch (err) {
      message.error(err.message || 'Failed to load file comparison');
      setFileComparison([]);
    } finally {
      setFileComparisonLoading(false);
    }
  }, [runId]);

  useEffect(() => {
    if (!run) return undefined;
    if (run.status !== 'RUNNING') {
      if (pollRef.current) clearInterval(pollRef.current);
      return undefined;
    }
    pollRef.current = setInterval(() => {
      load();
    }, 5000);
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, [run, load]);

  useEffect(() => {
    if (!run) return;
    if (run.status === 'WAITING_COMPARISON' || run.status === 'COMPLETED') {
      loadFileComparison();
    } else {
      setFileComparison([]);
    }
  }, [run, loadFileComparison]);

  useEffect(() => {
    setDraftInitialized(false);
    setReviewComment('');
    setLineComments([]);
    setLineCommentDraft(null);
    setBenchmarkCase(null);
    setProjectNameById({});
    setReportPreviewOpen(false);
    setContextMenuState({ open: false, x: 0, y: 0, side: null, line: null, path: null });
  }, [runId]);

  useEffect(() => {
    let active = true;
    const loadCaseAndProject = async () => {
      if (!run?.case_id) {
        setBenchmarkCase(null);
        return;
      }
      try {
        const [casesData, projectsData] = await Promise.all([
          apiRequest('/benchmark/cases'),
          apiRequest('/projects'),
        ]);
        if (!active) return;
        const cases = Array.isArray(casesData) ? casesData : [];
        const projects = Array.isArray(projectsData) ? projectsData : [];
        setBenchmarkCase(cases.find((item) => item?.id === run.case_id) || null);
        const map = {};
        projects.forEach((project) => {
          if (project?.id) {
            map[project.id] = project.name || project.id;
          }
        });
        setProjectNameById(map);
      } catch {
        if (!active) return;
        setBenchmarkCase(null);
        setProjectNameById({});
      }
    };
    loadCaseAndProject();
    return () => {
      active = false;
    };
  }, [run?.case_id]);

  useEffect(() => {
    if (!run) return;

    if (run.status === 'COMPLETED') {
      setReviewComment(run.review_comment || '');
      setLineComments(parseLineCommentsJson(run.line_comments_json));
      setLineCommentDraft(null);
      setDraftInitialized(true);
      clearDraft(runId);
      return;
    }

    if (!draftInitialized) {
      const draft = loadDraft(runId);
      if (draft) {
        setReviewComment(draft.reviewComment);
        setLineComments(draft.lineComments);
      } else {
        setReviewComment(run.review_comment || '');
        setLineComments(parseLineCommentsJson(run.line_comments_json));
      }
      setDraftInitialized(true);
    }
  }, [run, runId, draftInitialized]);

  useEffect(() => {
    if (!run || !draftInitialized) return;
    if (run.status === 'COMPLETED') return;
    saveDraft(runId, reviewComment, lineComments);
  }, [run, runId, draftInitialized, reviewComment, lineComments]);

  const filesWithMeta = useMemo(
    () => fileComparison.map((file) => ({ ...file, __different: isFileDifferent(file) })),
    [fileComparison],
  );

  const filteredFiles = useMemo(() => {
    if (fileFilter === 'all') return filesWithMeta;
    if (fileFilter === 'same') return filesWithMeta.filter((item) => !item.__different);
    return filesWithMeta.filter((item) => item.__different);
  }, [filesWithMeta, fileFilter]);

  useEffect(() => {
    if (!filteredFiles.length) {
      setSelectedFilePath(null);
      return;
    }
    const exists = filteredFiles.some((item) => item.path === selectedFilePath);
    if (!exists) {
      setSelectedFilePath(filteredFiles[0].path);
    }
  }, [filteredFiles, selectedFilePath]);

  useEffect(() => {
    selectedFilePathRef.current = selectedFilePath;
  }, [selectedFilePath]);

  const summary = useMemo(() => {
    const changedA = fileComparison.filter((item) => isFileChangedOnSide(item, 'a')).length;
    const changedB = fileComparison.filter((item) => isFileChangedOnSide(item, 'b')).length;
    const different = fileComparison.filter((item) => isFileDifferent(item)).length;
    const same = Math.max(0, fileComparison.length - different);
    return {
      changedA,
      changedB,
      different,
      same,
      total: fileComparison.length,
    };
  }, [fileComparison]);

  const treeData = useMemo(() => toTreeData(filteredFiles), [filteredFiles]);

  const fileByPath = useMemo(() => {
    const map = new Map();
    filesWithMeta.forEach((item) => {
      map.set(item.path, item);
    });
    return map;
  }, [filesWithMeta]);

  const selectedFile = useMemo(() => fileByPath.get(selectedFilePath) || null, [fileByPath, selectedFilePath]);

  const selectedLanguage = detectLanguage(selectedFile?.path || '');
  const monacoTheme = getMonacoThemeName(isDark);

  const isWaiting = run?.status === 'WAITING_COMPARISON';
  const isCompleted = run?.status === 'COMPLETED';
  const isFailed = run?.status === 'FAILED';
  const isRunning = run?.status === 'RUNNING';
  const canOpenVerdict = isWaiting || isCompleted;
  const canEditComments = isWaiting;
  const hasArtifactB = Boolean(run?.artifact_id_b);
  const artifactLabelA = run?.artifact_id
    ? `${run?.artifact_type || 'Artifact'}: ${run.artifact_id}`
    : 'Run A';
  const artifactLabelB = run?.artifact_id_b
    ? `${run?.artifact_type_b || run?.artifact_type || 'Artifact'}: ${run.artifact_id_b}`
    : formatWithoutArtifactLabel(run?.artifact_type);
  const verdictPrompt = hasArtifactB
    ? 'Which run result is better?'
    : `Did the ${run?.artifact_type?.toLowerCase()} improve the result?`;
  const verdictApproveLabel = hasArtifactB
    ? `Run A better (${run?.artifact_id || 'A'})`
    : (run?.artifact_type === 'SKILL' ? 'Skill was useful' : 'Rule was useful');
  const verdictRejectLabel = hasArtifactB
    ? `Run B better (${run?.artifact_id_b || 'B'})`
    : (run?.artifact_type === 'SKILL' ? 'Skill did not help' : 'Rule did not help');
  const verdictNeutralLabel = hasArtifactB ? 'Tie / Skip' : 'Neutral / Skip';
  const runIdBySide = useMemo(() => ({
    A: run?.run_a_id ? String(run.run_a_id) : null,
    B: run?.run_b_id ? String(run.run_b_id) : null,
  }), [run?.run_a_id, run?.run_b_id]);

  const lineCommentsSorted = useMemo(() => [...lineComments].sort((a, b) => {
    if ((a.run_id || '') !== (b.run_id || '')) return (a.run_id || '').localeCompare(b.run_id || '');
    if (a.path !== b.path) return a.path.localeCompare(b.path);
    if (a.side !== b.side) return a.side.localeCompare(b.side);
    return a.line - b.line;
  }), [lineComments]);

  const reportMarkdown = useMemo(() => {
    if (!run) return '';
    const caseName = normalizeInlineText(benchmarkCase?.name || 'Untitled');
    const reportRunId = normalizeInlineText(run.id, '');
    const benchmarkLink = reportRunId ? `#/benchmark/${reportRunId}` : '#/benchmark';
    const instruction = normalizeInlineText(benchmarkCase?.instruction || run?.instruction);
    const caseId = normalizeInlineText(run.case_id);
    const projectId = benchmarkCase?.project_id;
    const project = projectId ? (projectNameById[projectId] || projectId) : '—';
    const author = normalizeInlineText(
      user?.username || user?.display_name || user?.displayName || run?.created_by || 'unknown',
      'unknown',
    );
    const comparedA = resolveComparedLabel(run?.artifact_type, run?.artifact_id, 'control');
    const comparedB = run?.artifact_id_b
      ? resolveComparedLabel(run?.artifact_type_b || run?.artifact_type, run?.artifact_id_b, 'control')
      : `control (${formatWithoutArtifactLabel(run?.artifact_type)})`;
    const lineCommentsBlock = lineCommentsSorted.length > 0
      ? lineCommentsSorted.map((item) => (
        `- [${item.side}] ${item.path}:${item.line} — ${normalizeInlineText(item.text, '')}`
      )).join('\n')
      : '- none';
    const decisionVerdict = resolveDecisionVerdictLabel(run?.human_verdict);
    const reasonFromComment = extractPrimaryReason(reviewComment);
    const reasons = [
      reasonFromComment || `Manual verdict: ${formatVerdictLabel(run?.human_verdict, run)}.`,
      `File deltas: changed A ${summary.changedA} / B ${summary.changedB}, different ${summary.different}, same ${summary.same}.`,
      lineCommentsSorted.length > 0
        ? `Risks: ${lineCommentsSorted.length} line comment(s) were recorded and should be reviewed before rollout.`
        : 'Risks/tradeoffs: no line-level risks were explicitly recorded.',
    ];
    const generalComment = String(reviewComment || '').trim() || 'No general comment.';

    return [
      `# Benchmark Report: ${caseName}`,
      `Benchmark: [${caseName}](${benchmarkLink})`,
      '',
      `Date: ${formatReportDate(run?.completed_at || run?.created_at)}`,
      '',
      `Author: ${author}`,
      '',
      `Case: ${caseId}`,
      '',
      `Project: ${project}`,
      '',
      `Instruction: ${instruction}`,
      '',
      '## Compared',
      `- Run A: ${comparedA}`,
      `- Run B: ${comparedB}`,
      `- Coding agent: ${normalizeInlineText(run?.coding_agent)}`,
      '',
      '## Summary',
      `- Files changed: A ${summary.changedA} / B ${summary.changedB}`,
      `- Files different: ${summary.different}`,
      `- Files same: ${summary.same}`,
      '',
      '## Review Notes',
      '### Line comments',
      lineCommentsBlock,
      '',
      '### General comment',
      generalComment,
      '',
      '## Decision',
      `- Verdict: ${decisionVerdict}`,
      '- Why:',
      `  1. ${reasons[0]}`,
      `  2. ${reasons[1]}`,
      `  3. ${reasons[2]}`,
      '',
    ].join('\n');
  }, [benchmarkCase, lineCommentsSorted, projectNameById, reviewComment, run, summary, user]);

  const reportPreviewTitle = useMemo(
    () => `Benchmark Report: ${normalizeInlineText(benchmarkCase?.name || 'Untitled')}`,
    [benchmarkCase?.name],
  );
  const reportMarkdownPreviewBody = useMemo(
    () => stripFirstMarkdownHeading(reportMarkdown),
    [reportMarkdown],
  );

  const canExportReport = isCompleted && Boolean(run?.human_verdict);

  const exportMarkdownReport = useCallback(() => {
    if (!reportMarkdown || typeof window === 'undefined') {
      return;
    }
    const blob = new Blob([reportMarkdown], { type: 'text/markdown;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `benchmark-report-${run?.id || runId}.md`;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    window.URL.revokeObjectURL(url);
    message.success('Markdown report exported');
  }, [reportMarkdown, run?.id, runId]);

  const openLineCommentDraft = useCallback((side, line, path) => {
    const effectivePath = path || selectedFilePathRef.current;
    const effectiveRunId = runIdBySide[side] || null;
    if (!effectivePath || !Number.isInteger(line) || line < 1) {
      return;
    }
    const existing = lineComments.find(
      (item) => item.path === effectivePath
        && item.side === side
        && (item.run_id === effectiveRunId || !item.run_id)
        && item.line === line,
    );
    setLineCommentDraft({
      benchmark_run_id: runId,
      run_id: effectiveRunId,
      path: effectivePath,
      side,
      line,
      text: existing?.text || '',
    });
  }, [lineComments, runIdBySide, runId]);

  const closeContextMenu = useCallback(() => {
    setContextMenuState((prev) => (prev.open
      ? {
        open: false,
        x: 0,
        y: 0,
        side: null,
        line: null,
        path: null,
      }
      : prev));
  }, []);

  const handleEditorMount = useCallback((side) => (editor, monaco) => {
    editorRefs.current[side] = editor;
    monacoRefs.current[side] = monaco;

    if (contextMenuListenerRef.current[side]) {
      contextMenuListenerRef.current[side].dispose();
    }
    if (mouseDownListenerRef.current[side]) {
      mouseDownListenerRef.current[side].dispose();
    }

    contextMenuListenerRef.current[side] = editor.onContextMenu((event) => {
      if (runStatusRef.current !== 'WAITING_COMPARISON') {
        return;
      }
      const position = event?.target?.position || editor.getPosition();
      if (!position) return;

      const browserEvent = event?.event?.browserEvent;
      if (browserEvent?.preventDefault) {
        browserEvent.preventDefault();
      }

      const path = selectedFilePathRef.current;
      if (!path) return;

      setContextMenuState({
        open: true,
        x: Number(browserEvent?.clientX || 0),
        y: Number(browserEvent?.clientY || 0),
        side,
        line: position.lineNumber,
        path,
      });
    });

    mouseDownListenerRef.current[side] = editor.onMouseDown(() => {
      closeContextMenu();
    });
  }, [closeContextMenu]);

  useEffect(() => {
    if (!canEditComments) {
      closeContextMenu();
    }
  }, [canEditComments, closeContextMenu]);

  useEffect(() => {
    closeContextMenu();
  }, [selectedFilePath, closeContextMenu]);

  useEffect(() => {
    if (!contextMenuState.open) {
      return undefined;
    }

    const onClick = () => closeContextMenu();
    const onEsc = (event) => {
      if (event.key === 'Escape') {
        closeContextMenu();
      }
    };

    window.addEventListener('click', onClick);
    window.addEventListener('scroll', onClick, true);
    window.addEventListener('keydown', onEsc);
    return () => {
      window.removeEventListener('click', onClick);
      window.removeEventListener('scroll', onClick, true);
      window.removeEventListener('keydown', onEsc);
    };
  }, [contextMenuState.open, closeContextMenu]);

  useEffect(() => {
    ['A', 'B'].forEach((side) => {
      const editor = editorRefs.current[side];
      const monaco = monacoRefs.current[side];
      if (!editor || !monaco) return;

      const path = selectedFile?.path;
      const runRef = runIdBySide[side] || null;
      const relevant = path
        ? lineComments.filter((item) => item.path === path
          && item.side === side
          && (item.run_id === runRef || !item.run_id))
        : [];

      const decorations = relevant.map((comment) => ({
        range: new monaco.Range(comment.line, 1, comment.line, 1),
        options: {
          isWholeLine: true,
          glyphMarginClassName: 'benchmark-line-comment-glyph',
          className: 'benchmark-line-comment-line',
          glyphMarginHoverMessage: [{ value: comment.text }],
        },
      }));

      decorationIdsRef.current[side] = editor.deltaDecorations(
        decorationIdsRef.current[side] || [],
        decorations,
      );
    });
  }, [lineComments, selectedFile?.path, runIdBySide]);

  useEffect(() => () => {
    ['A', 'B'].forEach((side) => {
      if (contextMenuListenerRef.current[side]) {
        contextMenuListenerRef.current[side].dispose();
      }
      if (mouseDownListenerRef.current[side]) {
        mouseDownListenerRef.current[side].dispose();
      }
    });
    if (pollRef.current) {
      clearInterval(pollRef.current);
    }
  }, []);

  const submitVerdict = async (verdict) => {
    setSubmitting(true);
    try {
      const data = await apiRequest(`/benchmark/runs/${runId}/verdict`, {
        method: 'POST',
        body: JSON.stringify({
          verdict,
          review_comment: reviewComment.trim() || null,
          line_comments_json: toLineCommentsJson(lineComments),
        }),
      });
      setRun(data);
      message.success('Verdict and comments saved');
    } catch (err) {
      message.error(err.message || 'Failed to submit verdict');
    } finally {
      setSubmitting(false);
    }
  };

  const saveLineCommentDraft = () => {
    if (!lineCommentDraft || !canEditComments) {
      return;
    }

    const text = String(lineCommentDraft.text || '').trim();
    const keyMatch = (item) => (
      item.benchmark_run_id === lineCommentDraft.benchmark_run_id
      && item.run_id === lineCommentDraft.run_id
      && item.path === lineCommentDraft.path
      && item.side === lineCommentDraft.side
      && item.line === lineCommentDraft.line
    );

    setLineComments((prev) => {
      const idx = prev.findIndex(keyMatch);
      if (!text) {
        if (idx === -1) return prev;
        const next = [...prev];
        next.splice(idx, 1);
        return next;
      }

      const nextItem = {
        id: idx >= 0
          ? prev[idx].id
          : `${lineCommentDraft.benchmark_run_id || runId}:${lineCommentDraft.run_id || lineCommentDraft.side}:${lineCommentDraft.path}:${lineCommentDraft.line}`,
        benchmark_run_id: lineCommentDraft.benchmark_run_id || runId,
        run_id: lineCommentDraft.run_id || null,
        path: lineCommentDraft.path,
        side: lineCommentDraft.side,
        line: lineCommentDraft.line,
        text,
        created_at: idx >= 0 ? prev[idx].created_at : new Date().toISOString(),
      };

      if (idx >= 0) {
        const next = [...prev];
        next[idx] = nextItem;
        return next;
      }

      return [...prev, nextItem];
    });

    setLineCommentDraft(null);
  };

  const removeLineComment = (comment) => {
    if (!canEditComments) return;
    setLineComments((prev) => prev.filter((item) => !(item.benchmark_run_id === comment.benchmark_run_id
      && item.run_id === comment.run_id
      && item.path === comment.path
      && item.side === comment.side
      && item.line === comment.line)));
    if (lineCommentDraft
      && lineCommentDraft.benchmark_run_id === comment.benchmark_run_id
      && lineCommentDraft.run_id === comment.run_id
      && lineCommentDraft.path === comment.path
      && lineCommentDraft.side === comment.side
      && lineCommentDraft.line === comment.line) {
      setLineCommentDraft(null);
    }
  };

  const focusLineComment = (comment, attempt = 0) => {
    if (selectedFilePathRef.current !== comment.path) {
      setSelectedFilePath(comment.path);
    }

    const editor = editorRefs.current[comment.side];
    if (!editor) {
      if (attempt < 8) {
        setTimeout(() => focusLineComment(comment, attempt + 1), 80);
      }
      return;
    }

    editor.revealLineInCenter(comment.line);
    editor.setPosition({ lineNumber: comment.line, column: 1 });
    editor.focus();
  };

  const openRunConsole = (targetRunId) => {
    if (!targetRunId) return;
    navigate(`/run-console?runId=${targetRunId}`);
  };

  const renderPaneContent = (side) => {
    if (!selectedFile) {
      return (
        <div className="benchmark-pane-empty">
          Select a file to compare content.
        </div>
      );
    }

    const isA = side === 'A';
    const exists = isA ? selectedFile.exists_a : selectedFile.exists_b;
    const binary = isA ? selectedFile.binary_a : selectedFile.binary_b;
    const content = isA ? selectedFile.content_a : selectedFile.content_b;

    if (binary) {
      return <div className="benchmark-pane-empty">Binary file</div>;
    }

    if (!exists) {
      return (
        <div className="benchmark-pane-empty">
          {isA ? 'File does not exist in Run A' : 'File does not exist in Run B'}
        </div>
      );
    }

    const editorOptions = {
      readOnly: true,
      contextmenu: false,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      wordWrap: 'off',
      glyphMargin: true,
      fontSize: 13,
      automaticLayout: true,
      scrollbar: { vertical: 'auto', horizontal: 'auto', verticalScrollbarSize: 10, horizontalScrollbarSize: 10 },
    };

    return (
      <div className="benchmark-editor-wrap">
        <Editor
          height="100%"
          beforeMount={configureMonacoThemes}
          language={selectedLanguage}
          value={content || ''}
          theme={monacoTheme}
          options={editorOptions}
          onMount={handleEditorMount(side)}
        />
      </div>
    );
  };

  const contextMenuCommentExists = useMemo(() => {
    if (!contextMenuState.open || !contextMenuState.path || !contextMenuState.side || !contextMenuState.line) {
      return false;
    }
    const runRef = contextMenuState.side ? runIdBySide[contextMenuState.side] : null;
    return lineComments.some((item) => item.path === contextMenuState.path
      && item.side === contextMenuState.side
      && (item.run_id === runRef || !item.run_id)
      && item.line === contextMenuState.line);
  }, [contextMenuState, lineComments, runIdBySide]);

  const commentGroups = useMemo(() => {
    const groups = new Map();
    lineCommentsSorted.forEach((item) => {
      const runRef = item.run_id || (item.side === 'A' ? runIdBySide.A : runIdBySide.B) || 'unknown';
      const key = `${runRef}::${item.path}::${item.side}`;
      if (!groups.has(key)) {
        groups.set(key, {
          key,
          side: item.side,
          path: item.path,
          runRef,
          comments: [],
        });
      }
      groups.get(key).comments.push(item);
    });
    return Array.from(groups.values()).sort((a, b) => a.key.localeCompare(b.key));
  }, [lineCommentsSorted, runIdBySide]);

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!run) {
    return (
      <div style={{ padding: 24 }}>
        <Alert type="error" message="Benchmark run not found" />
      </div>
    );
  }

  return (
    <div className="cards-page benchmark-run-page">
      <div className="page-header benchmark-run-header">
        <div>
          <Title level={4} style={{ margin: 0 }}>
            Benchmark Run
            <span style={{ marginLeft: 12, verticalAlign: 'middle', display: 'inline-flex' }}>
              <StatusTag value={String(run.status || '').toLowerCase()} />
            </span>
          </Title>
          <Text type="secondary">
            {artifactLabelA} vs <strong>{artifactLabelB}</strong>
          </Text>
        </div>
        <Space>
          <Button onClick={() => setVerdictOpen(true)} disabled={!canOpenVerdict}>
            Review Panel
          </Button>
          {canExportReport && (
            <>
              <Button onClick={() => setReportPreviewOpen(true)}>
                Preview Report
              </Button>
            </>
          )}
          <Button
            icon={<ReloadOutlined />}
            onClick={async () => {
              const data = await load();
              if (data && (data.status === 'WAITING_COMPARISON' || data.status === 'COMPLETED')) {
                loadFileComparison();
              }
            }}
            loading={loading}
          >
            Refresh
          </Button>
        </Space>
      </div>

      {isFailed && (
        <Alert
          type="error"
          message="One or both runs failed"
          description="Check the individual runs for details."
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      {isRunning && (
        <Alert
          className="benchmark-running-alert"
          type="info"
          message="Runs in progress"
          description="Both agents are working. The page auto-refreshes every 5 seconds."
          style={{ marginBottom: 16 }}
          showIcon
        />
      )}

      {(isWaiting || isCompleted) && (
        <>
          <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
            <Col xs={24} sm={12} md={6}>
              <Card size="small" className="benchmark-summary-card">
                <Text type="secondary">Files changed (A vs B)</Text>
                <div className="benchmark-summary-value">{summary.changedA} vs {summary.changedB}</div>
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card size="small" className="benchmark-summary-card">
                <Text type="secondary">Files different</Text>
                <div className="benchmark-summary-value">{summary.different}</div>
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card size="small" className="benchmark-summary-card">
                <Text type="secondary">Files same</Text>
                <div className="benchmark-summary-value">{summary.same}</div>
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card size="small" className="benchmark-summary-card">
                <Text type="secondary">Total compared</Text>
                <div className="benchmark-summary-value">{summary.total}</div>
              </Card>
            </Col>
          </Row>

          <Row gutter={[12, 12]} className="benchmark-main-row">
            <Col xs={24} lg={7}>
              <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 8, minHeight: 0 }}>
                <Card size="small">
                  <Space direction="vertical" size={2}>
                    <div className="benchmark-run-link-row">
                      <Text type="secondary" className="benchmark-run-link-label">
                        Run A{run?.artifact_id ? ` (${shortArtifactId(run.artifact_id)})` : ''}:
                      </Text>
                      {run.run_a_id ? (
                        <Button className="benchmark-run-id-link" type="link" size="small" style={{ padding: 0 }} onClick={() => openRunConsole(run.run_a_id)} title={String(run.run_a_id)}>
                          {run.run_a_id}
                        </Button>
                      ) : (
                        <Text type="secondary">—</Text>
                      )}
                    </div>
                    <div className="benchmark-run-link-row">
                      <Text type="secondary" className="benchmark-run-link-label">
                        Run B{hasArtifactB
                          ? ` (${shortArtifactId(run?.artifact_id_b || 'B')})`
                          : ` (${formatWithoutArtifactLabel(run?.artifact_type)})`}
                        :
                      </Text>
                      {run.run_b_id ? (
                        <Button className="benchmark-run-id-link" type="link" size="small" style={{ padding: 0 }} onClick={() => openRunConsole(run.run_b_id)} title={String(run.run_b_id)}>
                          {run.run_b_id}
                        </Button>
                      ) : (
                        <Text type="secondary">—</Text>
                      )}
                    </div>
                  </Space>
                </Card>

                <Card
                  size="small"
                  title="Files"
                  className="benchmark-files-card"
                  style={{ flex: '1 1 auto', minHeight: 0 }}
                  extra={(
                    <Segmented
                      size="small"
                      value={fileFilter}
                      onChange={setFileFilter}
                      options={FILE_FILTER_OPTIONS}
                    />
                  )}
                  styles={{ body: { height: '100%', minHeight: 0, overflow: 'auto' } }}
                >
                  {fileComparisonLoading ? (
                    <Spin />
                  ) : filteredFiles.length === 0 ? (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No files for selected filter" />
                  ) : (
                    <Tree
                      treeData={treeData}
                      selectedKeys={selectedFilePath ? [selectedFilePath] : []}
                      defaultExpandAll
                      titleRender={(node) => {
                        if (!node.isLeaf) {
                          return <span>{node.title}</span>;
                        }
                        const file = fileByPath.get(node.key);
                        const sizeA = file?.exists_a ? formatBytes(file?.size_bytes_a) : '—';
                        const sizeB = file?.exists_b ? formatBytes(file?.size_bytes_b) : '—';
                        return (
                          <span className="benchmark-file-tree-row">
                            <span className="benchmark-file-tree-name">{node.title}</span>
                            <span className="benchmark-file-tree-meta">
                              {file?.__different ? <Tag color="red">diff</Tag> : <Tag>same</Tag>}
                              <span>A: {sizeA}</span>
                              <span>B: {sizeB}</span>
                            </span>
                          </span>
                        );
                      }}
                      onSelect={(keys) => {
                        const key = keys?.[0];
                        if (typeof key === 'string' && fileByPath.has(key)) {
                          setSelectedFilePath(key);
                        }
                      }}
                    />
                  )}
                </Card>
              </div>
            </Col>

            <Col xs={24} lg={17}>
              <Card
                size="small"
                className="benchmark-viewer-card"
                title={selectedFile ? selectedFile.path : 'File Content'}
                style={{ height: '100%' }}
                styles={{ body: { padding: 0, height: '100%', minHeight: 0, overflow: 'hidden' } }}
              >
                <div className="benchmark-columns">
                  <div className="benchmark-column benchmark-column-left">
                    <div className="benchmark-column-head">
                      <Text strong>{hasArtifactB ? `Run A · ${run?.artifact_id || 'A'}` : 'Run A'}</Text>
                      <Tag>{selectedFile?.status_a || 'unchanged'}</Tag>
                    </div>
                    {renderPaneContent('A')}
                  </div>

                  <div className="benchmark-column">
                    <div className="benchmark-column-head">
                      <Text strong>{hasArtifactB ? `Run B · ${run?.artifact_id_b || 'B'}` : `Run B · ${formatWithoutArtifactLabel(run?.artifact_type)}`}</Text>
                      <Tag>{selectedFile?.status_b || 'unchanged'}</Tag>
                    </div>
                    {renderPaneContent('B')}
                  </div>
                </div>
              </Card>
            </Col>
          </Row>
        </>
      )}

      {contextMenuState.open && canEditComments && (
        <div
          className="benchmark-editor-context-menu"
          style={{
            position: 'fixed',
            left: contextMenuState.x,
            top: contextMenuState.y,
            zIndex: 1500,
          }}
          onContextMenu={(event) => event.preventDefault()}
        >
          <Button
            type="text"
            onClick={() => {
              closeContextMenu();
              openLineCommentDraft(contextMenuState.side, contextMenuState.line, contextMenuState.path);
            }}
          >
            {contextMenuCommentExists ? 'Edit line comment' : 'Add line comment'}
          </Button>
        </div>
      )}

      <Modal
        title="Line comment"
        open={!!lineCommentDraft}
        onCancel={() => setLineCommentDraft(null)}
        onOk={saveLineCommentDraft}
        okText="Save"
        okButtonProps={{ disabled: !canEditComments }}
      >
        <Space direction="vertical" size={10} style={{ width: '100%' }}>
          <Text>
            {lineCommentDraft?.path || '—'} • {lineCommentDraft?.side}:{lineCommentDraft?.line}
          </Text>
          <Input.TextArea
            rows={4}
            value={lineCommentDraft?.text || ''}
            onChange={(event) => setLineCommentDraft((prev) => (prev ? { ...prev, text: event.target.value } : prev))}
            disabled={!canEditComments}
            placeholder="Describe issue or note"
          />
          <Text type="secondary">Leave empty and save to remove comment.</Text>
        </Space>
      </Modal>

      <Drawer
        title="Review & Verdict"
        placement="right"
        width={460}
        open={verdictOpen}
        onClose={() => setVerdictOpen(false)}
      >
        {canOpenVerdict ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Text className="muted">Review comments</Text>
            {canEditComments && (
              <Text type="secondary">Right click in Monaco editor and choose "Add line comment".</Text>
            )}
            {commentGroups.length === 0 ? (
              <List
                size="small"
                bordered
                locale={{ emptyText: 'No line comments yet.' }}
                dataSource={[]}
              />
            ) : (
              <Collapse
                className="benchmark-comments-collapse"
                items={commentGroups.map((group) => ({
                  key: group.key,
                  label: `${group.side} • ${group.path}`,
                  extra: <Text type="secondary" className="mono">{group.runRef}</Text>,
                  children: (
                    <List
                      size="small"
                      dataSource={group.comments}
                      renderItem={(item) => (
                        <List.Item
                          actions={[
                            <Button key="jump" type="link" size="small" onClick={() => focusLineComment(item)}>
                              Jump
                            </Button>,
                            canEditComments ? (
                              <Button
                                key="edit"
                                type="link"
                                size="small"
                                onClick={() => openLineCommentDraft(item.side, item.line, item.path)}
                              >
                                Edit
                              </Button>
                            ) : null,
                            canEditComments ? (
                              <Button
                                key="delete"
                                type="link"
                                danger
                                size="small"
                                onClick={() => removeLineComment(item)}
                              >
                                Delete
                              </Button>
                            ) : null,
                          ].filter(Boolean)}
                        >
                          <List.Item.Meta
                            title={`${item.side}:${item.line}`}
                            description={item.text}
                          />
                        </List.Item>
                      )}
                    />
                  ),
                }))}
              />
            )}

            <Text className="muted">General comment</Text>
            <Input.TextArea
              rows={5}
              value={reviewComment}
              onChange={(event) => setReviewComment(event.target.value)}
              placeholder="Overall conclusion and important observations"
              disabled={!canEditComments}
            />

            {isWaiting && (
              <>
                <Text className="muted">Verdict</Text>
                <Text type="secondary">
                  {verdictPrompt}
                </Text>
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                  <Button
                    type="primary"
                    icon={<CheckCircleOutlined />}
                    onClick={() => submitVerdict('SKILL_USEFUL')}
                    loading={submitting}
                    style={{ background: '#52c41a', borderColor: '#52c41a' }}
                    block
                  >
                    {verdictApproveLabel}
                  </Button>
                  <Button
                    danger
                    icon={<CloseCircleOutlined />}
                    onClick={() => submitVerdict('SKILL_NOT_HELPFUL')}
                    loading={submitting}
                    block
                  >
                    {verdictRejectLabel}
                  </Button>
                  <Button
                    icon={<MinusCircleOutlined />}
                    onClick={() => submitVerdict('NEUTRAL')}
                    loading={submitting}
                    block
                  >
                    {verdictNeutralLabel}
                  </Button>
                </Space>
              </>
            )}

            {isCompleted && (
              <>
                <Text className="muted">Saved verdict</Text>
                {run.human_verdict ? (
                  <Tag
                    color={
                      run.human_verdict === 'SKILL_USEFUL' ? 'green'
                        : run.human_verdict === 'SKILL_NOT_HELPFUL' ? 'red'
                          : 'default'
                    }
                    style={{ fontSize: 14, padding: '4px 12px' }}
                  >
                    {formatVerdictLabel(run.human_verdict, run)}
                  </Tag>
                ) : (
                  <Text>No verdict submitted.</Text>
                )}
              </>
            )}
          </Space>
        ) : (
          <Text type="secondary">Verdict becomes available after run comparison is ready.</Text>
        )}
      </Drawer>

      <Modal
        title="Descision Report"
        open={reportPreviewOpen}
        width={900}
        onCancel={() => setReportPreviewOpen(false)}
        styles={{ body: { height: 560, display: 'flex', flexDirection: 'column' } }}
        footer={(
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button
              icon={<DownloadOutlined />}
              onClick={exportMarkdownReport}
            >
              Download
            </Button>
          </div>
        )}
      >
        <div className="benchmark-report-preview-head">
          <Title level={4} style={{ margin: 0 }}>
            {reportPreviewTitle}
          </Title>
        </div>
        <div className="benchmark-report-preview-scroll">
          <MarkdownPreview markdown={reportMarkdownPreviewBody} />
        </div>
      </Modal>
    </div>
  );
}
