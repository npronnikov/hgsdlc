import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Select, Space, Tag, Typography } from 'antd';
import { apiRequest } from '../../api/request.js';

const { Text } = Typography;

const EVENT_TYPE_OPTIONS = [
  { value: 'node_execution_started', label: 'node_started' },
  { value: 'node_execution_succeeded', label: 'node_succeeded' },
  { value: 'node_execution_failed', label: 'node_failed' },
  { value: 'gate_opened', label: 'gate_opened' },
  { value: 'gate_approved', label: 'gate_approved' },
  { value: 'gate_rework_requested', label: 'gate_rework' },
  { value: 'gate_input_submitted', label: 'gate_input' },
  { value: 'agent_invocation_started', label: 'agent_started' },
  { value: 'agent_invocation_finished', label: 'agent_finished' },
  { value: 'checkpoint_created', label: 'checkpoint' },
];

const ACTOR_TAG_COLOR = {
  system: 'default',
  human: 'blue',
  agent: 'purple',
};

function formatTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function AuditStream({ runId, nodeExecutionId }) {
  const [events, setEvents] = useState([]);
  const [filterEventType, setFilterEventType] = useState(undefined);
  const [nextCursor, setNextCursor] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const listRef = useRef(null);

  const fetchEvents = useCallback(async (cursor) => {
    if (!runId) return;
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (nodeExecutionId) params.set('nodeExecutionId', nodeExecutionId);
      if (filterEventType) params.set('eventType', filterEventType);
      if (cursor) params.set('cursor', String(cursor));
      params.set('limit', '30');
      const data = await apiRequest(`/runs/${runId}/audit/query?${params}`);
      if (cursor) {
        setEvents((prev) => [...prev, ...(data.events || [])]);
      } else {
        setEvents(data.events || []);
      }
      setNextCursor(data.next_cursor ?? null);
      setHasMore(data.has_more ?? false);
    } catch {
      /* silently ignore in stream */
    } finally {
      setLoading(false);
    }
  }, [runId, nodeExecutionId, filterEventType]);

  useEffect(() => {
    fetchEvents(null);
  }, [fetchEvents]);

  useEffect(() => {
    const id = setInterval(() => fetchEvents(null), 5000);
    return () => clearInterval(id);
  }, [fetchEvents]);

  return (
    <div className="audit-stream">
      <div className="audit-stream-toolbar">
        <Text strong style={{ fontSize: 12 }}>Events</Text>
        <Select
          allowClear
          size="small"
          placeholder="Filter"
          value={filterEventType}
          onChange={setFilterEventType}
          options={EVENT_TYPE_OPTIONS}
          style={{ minWidth: 140, flex: 1 }}
          popupMatchSelectWidth={false}
        />
      </div>
      <div className="audit-stream-list" ref={listRef}>
        {events.length === 0 && !loading && (
          <div className="audit-stream-empty">No events</div>
        )}
        {events.map((ev) => (
          <div key={ev.event_id} className="audit-stream-item">
            <span className="audit-stream-time">{formatTime(ev.event_time)}</span>
            <Tag color={ACTOR_TAG_COLOR[ev.actor_type] || 'default'} className="audit-stream-actor">
              {ev.actor_type}
            </Tag>
            <span className="audit-stream-type">{ev.event_type}</span>
          </div>
        ))}
        {hasMore && (
          <Button
            size="small"
            type="link"
            loading={loading}
            onClick={() => fetchEvents(nextCursor)}
            className="audit-stream-more"
          >
            Load more
          </Button>
        )}
      </div>
    </div>
  );
}
