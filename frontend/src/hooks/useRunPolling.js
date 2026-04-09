import { useCallback, useEffect, useRef, useState } from 'react';
import { apiRequest } from '../api/request.js';

const ACTIVE_RUN_STATUSES = ['created', 'running', 'waiting_gate', 'waiting_publish', 'publish_failed'];

export function useRunPolling(runId) {
  const [run, setRun] = useState(null);
  const [nodeExecutions, setNodeExecutions] = useState([]);
  const [gates, setGates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const mountedRef = useRef(true);

  const isActive = ACTIVE_RUN_STATUSES.includes(run?.status);

  const load = useCallback(async ({ silent = false } = {}) => {
    if (!runId) return;
    if (!silent) setLoading(true);
    try {
      const [runData, nodesData, gatesData] = await Promise.all([
        apiRequest(`/runs/${runId}`),
        apiRequest(`/runs/${runId}/nodes`),
        apiRequest(`/runs/${runId}/gates`),
      ]);
      if (!mountedRef.current) return;
      setRun(runData);
      setNodeExecutions(nodesData || []);
      setGates(gatesData || []);
      setError(null);
    } catch (err) {
      if (!mountedRef.current) return;
      setError(err);
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, [runId]);

  useEffect(() => {
    mountedRef.current = true;
    load();
    return () => { mountedRef.current = false; };
  }, [load]);

  useEffect(() => {
    if (!runId || !run) return undefined;
    const interval = isActive ? 2000 : 10000;
    const timerId = window.setInterval(() => load({ silent: true }), interval);
    return () => window.clearInterval(timerId);
  }, [runId, run?.status, isActive, load]);

  return { run, nodeExecutions, gates, isActive, loading, error, refresh: load };
}
