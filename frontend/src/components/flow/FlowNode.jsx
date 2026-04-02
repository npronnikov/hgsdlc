import { Handle, Position } from 'reactflow';

export const NODE_KIND_META = {
  ai: {
    label: 'AI Executor',
    variant: 'executor ai',
  },
  command: {
    label: 'Command Executor',
    variant: 'executor command',
  },
  human_input: {
    label: 'Human Input Gate',
    variant: 'gate input',
  },
  human_approval: {
    label: 'Human Approval Gate',
    variant: 'gate approval',
  },
  terminal: {
    label: 'Terminal',
    variant: 'terminal',
  },
};

export function FlowNode({ data, selected }) {
  const visuals = NODE_KIND_META[data.nodeKind] || {};
  return (
    <div className={`flow-node ${visuals.variant || data.variant || ''} ${selected ? 'is-selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="flow-node-header">
        <div>
          <div className="flow-node-title">{data.title}</div>
          <div className="flow-node-meta">{visuals.label || data.typeLabel}</div>
        </div>
        {data.isStart && <span className="flow-node-start">Start</span>}
      </div>
      <div className="flow-node-id">{data.id}</div>
      {data.isTerminal && <span className="flow-node-stop">Stop</span>}
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

export const nodeTypes = { flowNode: FlowNode };
