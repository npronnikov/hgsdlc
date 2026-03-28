import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Dropdown,
  Input,
  Modal,
  Radio,
  Row,
  Space,
  Tag,
  Tabs,
  Tree,
  Typography,
  message,
} from 'antd';
import { CheckCircleOutlined, FormOutlined } from '@ant-design/icons';
import { DiffEditor } from '@monaco-editor/react';
import { useThemeMode } from '../theme/ThemeContext.jsx';
import { configureMonacoThemes, getMonacoThemeName } from '../utils/monacoTheme.js';

const { Title, Text } = Typography;

const MOCK_FILES = [
  {
    id: '1',
    path: 'app/nodes/input_gate_node.rb',
    status: 'modified',
    additions: 18,
    deletions: 4,
    original: `class InputGateNode
  def execute(context)
    context[:status] = "pending"
  end
end
`,
    modified: `class InputGateNode
  def execute(context)
    context[:status] = "waiting_human_input"
    context[:required_answers] = ["acceptance_criteria", "risk_notes", "test_plan"]
  end
end
`,
  },
  {
    id: '2',
    path: 'app/services/input_payload_builder.rb',
    status: 'added',
    additions: 31,
    deletions: 0,
    original: '',
    modified: `class InputPayloadBuilder
  def self.call(gate:, answers:, reviewed_files:)
    {
      gate_id: gate.id,
      answers: answers,
      reviewed_files: reviewed_files,
      handoff_to_node: gate.on_submit
    }
  end
end
`,
  },
  {
    id: '3',
    path: 'frontend/src/pages/HumanInputGate.jsx',
    status: 'modified',
    additions: 25,
    deletions: 7,
    original: `export default function HumanInputGate() {
  return <div>Input gate</div>;
}
`,
    modified: `export default function HumanInputGate() {
  return (
    <section>
      <h2>Human Input Gate</h2>
      <p>Review context and answer required questions.</p>
    </section>
  );
}
`,
  },
  {
    id: '4',
    path: 'docs/process/human-input-gate.md',
    status: 'added',
    additions: 16,
    deletions: 0,
    original: '',
    modified: `# Human input gate

1. Review changed files and context
2. Answer required questions
3. Submit payload to next node
`,
  },
];

const QUESTIONS = [
  {
    id: 'acceptance_criteria',
    title: 'Acceptance criteria',
    placeholder: 'What should be considered done?',
  },
  {
    id: 'risk_notes',
    title: 'Risk notes',
    placeholder: 'Any risks, constraints, or unknowns?',
  },
  {
    id: 'test_plan',
    title: 'Test plan',
    placeholder: 'What should be tested before merge?',
  },
];

function detectLanguage(path) {
  const extension = path.split('.').pop()?.toLowerCase();
  const map = {
    rb: 'ruby',
    js: 'javascript',
    jsx: 'javascript',
    ts: 'typescript',
    tsx: 'typescript',
    md: 'markdown',
    json: 'json',
    yml: 'yaml',
    yaml: 'yaml',
  };
  return map[extension] || 'plaintext';
}

function statusTag(status) {
  if (status === 'added') return <Tag color="green">added</Tag>;
  if (status === 'deleted') return <Tag color="red">deleted</Tag>;
  return <Tag color="blue">modified</Tag>;
}

function buildFileTreeData(files) {
  const root = { key: 'root', title: 'changed-files', children: [] };
  const index = new Map([['', root]]);

  files.forEach((file) => {
    const parts = file.path.split('/');
    let currentPath = '';
    parts.forEach((part, depth) => {
      const isLeaf = depth === parts.length - 1;
      const parentPath = currentPath;
      currentPath = currentPath ? `${currentPath}/${part}` : part;
      if (index.has(currentPath)) return;
      const parentNode = index.get(parentPath) || root;
      const node = {
        key: isLeaf ? file.id : `dir:${currentPath}`,
        title: isLeaf
          ? (
            <div className="human-approval-tree-leaf">
              <span className="mono">{part}</span>
              <span>
                <Text style={{ color: 'var(--success)', fontSize: 12, marginRight: 6 }}>+{file.additions}</Text>
                <Text style={{ color: 'var(--danger)', fontSize: 12 }}>-{file.deletions}</Text>
              </span>
            </div>
          )
          : part,
        children: [],
      };
      parentNode.children.push(node);
      index.set(currentPath, node);
    });
  });

  return root.children;
}

