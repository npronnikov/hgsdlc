import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Col, Drawer, Empty, Row, Space, Spin, Tabs, Tag, Tree, Typography, message } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, MinusCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import MarkdownPreview from '../components/MarkdownPreview.jsx';
import { apiRequest } from '../api/request.js';
import { useThemeMode } from '../theme/ThemeContext.jsx';
import { getMonacoThemeName } from '../utils/monacoTheme.js';

const { Title, Text } = Typography;

const STATUS_LABEL = {
  RUNNING: 'Running',
  WAITING_COMPARISON: 'Ready for Comparison',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};

const STATUS_COLOR = {
  RUNNING: 'processing',
  WAITING_COMPARISON: 'warning',
  COMPLETED: 'success',
  FAILED: 'error',
};

const VERDICT_LABEL = {
  SKILL_USEFUL: 'Skill was useful',
  SKILL_NOT_HELPFUL: 'Skill did not help',
  NEUTRAL: 'Neutral / Skip',
};

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

export default function BenchmarkRun() {
  const navigate = useNavigate();
  const { runId } = useParams();
  const { isDark } = useThemeMode();
  const [run, setRun] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [fileComparison, setFileComparison] = useState([]);
  const [fileComparisonLoading, setFileComparisonLoading] = useState(false);
  const [selectedFilePath, setSelectedFilePath] = useState(null);
  const [verdictOpen, setVerdictOpen] = useState(false);
  const [markdownViewA, setMarkdownViewA] = useState('source');
  const [markdownViewB, setMarkdownViewB] = useState('source');
  const pollRef = useRef(null);

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

  // Poll while RUNNING
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
    if (!fileComparison.length) {
      setSelectedFilePath(null);
      return;
    }
    const exists = fileComparison.some((item) => item.path === selectedFilePath);
    if (!exists) {
      setSelectedFilePath(fileComparison[0].path);
    }
  }, [fileComparison, selectedFilePath]);

  useEffect(() => {
    setMarkdownViewA('source');
    setMarkdownViewB('source');
  }, [selectedFilePath]);

  const submitVerdict = async (verdict) => {
    setSubmitting(true);
    try {
      const data = await apiRequest(`/benchmark/runs/${runId}/verdict`, {
        method: 'POST',
        body: JSON.stringify({ verdict }),
      });
      setRun(data);
      message.success('Verdict saved');
    } catch (err) {
      message.error(err.message || 'Failed to submit verdict');
    } finally {
      setSubmitting(false);
    }
  };

  const treeData = useMemo(() => toTreeData(fileComparison), [fileComparison]);
  const fileByPath = useMemo(() => {
    const map = new Map();
    fileComparison.forEach((item) => {
      map.set(item.path, item);
    });
    return map;
  }, [fileComparison]);
  const selectedFile = useMemo(
    () => fileComparison.find((item) => item.path === selectedFilePath) || null,
    [fileComparison, selectedFilePath],
  );

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

  const isWaiting = run.status === 'WAITING_COMPARISON';
  const isCompleted = run.status === 'COMPLETED';
  const isFailed = run.status === 'FAILED';
  const isRunning = run.status === 'RUNNING';
  const canOpenVerdict = isWaiting || isCompleted;

  const monacoTheme = getMonacoThemeName(isDark ? 'dark' : 'light');
  const selectedLanguage = detectLanguage(selectedFile?.path || '');
  const isMarkdownFile = selectedLanguage === 'markdown';
  const openRunConsole = (targetRunId) => {
    if (!targetRunId) return;
    navigate(`/run-console?runId=${targetRunId}`);
  };

  const renderPaneContent = (side) => {
    if (!selectedFile) {
      return (
        <div style={{ padding: 16, color: 'var(--color-text-secondary, #888)' }}>
          Select a file in the tree to compare content.
        </div>
      );
    }
    const isA = side === 'a';
    const exists = isA ? selectedFile.exists_a : selectedFile.exists_b;
    const binary = isA ? selectedFile.binary_a : selectedFile.binary_b;
    const content = isA ? selectedFile.content_a : selectedFile.content_b;
    const viewMode = isA ? markdownViewA : markdownViewB;

    if (binary) {
      return <div style={{ padding: 16, color: 'var(--color-text-secondary, #888)' }}>Binary file</div>;
    }
    if (!exists) {
      return (
        <div style={{ padding: 16, color: 'var(--color-text-secondary, #888)' }}>
          {isA ? 'File does not exist in Run A' : 'File does not exist in Run B'}
        </div>
      );
    }
    if (isMarkdownFile && viewMode === 'preview') {
      return (
        <div className="human-gate-markdown-tab-body human-gate-markdown-preview">
          <div className="human-gate-markdown-preview-content">
            <MarkdownPreview markdown={content || '*No content*'} />
          </div>
        </div>
      );
    }
    if (isMarkdownFile && viewMode === 'source') {
      return (
        <div className="human-gate-markdown-tab-body">
          <Editor
            height="100%"
            language={selectedLanguage}
            value={content || ''}
            theme={monacoTheme}
            options={{
              readOnly: true,
              minimap: { enabled: false },
              scrollBeyondLastLine: false,
              wordWrap: 'off',
              scrollbar: {
                vertical: 'auto',
                horizontal: 'auto',
                verticalScrollbarSize: 10,
                horizontalScrollbarSize: 10,
                alwaysConsumeMouseWheel: false,
              },
              fontSize: 13,
              automaticLayout: true,
            }}
          />
        </div>
      );
    }
    return (
      <Editor
        height="100%"
        language={selectedLanguage}
        value={content || ''}
        theme={monacoTheme}
        options={{
          readOnly: true,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          wordWrap: 'off',
          scrollbar: {
            vertical: 'auto',
            horizontal: 'auto',
            verticalScrollbarSize: 10,
            horizontalScrollbarSize: 10,
            alwaysConsumeMouseWheel: false,
          },
          fontSize: 13,
          automaticLayout: true,
        }}
      />
    );
  };

  return (
    <div className="cards-page gates-inbox-page">
      <div className="page-header gates-inbox-header" style={{ marginBottom: 20 }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>
            Benchmark Run
            <Tag
              color={STATUS_COLOR[run.status] || 'default'}
              style={{ marginLeft: 12, verticalAlign: 'middle' }}
            >
              {STATUS_LABEL[run.status] || run.status}
            </Tag>
          </Title>
          <Text type="secondary">
            {run.artifact_type}: <strong>{run.artifact_id}</strong>
          </Text>
        </div>
        <Space>
          <Button
            onClick={() => setVerdictOpen(true)}
            disabled={!canOpenVerdict}
          >
            Your verdict
          </Button>
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
        />
      )}

      {isRunning && (
        <Alert
          type="info"
          message="Runs in progress"
          description="Both agents are working. The page will auto-refresh every 5 seconds."
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Diff comparison */}
      {(isWaiting || isCompleted) && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, flex: 1, minHeight: 0, overflow: 'hidden' }}>
          <Row gutter={[16, 16]} style={{ flex: 1, minHeight: 0 }}>
            <Col xs={24} lg={6} style={{ display: 'flex', minHeight: 0 }}>
              <Card
                size="small"
                title="Changed files"
                style={{ width: '100%', display: 'flex', flexDirection: 'column' }}
                styles={{ body: { flex: 1, minHeight: 0, overflow: 'auto' } }}
              >
                {fileComparisonLoading ? (
                  <Spin />
                ) : fileComparison.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No files to compare" />
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
                        <span style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, width: '100%' }}>
                          <span>{node.title}</span>
                          <span style={{ color: 'var(--color-text-secondary, #888)', fontSize: 11, whiteSpace: 'nowrap' }}>
                            A: {sizeA} | B: {sizeB}
                          </span>
                        </span>
                      );
                    }}
                    onSelect={(keys) => {
                      const key = keys?.[0];
                      if (typeof key === 'string') {
                        const found = fileComparison.find((item) => item.path === key);
                        if (found) setSelectedFilePath(key);
                      }
                    }}
                  />
                )}
              </Card>
            </Col>
            <Col xs={24} lg={18} style={{ display: 'flex', minHeight: 0 }}>
              <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 8, minHeight: 0 }}>
                <div
                  style={{
                    display: 'flex',
                    gap: 8,
                  }}
                >
                  <Card
                    size="small"
                    style={{ width: '50%', background: '#fff' }}
                    styles={{ body: { padding: '8px 12px' } }}
                  >
                    <Text type="secondary">Run A (with {run.artifact_type?.toLowerCase()})</Text>
                    {run.run_a_id && (
                      <div>
                        <Button
                          type="link"
                          size="small"
                          style={{ padding: 0, height: 'auto' }}
                          onClick={() => openRunConsole(run.run_a_id)}
                        >
                          {run.run_a_id}
                        </Button>
                      </div>
                    )}
                  </Card>
                  <Card
                    size="small"
                    style={{ width: '50%', background: '#fff' }}
                    styles={{ body: { padding: '8px 12px' } }}
                  >
                    <Text type="secondary">Run B (control, no {run.artifact_type?.toLowerCase()})</Text>
                    {run.run_b_id && (
                      <div>
                        <Button
                          type="link"
                          size="small"
                          style={{ padding: 0, height: 'auto' }}
                          onClick={() => openRunConsole(run.run_b_id)}
                        >
                          {run.run_b_id}
                        </Button>
                      </div>
                    )}
                  </Card>
                </div>

                <Card
                  size="small"
                  title={selectedFile ? selectedFile.path : 'Files & Content'}
                  style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}
                  styles={{ body: { padding: 0, flex: 1, minHeight: 0, display: 'flex', overflow: 'hidden' } }}
                >
                  <Row gutter={0} style={{ flex: 1, minHeight: 0 }}>
                    <Col span={12} style={{ borderRight: '1px solid var(--color-border, #f0f0f0)', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                      <div style={{ padding: '8px 12px', borderBottom: '1px solid var(--color-border, #f0f0f0)' }}>
                        <Text strong>Run A</Text>
                        <Tag style={{ marginLeft: 8 }}>{selectedFile?.status_a || 'unchanged'}</Tag>
                      </div>
                      {isMarkdownFile && selectedFile?.exists_a && !selectedFile?.binary_a && (
                        <Tabs
                          size="small"
                          activeKey={markdownViewA}
                          onChange={setMarkdownViewA}
                          items={[
                            { key: 'source', label: 'Source' },
                            { key: 'preview', label: 'Preview' },
                          ]}
                          style={{ padding: '0 12px', borderBottom: '1px solid var(--color-border, #f0f0f0)' }}
                        />
                      )}
                      <div className="human-gate-fill-pane">
                        {renderPaneContent('a')}
                      </div>
                    </Col>
                    <Col span={12} style={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                      <div style={{ padding: '8px 12px', borderBottom: '1px solid var(--color-border, #f0f0f0)' }}>
                        <Text strong>Run B</Text>
                        <Tag style={{ marginLeft: 8 }}>{selectedFile?.status_b || 'unchanged'}</Tag>
                      </div>
                      {isMarkdownFile && selectedFile?.exists_b && !selectedFile?.binary_b && (
                        <Tabs
                          size="small"
                          activeKey={markdownViewB}
                          onChange={setMarkdownViewB}
                          items={[
                            { key: 'source', label: 'Source' },
                            { key: 'preview', label: 'Preview' },
                          ]}
                          style={{ padding: '0 12px', borderBottom: '1px solid var(--color-border, #f0f0f0)' }}
                        />
                      )}
                      <div className="human-gate-fill-pane">
                        {renderPaneContent('b')}
                      </div>
                    </Col>
                  </Row>
                </Card>
              </div>
            </Col>
          </Row>
        </div>
      )}
      <Drawer
        title="Your verdict"
        placement="right"
        width={460}
        open={verdictOpen}
        onClose={() => setVerdictOpen(false)}
      >
        {isWaiting && (
          <>
            <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
              Did the {run.artifact_type?.toLowerCase()} improve the result?
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
                {run.artifact_type === 'SKILL' ? 'Skill was useful' : 'Rule was useful'}
              </Button>
              <Button
                danger
                icon={<CloseCircleOutlined />}
                onClick={() => submitVerdict('SKILL_NOT_HELPFUL')}
                loading={submitting}
                block
              >
                {run.artifact_type === 'SKILL' ? 'Skill did not help' : 'Rule did not help'}
              </Button>
              <Button
                icon={<MinusCircleOutlined />}
                onClick={() => submitVerdict('NEUTRAL')}
                loading={submitting}
                block
              >
                Neutral / Skip
              </Button>
            </Space>
          </>
        )}
        {isCompleted && (
          <>
            <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
              Saved verdict:
            </Text>
            {run.human_verdict ? (
              <Tag
                color={
                  run.human_verdict === 'SKILL_USEFUL' ? 'green'
                    : run.human_verdict === 'SKILL_NOT_HELPFUL' ? 'red'
                      : 'default'
                }
                style={{ fontSize: 14, padding: '4px 12px' }}
              >
                {VERDICT_LABEL[run.human_verdict] || run.human_verdict}
              </Tag>
            ) : (
              <Text>No verdict submitted.</Text>
            )}
          </>
        )}
        {!canOpenVerdict && (
          <Text type="secondary">Verdict becomes available after run comparison is ready.</Text>
        )}
      </Drawer>
    </div>
  );
}
