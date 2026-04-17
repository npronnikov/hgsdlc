import React, { useMemo } from 'react';

export function StageTracker({ gates = [], currentGateId }) {
  const stages = useMemo(() => {
    const approvalGates = gates.filter((g) => g.gate_kind === 'human_approval');
    const byNodeId = new Map();
    for (const g of approvalGates) {
      byNodeId.set(g.node_id, g);
    }
    return Array.from(byNodeId.values());
  }, [gates]);

  if (stages.length === 0) return null;

  return (
    <div className="stage-tracker">
      {stages.map((stage, idx) => {
        const isCurrent = stage.gate_id === currentGateId;
        const isDone = stage.status === 'approved' || stage.status === 'rework_requested';
        return (
          <React.Fragment key={stage.node_id}>
            <div className={`stage-item ${isCurrent ? 'is-current' : ''} ${isDone ? 'is-done' : ''}`}>
              <div className="stage-num">{idx + 1}</div>
              <div className="stage-label">{stage.node_id}</div>
              <div className="stage-decision">
                {stage.status === 'approved' && <span className="sd-approved">Approved</span>}
                {stage.status === 'rework_requested' && <span className="sd-rework">↩ Rework</span>}
                {isCurrent && !isDone && <span className="sd-current">This Review</span>}
                {!isCurrent && !isDone && <span className="sd-pending">Pending</span>}
              </div>
            </div>
            {idx < stages.length - 1 && <div className="stage-connector" />}
          </React.Fragment>
        );
      })}
    </div>
  );
}
