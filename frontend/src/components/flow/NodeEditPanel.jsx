import { Button, Card, Divider, Input, List, Modal, Select, Switch, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import { configureMonacoThemes } from '../../utils/monacoTheme.js';
import { EXECUTION_CONTEXT_TYPES } from '../../utils/flowValidator.js';
import { DEFAULT_REWORK } from '../../utils/flowSerializer.js';

const { Title, Text } = Typography;

const SCOPE_OPTIONS = [
  { value: 'project', label: 'Scope:project' },
  { value: 'run', label: 'Scope:run' },
];
const TRANSFER_MODE_OPTIONS = [
  { value: 'by_ref', label: 'Transfer: by ref' },
  { value: 'by_value', label: 'Transfer: by value' },
];
const MODIFIABLE_OPTIONS = [
  { value: 'no', label: 'Modifiable: NO' },
  { value: 'yes', label: 'Modifiable: YES' },
];
const SHOW_AI_RESPONSE_SCHEMA_EDITOR = false;
const SHOW_EXPECTED_CHANGES_EDITOR = false;

const requiredLabel = (label) => `${label} *`;

export function NodeEditPanel({ editor }) {
  const {
    selectedNode, selectedNodeKind, showExecutionContextEditor,
    nodeIdDraft, setNodeIdDraft, canEditNodeId,
    isReadOnly, nodeIdOptions, skillOptions, skillsCatalog,
    monacoTheme,
    flowVersion, versionOptions, handleVersionSelect, isEditing, flowMeta,
    updateSelectedNode, renameSelectedNodeId,
    updateSelectedNodeList, addSelectedNodeListItem, removeSelectedNodeListItem,
  } = editor;

  const renderVersionSelector = () => {
    if (flowMeta.flowId) {
      return (
        <Select
          value={flowVersion || undefined}
          options={versionOptions}
          onChange={(value) => handleVersionSelect(value)}
          className="rule-version-select"
          placeholder="Version"
          disabled={isEditing}
        />
      );
    }
    return <span className="rule-version-pill">new</span>;
  };

  return (
    <Card className="flow-panel-card">
      <div className="rule-fields-header">
        <Title level={5} style={{ margin: 0 }}>Selected node</Title>
        {renderVersionSelector()}
      </div>
      <div className="form-stack">
        <div>
          <Text className="muted">{requiredLabel('Node ID')}</Text>
          <div className="field-control">
            <Input
              value={nodeIdDraft}
              disabled={!canEditNodeId}
              title="Unique node identifier within the flow."
              onChange={(event) => {
                if (!canEditNodeId) {
                  return;
                }
                setNodeIdDraft(event.target.value);
              }}
              onBlur={(event) => {
                if (!canEditNodeId) {
                  return;
                }
                renameSelectedNodeId(event.target.value);
              }}
              onPressEnter={(event) => {
                if (!canEditNodeId) {
                  return;
                }
                renameSelectedNodeId(event.currentTarget.value);
              }}
            />
          </div>
        </div>
        <div>
          <Text className="muted">{requiredLabel('Name')}</Text>
          <div className="field-control">
            <Input
              value={selectedNode.data.title}
              disabled={isReadOnly}
              title="Short display name of the node."
              onChange={(event) => updateSelectedNode({ title: event.target.value })}
            />
          </div>
        </div>
        <div>
          <Text className="muted">Description</Text>
          <div className="field-control">
            <Input.TextArea
              rows={2}
              value={selectedNode.data.description}
              disabled={isReadOnly}
              title="Description of the node purpose."
              onChange={(event) => updateSelectedNode({ description: event.target.value })}
            />
          </div>
        </div>
        <div>
          <Text className="muted">{requiredLabel('Node type')}</Text>
          <div className="field-control">
            <Select
              value={selectedNode.data.nodeKind || selectedNode.data.type}
              disabled={isReadOnly}
              title="Node type defines runtime behavior."
              onChange={(value) => {
                updateSelectedNode({
                  nodeKind: value,
                  type: value,
                  skillRefs: value === 'ai' ? selectedNode.data.skillRefs || [] : [],
                  onSubmit: value === 'human_input' ? selectedNode.data.onSubmit || '' : '',
                  onApprove: value === 'human_approval' ? selectedNode.data.onApprove || '' : '',
                  onRework: value === 'human_approval'
                    ? selectedNode.data.onRework || { ...DEFAULT_REWORK }
                    : { ...DEFAULT_REWORK },
                  executionContext: value === 'command' ? [] : (selectedNode.data.executionContext || []),
                  responseSchema: value === 'command' ? '' : (selectedNode.data.responseSchema || ''),
                  onSuccess: value === 'terminal' ? '' : selectedNode.data.onSuccess || '',
                  onFailure: value === 'ai' || value === 'command' ? (selectedNode.data.onFailure || '') : '',
                  checkpointBeforeRun: value === 'ai' || value === 'command'
                    ? (selectedNode.data.checkpointBeforeRun ?? true)
                    : false,
                });
              }}
              options={[
                { value: 'ai', label: 'AI Executor' },
                { value: 'command', label: 'Shell Command' },
                { value: 'human_input', label: 'Human Input Gate' },
                { value: 'human_approval', label: 'Human Approval Gate' },
                { value: 'terminal', label: 'Stop Flow' },
              ]}
            />
          </div>
        </div>
        {showExecutionContextEditor && (
          <>
            <Divider />
            <div>
              <Title level={5}>Execution context</Title>
              <Text className="muted">Node input data</Text>
              <div className="context-list">
                {(selectedNode.data.executionContext || []).map((entry, index) => (
                  <div key={`${entry.type}-${index}`} className="context-row">
                    <div className="context-row-header">
                      <Select
                        value={entry.type}
                        options={EXECUTION_CONTEXT_TYPES}
                        disabled={isReadOnly}
                        title="Input context type for the node."
                        onChange={(value) => updateSelectedNodeList(
                          'executionContext',
                          index,
                          { type: value, scope: entry.scope || 'run', transfer_mode: entry.transfer_mode || 'by_ref' }
                        )}
                      />
                      <Button
                        size="small"
                        type="default"
                        danger
                        className="artifact-delete-btn"
                        icon={<DeleteOutlined />}
                        disabled={isReadOnly}
                        onClick={() => removeSelectedNodeListItem('executionContext', index)}
                      />
                    </div>
                    {entry.type === 'artifact_ref' && (
                      <div className="context-row-fields">
                        <Select
                          value={entry.scope || 'run'}
                          options={SCOPE_OPTIONS}
                          disabled={isReadOnly}
                          title="Artifact lookup scope: run or project."
                          onChange={(value) => updateSelectedNodeList('executionContext', index, {
                            scope: value,
                            node_id: value === 'project' ? undefined : entry.node_id,
                          })}
                        />
                        {selectedNodeKind === 'ai' && (
                          <Select
                            value={entry.transfer_mode || 'by_ref'}
                            options={TRANSFER_MODE_OPTIONS}
                            disabled={isReadOnly}
                            title="How artifacts are transferred into AI context."
                            onChange={(value) => updateSelectedNodeList('executionContext', index, {
                              transfer_mode: value,
                            })}
                          />
                        )}
                        {(entry.scope || 'run') === 'run' && (
                          <Select
                            value={entry.node_id || undefined}
                            options={nodeIdOptions.filter((opt) => opt.value !== selectedNode.id)}
                            placeholder="source-node"
                            disabled={isReadOnly}
                            title="Source node of the artifact in the current run."
                            allowClear
                            popupClassName="node-source-select-dropdown"
                            popupMatchSelectWidth
                            getPopupContainer={(trigger) => trigger.parentElement || document.body}
                            onChange={(value) => updateSelectedNodeList('executionContext', index, { node_id: value || '' })}
                          />
                        )}
                        <Input
                          className="context-field-full"
                          value={entry.path || ''}
                          placeholder="file name"
                          disabled={isReadOnly}
                          title="Path to the input artifact."
                          onChange={(event) => updateSelectedNodeList('executionContext', index, { path: event.target.value })}
                        />
                      </div>
                    )}
                  </div>
                ))}
                <Button
                  type="default"
                  icon={<PlusOutlined />}
                  disabled={isReadOnly}
                  onClick={() =>
                    addSelectedNodeListItem('executionContext', {
                      type: 'artifact_ref',
                      required: true,
                      scope: 'run',
                      path: '',
                      transfer_mode: 'by_ref',
                    })
                  }
                >
                  Add context
                </Button>
              </div>
            </div>
          </>
        )}

        {selectedNodeKind === 'ai' && (
          <>
            <div>
              <Text className="muted">Instruction</Text>
              <div className="field-control">
                <Input.TextArea
                  rows={3}
                  value={selectedNode.data.instruction}
                  disabled={isReadOnly}
                  title="Instruction for the AI node."
                  onChange={(event) => updateSelectedNode({ instruction: event.target.value })}
                />
              </div>
            </div>
            {SHOW_AI_RESPONSE_SCHEMA_EDITOR && (
              <div>
                <Text className="muted">Response schema (optional, JSON)</Text>
                <div className="field-control schema-editor-wrap">
                  <Editor
                    height="120px"
                    defaultLanguage="json"
                    beforeMount={configureMonacoThemes}
                    theme={monacoTheme}
                    value={selectedNode.data.responseSchema || ''}
                    onChange={(value) => updateSelectedNode({ responseSchema: value ?? '' })}
                    options={{
                      readOnly: isReadOnly,
                      minimap: { enabled: false },
                      lineNumbers: 'off',
                      scrollBeyondLastLine: false,
                      folding: false,
                      fontSize: 12,
                      tabSize: 2,
                      automaticLayout: true,
                      wordWrap: 'on',
                      overviewRulerLanes: 0,
                      hideCursorInOverviewRuler: true,
                      scrollbar: { vertical: 'auto', horizontal: 'hidden' },
                    }}
                  />
                </div>
              </div>
            )}
          </>
        )}

        {(selectedNodeKind === 'human_input' || selectedNodeKind === 'human_approval') && (
          <div>
            <Text className="muted">Instruction for gate</Text>
            <div className="field-control">
              <Input.TextArea
                rows={3}
                value={selectedNode.data.instruction}
                disabled={isReadOnly}
                title="Instruction text shown to the user at the human gate step."
                onChange={(event) => updateSelectedNode({ instruction: event.target.value })}
              />
            </div>
          </div>
        )}

        {selectedNodeKind === 'command' && (
          <>
            <Divider />
            <div>
              <Text className="muted">Bash commands</Text>
              <div className="field-control">
                <Input.TextArea
                  rows={4}
                  value={selectedNode.data.instruction}
                  placeholder={'#!/usr/bin/env bash\nset -euo pipefail\necho "Hello"'}
                  disabled={isReadOnly}
                  title="Commands executed by the command node."
                  onChange={(event) => updateSelectedNode({ instruction: event.target.value })}
                />
              </div>
            </div>
          </>
        )}

        {(selectedNodeKind === 'ai' || selectedNodeKind === 'command') && (
          <div>
            <Text className="muted">Save node state before launch</Text>
            <div
              className="field-control"
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}
            >
              <div style={{ display: 'grid', gap: 2 }}>
                <Text>Create Git checkpoint before execution</Text>
                <Text type="secondary">
                  Saves a checkpoint before the node starts, so rework can roll back to this state.
                </Text>
              </div>
              <Switch
                checked={!!selectedNode.data.checkpointBeforeRun}
                disabled={isReadOnly}
                checkedChildren="On"
                unCheckedChildren="Off"
                onChange={(checked) => {
                  if (checked || !selectedNode.data.checkpointBeforeRun) {
                    updateSelectedNode({ checkpointBeforeRun: checked });
                    return;
                  }
                  Modal.confirm({
                    title: 'Disable Git checkpoint?',
                    content: 'If disabled, full rework will be impossible because the node will not be able to roll back its state.',
                    okText: 'Disable',
                    okButtonProps: { danger: true },
                    cancelText: 'Cancel',
                    onOk: () => updateSelectedNode({ checkpointBeforeRun: false }),
                  });
                }}
              />
            </div>
          </div>
        )}

        {selectedNodeKind === 'ai' && (
          <>
            <Divider />
            <div className="linked-block">
              <div className="linked-header">
                <Title level={5}>Linked skills</Title>
                <Text className="muted">AI only</Text>
              </div>
              <Select
                mode="multiple"
                options={skillOptions}
                value={selectedNode.data.skillRefs || []}
                disabled={isReadOnly}
                onChange={(value) => updateSelectedNode({ skillRefs: value })}
                placeholder="Select skills"
              />
              <List
                dataSource={selectedNode.data.skillRefs || []}
                locale={{ emptyText: 'No linked skills' }}
                renderItem={(ref) => {
                  const skill = skillsCatalog.find((item) => item.canonical === ref);
                  const description = skill?.description || '';
                  const snippet = description.length > 80
                    ? `${description.slice(0, 80)}...`
                    : description;
                  return (
                    <List.Item className="linked-item">
                      <div className="linked-rule-row">
                        <span className="mono linked-rule-name">{ref}</span>
                      </div>
                      {snippet && (
                        <Text type="secondary" className="linked-rule-description">
                          {snippet}
                        </Text>
                      )}
                    </List.Item>
                  );
                }}
              />
            </div>
          </>
        )}

        {selectedNodeKind !== 'terminal' && (
          <>
            <Divider />

            <div>
              <Title level={5}>Expected outputs</Title>
              <Text className="muted">Generated artifacts</Text>
              <div className="context-list">
                {(selectedNode.data.producedArtifacts || []).map((entry, index) => (
                  <div key={`artifact-${index}`} className="context-row artifact-context-row">
                    <div className="context-row-header">
                      <Text className="muted">Artifact #{index + 1}</Text>
                      <Button
                        size="small"
                        type="default"
                        danger
                        className="artifact-delete-btn"
                        icon={<DeleteOutlined />}
                        disabled={isReadOnly}
                        onClick={() => removeSelectedNodeListItem('producedArtifacts', index)}
                      />
                    </div>
                    <div className="context-row-fields artifact-row-fields">
                      <Select
                        value={entry.scope || 'run'}
                        options={SCOPE_OPTIONS}
                        disabled={isReadOnly}
                        title="Scope where the artifact will be created."
                        onChange={(value) => updateSelectedNodeList('producedArtifacts', index, { scope: value })}
                      />
                      <Select
                        value={entry.modifiable === true ? 'yes' : 'no'}
                        options={MODIFIABLE_OPTIONS}
                        disabled={isReadOnly}
                        title="Whether the artifact can be edited at the human_input step."
                        onChange={(value) => updateSelectedNodeList('producedArtifacts', index, { modifiable: value === 'yes' })}
                      />
                      <Input
                        className="context-field-full artifact-path-input"
                        value={entry.path || ''}
                        placeholder="path"
                        disabled={isReadOnly}
                        title="Path of the produced artifact."
                        onChange={(event) =>
                          updateSelectedNodeList('producedArtifacts', index, { path: event.target.value })
                        }
                      />
                    </div>
                  </div>
                ))}
                <Button
                  type="default"
                  icon={<PlusOutlined />}
                  disabled={isReadOnly}
                  onClick={() => addSelectedNodeListItem('producedArtifacts', {
                    path: '',
                    required: true,
                    scope: 'run',
                    modifiable: false,
                  })}
                >
                  Add artifact
                </Button>
              </div>

              {SHOW_EXPECTED_CHANGES_EDITOR && (
                <>
                  <Text className="muted" style={{ marginTop: 12 }}>Expected changes</Text>
                  <div className="context-list">
                    {(selectedNode.data.expectedMutations || []).map((entry, index) => (
                      <div key={`mutation-${index}`} className="mutation-row">
                        <Select
                          value={entry.scope || 'project'}
                          options={SCOPE_OPTIONS}
                          disabled={isReadOnly}
                          title="Scope where the expected change should happen."
                          onChange={(value) => updateSelectedNodeList('expectedMutations', index, { scope: value })}
                        />
                        <Input
                          value={entry.path || ''}
                          placeholder="path"
                          disabled={isReadOnly}
                          title="Path of the file that must be changed."
                          onChange={(event) => updateSelectedNodeList('expectedMutations', index, { path: event.target.value })}
                        />
                        <Button
                          size="small"
                          type="default"
                          danger
                          icon={<DeleteOutlined />}
                          disabled={isReadOnly}
                          onClick={() => removeSelectedNodeListItem('expectedMutations', index)}
                        />
                      </div>
                    ))}
                    <Button
                      type="default"
                      icon={<PlusOutlined />}
                      disabled={isReadOnly}
                      onClick={() => addSelectedNodeListItem('expectedMutations', { path: '', required: true, scope: 'project' })}
                    >
                      Add change
                    </Button>
                  </div>
                </>
              )}
            </div>

            <Divider />

            <div>
              <Title level={5}>Transitions</Title>
              {(selectedNodeKind === 'ai' || selectedNodeKind === 'command') && (
                <>
                  <div className="transition-block">
                    <Text className="muted mono">on_success</Text>
                    <div className="field-control">
                      <Select
                        value={selectedNode.data.onSuccess || undefined}
                        disabled={isReadOnly}
                        allowClear
                        options={nodeIdOptions}
                        placeholder="Select node"
                        title="Transition node on success."
                        onChange={(value) => updateSelectedNode({ onSuccess: value || '' })}
                      />
                    </div>
                  </div>
                  <div className="transition-block">
                    <Text className="muted mono">on_failure</Text>
                    <div className="field-control">
                      <Select
                        value={selectedNode.data.onFailure || undefined}
                        disabled={isReadOnly}
                        allowClear
                        options={nodeIdOptions}
                        placeholder="Select node"
                        title="Transition node on failure."
                        onChange={(value) => updateSelectedNode({ onFailure: value || '' })}
                      />
                    </div>
                  </div>
                </>
              )}
              {selectedNodeKind === 'human_input' && (
                <div className="transition-block">
                  <Text className="muted mono">on_submit</Text>
                  <div className="field-control">
                    <Select
                      value={selectedNode.data.onSubmit || undefined}
                      disabled={isReadOnly}
                      allowClear
                      options={nodeIdOptions}
                      placeholder="Select node"
                      title="Transition node after user input."
                      onChange={(value) => updateSelectedNode({ onSubmit: value || '' })}
                    />
                  </div>
                </div>
              )}
              {selectedNodeKind === 'human_approval' && (
                <>
                  <div className="transition-block">
                    <Text className="muted mono">on_approve</Text>
                    <div className="field-control">
                      <Select
                        value={selectedNode.data.onApprove || undefined}
                        disabled={isReadOnly}
                        allowClear
                        options={nodeIdOptions}
                        placeholder="Select node"
                        title="Transition node after approve."
                        onChange={(value) => updateSelectedNode({ onApprove: value || '' })}
                      />
                    </div>
                  </div>
                  <div className="transition-block">
                    <Text className="muted mono">on_rework</Text>
                    <div className="field-control">
                      <Select
                        value={(selectedNode.data.onRework || DEFAULT_REWORK).nextNode || undefined}
                        disabled={isReadOnly}
                        allowClear
                        options={nodeIdOptions}
                        placeholder="Select node"
                        title="Transition node on rework."
                        onChange={(value) => {
                          const current = selectedNode.data.onRework || DEFAULT_REWORK;
                          updateSelectedNode({
                            onRework: {
                              ...current,
                              nextNode: value || '',
                            },
                          });
                        }}
                      />
                    </div>
                    <div
                      className="field-control"
                      style={{ marginTop: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}
                    >
                      <Text type="secondary">Rework transition target for this gate.</Text>
                    </div>
                  </div>
                </>
              )}
            </div>
          </>
        )}
      </div>
    </Card>
  );
}
