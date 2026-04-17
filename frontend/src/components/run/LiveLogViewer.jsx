import { useEffect, useMemo, useRef, useState } from 'react';
import { Input, Select, Switch, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ThunderboltOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useNodeLog } from '../../hooks/useNodeLog.js';

function parseLogLine(line) {
  const tsMatch = line.match(/^(\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?)\s+/);
  const ts = tsMatch?.[1] ?? null;
  const rest = tsMatch ? line.slice(tsMatch[0].length) : line;

  if (/^(ERROR|FATAL)/i.test(rest)) return { ts, kind: 'error', text: rest };
  if (/^WARN/i.test(rest)) return { ts, kind: 'warn', text: rest };
  if (/tool_use:/i.test(rest)) return { ts, kind: 'tool', text: rest };
  if (/tool_result:/i.test(rest)) return { ts, kind: 'result', text: rest };
  return { ts, kind: 'plain', text: rest };
}

const KIND_ICON = {
  error: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
  warn: <WarningOutlined style={{ color: '#faad14' }} />,
  tool: <ThunderboltOutlined style={{ color: '#1677ff' }} />,
  result: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
};

const KIND_COLOR = {
  error: '#ff4d4f',
  warn: '#faad14',
  tool: '#1677ff',
  result: '#52c41a',
};

export function LiveLogViewer({ runId, nodeExecutionId }) {
  const { lines, running } = useNodeLog(runId, nodeExecutionId);
  const [filter, setFilter] = useState('all');
  const [search, setSearch] = useState('');
  const [autoScroll, setAutoScroll] = useState(true);
  const scrollRef = useRef(null);

  const parsed = useMemo(() => lines.map(parseLogLine), [lines]);
  const filtered = useMemo(() => {
    let result = parsed;
    if (filter !== 'all') result = result.filter((l) => l.kind === filter);
    if (search) {
      const lc = search.toLowerCase();
      result = result.filter((l) => l.text.toLowerCase().includes(lc));
    }
    return result;
  }, [parsed, filter, search]);

  useEffect(() => {
    if (autoScroll && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [filtered, autoScroll]);

  if (!nodeExecutionId) {
    return (
      <div className="llv-empty">
        <Typography.Text type="secondary">Select a node to view logs</Typography.Text>
      </div>
    );
  }

  return (
    <div className="llv-container">
      <div className="llv-toolbar">
        <Select
          size="small"
          value={filter}
          onChange={setFilter}
          options={[
            { value: 'all', label: 'All' },
            { value: 'error', label: 'Errors' },
            { value: 'tool', label: 'Tools' },
            { value: 'result', label: 'Results' },
          ]}
          style={{ width: 100 }}
        />
        <Input
          size="small"
          placeholder="Search..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          allowClear
          style={{ width: 180 }}
        />
        <Switch size="small" checked={autoScroll} onChange={setAutoScroll} />
        <span className="llv-follow-label">Follow</span>
      </div>
      <div className="llv-log" ref={scrollRef}>
        {filtered.length === 0 && (
          <div className="llv-empty-log">
            {running ? 'Waiting for output...' : 'Log is empty.'}
          </div>
        )}
        {filtered.map((line, i) => (
          <div key={i} className={`llv-line llv-kind-${line.kind}`} style={{ color: KIND_COLOR[line.kind] }}>
            {KIND_ICON[line.kind] && <span className="llv-icon">{KIND_ICON[line.kind]}</span>}
            {line.ts && <span className="llv-ts">{line.ts}</span>}
            <span className="llv-text">{line.text}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
