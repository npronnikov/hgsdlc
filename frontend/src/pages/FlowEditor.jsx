import { useEffect, useRef, useState } from 'react';
import { Background, Controls, ReactFlow } from 'reactflow';
import 'reactflow/dist/style.css';
import { Button, Card, List, Modal, Select, Space, Typography } from 'antd';
import { BugOutlined, CompressOutlined, ExpandOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { nodeTypes } from '../components/flow/FlowNode.jsx';
import { FlowMetaPanel } from '../components/flow/FlowMetaPanel.jsx';
import { NodeEditPanel } from '../components/flow/NodeEditPanel.jsx';
import { ImportYamlModal } from '../components/flow/ImportYamlModal.jsx';
import { DebugRunDrawer } from '../components/flow/DebugRunDrawer.jsx';
import { useFlowEditor } from '../hooks/useFlowEditor.js';

const { Title, Text } = Typography;

const NODE_TYPE_OPTIONS = [
  { key: 'ai', label: 'AI Executor' },
  { key: 'command', label: 'Shell Command' },
  { key: 'human_input', label: 'Human Input Gate' },
  { key: 'human_approval', label: 'Human Approval Gate' },
  { key: 'terminal', label: 'Stop Flow' },
];

export default function FlowEditor() {
  const { flowId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
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
  const [debugDrawerOpen, setDebugDrawerOpen] = useState(false);
  const [testRunModalOpen, setTestRunModalOpen] = useState(false);
  const [isCanvasFullscreen, setIsCanvasFullscreen] = useState(false);
  const canvasCardRef = useRef(null);

  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsCanvasFullscreen(document.fullscreenElement === canvasCardRef.current);
    };
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
    };
  }, []);

  const toggleCanvasFullscreen = async () => {
    try {
      if (document.fullscreenElement === canvasCardRef.current) {
        await document.exitFullscreen();
        return;
      }
      if (!document.fullscreenElement && canvasCardRef.current?.requestFullscreen) {
        await canvasCardRef.current.requestFullscreen();
      }
    } catch (error) {
      // Ignore fullscreen API failures (browser policy or user cancellation).
    }
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
                {flowMeta.canonicalName && (
                  <Button
                    type="default"
                    icon={<BugOutlined />}
                    onClick={() => setDebugDrawerOpen(true)}
                  >
                    Debug Run
                  </Button>
                )}
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
                    if (isCreateMode && flowMeta.flowId) {
                      navigate(`/flows/${flowMeta.flowId}`, { replace: true });
                    }
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
        <div
          ref={canvasCardRef}
          className={`flow-canvas-card-shell${isCanvasFullscreen ? ' is-fullscreen' : ''}`}
        >
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
              <Button
                type="default"
                icon={isCanvasFullscreen ? <CompressOutlined /> : <ExpandOutlined />}
                onClick={toggleCanvasFullscreen}
              >
                {isCanvasFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
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
        </div>

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
      <ImportYamlModal
        open={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onImport={importFlowYaml}
      />
      <DebugRunDrawer
        open={debugDrawerOpen}
        onClose={() => setDebugDrawerOpen(false)}
        canonicalName={flowMeta.canonicalName}
      />
    </div>
  );
}