export default function HumanInputGateMock() {
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);
  const [query, setQuery] = useState('');
  const [selectedFileId, setSelectedFileId] = useState(MOCK_FILES[0].id);
  const [answers, setAnswers] = useState({});
  const [reviewComment, setReviewComment] = useState('');
  const [reworkMode, setReworkMode] = useState('discard');
  const [reworkModalOpen, setReworkModalOpen] = useState(false);
  const [reworkInstruction, setReworkInstruction] = useState('');
  const [lineComments, setLineComments] = useState([]);
  const [activeCommentTarget, setActiveCommentTarget] = useState(null);
  const modifiedEditorRef = useRef(null);
  const monacoRef = useRef(null);
  const commentDecorationIdsRef = useRef([]);
  const commentWidgetRef = useRef(null);
  const commentWidgetDomRef = useRef(null);
  const commentWidgetStateRef = useRef({ visible: false, lineNumber: 1 });
  const pendingCommentRangeRef = useRef(null);
  const selectedFileIdRef = useRef(MOCK_FILES[0].id);
  const lastSelectionRef = useRef(null);
  const selectedFile = MOCK_FILES.find((item) => item.id === selectedFileId) || MOCK_FILES[0];

  const filteredFiles = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return MOCK_FILES;
    return MOCK_FILES.filter((item) => item.path.toLowerCase().includes(normalized));
  }, [query]);
  const treeData = useMemo(() => buildFileTreeData(filteredFiles), [filteredFiles]);

  const totals = useMemo(() => {
    return MOCK_FILES.reduce(
      (acc, item) => ({
        additions: acc.additions + item.additions,
        deletions: acc.deletions + item.deletions,
      }),
      { additions: 0, deletions: 0 },
    );
  }, []);

  const answeredCount = QUESTIONS.filter((question) => (answers[question.id] || '').trim()).length;
  const reviewedFiles = filteredFiles.map((item) => item.path);
  const payloadPreview = {
    gate_kind: 'human_input',
    reviewed_files: reviewedFiles,
    answers,
    review_comment: reviewComment || null,
    next_node: 'implement_changes',
  };

  const reviewActionItems = [
    {
      key: 'submit',
      label: 'Submit answers to next node',
    },
    {
      key: 'rework',
      label: 'Request rework before submit',
    },
  ];

  const selectedFileComments = useMemo(
    () => lineComments
      .filter((comment) => comment.fileId === selectedFile.id)
      .sort((a, b) => a.createdAt - b.createdAt),
    [lineComments, selectedFile.id],
  );
  const originalModelPath = `inmemory://human-input-gate/original/${selectedFile.id}/${selectedFile.path}`;
  const modifiedModelPath = `inmemory://human-input-gate/modified/${selectedFile.id}/${selectedFile.path}`;

  useEffect(() => {
    selectedFileIdRef.current = selectedFile.id;
    pendingCommentRangeRef.current = null;
    setActiveCommentTarget(null);
    const editor = modifiedEditorRef.current;
    const widget = commentWidgetRef.current;
    if (editor && widget && commentWidgetStateRef.current.visible) {
      commentWidgetStateRef.current.visible = false;
      editor.layoutContentWidget(widget);
    }
  }, [selectedFile.id]);

  const addLineComment = (textValue) => {
    const text = (textValue || '').trim();
    if (!text) {
      message.warning('Comment text is required');
      return false;
    }
    const range = pendingCommentRangeRef.current;
    if (!range) {
      message.warning('Select lines before adding a comment');
      return false;
    }
    setLineComments((prev) => [
      ...prev,
      {
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        fileId: selectedFileIdRef.current,
        text,
        createdAt: Date.now(),
        range: {
          startLineNumber: range.startLineNumber,
          endLineNumber: range.endLineNumber,
        },
      },
    ]);
    message.success('Comment added to selection');
    return true;
  };

  const ensureCommentWidget = () => {
    const editor = modifiedEditorRef.current;
    const monaco = monacoRef.current;
    if (!editor || !monaco) {
      return null;
    }
    if (commentWidgetRef.current) {
      return commentWidgetRef.current;
    }
    const container = document.createElement('div');
    container.className = 'human-input-inline-comment-widget';
    const textarea = document.createElement('textarea');
    textarea.className = 'human-input-inline-comment-textarea';
    textarea.placeholder = 'Write a comment for selected lines';
    const footer = document.createElement('div');
    footer.className = 'human-input-inline-comment-footer';
    const meta = document.createElement('span');
    meta.className = 'human-input-inline-comment-meta';
    meta.textContent = '';
    const actions = document.createElement('div');
    actions.className = 'human-input-inline-comment-actions';
    const cancelButton = document.createElement('button');
    cancelButton.type = 'button';
    cancelButton.className = 'human-input-inline-comment-btn secondary';
    cancelButton.textContent = 'Cancel';
    const addButton = document.createElement('button');
    addButton.type = 'button';
    addButton.className = 'human-input-inline-comment-btn primary';
    addButton.textContent = 'Add comment';
    actions.append(cancelButton, addButton);
    footer.append(meta, actions);
    container.append(textarea, footer);
    commentWidgetDomRef.current = { container, textarea, meta };

    cancelButton.addEventListener('click', () => {
      const currentEditor = modifiedEditorRef.current;
      const widget = commentWidgetRef.current;
      pendingCommentRangeRef.current = null;
      setActiveCommentTarget(null);
      if (currentEditor && widget) {
        commentWidgetStateRef.current.visible = false;
        currentEditor.layoutContentWidget(widget);
      }
    });
    addButton.addEventListener('click', () => {
      const ok = addLineComment(textarea.value);
      if (!ok) {
        return;
      }
      const currentEditor = modifiedEditorRef.current;
      const widget = commentWidgetRef.current;
      textarea.value = '';
      pendingCommentRangeRef.current = null;
      setActiveCommentTarget(null);
      if (currentEditor && widget) {
        commentWidgetStateRef.current.visible = false;
        currentEditor.layoutContentWidget(widget);
      }
    });

    const widget = {
      allowEditorOverflow: true,
      getId: () => 'human-input-inline-comment-widget',
      getDomNode: () => container,
      getPosition: () => {
        if (!commentWidgetStateRef.current.visible) {
          return null;
        }
        return {
          position: {
            lineNumber: commentWidgetStateRef.current.lineNumber,
            column: 1,
          },
          preference: [monaco.editor.ContentWidgetPositionPreference.BELOW],
        };
      },
    };

    commentWidgetRef.current = widget;
    editor.addContentWidget(widget);
    return widget;
  };

  useEffect(() => {
    const editor = modifiedEditorRef.current;
    const model = editor?.getModel?.();
    if (!editor?.deltaDecorations || !model || model.isDisposed?.()) {
      return;
    }
    const nextDecorations = selectedFileComments.map((comment) => ({
      range: {
        startLineNumber: comment.range.startLineNumber,
        startColumn: 1,
        endLineNumber: comment.range.endLineNumber,
        endColumn: 1,
      },
      options: {
        isWholeLine: true,
        className: 'human-input-comment-line-highlight',
        linesDecorationsClassName: 'human-input-comment-line-stripe',
        glyphMarginClassName: 'human-input-comment-glyph',
        glyphMarginHoverMessage: [{ value: comment.text }],
      },
    }));
    try {
      commentDecorationIdsRef.current = editor.deltaDecorations(commentDecorationIdsRef.current, nextDecorations);
    } catch {
      commentDecorationIdsRef.current = [];
    }
  }, [selectedFileComments, selectedFile.id]);

  useEffect(() => {
    return () => {
      const editor = modifiedEditorRef.current;
      const model = editor?.getModel?.();
      if (editor?.deltaDecorations && model && !model.isDisposed?.()) {
        try {
          editor.deltaDecorations(commentDecorationIdsRef.current, []);
        } catch {
          // Monaco can dispose model before React cleanup in rapid tab/file switches.
        }
      }
    };
  }, []);

  const openCommentComposerFromSelection = () => {
    const editor = modifiedEditorRef.current;
    const model = editor?.getModel();
    if (!editor || !model || model.isDisposed?.()) {
      message.warning('Editor is not ready yet');
      return;
    }
    const liveSelection = editor.getSelection();
    const selection = liveSelection && !liveSelection.isEmpty() ? liveSelection : lastSelectionRef.current;
    const fallbackPosition = editor.getPosition();
    if (!selection && !fallbackPosition) {
      message.warning('Put cursor on a line in the modified pane');
      return;
    }
    const resolvedSelection = selection || {
      startLineNumber: fallbackPosition.lineNumber,
      endLineNumber: fallbackPosition.lineNumber,
      startColumn: 1,
      endColumn: 1,
    };
    const selectedText = (model.getValueInRange(resolvedSelection) || '').trim();
    pendingCommentRangeRef.current = {
      startLineNumber: resolvedSelection.startLineNumber,
      endLineNumber: resolvedSelection.endLineNumber,
    };
    setActiveCommentTarget({
      startLineNumber: resolvedSelection.startLineNumber,
      endLineNumber: resolvedSelection.endLineNumber,
      excerpt: selectedText,
    });
    const widget = ensureCommentWidget();
    if (!widget || !commentWidgetDomRef.current) {
      message.warning('Editor comment widget is not ready');
      return;
    }
    commentWidgetStateRef.current.lineNumber = resolvedSelection.endLineNumber;
    commentWidgetStateRef.current.visible = true;
    commentWidgetDomRef.current.textarea.value = '';
    commentWidgetDomRef.current.meta.textContent = `Lines ${resolvedSelection.startLineNumber}-${resolvedSelection.endLineNumber}`;
    editor.layoutContentWidget(widget);
    commentWidgetDomRef.current.textarea.focus();
  };

  const removeLineComment = (commentId) => {
    setLineComments((prev) => prev.filter((comment) => comment.id !== commentId));
  };

  const jumpToComment = (comment) => {
    const editor = modifiedEditorRef.current;
    if (!editor) {
      return;
    }
    editor.revealLineInCenter(comment.range.startLineNumber);
    editor.setSelection({
      startLineNumber: comment.range.startLineNumber,
      startColumn: 1,
      endLineNumber: comment.range.endLineNumber,
      endColumn: 1,
    });
    editor.focus();
  };

  return (
    <div className="human-approval-gate-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Human Input Gate</Title>
        <Space size={8}>
          <Tag color="processing">gate: human_input</Tag>
          <Tag color="gold">Mock Data</Tag>
        </Space>
      </div>
      <Card className="human-approval-summary">
        <div className="human-approval-summary-row">
          <Space size={20} wrap>
            <Text><strong>{MOCK_FILES.length}</strong> files changed</Text>
            <Text style={{ color: 'var(--success)' }}>+{totals.additions}</Text>
            <Text style={{ color: 'var(--danger)' }}>-{totals.deletions}</Text>
            <Text><CheckCircleOutlined style={{ color: 'var(--success)', marginRight: 6 }} />Review + answer required</Text>
            <Text><FormOutlined style={{ marginRight: 6 }} />Answered {answeredCount}/{QUESTIONS.length}</Text>
          </Space>
          <Dropdown
            menu={{
              items: reviewActionItems,
              onClick: ({ key }) => {
                if (key === 'submit') {
                  if (answeredCount < QUESTIONS.length) {
                    message.warning('Please answer all required questions');
                    return;
                  }
                  message.success('Mock input payload sent to next node');
                  return;
                }
                setReworkModalOpen(true);
              },
            }}
            trigger={['click']}
          >
            <Button type="primary">Input actions</Button>
          </Dropdown>
        </div>
      </Card>
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={7}>
          <Card title="Reviewed files" bodyStyle={{ padding: 12 }}>
            <Input
              placeholder="Filter changed files"
              allowClear
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              style={{ marginBottom: 12 }}
            />
            <div className="human-approval-file-list">
              {filteredFiles.length > 0 ? (
                <Tree
                  showLine
                  blockNode
                  defaultExpandAll
                  selectedKeys={[selectedFileId]}
                  treeData={treeData}
                  onSelect={(keys) => {
                    const [key] = keys;
                    const nextFile = filteredFiles.find((file) => file.id === key);
                    if (nextFile) setSelectedFileId(nextFile.id);
                  }}
                />
              ) : (
                <div className="card-muted">
                  <Text type="secondary">No files match filter</Text>
                </div>
              )}
            </div>
          </Card>
        </Col>
        <Col xs={24} xl={17}>
          <Card bodyStyle={{ padding: 0 }}>
            <Tabs
              items={[
                {
                  key: 'diff',
                  label: 'Review Diff',
                  children: (
                    <div>
                      <div className="human-input-file-header">
                        <Space size={8} wrap>
                          <Text className="mono">{selectedFile.path}</Text>
                          {statusTag(selectedFile.status)}
                          <Tag color="cyan">{selectedFileComments.length} line comments</Tag>
                        </Space>
                        <Space size={8}>
                          <Button size="small" onClick={openCommentComposerFromSelection}>
                            Comment selection
                          </Button>
                          {activeCommentTarget && (
                            <Tag color="geekblue">
                              target: {activeCommentTarget.startLineNumber}-{activeCommentTarget.endLineNumber}
                            </Tag>
                          )}
                          {selectedFileComments.length > 0 && (
                            <Button
                              size="small"
                              danger
                              ghost
                              onClick={() => {
                                setLineComments((prev) => prev.filter((comment) => comment.fileId !== selectedFile.id));
                                message.info('Comments for this file were cleared');
                              }}
                            >
                              Clear file comments
                            </Button>
                          )}
                        </Space>
                      </div>
                      <DiffEditor
                        height="58vh"
                        language={detectLanguage(selectedFile.path)}
                        original={selectedFile.original}
                        modified={selectedFile.modified}
                        originalModelPath={originalModelPath}
                        modifiedModelPath={modifiedModelPath}
                        keepCurrentOriginalModel
                        keepCurrentModifiedModel
                        beforeMount={(monaco) => {
                          monacoRef.current = monaco;
                          configureMonacoThemes(monaco);
                        }}
                        theme={monacoTheme}
                        options={{
                          readOnly: true,
                          minimap: { enabled: false },
                          scrollBeyondLastLine: false,
                          renderSideBySide: true,
                          fontSize: 13,
                          originalEditable: false,
                          wordWrap: 'on',
                          automaticLayout: true,
                          glyphMargin: true,
                        }}
                        onMount={(editorInstance) => {
                          const modifiedEditor = editorInstance.getModifiedEditor();
                          modifiedEditorRef.current = modifiedEditor;
                          modifiedEditor.onDidChangeCursorSelection((event) => {
                            const nextSelection = event.selection;
                            lastSelectionRef.current = nextSelection && !nextSelection.isEmpty()
                              ? nextSelection
                              : null;
                          });
                        }}
                        onUnmount={() => {
                          const editor = modifiedEditorRef.current;
                          const widget = commentWidgetRef.current;
                          if (editor && widget) {
                            try {
                              editor.removeContentWidget(widget);
                            } catch {
                              // noop
                            }
                          }
                          modifiedEditorRef.current = null;
                          commentWidgetRef.current = null;
                          commentWidgetDomRef.current = null;
                          commentDecorationIdsRef.current = [];
                          lastSelectionRef.current = null;
                          pendingCommentRangeRef.current = null;
                          setActiveCommentTarget(null);
                        }}
                      />
                      <div className="human-input-comment-list">
                        {selectedFileComments.length === 0 ? (
                          <Text type="secondary">No comments yet. Select lines in modified code and click "Comment selection".</Text>
                        ) : (
                          selectedFileComments.map((comment) => (
                            <div key={comment.id} className="human-input-comment-item">
                              <div className="human-input-comment-meta">
                                <Text strong>
                                  Lines {comment.range.startLineNumber}-{comment.range.endLineNumber}
                                </Text>
                                <Space size={6}>
                                  <Button size="small" type="link" onClick={() => jumpToComment(comment)}>
                                    Jump
                                  </Button>
                                  <Button size="small" type="link" danger onClick={() => removeLineComment(comment.id)}>
                                    Delete
                                  </Button>
                                </Space>
                              </div>
                              <Text>{comment.text}</Text>
                            </div>
                          ))
                        )}
                      </div>
                    </div>
                  ),
                },
                {
                  key: 'input',
                  label: 'Human Input',
                  children: (
                    <div className="human-input-tab-content">
                      <Text className="muted">This gate combines approval-style review with required human answers for downstream execution.</Text>
                      {QUESTIONS.map((question) => (
                        <div key={question.id} className="human-input-question">
                          <Text strong>{question.title}</Text>
                          <Input.TextArea
                            rows={3}
                            placeholder={question.placeholder}
                            value={answers[question.id] || ''}
                            onChange={(event) => {
                              setAnswers((prev) => ({ ...prev, [question.id]: event.target.value }));
                            }}
                            style={{ marginTop: 8 }}
                          />
                        </div>
                      ))}
                      <div className="human-input-question">
                        <Text strong>Review comment</Text>
                        <Input.TextArea
                          rows={2}
                          placeholder="Optional comment about reviewed changes"
                          value={reviewComment}
                          onChange={(event) => setReviewComment(event.target.value)}
                          style={{ marginTop: 8 }}
                        />
                      </div>
                      <div className="human-input-payload">
                        <Text className="muted">Payload to next node</Text>
                        <pre className="code-block">{JSON.stringify(payloadPreview, null, 2)}</pre>
                      </div>
                    </div>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
      <Modal
        title="Request rework"
        open={reworkModalOpen}
        onCancel={() => setReworkModalOpen(false)}
        onOk={() => {
          message.info(`Mock rework requested (${reworkMode})`);
          setReworkModalOpen(false);
        }}
        okText="Request rework"
      >
        <Text className="muted">Instruction for rework</Text>
        <Input.TextArea rows={5} style={{ marginTop: 8 }} value={reworkInstruction} onChange={(event) => setReworkInstruction(event.target.value)} />
        <Text className="muted" style={{ marginTop: 12, display: 'block' }}>Changes handling</Text>
        <Radio.Group
          value={reworkMode}
          onChange={(event) => setReworkMode(event.target.value)}
          optionType="button"
          buttonStyle="solid"
          style={{ display: 'block', marginTop: 8 }}
        >
          <Radio.Button value="keep">Keep changes</Radio.Button>
          <Radio.Button value="discard">Discard changes</Radio.Button>
        </Radio.Group>
      </Modal>
    </div>
  );
}
