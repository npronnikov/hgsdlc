import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Drawer, Input, Select, Space, Typography, message } from 'antd';
import { FilterOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { formatStatusLabel } from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;
const PAGE_LIMIT = 24;

const truncateCardName = (value, max = 26) => {
  if (!value) return '';
  return value.length > max ? `${value.slice(0, max)}...` : value;
};

export default function Flows() {
  const defaultFilters = {
    search: '',
    codingAgent: null,
    teamCode: null,
    scope: null,
    platformCode: null,
    flowKind: null,
    riskLevel: null,
    lifecycleStatus: null,
    tag: null,
    status: null,
    version: '',
    hasDescription: null,
  };
  const navigate = useNavigate();
  const [flows, setFlows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [nextCursor, setNextCursor] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [filters, setFilters] = useState(defaultFilters);

  const loadFlows = async ({ cursor = null, append = false } = {}) => {
    if (append) {
      setLoadingMore(true);
    } else {
      setLoading(true);
    }
    try {
      const params = new URLSearchParams();
      params.set('limit', String(PAGE_LIMIT));
      if (cursor) params.set('cursor', cursor);
      if (filters.search.trim()) params.set('search', filters.search.trim());
      if (filters.codingAgent) params.set('codingAgent', filters.codingAgent);
      if (filters.teamCode) params.set('teamCode', filters.teamCode);
      if (filters.scope) params.set('scope', filters.scope);
      if (filters.platformCode) params.set('platformCode', filters.platformCode);
      if (filters.flowKind) params.set('flowKind', filters.flowKind);
      if (filters.riskLevel) params.set('riskLevel', filters.riskLevel);
      if (filters.lifecycleStatus) params.set('lifecycleStatus', filters.lifecycleStatus);
      if (filters.tag) params.set('tag', filters.tag);
      if (filters.status) params.set('status', filters.status);
      if (filters.version.trim()) params.set('version', filters.version.trim());
      if (filters.hasDescription !== null) params.set('hasDescription', String(filters.hasDescription));

      const data = await apiRequest(`/flows/query?${params.toString()}`);
      const mapped = (data.items || []).map((flow) => ({
        key: flow.flow_id,
        name: flow.title || flow.flow_id,
        flowId: flow.flow_id,
        description: flow.description || '',
        codingAgent: flow.coding_agent,
        teamCode: flow.team_code,
        scope: flow.scope,
        platformCode: flow.platform_code,
        tags: flow.tags || [],
        flowKind: flow.flow_kind,
        riskLevel: flow.risk_level,
        lifecycleStatus: flow.lifecycle_status,
        nodeCount: flow.node_count,
        status: flow.status,
        version: flow.version,
        canonical: flow.canonical_name,
      }));
      if (append) {
        setFlows((prev) => [...prev, ...mapped]);
      } else {
        setFlows(mapped);
      }
      setNextCursor(data.next_cursor || null);
      setHasMore(Boolean(data.has_more));
    } catch (err) {
      message.error(err.message || 'Failed to load Flows');
    } finally {
      if (append) {
        setLoadingMore(false);
      } else {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    const handle = setTimeout(() => {
      loadFlows({ cursor: null, append: false });
    }, 250);
    return () => clearTimeout(handle);
  }, [
    filters.search,
    filters.codingAgent,
    filters.teamCode,
    filters.scope,
    filters.platformCode,
    filters.flowKind,
    filters.riskLevel,
    filters.lifecycleStatus,
    filters.tag,
    filters.status,
    filters.version,
    filters.hasDescription,
  ]);

  const codingAgents = useMemo(() => Array.from(new Set(flows.map((flow) => flow.codingAgent).filter(Boolean))), [flows]);
  const statuses = useMemo(() => Array.from(new Set(flows.map((flow) => flow.status).filter(Boolean))), [flows]);
  const teamCodes = useMemo(() => Array.from(new Set(flows.map((flow) => flow.teamCode).filter(Boolean))), [flows]);
  const scopes = useMemo(() => Array.from(new Set(flows.map((flow) => flow.scope).filter(Boolean))), [flows]);
  const platformCodes = useMemo(() => Array.from(new Set(flows.map((flow) => flow.platformCode).filter(Boolean))), [flows]);
  const flowKinds = useMemo(() => Array.from(new Set(flows.map((flow) => flow.flowKind).filter(Boolean))), [flows]);
  const riskLevels = useMemo(() => Array.from(new Set(flows.map((flow) => flow.riskLevel).filter(Boolean))), [flows]);
  const lifecycleStatuses = useMemo(() => Array.from(new Set(flows.map((flow) => flow.lifecycleStatus).filter(Boolean))), [flows]);
  const tags = useMemo(() => Array.from(new Set(flows.flatMap((flow) => flow.tags || []).filter(Boolean))), [flows]);
  const activeFilters = useMemo(() => {
    const items = [];
    if (filters.search.trim()) items.push({ key: 'search', label: `Search: ${filters.search.trim()}` });
    if (filters.codingAgent) items.push({ key: 'codingAgent', label: `Agent: ${filters.codingAgent}` });
    if (filters.status) items.push({ key: 'status', label: `Status: ${filters.status}` });
    if (filters.teamCode) items.push({ key: 'teamCode', label: `Team: ${filters.teamCode}` });
    if (filters.scope) items.push({ key: 'scope', label: `Scope: ${filters.scope}` });
    if (filters.platformCode) items.push({ key: 'platformCode', label: `Platform: ${filters.platformCode}` });
    if (filters.flowKind) items.push({ key: 'flowKind', label: `Type: ${filters.flowKind}` });
    if (filters.riskLevel) items.push({ key: 'riskLevel', label: `Risk: ${filters.riskLevel}` });
    if (filters.lifecycleStatus) items.push({ key: 'lifecycleStatus', label: `Lifecycle: ${filters.lifecycleStatus}` });
    if (filters.tag) items.push({ key: 'tag', label: `Tag: ${filters.tag}` });
    if (filters.version.trim()) items.push({ key: 'version', label: `Version: ${filters.version.trim()}` });
    if (filters.hasDescription === true) items.push({ key: 'hasDescription', label: 'Description: yes' });
    if (filters.hasDescription === false) items.push({ key: 'hasDescription', label: 'Description: no' });
    return items;
  }, [filters]);

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Flows</Title>
        <Space>
          <Button type="default" icon={<PlusOutlined />} onClick={() => navigate('/flows/create')}>New Flow</Button>
          <Button type="default" icon={<FilterOutlined />} onClick={() => setIsFilterOpen(true)}>Filter</Button>
        </Space>
      </div>
      {activeFilters.length > 0 && (
        <div className="active-filters-row">
          {activeFilters.map((item) => (
            <button
              key={item.key}
              type="button"
              className="active-filter-chip"
              onClick={() => setFilters((prev) => ({ ...prev, [item.key]: defaultFilters[item.key] }))}
              title="Clear filter"
            >
              <span>{item.label}</span>
              <span className="active-filter-chip-close">x</span>
            </button>
          ))}
          <Button size="small" type="default" onClick={() => setFilters(defaultFilters)}>
            Clear all
          </Button>
        </div>
      )}
      <div className="cards-fullscreen">
        {loading ? (
          <div className="card-muted">Loading...</div>
        ) : (
          <div className="cards-grid">
            {flows.map((flow) => (
              <Card
                key={flow.key}
                className={`resource-card flow-card status-${(flow.status || 'unknown').toLowerCase()}`}
                hoverable
                onClick={() => navigate(`/flows/${flow.flowId}`)}
              >
                <div className="resource-card-header minimal-card-header">
                  <div className="resource-card-title minimal-card-title">
                    <span className="resource-card-name" title={flow.name}>{truncateCardName(flow.name)}</span>
                    <span className="resource-card-subtitle mono">{flow.flowId}@{flow.version}</span>
                  </div>
                  <span className="minimal-card-status">{formatStatusLabel(flow.status || 'unknown')}</span>
                </div>
                {flow.description && <Text type="secondary" className="resource-card-description">{flow.description}</Text>}
                <div className="minimal-card-meta-line">
                  <span>{flow.flowKind || 'type: —'}</span>
                  <span>{flow.riskLevel || 'risk: —'}</span>
                  <span>{flow.scope || 'scope: —'}</span>
                  <span>{flow.platformCode || 'platform: —'}</span>
                  <span>nodes: {flow.nodeCount ?? '—'}</span>
                </div>
                {(flow.tags || []).length > 0 && (
                  <div className="resource-tags-row minimal-card-tags-row">
                    {(flow.tags || []).slice(0, 5).map((tag) => (
                      <span key={`${flow.key}-${tag}`} className="resource-tag minimal-card-tag">#{tag}</span>
                    ))}
                  </div>
                )}
                <div className="resource-card-footer minimal-card-footer">
                  <span className="resource-chip minimal-card-chip">{flow.codingAgent || 'no agent'}</span>
                  <span className="minimal-card-footer-item">TEAM: {flow.teamCode || 'no team'}</span>
                </div>
              </Card>
            ))}
            {flows.length === 0 && <div className="card-muted">Flows not found.</div>}
          </div>
        )}
        {!loading && flows.length > 0 && (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
            <Button
              type="default"
              onClick={() => loadFlows({ cursor: nextCursor, append: true })}
              loading={loadingMore}
              disabled={!hasMore || loadingMore}
            >
              {hasMore ? 'Load more' : 'No more flows'}
            </Button>
          </div>
        )}
      </div>

      <Drawer
        title="Flow filter"
        placement="right"
        open={isFilterOpen}
        onClose={() => setIsFilterOpen(false)}
        width={360}
        className="filter-drawer"
      >
        <div className="filter-drawer-body filter-grid">
          <div className="filter-row filter-row-span-2">
            <Text className="muted">Search</Text>
            <Input
              value={filters.search}
              onChange={(event) => setFilters((prev) => ({ ...prev, search: event.target.value }))}
              placeholder="Name, ID, description"
            />
          </div>
          <div className="filter-row"><Text className="muted">Coding Agent</Text><Select allowClear value={filters.codingAgent} onChange={(value) => setFilters((prev) => ({ ...prev, codingAgent: value || null }))} options={codingAgents.map((value) => ({ value, label: value }))} placeholder="Select agent" /></div>
          <div className="filter-row"><Text className="muted">Status</Text><Select allowClear value={filters.status} onChange={(value) => setFilters((prev) => ({ ...prev, status: value || null }))} options={statuses.map((status) => ({ value: status, label: formatStatusLabel(status) }))} placeholder="Select status" /></div>
          <div className="filter-row"><Text className="muted">Team</Text><Select allowClear value={filters.teamCode} onChange={(value) => setFilters((prev) => ({ ...prev, teamCode: value || null }))} options={teamCodes.map((value) => ({ value, label: value }))} placeholder="Select team" /></div>
          <div className="filter-row"><Text className="muted">Scope</Text><Select allowClear value={filters.scope} onChange={(value) => setFilters((prev) => ({ ...prev, scope: value || null }))} options={scopes.map((value) => ({ value, label: value }))} placeholder="Select scope" /></div>
          <div className="filter-row"><Text className="muted">Platform</Text><Select allowClear value={filters.platformCode} onChange={(value) => setFilters((prev) => ({ ...prev, platformCode: value || null }))} options={platformCodes.map((value) => ({ value, label: value }))} placeholder="Select platform" /></div>
          <div className="filter-row"><Text className="muted">Type</Text><Select allowClear value={filters.flowKind} onChange={(value) => setFilters((prev) => ({ ...prev, flowKind: value || null }))} options={flowKinds.map((value) => ({ value, label: value }))} placeholder="Select type" /></div>
          <div className="filter-row"><Text className="muted">Risk</Text><Select allowClear value={filters.riskLevel} onChange={(value) => setFilters((prev) => ({ ...prev, riskLevel: value || null }))} options={riskLevels.map((value) => ({ value, label: value }))} placeholder="Select risk" /></div>
          <div className="filter-row"><Text className="muted">Lifecycle</Text><Select allowClear value={filters.lifecycleStatus} onChange={(value) => setFilters((prev) => ({ ...prev, lifecycleStatus: value || null }))} options={lifecycleStatuses.map((value) => ({ value, label: value }))} placeholder="Select lifecycle" /></div>
          <div className="filter-row filter-row-span-2"><Text className="muted">Tag</Text><Select allowClear showSearch value={filters.tag} onChange={(value) => setFilters((prev) => ({ ...prev, tag: value || null }))} options={tags.map((value) => ({ value, label: value }))} placeholder="Select tag" /></div>
          <div className="filter-row">
            <Text className="muted">Version</Text>
            <Input
              value={filters.version}
              onChange={(event) => setFilters((prev) => ({ ...prev, version: event.target.value }))}
              placeholder="For example 1.0.0"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Description</Text>
            <Select
              allowClear
              value={filters.hasDescription}
              onChange={(value) => setFilters((prev) => ({ ...prev, hasDescription: value ?? null }))}
              options={[
                { value: true, label: 'Has description' },
                { value: false, label: 'No description' },
              ]}
              placeholder="Any"
            />
          </div>
        </div>
        <div className="filter-drawer-footer">
          <Button
            type="default"
            onClick={() => setFilters(defaultFilters)}
          >
            Reset
          </Button>
          <Button type="default" onClick={() => setIsFilterOpen(false)}>
            Apply
          </Button>
        </div>
      </Drawer>
    </div>
  );
}
