import { useRef, useState } from 'react';
import { Background, Controls, ReactFlow } from 'reactflow';
import 'reactflow/dist/style.css';
import { Button, Card, Input, List, Modal, Select, Space, Typography } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { useLocation, useParams } from 'react-router-dom';
import { nodeTypes } from '../components/flow/FlowNode.jsx';
import { FlowMetaPanel } from '../components/flow/FlowMetaPanel.jsx';
import { NodeEditPanel } from '../components/flow/NodeEditPanel.jsx';
import { useFlowEditor } from '../hooks/useFlowEditor.js';

const { Title, Text } = Typography;

const NODE_TYPE_OPTIONS = [
  { key: 'ai', label: 'AI Executor' },
  { key: 'command', label: 'Command Executor' },
  { key: 'human_input', label: 'Human Input Gate' },
  { key: 'human_approval', label: 'Human Approval Gate' },
  { key: 'terminal', label: 'Terminal' },
];

export default function FlowEditor() {
  const { flowId } = useParams();
  const location = useLocation();
  const isCreateMode = flowId === 'create' || location.pathname.endsWith('/flows/create');
  const editor = useFlowEditor({ flowId, isCreateMode });

  const {
    flowMeta, nodes, edges, onNodesChange,
    showYaml, setShowYaml,
    selectedNode, selectedNodeId, setSelectedNodeId,
    isReadOnly, isEditing, setIsEditing,
    flowWrapperRef, flowInstance, setFlowInstance,
    contextMenu, setContextMenu,
    pendingConnection, setPendingConnection,
    routeOptions, routeChoice, setRouteChoice,
    publishDialogOpen, setPublishDialogOpen,
    publishVariant, setPublishVariant,
    publishLabel, releaseLabel,
    flowVersionLabel,
    validationErrors,
    canEditCurrentDraft, canDeleteDraft,
    canManageCatalog,
    nextDraftVersion, currentStatus,
    getRouteOptions, applyConnection, removeConnection,
    addNode, removeNodeById,
    saveFlow, openPublishDialog, deleteCurrentDraft, confirmPublish,
    startDraftFromPublished,
    importFlowYaml,
  } = editor;

  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importYamlText, setImportYamlText] = useState('');
  const fileInputRef = useRef(null);

  const handleImportConfirm = () => {
    if (!importYamlText.trim()) {
      return;
    }
    const ok = importFlowYaml(importYamlText);
    if (ok) {
      setImportModalOpen(false);
      setImportYamlText('');
    }
  };

  const handleImportFile = (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result;
      if (typeof text === 'string') {
        setImportYamlText(text);
      }
    };
    reader.readAsText(file);
    event.target.value = '';
  };

  return (
    <div className="flow-editor-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Flow editor</Title>
        <Space>
          {canManageCatalog && !isEditing && (
            currentStatus === 'draft' ? (
              <>
                <Button type="default" onClick={() => setIsEditing(true)} disabled={!canEditCurrentDraft}>
                  Edit
                </Button>
                <Button type="default" onClick={openPublishDialog} disabled={!canEditCurrentDraft}>
                  Request publication
                </Button>
                {canDeleteDraft && (
                  <Button danger type="default" onClick={deleteCurrentDraft}>
                    Delete draft
                  </Button>
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
              <Button
                type="default"
                disabled={currentStatus === 'draft' && !canEditCurrentDraft}
                onClick={async () => {
                  const ok = await saveFlow({ publish: false });
                  if (ok) {
                    setIsEditing(false);
                  }
                }}
              >
                Save
              </Button>
              <Button type="default" onClick={openPublishDialog} disabled={currentStatus === 'draft' && !canEditCurrentDraft}>
                Request publication
              </Button>
            </>
          )}
        </Space>
      </div>

      <div className="split-layout flow-editor-layout">
        <Card className="flow-canvas-card">
          <div className="flow-canvas-header">
            <div>
              <Text className="muted">Canvas</Text>
              <div className="mono">{flowMeta.flowId || 'new-flow'}@{flowVersionLabel}</div>
            </div>
            <Space>
              {isEditing && (
                <Button
                  type="default"
                  onClick={() => setImportModalOpen(true)}
                >
                  Import YAML
                </Button>
              )}
              <Button
                type="default"
                onClick={() => setShowYaml((prev) => !prev)}
              >
                {showYaml ? 'Show designer' : 'YAML view'}
              </Button>
            </Space>
          </div>
          <div className="flow-canvas" ref={flowWrapperRef}>
            {showYaml ? (
              <pre className="code-block" style={{ margin: 0, height: '100%' }}>
                {editor.buildFlowYaml()}
              </pre>
            ) : (
              <ReactFlow
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={(changes) => {
                  if (isReadOnly) {
                    return;
                  }
                  changes
                    .filter((change) => change.type === 'remove')
                    .forEach((change) => {
                      const removedEdge = edges.find((edge) => edge.id === change.id);
                      if (removedEdge) {
                        removeConnection(removedEdge);
                      }
                    });
                }}
                onConnect={(connection) => {
                  if (isReadOnly) {
                    return;
                  }
                  if (!connection.source || !connection.target) {
                    return;
                  }
                  const sourceNode = nodes.find((node) => node.id === connection.source);
                  const options = getRouteOptions(sourceNode);
                  if (options.length === 0) {
                    return;
                  }
                  if (options.length === 1) {
                    applyConnection(connection.source, connection.target, options[0].value);
                    return;
                  }
                  setPendingConnection({
                    source: connection.source,
                    target: connection.target,
                  });
                  editor.setRouteOptions(options);
                  setRouteChoice(options[0]?.value || null);
                }}
                nodeTypes={nodeTypes}
                nodesDraggable={!isReadOnly}
                nodesConnectable={!isReadOnly}
                fitView
                onInit={setFlowInstance}
                onNodeClick={(_, node) => {
                  setSelectedNodeId(node.id);
                  setContextMenu(null);
                }}
                onPaneClick={() => {
                  setSelectedNodeId(null);
                  setContextMenu(null);
                }}
                onPaneContextMenu={(event) => {
                  if (isReadOnly) {
                    return;
                  }
                  event.preventDefault();
                  setContextMenu({
                    x: event.clientX,
                    y: event.clientY,
                    type: 'pane',
                  });
                }}
                onNodeContextMenu={(event, node) => {
                  if (isReadOnly) {
                    return;
                  }
                  event.preventDefault();
                  setSelectedNodeId(node.id);
                  setContextMenu({
                    x: event.clientX,
                    y: event.clientY,
                    type: 'node',
                    nodeId: node.id,
                  });
                }}
                proOptions={{ hideAttribution: true }}
              >
                <Background gap={20} color="#e2e8f0" />
                <Controls />
              </ReactFlow>
            )}
            {contextMenu && (
              <div
                className="flow-context-menu"
                style={{ top: contextMenu.y, left: contextMenu.x }}
                onMouseDown={(event) => event.stopPropagation()}
              >
                <div className="flow-context-title">Add node</div>
                <div className="flow-context-section">
                  {NODE_TYPE_OPTIONS.map((option) => (
                    <Button
                      key={option.key}
                      type="default"
                      className="flow-context-add-btn"
                      onClick={() => {
                        if (flowInstance && flowWrapperRef.current) {
                          const bounds = flowWrapperRef.current.getBoundingClientRect();
                          const position = flowInstance.project({
                            x: contextMenu.x - bounds.left,
                            y: contextMenu.y - bounds.top,
                          });
                          addNode(option.key, position);
                        } else {
                          addNode(option.key);
                        }
                        setContextMenu(null);
                      }}
                    >
                      {option.label}
                    </Button>
                  ))}
                </div>
                {selectedNodeId && (
                  <>
                    <div className="flow-context-divider" />
                    <Button
                      danger
                      type="default"
                      onClick={() => {
                        const targetId = contextMenu.nodeId || selectedNodeId;
                        setContextMenu(null);
                        Modal.confirm({
                          title: 'Delete node?',
                          content: `Node ${targetId} will be removed with its links.`,
                          okText: 'Delete',
                          cancelText: 'Cancel',
                          okButtonProps: { danger: true },
                          onOk: () => removeNodeById(targetId),
                        });
                      }}
                    >
                      Delete node
                    </Button>
                  </>
                )}
              </div>
            )}
          </div>
        </Card>

        <div className="flow-right-panel">
          {!selectedNode ? (
            <FlowMetaPanel editor={editor} />
          ) : (
            <NodeEditPanel editor={editor} />
          )}

          <Card className="flow-panel-card" style={{ marginTop: 16 }}>
            <Title level={5}>Validation</Title>
            {validationErrors.length === 0 ? (
              <div className="card-muted">No validation errors found.</div>
            ) : (
              <List
                dataSource={validationErrors}
                renderItem={(item) => <List.Item className="card-muted">{item}</List.Item>}
              />
            )}
          </Card>
        </div>
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
        </div>
      </Modal>
      <Modal
        open={!!pendingConnection}
        title="Select link type"
        okText="Apply"
        cancelText="Cancel"
        onCancel={() => setPendingConnection(null)}
        onOk={() => {
          if (pendingConnection?.source && pendingConnection?.target && routeChoice) {
            applyConnection(pendingConnection.source, pendingConnection.target, routeChoice);
          }
          setPendingConnection(null);
        }}
      >
        <Select
          value={routeChoice}
          onChange={(value) => setRouteChoice(value)}
          options={routeOptions}
          style={{ width: '100%' }}
        />
      </Modal>
      <Modal
        title="Import flow from YAML"
        open={importModalOpen}
        onCancel={() => {
          setImportModalOpen(false);
          setImportYamlText('');
        }}
        onOk={handleImportConfirm}
        okText="Import"
        cancelText="Cancel"
        okButtonProps={{ disabled: !importYamlText.trim() }}
        width={720}
      >
        <div style={{ display: 'grid', gap: 12 }}>
          <Text type="secondary">
            Paste YAML content or upload a .yaml file. This will replace the current nodes and flow metadata.
          </Text>
          <div>
            <input
              ref={fileInputRef}
              type="file"
              accept=".yaml,.yml"
              onChange={handleImportFile}
              style={{ display: 'none' }}
            />
            <Button
              icon={<UploadOutlined />}
              onClick={() => fileInputRef.current?.click()}
            >
              Upload .yaml file
            </Button>
          </div>
          <Input.TextArea
            rows={18}
            value={importYamlText}
            onChange={(e) => setImportYamlText(e.target.value)}
            placeholder="Paste flow YAML here..."
            style={{ fontFamily: 'monospace', fontSize: 12 }}
          />
        </div>
      </Modal>
    </div>
  );
}
