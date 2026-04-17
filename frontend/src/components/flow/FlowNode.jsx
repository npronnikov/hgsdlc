import { Handle, Position } from 'reactflow';

export const NODE_KIND_META = {
  ai: { label: 'AI Executor', variant: 'executor ai' },
  command: { label: 'Shell Command', variant: 'executor command' },
  human_input: { label: 'Human Input Gate', variant: 'gate input' },
  human_approval: { label: 'Human Approval Gate', variant: 'gate approval' },
  terminal: { label: 'Stop Flow', variant: 'terminal' },
};

const RUN_STATUS_CLASS = {
  running: 'is-running',
  succeeded: 'is-succeeded',
  failed: 'is-failed',
  waiting_gate: 'is-waiting-gate',
};

export function FlowNode({ data, selected }) {
  const visuals = NODE_KIND_META[data.nodeKind] || {};
  const statusClass = RUN_STATUS_CLASS[data.runStatus] || '';
  return (
    <div className={`flow-node ${visuals.variant || ''} ${statusClass} ${selected ? 'is-selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="flow-node-header">
        <div>
          <div className="flow-node-title">{data.title}</div>
          <div className="flow-node-meta">{visuals.label || data.typeLabel}</div>
        </div>
        {data.isStart && <span className="flow-node-start">Start</span>}
        {data.runStatus === 'running' && (
          <span className="flow-node-run-badge is-running">running</span>
        )}
        {data.runStatus === 'waiting_gate' && (
          <span className="flow-node-run-badge is-waiting">waiting</span>
        )}
        {data.runStatus === 'failed' && (
          <span className="flow-node-run-badge is-failed">{data.errorCode || 'failed'}</span>
        )}
      </div>
      <div className="flow-node-id">{data.id}</div>
      {data.isTerminal && <span className="flow-node-stop">Stop</span>}
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

export const nodeTypes = { flowNode: FlowNode };
