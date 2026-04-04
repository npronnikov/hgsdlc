import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Drawer, Input, Select, Space, Typography, message } from 'antd';
import { FilterOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { formatStatusLabel } from '../components/StatusTag.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
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
    teamCode: null,
    scope: null,
    platformCode: null,
    skillKind: null,
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
  const { user } = useAuth();
  const canManageCatalog = user?.roles?.includes('ADMIN') || user?.roles?.includes('FLOW_CONFIGURATOR');

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
      if (filters.teamCode) params.set('teamCode', filters.teamCode);
      if (filters.scope) params.set('scope', filters.scope);
      if (filters.platformCode) params.set('platformCode', filters.platformCode);
      if (filters.skillKind) params.set('skillKind', filters.skillKind);
      if (filters.tag) params.set('tag', filters.tag);

      const data = await apiRequest(`/skills/query?${params.toString()}`);
      const mapped = (data.items || []).map((skill) => ({
        key: skill.skill_id,
        name: skill.name || skill.skill_id,
        skillId: skill.skill_id,
        description: skill.description || '',
        codingAgent: skill.coding_agent,
        teamCode: skill.team_code,
        scope: skill.scope,
        status: skill.status,
        platformCode: skill.platform_code,
        tags: skill.tags || [],
        skillKind: skill.skill_kind,
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
    filters.teamCode,
    filters.scope,
    filters.platformCode,
    filters.skillKind,
    filters.tag,
  ]);

  const codingAgents = useMemo(() => Array.from(new Set(skills.map((s) => s.codingAgent).filter(Boolean))), [skills]);
  const statuses = useMemo(() => Array.from(new Set(skills.map((s) => s.status).filter(Boolean))), [skills]);
  const teamCodes = useMemo(() => Array.from(new Set(skills.map((s) => s.teamCode).filter(Boolean))), [skills]);
  const scopes = useMemo(() => Array.from(new Set(skills.map((s) => s.scope).filter(Boolean))), [skills]);
  const platforms = useMemo(() => Array.from(new Set(skills.map((s) => s.platformCode).filter(Boolean))), [skills]);
  const skillKinds = useMemo(() => Array.from(new Set(skills.map((s) => s.skillKind).filter(Boolean))), [skills]);
  const tags = useMemo(() => Array.from(new Set(skills.flatMap((s) => s.tags || []).filter(Boolean))), [skills]);
  const activeFilters = useMemo(() => {
    const items = [];
    if (filters.search.trim()) items.push({ key: 'search', label: `Search: ${filters.search.trim()}` });
    if (filters.codingAgent) items.push({ key: 'codingAgent', label: `Agent: ${filters.codingAgent}` });
    if (filters.status) items.push({ key: 'status', label: `Status: ${filters.status}` });
    if (filters.teamCode) items.push({ key: 'teamCode', label: `Team: ${filters.teamCode}` });
    if (filters.scope) items.push({ key: 'scope', label: `Scope: ${filters.scope}` });
    if (filters.platformCode) items.push({ key: 'platformCode', label: `Platform: ${filters.platformCode}` });
    if (filters.skillKind) items.push({ key: 'skillKind', label: `Type: ${filters.skillKind}` });
    if (filters.tag) items.push({ key: 'tag', label: `Tag: ${filters.tag}` });
    return items;
  }, [filters]);

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Skills</Title>
        <Space>
          {canManageCatalog ? (
            <Button type="default" icon={<PlusOutlined />} onClick={() => navigate('/skills/create')}>New Skill</Button>
          ) : null}
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
                <div className="resource-card-header skill-card-header">
                  <div className="resource-card-title skill-card-title">
                    <span className="resource-card-name" title={skill.name}>{truncateCardName(skill.name)}</span>
                    <span className="resource-card-subtitle mono">{skill.skillId}@{skill.version}</span>
                  </div>
                  <span className="skill-card-status">{formatStatusLabel(skill.status || 'unknown')}</span>
                </div>
                {skill.description && <Text type="secondary" className="resource-card-description">{skill.description}</Text>}
                <div className="skill-card-meta-line">
                  <span>{skill.skillKind || 'type: —'}</span>
                  <span>{skill.scope || 'scope: —'}</span>
                  <span>{skill.platformCode || 'platform: —'}</span>
                </div>
                {(skill.tags || []).length > 0 && (
                  <div className="resource-tags-row skill-card-tags-row">
                    {(skill.tags || []).slice(0, 5).map((tag) => (
                      <span key={`${skill.key}-${tag}`} className="resource-tag skill-card-tag">#{tag}</span>
                    ))}
                  </div>
                )}
                <div className="resource-card-footer skill-card-footer">
                  <span className="resource-chip skill-card-chip">{skill.codingAgent || 'no agent'}</span>
                  <span className="skill-card-footer-item">TEAM: {skill.teamCode || 'no team'}</span>
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
          <div className="filter-row"><Text className="muted">Team</Text><Select allowClear value={filters.teamCode} onChange={(value) => setFilters((prev) => ({ ...prev, teamCode: value || null }))} options={teamCodes.map((value) => ({ value, label: value }))} placeholder="Select team" /></div>
          <div className="filter-row"><Text className="muted">Scope</Text><Select allowClear value={filters.scope} onChange={(value) => setFilters((prev) => ({ ...prev, scope: value || null }))} options={scopes.map((value) => ({ value, label: value }))} placeholder="Select scope" /></div>
          <div className="filter-row"><Text className="muted">Platform</Text><Select allowClear value={filters.platformCode} onChange={(value) => setFilters((prev) => ({ ...prev, platformCode: value || null }))} options={platforms.map((value) => ({ value, label: value }))} placeholder="Select platform" /></div>
          <div className="filter-row"><Text className="muted">Type</Text><Select allowClear value={filters.skillKind} onChange={(value) => setFilters((prev) => ({ ...prev, skillKind: value || null }))} options={skillKinds.map((value) => ({ value, label: value }))} placeholder="Select type" /></div>
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
