import React from 'react';
import { Tag } from 'antd';

const colorMap = {
  published: '#16a34a',
  draft: '#94a3b8',
  active: '#16a34a',
  archived: '#64748b',
  waiting_gate: '#d97706',
  failed: '#dc2626',
  completed: '#16a34a',
  cancelled: '#64748b',
  awaiting_input: '#d97706',
  awaiting_decision: '#d97706',
  approved: '#16a34a',
  rejected: '#dc2626',
  rework_requested: '#d97706',
  AI: '#4f46e5',
  human_input: '#d97706',
  human_approval: '#0891b2',
  runtime: '#0891b2',
};

export default function StatusTag({ value }) {
  const color = colorMap[value] || '#64748b';
  return (
    <Tag color={color}>
      {value}
    </Tag>
  );
}
