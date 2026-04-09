import { useEffect, useRef, useState } from 'react';
import { apiRequest } from '../api/request.js';

export function useNodeLog(runId, nodeExecutionId) {
  const [lines, setLines] = useState([]);
  const [running, setRunning] = useState(true);
  const offsetRef = useRef(0);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    setLines([]);
    offsetRef.current = 0;
    setRunning(true);

    if (!runId || !nodeExecutionId) return () => { mountedRef.current = false; };

    let cancelled = false;
    let timerId = null;

    const poll = async () => {
      if (cancelled) return;
      try {
        const data = await apiRequest(
          `/runs/${runId}/nodes/${nodeExecutionId}/log?offset=${offsetRef.current}`,
        );
        if (cancelled || !mountedRef.current) return;
        if (data.content) {
          setLines((prev) => [...prev, ...data.content.split('\n').filter(Boolean)]);
        }
        if (data.offset !== undefined) offsetRef.current = data.offset;
        if (data.running !== undefined) setRunning(data.running);
      } catch (_) {
        // ignore polling errors
      }
      if (!cancelled) {
        timerId = window.setTimeout(poll, running ? 1500 : 5000);
      }
    };
    poll();

    return () => {
      cancelled = true;
      mountedRef.current = false;
      if (timerId) window.clearTimeout(timerId);
    };
  }, [runId, nodeExecutionId]);

  return { lines, running };
}
