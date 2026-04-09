import React, { useEffect, useMemo, useState } from 'react';
import { Button, Space, Typography, message } from 'antd';
import {
  ArrowLeftOutlined,
  FullscreenExitOutlined,
  FullscreenOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { useRunPolling } from '../hooks/useRunPolling.js';
import { LiveGraph } from '../components/run/LiveGraph.jsx';
import { ExecutionTimeline } from '../components/run/ExecutionTimeline.jsx';
import { LiveLogViewer } from '../components/run/LiveLogViewer.jsx';
import { GateInputPanel } from '../components/run/GateInputPanel.jsx';
import { GateReviewPanel } from '../components/run/GateReviewPanel.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

function shortId(id, len = 12) {
  return String(id || '').slice(0, len);
}

class RunWorkspaceErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 32, textAlign: 'center' }}>
          <Typography.Title level={4}>Something went wrong</Typography.Title>
          <Typography.Text type="secondary">{this.state.error?.message}</Typography.Text>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function RunWorkspace() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');

  const { run, nodeExecutions, gates, isActive, loading, error, refresh } = useRunPolling(runId);

  const [workspaceMode, setWorkspaceMode] = useState('execute');
  const [selectedNodeExecId, setSelectedNodeExecId] = useState(null);
  const [timelineCollapsed, setTimelineCollapsed] = useState(false);
  const [graphFocused, setGraphFocused] = useState(false);
  const [gateDismissedId, setGateDismissedId] = useState(null);

  const activeNodeId = useMemo(() => {
    const running = nodeExecutions.find((e) => e.status === 'running');
    if (running) return running.node_id;
    const waiting = nodeExecutions.find((e) => e.status === 'waiting_gate');
    if (waiting) return waiting.node_id;
    return nodeExecutions.at(-1)?.node_id || null;
  }, [nodeExecutions]);

  useEffect(() => {
    setSelectedNodeExecId(null);
  }, [activeNodeId]);

  const focusedNodeId = useMemo(() => {
    if (selectedNodeExecId) {
      const exec = nodeExecutions.find((e) => e.node_execution_id === selectedNodeExecId);
      if (exec) return exec.node_id;
    }
    return activeNodeId;
  }, [selectedNodeExecId, nodeExecutions, activeNodeId]);

  useEffect(() => {
    if (run && !run.debug_mode) {
      navigate(`/run-console?runId=${runId}`, { replace: true });
    }
  }, [run?.debug_mode, runId, navigate]);

  useEffect(() => {
    if (!run?.current_gate) {
      setWorkspaceMode('execute');
      return;
    }
    if (run.current_gate.gate_id === gateDismissedId) return;
    setWorkspaceMode(
      run.current_gate.gate_kind === 'human_input' ? 'gate:input' : 'gate:approval',
    );
    if (run.current_gate.gate_kind === 'human_approval') {
      setTimelineCollapsed(true);
    }
    setGraphFocused(false);
  }, [run?.current_gate?.gate_id, gateDismissedId]);

  const logNodeExecId = useMemo(() => {
    if (selectedNodeExecId) return selectedNodeExecId;
    const running = nodeExecutions.find((e) => e.status === 'running');
    if (running) return running.node_execution_id;
    return nodeExecutions.at(-1)?.node_execution_id || null;
  }, [selectedNodeExecId, nodeExecutions]);

  const cancelRun = async () => {
    try {
      await apiRequest(`/runs/${runId}/cancel`, { method: 'POST' });
      message.success('Run cancelled');
      refresh();
    } catch (err) {
      message.error(err.message || 'Failed to cancel run');
    }
  };

  const handleGateDismiss = () => {
    if (run?.current_gate?.gate_id) {
      setGateDismissedId(run.current_gate.gate_id);
      setWorkspaceMode('execute');
      setTimelineCollapsed(false);
    }
  };

  const handleGateAction = () => {
    refresh();
    setWorkspaceMode('execute');
    setTimelineCollapsed(false);
  };

  const handleNodeSelect = (nodeExecId) => {
    setSelectedNodeExecId(nodeExecId);
    if (graphFocused) setGraphFocused(false);
  };

  if (!runId) {
    return (
      <div style={{ padding: 32, textAlign: 'center' }}>
        <Text type="secondary">No run ID specified</Text>
      </div>
    );
  }

  if (error && !run) {
    return (
      <div style={{ padding: 32, textAlign: 'center' }}>
        <Title level={4}>Failed to load run</Title>
        <Text type="secondary">{error.message}</Text>
        <br />
        <Button onClick={() => refresh()} style={{ marginTop: 16 }}>Retry</Button>
      </div>
    );
  }

  const wsCls = [
    'run-workspace',
    timelineCollapsed ? 'tl-collapsed' : '',
    graphFocused ? 'graph-focused' : '',
  ].filter(Boolean).join(' ');

  return (
    <div className={wsCls}>
      <div className="page-header rw-header">
        <div className="rw-title-block">
          <Button
            type="text"
            size="small"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(`/run-console?runId=${runId}`)}
          />
          <Title level={4} style={{ margin: 0 }}>Debug Run</Title>
          {run && <StatusTag status={run.status} />}
        </div>
        <Text type="secondary" className="rw-subtitle">
          <span className="mono">{shortId(runId, 12)}</span>
          {run?.flow_canonical_name && <> · {run.flow_canonical_name}</>}
        </Text>
        <Space className="rw-actions">
          <Button
            type="text"
            size="small"
            icon={graphFocused ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
            onClick={() => setGraphFocused((v) => !v)}
            title={graphFocused ? 'Show panels' : 'Focus graph'}
          />
          <Button size="small" icon={<ReloadOutlined />} onClick={() => refresh()} loading={loading} />
          {isActive && <Button size="small" danger onClick={cancelRun}>Stop</Button>}
        </Space>
      </div>

      {workspaceMode !== 'execute' && run?.current_gate && (
        <div className="rw-gate-banner">
          <WarningOutlined />
          <span>
            {workspaceMode === 'gate:input'
              ? `Input required — ${run.current_gate.node_id}`
              : `Review required — ${run.current_gate.node_id}`}
          </span>
          <Button type="link" size="small" onClick={handleGateDismiss}>Dismiss</Button>
        </div>
      )}

      <RunWorkspaceErrorBoundary>
        <div className="rw-body">
          {!graphFocused && (
            <div className="rw-timeline">
              <ExecutionTimeline
                nodeExecutions={nodeExecutions}
                selectedId={logNodeExecId}
                onSelect={handleNodeSelect}
                collapsed={timelineCollapsed}
              />
            </div>
          )}

          {workspaceMode !== 'gate:approval' && (
            <div className="rw-graph">
              <LiveGraph
                flowSnapshot={run?.flow_snapshot}
                nodeExecutions={nodeExecutions}
                activeNodeId={focusedNodeId}
                isActive={isActive}
                onNodeClick={handleNodeSelect}
              />
            </div>
          )}

          {!graphFocused && (
            <div className={`rw-detail ${workspaceMode === 'gate:approval' ? 'is-fullwidth' : ''}`}>
              {workspaceMode === 'execute' && (
                <LiveLogViewer runId={runId} nodeExecutionId={logNodeExecId} />
              )}
              {workspaceMode === 'gate:input' && run?.current_gate && (
                <GateInputPanel
                  gate={run.current_gate}
                  onSubmitted={handleGateAction}
                />
              )}
              {workspaceMode === 'gate:approval' && run?.current_gate && (
                <GateReviewPanel
                  runId={runId}
                  gate={run.current_gate}
                  gates={gates}
                  onDecision={handleGateAction}
                  onRefresh={refresh}
                />
              )}
            </div>
          )}
        </div>
      </RunWorkspaceErrorBoundary>
    </div>
  );
}
