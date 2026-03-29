import React from 'react';
import { Tag } from 'antd';

const colorMap = {
  published: '#16a34a',
  draft: '#94a3b8',
  active: '#16a34a',
  archived: '#64748b',
  waiting_gate: '#d97706',
  waiting_publish: '#0ea5e9',
  publish_failed: '#dc2626',
  created: '#2563eb',
  running: '#2563eb',
  failed: '#dc2626',
  completed: '#16a34a',
  cancelled: '#64748b',
  local: '#0891b2',
  pr: '#4f46e5',
  pending: '#d97706',
  succeeded: '#16a34a',
  skipped: '#64748b',
  awaiting_input: '#d97706',
  submitted: '#16a34a',
  awaiting_decision: '#d97706',
  approved: '#16a34a',
  rejected: '#dc2626',
  rework_requested: '#d97706',
  failed_validation: '#dc2626',
  AI: '#4f46e5',
  human_input: '#d97706',
  human_approval: '#0891b2',
  runtime: '#0891b2',
  system: '#64748b',
  human: '#0891b2',
  agent: '#4f46e5',
  produced: '#16a34a',
  mutation: '#d97706',
};

export function formatStatusLabel(value) {
  if (!value) {
    return 'Unknown';
  }
  return String(value)
    .replace(/_/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (match) => match.toUpperCase());
}

export default function StatusTag({ value }) {
  const color = colorMap[value] || '#64748b';
  return (
    <Tag color={color}>
      {formatStatusLabel(value)}
    </Tag>
  );
}
