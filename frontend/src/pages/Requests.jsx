import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Input, Select, Space, Table, Typography, message } from 'antd';
import { useAuth } from '../auth/AuthContext.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;
const canModerateRequests = (role) => role === 'TECH_APPROVER' || role === 'ADMIN';

const formatTs = (value) => {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleString();
  } catch (_err) {
    return value;
  }
};

export default function Requests() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState(null);
  const [requests, setRequests] = useState([]);
  const [jobs, setJobs] = useState([]);
  const [rejectReason, setRejectReason] = useState({});

  const load = async () => {
    if (!canModerateRequests(user?.role)) {
      setRequests([]);
      setJobs([]);
      return;
    }
    setLoading(true);
    try {
      const query = statusFilter ? `?status=${encodeURIComponent(statusFilter)}` : '';
      const [reqData, jobsData] = await Promise.all([
        apiRequest(`/publications/requests${query}`),
        apiRequest('/publications/jobs'),
      ]);
      setRequests(Array.isArray(reqData) ? reqData : []);
      setJobs(Array.isArray(jobsData) ? jobsData : []);
    } catch (err) {
      message.error(err.message || 'Failed to load requests');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [user?.role, statusFilter]);

  const latestJobByRequestId = useMemo(() => {
    const map = new Map();
    for (const job of jobs) {
      if (!map.has(job.request_id)) {
        map.set(job.request_id, job);
      }
    }
    return map;
  }, [jobs]);

  const rows = useMemo(() => requests.map((request) => {
    const job = latestJobByRequestId.get(request.id);
    return {
      ...request,
      job_status: job?.status || null,
      job_step: job?.step || null,
      attempt_no: job?.attempt_no || null,
      branch_name: job?.branch_name || null,
      pr_url: job?.pr_url || null,
      pr_number: job?.pr_number || null,
      commit_sha: job?.commit_sha || null,
      job_error: job?.error || null,
      job_started_at: job?.started_at || null,
      job_finished_at: job?.finished_at || null,
    };
  }), [requests, latestJobByRequestId]);

  const approve = async (row) => {
    try {
      await apiRequest(`/publications/${row.entity_type}s/${row.entity_id}/versions/${row.version}/approve`, { method: 'POST' });
      message.success('Request approved');
      await load();
    } catch (err) {
      message.error(err.message || 'Failed to approve request');
    }
  };

  const reject = async (row) => {
    try {
      await apiRequest(`/publications/${row.entity_type}s/${row.entity_id}/versions/${row.version}/reject`, {
        method: 'POST',
        body: JSON.stringify({ reason: rejectReason[row.id] || 'Rejected by approver' }),
      });
      message.success('Request rejected');
      await load();
    } catch (err) {
      message.error(err.message || 'Failed to reject request');
    }
  };

  const retry = async (row) => {
    try {
      await apiRequest(`/publications/${row.entity_type}s/${row.entity_id}/versions/${row.version}/retry`, { method: 'POST' });
      message.success('Retry started');
      await load();
    } catch (err) {
      message.error(err.message || 'Failed to retry publication');
    }
  };

  if (!canModerateRequests(user?.role)) {
    return (
      <div className="cards-page">
        <div className="page-header">
          <Title level={3} style={{ margin: 0 }}>Requests</Title>
        </div>
        <Card>
          <Text type="secondary">Access denied. Approver role required.</Text>
        </Card>
      </div>
    );
  }

  const columns = [
    { title: 'Type', dataIndex: 'entity_type', key: 'entity_type', width: 90, fixed: 'left' },
    { title: 'Entity ID', dataIndex: 'entity_id', key: 'entity_id', width: 180, fixed: 'left' },
    { title: 'Version', dataIndex: 'version', key: 'version', width: 100, fixed: 'left' },
    { title: 'Author', dataIndex: 'author', key: 'author', width: 150 },
    { title: 'Status', dataIndex: 'status', key: 'status', width: 160 },
    { title: 'Mode', dataIndex: 'requested_mode', key: 'requested_mode', width: 100 },
    {
      title: 'Approvals',
      key: 'approvals',
      width: 120,
      render: (_, row) => `${row.approval_count}/${row.required_approvals}`,
    },
    { title: 'Job status', dataIndex: 'job_status', key: 'job_status', width: 120 },
    { title: 'Step', dataIndex: 'job_step', key: 'job_step', width: 140 },
    { title: 'Attempt', dataIndex: 'attempt_no', key: 'attempt_no', width: 90 },
    {
      title: 'PR',
      dataIndex: 'pr_url',
      key: 'pr_url',
      width: 90,
      render: (value, row) => (value
        ? <a href={value} target="_blank" rel="noreferrer">#{row.pr_number || 'open'}</a>
        : <Text type="secondary">—</Text>),
    },
    {
      title: 'Branch',
      dataIndex: 'branch_name',
      key: 'branch_name',
      width: 220,
      render: (value) => (value ? <span className="mono">{value}</span> : <Text type="secondary">—</Text>),
    },
    {
      title: 'Commit',
      dataIndex: 'commit_sha',
      key: 'commit_sha',
      width: 120,
      render: (value) => (value ? <span className="mono">{String(value).slice(0, 8)}</span> : <Text type="secondary">—</Text>),
    },
    {
      title: 'Last error',
      key: 'error',
      width: 260,
      render: (_, row) => {
        const text = row.last_error || row.job_error;
        return text ? <span title={text}>{String(text).slice(0, 120)}</span> : <Text type="secondary">—</Text>;
      },
    },
    {
      title: 'Updated',
      dataIndex: 'updated_at',
      key: 'updated_at',
      width: 180,
      render: (value) => <span className="mono">{formatTs(value)}</span>,
    },
    {
      title: 'Reject reason',
      key: 'reason',
      width: 220,
      render: (_, row) => (
        row.status === 'pending_approval' ? (
          <Input
            placeholder="Reason"
            value={rejectReason[row.id] || ''}
            onChange={(event) => setRejectReason((prev) => ({ ...prev, [row.id]: event.target.value }))}
          />
        ) : <Text type="secondary">—</Text>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 220,
      fixed: 'right',
      render: (_, row) => {
        if (row.status === 'pending_approval') {
          return (
            <Space>
              <Button size="small" onClick={() => reject(row)}>Reject</Button>
              <Button size="small" type="default" onClick={() => approve(row)}>Approve</Button>
            </Space>
          );
        }
        if (row.status === 'failed') {
          return <Button size="small" onClick={() => retry(row)}>Retry</Button>;
        }
        return <Text type="secondary">—</Text>;
      },
    },
  ];

  const statusOptions = [
    { value: 'pending_approval', label: 'pending_approval' },
    { value: 'approved', label: 'approved' },
    { value: 'publishing', label: 'publishing' },
    { value: 'published', label: 'published' },
    { value: 'failed', label: 'failed' },
    { value: 'rejected', label: 'rejected' },
  ];

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Requests</Title>
        <Space>
          <Select
            allowClear
            style={{ width: 220 }}
            value={statusFilter}
            onChange={(value) => setStatusFilter(value || null)}
            options={statusOptions}
            placeholder="Filter by status"
          />
          <Button onClick={load} loading={loading}>Refresh</Button>
        </Space>
      </div>
      <Card>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{ pageSize: 20 }}
          scroll={{ x: 2470 }}
        />
      </Card>
    </div>
  );
}
