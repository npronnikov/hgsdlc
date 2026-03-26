import React, { useMemo, useState } from 'react';
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
  Tree,
  Typography,
  message,
} from 'antd';
import { CheckCircleOutlined } from '@ant-design/icons';
import { DiffEditor } from '@monaco-editor/react';
import { useThemeMode } from '../theme/ThemeContext.jsx';
import { configureMonacoThemes, getMonacoThemeName } from '../utils/monacoTheme.js';

const { Title, Text } = Typography;

const MOCK_FILES = [
  {
    id: '1',
    path: 'app/controllers/approval_gate_controller.rb',
    status: 'modified',
    additions: 18,
    deletions: 4,
    original: `class ApprovalGateController < ApplicationController
  def update
    gate = HumanApprovalGate.find(params[:id])
    gate.update!(status: params[:status])
    render json: { ok: true }
  end
end
`,
    modified: `class ApprovalGateController < ApplicationController
  def update
    gate = HumanApprovalGate.find(params[:id])
    gate.transaction do
      gate.lock!
      gate.update!(
        status: params[:status],
        reviewer_comment: params[:comment],
        reviewed_at: Time.current
      )
    end
    render json: { ok: true, gate_id: gate.id }
  end
end
`,
  },
  {
    id: '2',
    path: 'app/services/human_approval_gate.rb',
    status: 'added',
    additions: 52,
    deletions: 0,
    original: '',
    modified: `class HumanApprovalGate
  Result = Struct.new(:approved, :reason, keyword_init: true)

  def initialize(changes:)
    @changes = changes
  end

  def evaluate!
    return Result.new(approved: false, reason: "No files provided") if @changes.empty?
    risky_file = @changes.any? { |entry| entry[:path].start_with?("infra/") }
    return Result.new(approved: false, reason: "Infrastructure change requires manual check") if risky_file

    Result.new(approved: true, reason: "Safe changes")
  end
end
`,
  },
  {
    id: '3',
    path: 'frontend/src/pages/HumanApprovalGate.jsx',
    status: 'modified',
    additions: 33,
    deletions: 11,
    original: `export default function HumanApprovalGate() {
  return <div>Gate</div>;
}
`,
    modified: `export default function HumanApprovalGate() {
  return (
    <section>
      <h2>Human Approval Gate</h2>
      <p>Review changed files before merging automation output.</p>
      <button>Approve</button>
      <button>Request rework</button>
    </section>
  );
}
`,
  },
  {
    id: '4',
    path: 'spec/services/human_approval_gate_spec.rb',
    status: 'modified',
    additions: 26,
    deletions: 2,
    original: `RSpec.describe HumanApprovalGate do
  it "approves safe changes" do
    gate = described_class.new(changes: [{ path: "app/model.rb" }])
    expect(gate.evaluate!.approved).to eq(true)
  end
end
`,
    modified: `RSpec.describe HumanApprovalGate do
  it "approves safe changes" do
    gate = described_class.new(changes: [{ path: "app/model.rb" }])
    expect(gate.evaluate!.approved).to eq(true)
  end

  it "rejects infra changes" do
    gate = described_class.new(changes: [{ path: "infra/terraform/main.tf" }])
    result = gate.evaluate!
    expect(result.approved).to eq(false)
    expect(result.reason).to include("manual check")
  end
end
`,
  },
  {
    id: '5',
    path: 'app/models/gate_decision.rb',
    status: 'added',
    additions: 24,
    deletions: 0,
    original: '',
    modified: `class GateDecision < ApplicationRecord
  enum :decision, { approved: "approved", rework: "rework" }

  validates :run_id, :gate_id, :reviewer, :decision, presence: true
end
`,
  },
  {
    id: '6',
    path: 'app/policies/human_approval_policy.rb',
    status: 'modified',
    additions: 17,
    deletions: 3,
    original: `class HumanApprovalPolicy
  def approve?
    user.admin?
  end
end
`,
    modified: `class HumanApprovalPolicy
  def approve?
    user.admin? || user.reviewer?
  end

  def request_rework?
    approve?
  end
end
`,
  },
  {
    id: '7',
    path: 'frontend/src/components/GateToolbar.tsx',
    status: 'modified',
    additions: 29,
    deletions: 8,
    original: `export function GateToolbar() {
  return <button>Approve</button>;
}
`,
    modified: `export function GateToolbar() {
  return (
    <div className="gate-toolbar">
      <button className="approve">Approve</button>
      <button className="rework">Request rework</button>
    </div>
  );
}
`,
  },
  {
    id: '8',
    path: 'frontend/src/styles/gates.css',
    status: 'modified',
    additions: 41,
    deletions: 6,
    original: `.gate { padding: 8px; }
`,
    modified: `.gate {
  padding: 12px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
}

.gate-toolbar {
  display: flex;
  gap: 8px;
}

.gate-toolbar .approve {
  background: #067647;
  color: #fff;
}

.gate-toolbar .rework {
  background: #fff7ed;
  color: #9a3412;
}
`,
  },
  {
    id: '9',
    path: 'infra/terraform/modules/approval_gate/main.tf',
    status: 'modified',
    additions: 13,
    deletions: 4,
    original: `resource "aws_sqs_queue" "gate_events" {
  name = "gate-events"
}
`,
    modified: `resource "aws_sqs_queue" "gate_events" {
  name                      = "gate-events"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 1209600
}
`,
  },
  {
    id: '10',
    path: 'infra/terraform/modules/approval_gate/variables.tf',
    status: 'added',
    additions: 15,
    deletions: 0,
    original: '',
    modified: `variable "queue_name" {
  type        = string
  description = "Approval gate queue name"
}

variable "retention_seconds" {
  type        = number
  description = "Queue retention period"
  default     = 1209600
}
`,
  },
  {
    id: '11',
    path: 'docs/process/human-approval-gate.md',
    status: 'modified',
    additions: 20,
    deletions: 9,
    original: `# Human approval gate

Manual review required.
`,
    modified: `# Human approval gate

## Goal
Manual review required for agent-produced code before merge.

## Reviewer checklist
- Validate risky files (infra/, auth/, billing/)
- Confirm tests were updated
- Leave structured comment on decision
`,
  },
  {
    id: '12',
    path: 'spec/requests/approval_gate_controller_spec.rb',
    status: 'added',
    additions: 37,
    deletions: 0,
    original: '',
    modified: `RSpec.describe "ApprovalGateController", type: :request do
  it "approves gate" do
    post "/approval_gates/1/approve", params: { comment: "looks good" }
    expect(response).to have_http_status(:ok)
  end

  it "requests rework" do
    post "/approval_gates/1/rework", params: { instruction: "add tests" }
    expect(response).to have_http_status(:ok)
  end
end
`,
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
    tf: 'hcl',
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
      if (index.has(currentPath)) {
        return;
      }
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

export default function HumanApprovalGateMock() {
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);
  const [query, setQuery] = useState('');
  const [selectedFileId, setSelectedFileId] = useState(MOCK_FILES[0].id);
  const [reworkModalOpen, setReworkModalOpen] = useState(false);
  const [comment, setComment] = useState('');
  const [instruction, setInstruction] = useState('');
  const [reworkMode, setReworkMode] = useState('discard');
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

  const reviewActionItems = [
    {
      key: 'approve',
      label: 'Approve',
    },
    {
      key: 'rework',
      label: 'Request rework',
    },
  ];

  return (
    <div className="human-approval-gate-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Human Approval Gate</Title>
        <Space size={8}>
          <Tag color="processing">gate: human_approval</Tag>
          <Tag color="gold">Mock Data</Tag>
        </Space>
      </div>
      <Card className="human-approval-summary">
        <div className="human-approval-summary-row">
          <Space size={20} wrap>
            <Text><strong>{MOCK_FILES.length}</strong> files changed</Text>
            <Text style={{ color: 'var(--success)' }}>+{totals.additions}</Text>
            <Text style={{ color: 'var(--danger)' }}>-{totals.deletions}</Text>
            <Text><CheckCircleOutlined style={{ color: 'var(--success)', marginRight: 6 }} />Ready for review</Text>
          </Space>
          <Dropdown
            menu={{
              items: reviewActionItems,
              onClick: ({ key }) => {
                if (key === 'approve') {
                  message.success('Mock gate approved');
                  return;
                }
                setReworkModalOpen(true);
              },
            }}
            trigger={['click']}
          >
            <Button type="primary">Review actions</Button>
          </Dropdown>
        </div>
      </Card>
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={7}>
          <Card title="Changed files" bodyStyle={{ padding: 12 }}>
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
                    if (nextFile) {
                      setSelectedFileId(nextFile.id);
                    }
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
          <Card
            title={(
              <Space size={8}>
                <Text className="mono">{selectedFile.path}</Text>
                {statusTag(selectedFile.status)}
              </Space>
            )}
            bodyStyle={{ padding: 0 }}
          >
            <DiffEditor
              height="70vh"
              language={detectLanguage(selectedFile.path)}
              original={selectedFile.original}
              modified={selectedFile.modified}
              beforeMount={configureMonacoThemes}
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
              }}
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
        <Input.TextArea rows={6} style={{ marginTop: 8 }} value={instruction} onChange={(event) => setInstruction(event.target.value)} />
        <Text className="muted" style={{ marginTop: 12, display: 'block' }}>Comment</Text>
        <Input.TextArea rows={2} style={{ marginTop: 8 }} value={comment} onChange={(event) => setComment(event.target.value)} />
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
