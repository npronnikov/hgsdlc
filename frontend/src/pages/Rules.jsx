import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Drawer, Input, Select, Space, Typography, message } from 'antd';
import {
  ApartmentOutlined,
  BranchesOutlined,
  ClusterOutlined,
  EyeOutlined,
  FilterOutlined,
  PlusOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { formatStatusLabel } from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;
const PAGE_LIMIT = 24;

const truncateCardName = (value, max = 25) => {
  if (!value) return '';
  return value.length > max ? `${value.slice(0, Math.max(0, max - 3))}...` : value;
};

export default function Rules() {
  const defaultFilters = {
    search: '',
    codingAgent: null,
    teamCode: null,
    platformCode: null,
    ruleKind: null,
    scope: null,
    environment: null,
    approvalStatus: null,
    contentSource: null,
    visibility: null,
    lifecycleStatus: null,
    tag: null,
    status: null,
    version: '',
    hasDescription: null,
  };
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [nextCursor, setNextCursor] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [filters, setFilters] = useState(defaultFilters);
  const navigate = useNavigate();

  const loadRules = async ({ cursor = null, append = false } = {}) => {
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
      if (filters.platformCode) params.set('platformCode', filters.platformCode);
      if (filters.ruleKind) params.set('ruleKind', filters.ruleKind);
      if (filters.scope) params.set('scope', filters.scope);
      if (filters.environment) params.set('environment', filters.environment);
      if (filters.approvalStatus) params.set('approvalStatus', filters.approvalStatus);
      if (filters.contentSource) params.set('contentSource', filters.contentSource);
      if (filters.visibility) params.set('visibility', filters.visibility);
      if (filters.lifecycleStatus) params.set('lifecycleStatus', filters.lifecycleStatus);
      if (filters.tag) params.set('tag', filters.tag);
      if (filters.status) params.set('status', filters.status);
      if (filters.version.trim()) params.set('version', filters.version.trim());
      if (filters.hasDescription !== null) params.set('hasDescription', String(filters.hasDescription));

      const data = await apiRequest(`/rules/query?${params.toString()}`);
      const mapped = (data.items || []).map((rule) => ({
        key: rule.rule_id,
        name: rule.title || rule.rule_id,
        description: rule.description || '',
        ruleId: rule.rule_id,
        codingAgent: rule.coding_agent,
        teamCode: rule.team_code,
        platformCode: rule.platform_code,
        tags: rule.tags || [],
        ruleKind: rule.rule_kind,
        scope: rule.scope,
        environment: rule.environment,
        approvalStatus: rule.approval_status,
        contentSource: rule.content_source,
        visibility: rule.visibility,
        lifecycleStatus: rule.lifecycle_status,
        status: rule.status,
        version: rule.version,
        canonical: rule.canonical_name,
      }));
      if (append) {
        setRules((prev) => [...prev, ...mapped]);
      } else {
        setRules(mapped);
      }
      setNextCursor(data.next_cursor || null);
      setHasMore(Boolean(data.has_more));
    } catch (err) {
      message.error(err.message || 'Failed to load Rules');
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
      loadRules({ cursor: null, append: false });
    }, 250);
    return () => clearTimeout(handle);
  }, [
    filters.search,
    filters.codingAgent,
    filters.teamCode,
    filters.platformCode,
    filters.ruleKind,
    filters.scope,
    filters.environment,
    filters.approvalStatus,
    filters.contentSource,
    filters.visibility,
    filters.lifecycleStatus,
    filters.tag,
    filters.status,
    filters.version,
    filters.hasDescription,
  ]);

  const codingAgents = useMemo(() => Array.from(new Set(rules.map((rule) => rule.codingAgent).filter(Boolean))), [rules]);
  const statuses = useMemo(() => Array.from(new Set(rules.map((rule) => rule.status).filter(Boolean))), [rules]);
  const teamCodes = useMemo(() => Array.from(new Set(rules.map((rule) => rule.teamCode).filter(Boolean))), [rules]);
  const platformCodes = useMemo(() => Array.from(new Set(rules.map((rule) => rule.platformCode).filter(Boolean))), [rules]);
  const ruleKinds = useMemo(() => Array.from(new Set(rules.map((rule) => rule.ruleKind).filter(Boolean))), [rules]);
  const scopes = useMemo(() => Array.from(new Set(rules.map((rule) => rule.scope).filter(Boolean))), [rules]);
  const environments = useMemo(() => Array.from(new Set(rules.map((rule) => rule.environment).filter(Boolean))), [rules]);
  const approvalStatuses = useMemo(() => Array.from(new Set(rules.map((rule) => rule.approvalStatus).filter(Boolean))), [rules]);
  const contentSources = useMemo(() => Array.from(new Set(rules.map((rule) => rule.contentSource).filter(Boolean))), [rules]);
  const visibilityOptions = useMemo(() => Array.from(new Set(rules.map((rule) => rule.visibility).filter(Boolean))), [rules]);
  const lifecycleStatuses = useMemo(() => Array.from(new Set(rules.map((rule) => rule.lifecycleStatus).filter(Boolean))), [rules]);
  const tags = useMemo(() => Array.from(new Set(rules.flatMap((rule) => rule.tags || []).filter(Boolean))), [rules]);
  const activeFilters = useMemo(() => {
    const items = [];
    if (filters.search.trim()) items.push({ key: 'search', label: `Search: ${filters.search.trim()}` });
    if (filters.codingAgent) items.push({ key: 'codingAgent', label: `Agent: ${filters.codingAgent}` });
    if (filters.status) items.push({ key: 'status', label: `Status: ${filters.status}` });
    if (filters.approvalStatus) items.push({ key: 'approvalStatus', label: `Approval: ${filters.approvalStatus}` });
    if (filters.teamCode) items.push({ key: 'teamCode', label: `Team: ${filters.teamCode}` });
    if (filters.platformCode) items.push({ key: 'platformCode', label: `Platform: ${filters.platformCode}` });
    if (filters.ruleKind) items.push({ key: 'ruleKind', label: `Type: ${filters.ruleKind}` });
    if (filters.scope) items.push({ key: 'scope', label: `Scope: ${filters.scope}` });
    if (filters.environment) items.push({ key: 'environment', label: `Environment: ${filters.environment}` });
    if (filters.contentSource) items.push({ key: 'contentSource', label: `Source: ${filters.contentSource}` });
    if (filters.visibility) items.push({ key: 'visibility', label: `Visibility: ${filters.visibility}` });
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
        <Title level={3} style={{ margin: 0 }}>Rules</Title>
        <Space>
          <Button type="default" icon={<PlusOutlined />} onClick={() => navigate('/rules/create')}>New Rule</Button>
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
            {rules.map((rule) => (
              <Card
                key={rule.key}
                className={`resource-card rule-card status-${(rule.status || 'unknown').toLowerCase()}`}
                hoverable
                onClick={() => navigate(`/rules/${rule.ruleId}`)}
              >
                <div className="resource-card-header">
                  <div className="resource-card-title">
                    <span className="resource-card-name" title={rule.name}>{truncateCardName(rule.name)}</span>
                    <span className="resource-card-subtitle mono">{rule.ruleId}@{rule.version}</span>
                  </div>
                  <span className="resource-chip resource-chip-agent"><RobotOutlined />{rule.codingAgent || 'no agent'}</span>
                </div>
                {rule.description && <Text type="secondary" className="resource-card-description">{rule.description}</Text>}
                <div className="resource-meta-list">
                  <div className="resource-meta-row"><span className="resource-meta-key"><ApartmentOutlined />Type</span><span className="resource-meta-value">{rule.ruleKind || '—'}</span></div>
                  <div className="resource-meta-row"><span className="resource-meta-key"><BranchesOutlined />Scope</span><span className="resource-meta-value">{rule.scope || '—'}</span></div>
                  <div className="resource-meta-row"><span className="resource-meta-key"><ClusterOutlined />Platform</span><span className="resource-meta-value">{rule.platformCode || '—'}</span></div>
                  <div className="resource-meta-row"><span className="resource-meta-key"><EyeOutlined />Visibility</span><span className="resource-meta-value">{rule.visibility || '—'}</span></div>
                </div>
                {(rule.tags || []).length > 0 && (
                  <div className="resource-tags-row">
                    {(rule.tags || []).slice(0, 5).map((tag) => (
                      <span key={`${rule.key}-${tag}`} className="resource-tag">#{tag}</span>
                    ))}
                  </div>
                )}
                <div className="resource-card-footer">
                  <div className="resource-card-chips">
                    {rule.approvalStatus && <span className="resource-chip"><SafetyCertificateOutlined />{rule.approvalStatus}</span>}
                    {rule.teamCode && <span className="resource-chip resource-chip-team"><TeamOutlined />{rule.teamCode}</span>}
                  </div>
                </div>
              </Card>
            ))}
            {rules.length === 0 && <div className="card-muted">Rules not found.</div>}
          </div>
        )}
        {!loading && rules.length > 0 && (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
            <Button
              type="default"
              onClick={() => loadRules({ cursor: nextCursor, append: true })}
              loading={loadingMore}
              disabled={!hasMore || loadingMore}
            >
              {hasMore ? 'Load more' : 'No more rules'}
            </Button>
          </div>
        )}
      </div>

      <Drawer title="Rules filter" placement="right" open={isFilterOpen} onClose={() => setIsFilterOpen(false)} width={360} className="filter-drawer">
        <div className="filter-drawer-body filter-grid">
          <div className="filter-row filter-row-span-2">
            <Text className="muted">Search</Text>
            <Input
              value={filters.search}
              onChange={(event) => setFilters((prev) => ({ ...prev, search: event.target.value }))}
              placeholder="Name, ID, canonical"
            />
          </div>
          <div className="filter-row"><Text className="muted">Coding Agent</Text><Select allowClear value={filters.codingAgent} onChange={(value) => setFilters((prev) => ({ ...prev, codingAgent: value || null }))} options={codingAgents.map((agent) => ({ value: agent, label: agent }))} placeholder="Select agent" /></div>
          <div className="filter-row"><Text className="muted">Status</Text><Select allowClear value={filters.status} onChange={(value) => setFilters((prev) => ({ ...prev, status: value || null }))} options={statuses.map((status) => ({ value: status, label: formatStatusLabel(status) }))} placeholder="Select status" /></div>
          <div className="filter-row"><Text className="muted">Approval</Text><Select allowClear value={filters.approvalStatus} onChange={(value) => setFilters((prev) => ({ ...prev, approvalStatus: value || null }))} options={approvalStatuses.map((value) => ({ value, label: formatStatusLabel(value) }))} placeholder="Select approval status" /></div>
          <div className="filter-row"><Text className="muted">Team</Text><Select allowClear value={filters.teamCode} onChange={(value) => setFilters((prev) => ({ ...prev, teamCode: value || null }))} options={teamCodes.map((value) => ({ value, label: value }))} placeholder="Select team" /></div>
          <div className="filter-row"><Text className="muted">Platform</Text><Select allowClear value={filters.platformCode} onChange={(value) => setFilters((prev) => ({ ...prev, platformCode: value || null }))} options={platformCodes.map((value) => ({ value, label: value }))} placeholder="Select platform" /></div>
          <div className="filter-row"><Text className="muted">Type</Text><Select allowClear value={filters.ruleKind} onChange={(value) => setFilters((prev) => ({ ...prev, ruleKind: value || null }))} options={ruleKinds.map((value) => ({ value, label: value }))} placeholder="Select type" /></div>
          <div className="filter-row"><Text className="muted">Scope</Text><Select allowClear value={filters.scope} onChange={(value) => setFilters((prev) => ({ ...prev, scope: value || null }))} options={scopes.map((value) => ({ value, label: value }))} placeholder="Select scope" /></div>
          <div className="filter-row"><Text className="muted">Environment</Text><Select allowClear value={filters.environment} onChange={(value) => setFilters((prev) => ({ ...prev, environment: value || null }))} options={environments.map((value) => ({ value, label: value }))} placeholder="Select environment" /></div>
          <div className="filter-row"><Text className="muted">Content source</Text><Select allowClear value={filters.contentSource} onChange={(value) => setFilters((prev) => ({ ...prev, contentSource: value || null }))} options={contentSources.map((value) => ({ value, label: value }))} placeholder="Select content source" /></div>
          <div className="filter-row"><Text className="muted">Visibility</Text><Select allowClear value={filters.visibility} onChange={(value) => setFilters((prev) => ({ ...prev, visibility: value || null }))} options={visibilityOptions.map((value) => ({ value, label: value }))} placeholder="Select visibility" /></div>
          <div className="filter-row"><Text className="muted">Lifecycle</Text><Select allowClear value={filters.lifecycleStatus} onChange={(value) => setFilters((prev) => ({ ...prev, lifecycleStatus: value || null }))} options={lifecycleStatuses.map((value) => ({ value, label: value }))} placeholder="Select lifecycle" /></div>
          <div className="filter-row filter-row-span-2"><Text className="muted">Tag</Text><Select allowClear value={filters.tag} onChange={(value) => setFilters((prev) => ({ ...prev, tag: value || null }))} options={tags.map((value) => ({ value, label: value }))} placeholder="Select tag" /></div>
          <div className="filter-row"><Text className="muted">Version</Text><Input value={filters.version} onChange={(event) => setFilters((prev) => ({ ...prev, version: event.target.value }))} placeholder="For example 1.0.0" /></div>
          <div className="filter-row"><Text className="muted">Description</Text><Select allowClear value={filters.hasDescription} onChange={(value) => setFilters((prev) => ({ ...prev, hasDescription: value ?? null }))} options={[{ value: true, label: 'Has description' }, { value: false, label: 'No description' }]} placeholder="Any" /></div>
        </div>
        <div className="filter-drawer-footer">
          <Button
            type="default"
            onClick={() => setFilters(defaultFilters)}
          >
            Reset
          </Button>
          <Button type="default" onClick={() => setIsFilterOpen(false)}>Apply</Button>
        </div>
      </Drawer>
    </div>
  );
}
