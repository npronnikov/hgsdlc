import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Radio, Typography } from 'antd';
import { Background, Controls, Panel, ReactFlow, useReactFlow, ReactFlowProvider } from 'reactflow';
import 'reactflow/dist/style.css';
import { FlowNode, nodeTypes } from '../flow/FlowNode.jsx';
import { buildFlowPreviewFromModel } from '../../utils/flowLayout.js';

function LiveGraphInner({ flowSnapshot, nodeExecutions = [], activeNodeId, isActive, onNodeClick }) {
  const [direction, setDirection] = useState('LR');
  const [layout, setLayout] = useState({ nodes: [], edges: [] });
  const reactFlow = useReactFlow();
  const prevActiveRef = useRef(null);
  const initialZoomDone = useRef(false);
  const wasActiveRef = useRef(isActive);

  useEffect(() => {
    if (!flowSnapshot) return;
    const preview = buildFlowPreviewFromModel(flowSnapshot, direction);
    setLayout({ nodes: preview.nodes, edges: preview.edges });
  }, [flowSnapshot, direction]);

  const enrichedNodes = useMemo(() => layout.nodes.map((node) => {
    const latest = nodeExecutions.filter((e) => e.node_id === node.id).at(-1);
    return {
      ...node,
      data: {
        ...node.data,
        runStatus: latest?.status ?? null,
        nodeExecutionId: latest?.node_execution_id ?? null,
        errorCode: latest?.error_code ?? null,
      },
    };
  }), [layout.nodes, nodeExecutions]);

  const zoomToNode = useCallback((nodeId) => {
    const target = enrichedNodes.find((n) => n.id === nodeId);
    if (!target) return false;
    const zoom = reactFlow.getZoom();
    reactFlow.setCenter(
      target.position.x + (target.width ?? 180) / 2,
      target.position.y + (target.height ?? 60) / 2,
      { zoom: Math.max(zoom, 1.2), duration: 400 },
    );
    return true;
  }, [enrichedNodes, reactFlow]);

  useEffect(() => {
    if (wasActiveRef.current && !isActive && enrichedNodes.length > 0) {
      setTimeout(() => reactFlow.fitView({ padding: 0.3, duration: 400 }), 50);
    }
    wasActiveRef.current = isActive;
  }, [isActive, enrichedNodes, reactFlow]);

  useEffect(() => {
    if (!isActive) return;
    if (!activeNodeId || enrichedNodes.length === 0) return;
    if (activeNodeId === prevActiveRef.current && initialZoomDone.current) return;
    const delay = initialZoomDone.current ? 50 : 250;
    const timer = setTimeout(() => {
      if (zoomToNode(activeNodeId)) {
        prevActiveRef.current = activeNodeId;
        initialZoomDone.current = true;
      }
    }, delay);
    return () => clearTimeout(timer);
  }, [isActive, activeNodeId, enrichedNodes, zoomToNode]);

  const onInit = useCallback((instance) => {
    setTimeout(() => instance.fitView({ padding: 0.3, duration: 200 }), 30);
  }, []);

  if (!flowSnapshot) {
    return (
      <div className="live-graph-canvas flow-canvas live-graph-empty">
        <Typography.Text type="secondary">Flow graph unavailable</Typography.Text>
      </div>
    );
  }

  return (
    <ReactFlow
      nodes={enrichedNodes}
      edges={layout.edges}
      nodeTypes={nodeTypes}
      nodesDraggable={false}
      nodesConnectable={false}
      zoomOnScroll={false}
      panOnScroll
      fitView
      fitViewOptions={{ padding: 0.3 }}
      onInit={onInit}
      onNodeClick={(_, node) => node.data?.nodeExecutionId && onNodeClick?.(node.data.nodeExecutionId)}
      proOptions={{ hideAttribution: true }}
    >
      <Background gap={20} color="var(--color-border-subtle, #e2e8f0)" />
      <Controls showInteractive={false} />
      <Panel position="top-right">
        <Radio.Group
          value={direction}
          onChange={(e) => setDirection(e.target.value)}
          optionType="button"
          size="small"
        >
          <Radio.Button value="TB">↕</Radio.Button>
          <Radio.Button value="LR">↔</Radio.Button>
        </Radio.Group>
      </Panel>
    </ReactFlow>
  );
}

export function LiveGraph(props) {
  if (!props.flowSnapshot) {
    return (
      <div className="live-graph-canvas flow-canvas live-graph-empty">
        <Typography.Text type="secondary">Flow graph unavailable</Typography.Text>
      </div>
    );
  }

  return (
    <div className="live-graph-canvas flow-canvas">
      <ReactFlowProvider>
        <LiveGraphInner {...props} />
      </ReactFlowProvider>
    </div>
  );
}
