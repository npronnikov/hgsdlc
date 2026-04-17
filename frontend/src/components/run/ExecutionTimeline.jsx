import React, { useEffect, useMemo, useState } from 'react';
import { Tooltip, Tag } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  MinusCircleOutlined,
  PauseCircleOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { NODE_KIND_META } from '../../utils/flowLayout.js';

function ElapsedTimer({ startedAt }) {
  const startMs = useMemo(() => new Date(startedAt).getTime(), [startedAt]);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    let handle;
    const tick = () => { setNow(Date.now()); handle = requestAnimationFrame(tick); };
    handle = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(handle);
  }, []);

  const elapsed = Math.floor((now - startMs) / 1000);
  const m = Math.floor(elapsed / 60);
  const s = elapsed % 60;
  return <span className="etl-elapsed-timer">{m > 0 ? `${m}m ` : ''}{s}s</span>;
}

function formatDuration(startedAt, finishedAt) {
  if (!startedAt || !finishedAt) return null;
  const ms = new Date(finishedAt).getTime() - new Date(startedAt).getTime();
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}m ${s}s`;
}

function nodeTimelineIcon(status) {
  switch (status) {
    case 'running': return <LoadingOutlined spin style={{ color: '#1677ff' }} />;
    case 'succeeded': return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
    case 'failed': return <CloseCircleOutlined style={{ color: '#ff4d4f' }} />;
    case 'cancelled': return <MinusCircleOutlined style={{ color: '#999' }} />;
    case 'waiting_gate': return <PauseCircleOutlined style={{ color: '#faad14' }} />;
    default: return <ClockCircleOutlined style={{ color: '#999' }} />;
  }
}

function TimelineItem({ item, isSelected, onSelect }) {
  const running = item.status === 'running';
  const done = ['succeeded', 'failed', 'cancelled'].includes(item.status);
  const duration = done ? formatDuration(item.started_at, item.finished_at) : null;

  return (
    <div
      className={`etl-item status-${item.status} ${isSelected ? 'is-selected' : ''}`}
      onClick={() => onSelect(item.node_execution_id)}
    >
      <div className="etl-dot">{nodeTimelineIcon(item.status)}</div>
      <div className="etl-body">
        <div className="etl-name">{item.node_id}</div>
        <div className="etl-meta">
          <span className="etl-kind">{NODE_KIND_META[item.node_kind]?.label || item.node_kind}</span>
          {item.attempt_no > 1 && <span className="etl-attempt">↩ #{item.attempt_no}</span>}
          {item.checkpoint_commit_sha && (
            <Tooltip title={`Checkpoint: ${item.checkpoint_commit_sha.slice(0, 8)}`}>
              <SaveOutlined className="etl-checkpoint" />
            </Tooltip>
          )}
        </div>
        <div className="etl-time">
          {running && item.started_at && <ElapsedTimer startedAt={item.started_at} />}
          {duration && <span className="etl-duration">{duration}</span>}
        </div>
        {item.error_code && (
          <Tooltip title={item.error_message}>
            <Tag color="error" className="etl-error-tag">{item.error_code}</Tag>
          </Tooltip>
        )}
      </div>
    </div>
  );
}

export function ExecutionTimeline({ nodeExecutions = [], selectedId, onSelect, collapsed = false }) {
  const grouped = useMemo(() => {
    const groups = [];
    let currentGroup = null;
    for (const exec of nodeExecutions) {
      if (!currentGroup || currentGroup.nodeId !== exec.node_id) {
        currentGroup = { nodeId: exec.node_id, items: [] };
        groups.push(currentGroup);
      }
      currentGroup.items.push(exec);
    }
    return groups;
  }, [nodeExecutions]);

  if (collapsed) {
    return (
      <div className="etl-collapsed">
        {nodeExecutions.map((item) => (
          <div
            key={item.node_execution_id}
            className="etl-dot-only"
            title={item.node_id}
            onClick={() => onSelect(item.node_execution_id)}
          >
            {nodeTimelineIcon(item.status)}
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="etl-timeline">
      {grouped.map((group) => {
        const latest = group.items.at(-1);
        const hasPrevious = group.items.length > 1;
        return (
          <div key={`${group.nodeId}-${latest.node_execution_id}`} className="etl-group">
            <TimelineItem
              item={latest}
              isSelected={selectedId === latest.node_execution_id}
              onSelect={onSelect}
            />
            {hasPrevious && (
              <details className="etl-previous">
                <summary className="etl-previous-toggle">
                  Show {group.items.length - 1} previous
                </summary>
                {group.items.slice(0, -1).map((item) => (
                  <TimelineItem
                    key={item.node_execution_id}
                    item={item}
                    isSelected={selectedId === item.node_execution_id}
                    onSelect={onSelect}
                  />
                ))}
              </details>
            )}
          </div>
        );
      })}
    </div>
  );
}
