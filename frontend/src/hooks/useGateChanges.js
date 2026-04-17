import { useCallback, useEffect, useState } from 'react';
import { apiRequest } from '../api/request.js';

function pathSegments(path) {
  return String(path || '').replaceAll('\\', '/').split('/').filter(Boolean);
}

function normalizePath(path) {
  return String(path || '').replaceAll('\\', '/');
}

function buildTree(changesList) {
  const root = new Map();
  changesList.forEach((change) => {
    const path = change.path;
    const parts = pathSegments(path);
    let level = root;
    parts.forEach((part, idx) => {
      const isLeaf = idx === parts.length - 1;
      const rawPath = String(path || '');
      const dirKey = `dir:${parts.slice(0, idx + 1).join('/')}`;
      const nodeKey = isLeaf ? rawPath : dirKey;
      if (!level.has(part)) {
        level.set(part, { key: nodeKey, title: part, children: new Map(), leaf: false, path: null, change: null });
      }
      const node = level.get(part);
      if (isLeaf) {
        node.leaf = true;
        node.key = rawPath;
        node.path = rawPath;
        node.change = change;
      }
      level = node.children;
    });
  });
  const toTreeData = (map) => Array.from(map.values())
    .sort((a, b) => Number(a.leaf) - Number(b.leaf) || a.title.localeCompare(b.title))
    .map((item) => ({
      key: item.key,
      title: item.title,
      path: item.path,
      change: item.change,
      isLeaf: item.leaf && item.children.size === 0,
      selectable: item.leaf && item.children.size === 0,
      children: toTreeData(item.children),
    }));
  return toTreeData(root);
}

export function matchesEditablePath(gitPath, editablePath) {
  if (!gitPath || !editablePath) return false;
  const g = normalizePath(gitPath).replace(/^\.\/+/, '');
  const e = normalizePath(editablePath).replace(/^\.\/+/, '');
  return g === e || g.endsWith(`/${e}`);
}

export function matchesAnyEditablePath(gitPath, editablePaths) {
  if (!gitPath || !Array.isArray(editablePaths) || editablePaths.length === 0) return false;
  return editablePaths.some((ep) => matchesEditablePath(gitPath, ep) || matchesEditablePath(ep, gitPath));
}

export { normalizePath, buildTree };

export function detectLanguage(path) {
  const normalized = String(path || '').toLowerCase();
  if (normalized.endsWith('.md') || normalized.endsWith('.markdown')) return 'markdown';
  if (normalized.endsWith('.yml') || normalized.endsWith('.yaml')) return 'yaml';
  if (normalized.endsWith('.json')) return 'json';
  if (normalized.endsWith('.js') || normalized.endsWith('.jsx')) return 'javascript';
  if (normalized.endsWith('.ts') || normalized.endsWith('.tsx')) return 'typescript';
  if (normalized.endsWith('.java')) return 'java';
  if (normalized.endsWith('.kt') || normalized.endsWith('.kts')) return 'kotlin';
  if (normalized.endsWith('.py')) return 'python';
  if (normalized.endsWith('.sh')) return 'shell';
  if (normalized.endsWith('.xml')) return 'xml';
  if (normalized.endsWith('.html') || normalized.endsWith('.htm')) return 'html';
  if (normalized.endsWith('.css') || normalized.endsWith('.scss')) return 'css';
  if (normalized.endsWith('.sql')) return 'sql';
  return 'plaintext';
}

export function useGateChanges(gateId) {
  const [changes, setChanges] = useState([]);
  const [summary, setSummary] = useState({ files_changed: 0, added_lines: 0, removed_lines: 0, status_label: '—' });
  const [selectedPath, setSelectedPath] = useState('');
  const [originalContent, setOriginalContent] = useState('');
  const [modifiedContent, setModifiedContent] = useState('');
  const [patchContent, setPatchContent] = useState('');
  const [loadingDiff, setLoadingDiff] = useState(false);
  const [diffMode, setDiffMode] = useState('side-by-side');
  const [viewedFiles, setViewedFiles] = useState(new Set());

  const loadChanges = useCallback(async () => {
    if (!gateId) return;
    const data = await apiRequest(`/gates/${gateId}/changes`);
    setChanges(data.git_changes || []);
    setSummary(data.git_summary || { files_changed: 0, added_lines: 0, removed_lines: 0, status_label: '—' });
    return data;
  }, [gateId]);

  const loadDiff = useCallback(async (path) => {
    if (!path || !gateId) {
      setOriginalContent(''); setModifiedContent(''); setPatchContent('');
      return;
    }
    setLoadingDiff(true);
    try {
      const data = await apiRequest(`/gates/${gateId}/diff?path=${encodeURIComponent(path)}`);
      setOriginalContent(data.original_content || '');
      setModifiedContent(data.modified_content || '');
      setPatchContent(data.patch || '');
    } catch (_) {
      setOriginalContent(''); setModifiedContent(''); setPatchContent('');
    } finally {
      setLoadingDiff(false);
    }
  }, [gateId]);

  useEffect(() => {
    if (selectedPath) loadDiff(selectedPath);
  }, [selectedPath, loadDiff]);

  const toggleViewed = useCallback((filePath) => {
    setViewedFiles((prev) => {
      const next = new Set(prev);
      if (next.has(filePath)) next.delete(filePath);
      else next.add(filePath);
      return next;
    });
  }, []);

  const treeData = buildTree(changes);
  const viewFiles = changes.filter((c) => c.path).map((c) => c.path);
  const viewedCount = viewFiles.filter((f) => viewedFiles.has(f)).length;

  return {
    changes, summary, treeData, viewFiles,
    selectedPath, setSelectedPath,
    originalContent, modifiedContent, patchContent, loadingDiff,
    diffMode, setDiffMode,
    viewedFiles, viewedCount, toggleViewed,
    loadChanges,
  };
}
