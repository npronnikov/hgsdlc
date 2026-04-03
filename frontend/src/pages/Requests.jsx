import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Input, Modal, Select, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import { CheckOutlined, CloseOutlined, RedoOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import StatusTag from '../components/StatusTag.jsx';
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

const statusOptions = [
  { value: 'pending_approval', label: 'Pending approval' },
  { value: 'approved', label: 'Approved' },
  { value: 'publishing', label: 'Publishing' },
  { value: 'published', label: 'Published' },
  { value: 'failed', label: 'Failed' },
  { value: 'rejected', label: 'Rejected' },
];

const normalizeSearch = (value) => String(value || '').trim().toLowerCase();

export default function Requests() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState('pending_approval');
  const [searchText, setSearchText] = useState('');
  const [requests, setRequests] = useState([]);
  const [jobs, setJobs] = useState([]);
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectTarget, setRejectTarget] = useState(null);
  const [rejectReason, setRejectReason] = useState('');

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

  const mergedRows = useMemo(() => requests.map((request) => {
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

  const rows = useMemo(() => {
    const query = normalizeSearch(searchText);
    if (!query) return mergedRows;
    return mergedRows.filter((row) => {
      const haystack = [
        row.entity_type,
        row.entity_id,
        row.version,
        row.canonical_name,
        row.author,
        row.status,
        row.job_status,
        row.branch_name,
        row.commit_sha,
      ].join(' ').toLowerCase();
      return haystack.includes(query);
    });
  }, [mergedRows, searchText]);

  const stats = useMemo(() => {
    const byStatus = rows.reduce((acc, row) => {
      const key = row.status || 'unknown';
      acc[key] = (acc[key] || 0) + 1;
      return acc;
    }, {});
    return {
      total: rows.length,
      pendingApproval: byStatus.pending_approval || 0,
      publishing: byStatus.publishing || 0,
      failed: byStatus.failed || 0,
      published: byStatus.published || 0,
    };
  }, [rows]);

  const approve = async (row) => {
    try {
      await apiRequest(`/publications/${row.entity_type}s/${row.entity_id}/versions/${row.version}/approve`, { method: 'POST' });
      message.success('Request approved');
      await load();
    } catch (err) {
      message.error(err.message || 'Failed to approve request');
    }
  };

  const reject = async (row, reason) => {
    if (!row) {
      return;
    }
    try {
      await apiRequest(`/publications/${row.entity_type}s/${row.entity_id}/versions/${row.version}/reject`, {
        method: 'POST',
        body: JSON.stringify({ reason: reason || 'Rejected by approver' }),
      });
      message.success('Request rejected');
      setRejectModalOpen(false);
      setRejectTarget(null);
      setRejectReason('');
      await load();
    } catch (err) {
      message.error(err.message || 'Failed to reject request');
    }
  };

  const openRejectDialog = (row) => {
    setRejectTarget(row);
    setRejectReason('');
    setRejectModalOpen(true);
  };

  const closeRejectDialog = () => {
    setRejectModalOpen(false);
    setRejectTarget(null);
    setRejectReason('');
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
      <div className="cards-page requests-page">
        <div className="page-header">
          <Title level={3} style={{ margin: 0 }}>Requests</Title>
        </div>
        <Alert
          type="warning"
          showIcon
          message="Access denied"
          description="Approver role is required to review publication requests."
        />
      </div>
    );
  }

  const columns = [
    {
      title: 'Request',
      key: 'request',
      render: (_, row) => (
        <div className="requests-entity-cell">
          <div className="requests-entity-main">{row.canonical_name || `${row.entity_id}@${row.version}`}</div>
          <div className="requests-entity-meta">
            <Tag className="requests-type-tag">{String(row.entity_type || 'entity').toUpperCase()}</Tag>
            <span className="mono">v{row.version}</span>
            <span className="mono">by {row.author || 'unknown'}</span>
          </div>
        </div>
      ),
    },
    {
      title: 'Publication',
      key: 'status',
      width: 180,
      render: (_, row) => <StatusTag value={row.status || 'draft'} />,
    },
    {
      title: 'Approvals',
      key: 'approvals',
      width: 130,
      render: (_, row) => (
        <div className="requests-approval-pill">
          <span>{row.approval_count}</span>
          <span className="requests-divider">/</span>
          <span>{row.required_approvals}</span>
        </div>
      ),
    },
    {
      title: 'Updated',
      dataIndex: 'updated_at',
      key: 'updated_at',
      width: 190,
      render: (value) => <span className="mono">{formatTs(value)}</span>,
    },
    {
      title: 'Action',
      key: 'action',
      width: 220,
      render: (_, row) => {
        if (row.status === 'pending_approval') {
          return (
            <Space>
              <Button size="small" type="default" danger onClick={() => openRejectDialog(row)} icon={<CloseOutlined />}>Reject</Button>
              <Button size="small" type="default" onClick={() => approve(row)} icon={<CheckOutlined />}>Approve</Button>
            </Space>
          );
        }
        if (row.status === 'failed') {
          return <Button size="small" onClick={() => retry(row)} icon={<RedoOutlined />}>Retry</Button>;
        }
        return <Text type="secondary">—</Text>;
      },
    },
  ];

  return (
    <div className="cards-page requests-page">
      <div className="page-header requests-header">
        <div>
          <Title level={3} style={{ margin: 0 }}>Publication Requests</Title>
          <Text type="secondary">Review, approve, reject, and monitor publication pipeline state.</Text>
        </div>
        <Space wrap>
          <Input
            allowClear
            value={searchText}
            onChange={(event) => setSearchText(event.target.value)}
            placeholder="Search id, canonical, author, branch"
            prefix={<SearchOutlined />}
            style={{ width: 280 }}
          />
          <Select
            allowClear
            style={{ width: 220 }}
            value={statusFilter}
            onChange={(value) => setStatusFilter(value || null)}
            options={statusOptions}
            placeholder="Filter by status"
            optionLabelProp="label"
            optionFilterProp="label"
          />
          <Button onClick={load} loading={loading} icon={<ReloadOutlined />}>Refresh</Button>
        </Space>
      </div>

      <div className="requests-summary-grid">
        <Card className="requests-summary-card">
          <div className="card-label">Total</div>
          <div className="requests-summary-value">{stats.total}</div>
        </Card>
        <Card className="requests-summary-card requests-summary-card-pending">
          <div className="card-label">Need Approval</div>
          <div className="requests-summary-value">{stats.pendingApproval}</div>
        </Card>
        <Card className="requests-summary-card requests-summary-card-progress">
          <div className="card-label">Publishing</div>
          <div className="requests-summary-value">{stats.publishing}</div>
        </Card>
        <Card className="requests-summary-card requests-summary-card-failed">
          <div className="card-label">Failed</div>
          <div className="requests-summary-value">{stats.failed}</div>
        </Card>
        <Card className="requests-summary-card requests-summary-card-success">
          <div className="card-label">Published</div>
          <div className="requests-summary-value">{stats.published}</div>
        </Card>
      </div>

      <Card className="requests-table-card">
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{ pageSize: 15, showSizeChanger: false }}
          rowClassName={(row) => `requests-row requests-row-${row.status || 'unknown'}`}
          expandable={{
            expandedRowRender: (row) => {
              const errorText = row.last_error || row.job_error;
              const hasPrData = Boolean(row.pr_url || row.pr_number || row.branch_name);
              return (
                <div className="requests-expanded">
                  <div className="requests-expanded-grid">
                    <div className="requests-expanded-item">
                      <span className="requests-expanded-label">Pipeline status</span>
                      <span>{row.job_status ? <StatusTag value={row.job_status} /> : <Text type="secondary">No job yet</Text>}</span>
                    </div>
                    <div className="requests-expanded-item">
                      <span className="requests-expanded-label">Pipeline step</span>
                      <span className="mono">{row.job_step || '—'}</span>
                    </div>
                    <div className="requests-expanded-item">
                      <span className="requests-expanded-label">Attempt</span>
                      <span className="mono">{row.attempt_no || 0}</span>
                    </div>
                    <div className="requests-expanded-item">
                      <span className="requests-expanded-label">Commit</span>
                      <span className="mono">{row.commit_sha || '—'}</span>
                    </div>
                    {hasPrData && (
                      <div className="requests-expanded-item">
                        <span className="requests-expanded-label">Branch</span>
                        <span className="mono" title={row.branch_name || ''}>{row.branch_name || '—'}</span>
                      </div>
                    )}
                    {hasPrData && (
                      <div className="requests-expanded-item">
                        <span className="requests-expanded-label">Pull request</span>
                        <span>
                          {row.pr_url ? (
                            <a href={row.pr_url} target="_blank" rel="noreferrer">PR #{row.pr_number || 'open'}</a>
                          ) : (
                            <Text type="secondary">No PR</Text>
                          )}
                        </span>
                      </div>
                    )}
                    <div className="requests-expanded-item">
                      <span className="requests-expanded-label">Created at</span>
                      <span className="mono">{formatTs(row.created_at)}</span>
                    </div>
                    <div className="requests-expanded-item">
                      <span className="requests-expanded-label">Updated at</span>
                      <span className="mono">{formatTs(row.updated_at)}</span>
                    </div>
                  </div>

                  {errorText && (
                    <div className="requests-expanded-error">
                      <span className="requests-expanded-label">Last error</span>
                      <Tooltip title={errorText}>
                        <div className="requests-error-text">{String(errorText)}</div>
                      </Tooltip>
                    </div>
                  )}

                </div>
              );
            },
          }}
        />
      </Card>
      <Modal
        title="Reject publication request"
        open={rejectModalOpen}
        onCancel={closeRejectDialog}
        onOk={() => reject(rejectTarget, rejectReason.trim())}
        okText="Reject"
        cancelText="Cancel"
        okButtonProps={{ danger: true, disabled: !rejectTarget }}
      >
        <div className="requests-expanded-reason">
          <span className="requests-expanded-label">Reason</span>
          <Input.TextArea
            autoFocus
            rows={4}
            placeholder="Explain why this publication request is rejected"
            value={rejectReason}
            onChange={(event) => setRejectReason(event.target.value)}
          />
        </div>
      </Modal>
    </div>
  );
}
