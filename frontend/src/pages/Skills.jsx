import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Drawer, Input, Select, Space, Typography, message } from 'antd';
import {
  ApartmentOutlined,
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

export default function Skills() {
  const defaultFilters = {
    search: '',
    codingAgent: null,
    status: null,
    approvalStatus: null,
    teamCode: null,
    platformCode: null,
    skillKind: null,
    environment: null,
    visibility: null,
    tag: null,
  };
  const [skills, setSkills] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [nextCursor, setNextCursor] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [filters, setFilters] = useState(defaultFilters);
  const navigate = useNavigate();

  const loadSkills = async ({ cursor = null, append = false } = {}) => {
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
      if (filters.status) params.set('status', filters.status);
      if (filters.approvalStatus) params.set('approvalStatus', filters.approvalStatus);
      if (filters.teamCode) params.set('teamCode', filters.teamCode);
      if (filters.platformCode) params.set('platformCode', filters.platformCode);
      if (filters.skillKind) params.set('skillKind', filters.skillKind);
      if (filters.environment) params.set('environment', filters.environment);
      if (filters.visibility) params.set('visibility', filters.visibility);
      if (filters.tag) params.set('tag', filters.tag);

      const data = await apiRequest(`/skills/query?${params.toString()}`);
      const mapped = (data.items || []).map((skill) => ({
        key: skill.skill_id,
        name: skill.name || skill.skill_id,
        skillId: skill.skill_id,
        description: skill.description || '',
        codingAgent: skill.coding_agent,
        teamCode: skill.team_code,
        status: skill.status,
        approvalStatus: skill.approval_status,
        environment: skill.environment,
        platformCode: skill.platform_code,
        tags: skill.tags || [],
        visibility: skill.visibility,
        skillKind: skill.skill_kind,
        publicationStatus: skill.publication_status,
        publicationTarget: skill.publication_target,
        publishedPrUrl: skill.published_pr_url,
        version: skill.version,
        canonical: skill.canonical_name,
      }));
      if (append) {
        setSkills((prev) => [...prev, ...mapped]);
      } else {
        setSkills(mapped);
      }
      setNextCursor(data.next_cursor || null);
      setHasMore(Boolean(data.has_more));
    } catch (err) {
      message.error(err.message || 'Failed to load Skills');
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
      loadSkills({ cursor: null, append: false });
    }, 250);
    return () => clearTimeout(handle);
  }, [
    filters.search,
    filters.codingAgent,
    filters.status,
    filters.approvalStatus,
    filters.teamCode,
    filters.platformCode,
    filters.skillKind,
    filters.environment,
    filters.visibility,
    filters.tag,
  ]);

  const codingAgents = useMemo(() => Array.from(new Set(skills.map((s) => s.codingAgent).filter(Boolean))), [skills]);
  const statuses = useMemo(() => Array.from(new Set(skills.map((s) => s.status).filter(Boolean))), [skills]);
  const approvalStatuses = useMemo(() => Array.from(new Set(skills.map((s) => s.approvalStatus).filter(Boolean))), [skills]);
  const teamCodes = useMemo(() => Array.from(new Set(skills.map((s) => s.teamCode).filter(Boolean))), [skills]);
  const platforms = useMemo(() => Array.from(new Set(skills.map((s) => s.platformCode).filter(Boolean))), [skills]);
  const skillKinds = useMemo(() => Array.from(new Set(skills.map((s) => s.skillKind).filter(Boolean))), [skills]);
  const environments = useMemo(() => Array.from(new Set(skills.map((s) => s.environment).filter(Boolean))), [skills]);
  const visibilityOptions = useMemo(() => Array.from(new Set(skills.map((s) => s.visibility).filter(Boolean))), [skills]);
  const tags = useMemo(() => Array.from(new Set(skills.flatMap((s) => s.tags || []).filter(Boolean))), [skills]);
  const activeFilters = useMemo(() => {
    const items = [];
    if (filters.search.trim()) items.push({ key: 'search', label: `Search: ${filters.search.trim()}` });
    if (filters.codingAgent) items.push({ key: 'codingAgent', label: `Agent: ${filters.codingAgent}` });
    if (filters.status) items.push({ key: 'status', label: `Status: ${filters.status}` });
    if (filters.approvalStatus) items.push({ key: 'approvalStatus', label: `Approval: ${filters.approvalStatus}` });
    if (filters.teamCode) items.push({ key: 'teamCode', label: `Team: ${filters.teamCode}` });
    if (filters.platformCode) items.push({ key: 'platformCode', label: `Platform: ${filters.platformCode}` });
    if (filters.skillKind) items.push({ key: 'skillKind', label: `Type: ${filters.skillKind}` });
    if (filters.environment) items.push({ key: 'environment', label: `Environment: ${filters.environment}` });
    if (filters.visibility) items.push({ key: 'visibility', label: `Visibility: ${filters.visibility}` });
    if (filters.tag) items.push({ key: 'tag', label: `Tag: ${filters.tag}` });
    return items;
  }, [filters]);

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Skills</Title>
        <Space>
          <Button type="default" icon={<PlusOutlined />} onClick={() => navigate('/skills/create')}>New Skill</Button>
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
          <Button
            size="small"
            type="default"
            onClick={() => setFilters(defaultFilters)}
          >
            Clear all
          </Button>
        </div>
      )}
      <div className="cards-fullscreen">
        {loading ? (
          <div className="card-muted">Loading...</div>
        ) : (
          <div className="cards-grid">
            {skills.map((skill) => (
              <Card
                key={skill.key}
                className={`resource-card skill-card status-${(skill.status || 'unknown').toLowerCase()}`}
                hoverable
                onClick={() => navigate(`/skills/${skill.skillId}`)}
              >
                <div className="resource-card-header">
                  <div className="resource-card-title">
                    <span className="resource-card-name" title={skill.name}>{truncateCardName(skill.name)}</span>
                    <span className="resource-card-subtitle mono">{skill.skillId}@{skill.version}</span>
                  </div>
                  <span className="resource-chip resource-chip-agent"><RobotOutlined />{skill.codingAgent || 'no agent'}</span>
                </div>
                {skill.description && <Text type="secondary" className="resource-card-description">{skill.description}</Text>}
                <div className="resource-meta-list">
                  <div className="resource-meta-row"><span className="resource-meta-key"><ApartmentOutlined />Type</span><span className="resource-meta-value">{skill.skillKind || '—'}</span></div>
                  <div className="resource-meta-row"><span className="resource-meta-key"><ClusterOutlined />Platform</span><span className="resource-meta-value">{skill.platformCode || '—'}</span></div>
                  <div className="resource-meta-row"><span className="resource-meta-key"><EyeOutlined />Visibility</span><span className="resource-meta-value">{skill.visibility || '—'}</span></div>
                  <div className="resource-meta-row"><span className="resource-meta-key"><SafetyCertificateOutlined />Approval</span><span className="resource-meta-value">{skill.approvalStatus || '—'}</span></div>
                  <div className="resource-meta-row"><span className="resource-meta-key"><SafetyCertificateOutlined />Publish</span><span className="resource-meta-value">{skill.publicationStatus || '—'}</span></div>
                </div>
                {(skill.tags || []).length > 0 && (
                  <div className="resource-tags-row">
                    {(skill.tags || []).slice(0, 5).map((tag) => (
                      <span key={`${skill.key}-${tag}`} className="resource-tag">#{tag}</span>
                    ))}
                  </div>
                )}
                <div className="resource-card-footer">
                  <div className="resource-card-chips">
                    {skill.teamCode && <span className="resource-chip resource-chip-team"><TeamOutlined />{skill.teamCode}</span>}
                    {skill.publicationTarget && <span className="resource-chip">{skill.publicationTarget}</span>}
                    {skill.publishedPrUrl && <a href={skill.publishedPrUrl} target="_blank" rel="noreferrer" className="resource-chip">PR</a>}
                  </div>
                </div>
              </Card>
            ))}
            {skills.length === 0 && <div className="card-muted">Skills not found.</div>}
          </div>
        )}
        {!loading && skills.length > 0 && (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
            <Button
              type="default"
              onClick={() => loadSkills({ cursor: nextCursor, append: true })}
              loading={loadingMore}
              disabled={!hasMore || loadingMore}
            >
              {hasMore ? 'Load more' : 'No more skills'}
            </Button>
          </div>
        )}
      </div>

      <Drawer title="Skills filter" placement="right" open={isFilterOpen} onClose={() => setIsFilterOpen(false)} width={360} className="filter-drawer">
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
          <div className="filter-row"><Text className="muted">Status</Text><Select allowClear value={filters.status} onChange={(value) => setFilters((prev) => ({ ...prev, status: value || null }))} options={statuses.map((value) => ({ value, label: formatStatusLabel(value) }))} placeholder="Select status" /></div>
          <div className="filter-row"><Text className="muted">Approval</Text><Select allowClear value={filters.approvalStatus} onChange={(value) => setFilters((prev) => ({ ...prev, approvalStatus: value || null }))} options={approvalStatuses.map((value) => ({ value, label: formatStatusLabel(value) }))} placeholder="Select approval" /></div>
          <div className="filter-row"><Text className="muted">Team</Text><Select allowClear value={filters.teamCode} onChange={(value) => setFilters((prev) => ({ ...prev, teamCode: value || null }))} options={teamCodes.map((value) => ({ value, label: value }))} placeholder="Select team" /></div>
          <div className="filter-row"><Text className="muted">Platform</Text><Select allowClear value={filters.platformCode} onChange={(value) => setFilters((prev) => ({ ...prev, platformCode: value || null }))} options={platforms.map((value) => ({ value, label: value }))} placeholder="Select platform" /></div>
          <div className="filter-row"><Text className="muted">Type</Text><Select allowClear value={filters.skillKind} onChange={(value) => setFilters((prev) => ({ ...prev, skillKind: value || null }))} options={skillKinds.map((value) => ({ value, label: value }))} placeholder="Select type" /></div>
          <div className="filter-row"><Text className="muted">Environment</Text><Select allowClear value={filters.environment} onChange={(value) => setFilters((prev) => ({ ...prev, environment: value || null }))} options={environments.map((value) => ({ value, label: value }))} placeholder="Select environment" /></div>
          <div className="filter-row"><Text className="muted">Visibility</Text><Select allowClear value={filters.visibility} onChange={(value) => setFilters((prev) => ({ ...prev, visibility: value || null }))} options={visibilityOptions.map((value) => ({ value, label: value }))} placeholder="Select visibility" /></div>
          <div className="filter-row filter-row-span-2"><Text className="muted">Tag</Text><Select allowClear showSearch value={filters.tag} onChange={(value) => setFilters((prev) => ({ ...prev, tag: value || null }))} options={tags.map((value) => ({ value, label: value }))} placeholder="Select tag" /></div>
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
