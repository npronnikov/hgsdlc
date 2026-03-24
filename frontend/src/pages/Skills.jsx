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
const truncateCardName = (value, max = 26) => {
  if (!value) return '';
  return value.length > max ? `${value.slice(0, max)}...` : value;
};

export default function Skills() {
  const [skills, setSkills] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [filters, setFilters] = useState({
    search: '',
    codingAgent: null,
    status: null,
    approvalStatus: null,
    environment: null,
    platformCode: null,
    contentSource: null,
    version: '',
    tag: '',
    hasDescription: null,
  });
  const navigate = useNavigate();

  const loadSkills = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/skills');
      const mapped = data.map((skill) => ({
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
        contentSource: skill.content_source,
        tags: skill.tags || [],
        visibility: skill.visibility,
        lifecycleStatus: skill.lifecycle_status,
        skillKind: skill.skill_kind,
        version: skill.version,
        canonical: skill.canonical_name,
      }));
      setSkills(mapped);
    } catch (err) {
      message.error(err.message || 'Failed to load Skills');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSkills();
  }, []);
  const codingAgents = useMemo(
    () => Array.from(new Set(skills.map((skill) => skill.codingAgent).filter(Boolean))),
    [skills]
  );
  const statuses = useMemo(
    () => Array.from(new Set(skills.map((skill) => skill.status).filter(Boolean))),
    [skills]
  );
  const approvalStatuses = useMemo(
    () => Array.from(new Set(skills.map((skill) => skill.approvalStatus).filter(Boolean))),
    [skills]
  );
  const environments = useMemo(
    () => Array.from(new Set(skills.map((skill) => skill.environment).filter(Boolean))),
    [skills]
  );
  const platforms = useMemo(
    () => Array.from(new Set(skills.map((skill) => skill.platformCode).filter(Boolean))),
    [skills]
  );
  const contentSources = useMemo(
    () => Array.from(new Set(skills.map((skill) => skill.contentSource).filter(Boolean))),
    [skills]
  );

  const filteredSkills = useMemo(() => {
    const search = filters.search.trim().toLowerCase();
    const version = filters.version.trim().toLowerCase();
    const tag = filters.tag.trim().toLowerCase();
    return skills.filter((skill) => {
      if (filters.codingAgent && skill.codingAgent !== filters.codingAgent) {
        return false;
      }
      if (filters.status && skill.status !== filters.status) {
        return false;
      }
      if (filters.approvalStatus && skill.approvalStatus !== filters.approvalStatus) {
        return false;
      }
      if (filters.environment && skill.environment !== filters.environment) {
        return false;
      }
      if (filters.platformCode && skill.platformCode !== filters.platformCode) {
        return false;
      }
      if (filters.contentSource && skill.contentSource !== filters.contentSource) {
        return false;
      }
      if (filters.hasDescription === true && !skill.description) {
        return false;
      }
      if (filters.hasDescription === false && skill.description) {
        return false;
      }
      if (version && (skill.version || '').toLowerCase() !== version) {
        return false;
      }
      if (tag && !(skill.tags || []).some((item) => item.toLowerCase().includes(tag))) {
        return false;
      }
      if (!search) {
        return true;
      }
      return [skill.name, skill.skillId, skill.canonical, skill.description, skill.codingAgent, skill.platformCode, skill.environment]
        .filter(Boolean)
        .some((value) => value.toLowerCase().includes(search));
    });
  }, [skills, filters]);

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Skills</Title>
        <Space>
          <Button type="default" icon={<PlusOutlined />} onClick={() => navigate('/skills/create')}>New Skill</Button>
          <Button type="default" icon={<FilterOutlined />} onClick={() => setIsFilterOpen(true)}>Filter</Button>
        </Space>
      </div>
      <div className="cards-fullscreen">
        {loading ? (
          <div className="card-muted">Loading...</div>
        ) : (
          <div className="cards-grid">
            {filteredSkills.map((skill) => (
              <Card
                key={skill.key}
                className={`resource-card skill-card status-${(skill.status || 'unknown').toLowerCase()}`}
                hoverable
                onClick={() => navigate(`/skills/${skill.skillId}`)}
              >
                <div className="resource-card-header">
                  <div className="resource-card-title">
                    <span className="resource-card-name" title={skill.name}>
                      {truncateCardName(skill.name)}
                    </span>
                    <span className="resource-card-subtitle mono">{skill.skillId}@{skill.version}</span>
                  </div>
                  <span className="resource-chip resource-chip-agent">
                    <RobotOutlined />
                    {skill.codingAgent || 'no agent'}
                  </span>
                </div>
                {skill.description && (
                  <Text type="secondary" className="resource-card-description">
                    {skill.description}
                  </Text>
                )}
                <div className="resource-meta-list">
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><ApartmentOutlined />Type</span>
                    <span className="resource-meta-value">{skill.skillKind || '—'}</span>
                  </div>
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><ClusterOutlined />Platform</span>
                    <span className="resource-meta-value">{skill.platformCode || '—'}</span>
                  </div>
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><EyeOutlined />Visibility</span>
                    <span className="resource-meta-value">{skill.visibility || '—'}</span>
                  </div>
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><SafetyCertificateOutlined />Approval</span>
                    <span className="resource-meta-value">{skill.approvalStatus || '—'}</span>
                  </div>
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
                  </div>
                </div>
              </Card>
            ))}
            {filteredSkills.length === 0 && (
              <div className="card-muted">Skills not found.</div>
            )}
          </div>
        )}
      </div>

      <Drawer
        title="Skills filter"
        placement="right"
        open={isFilterOpen}
        onClose={() => setIsFilterOpen(false)}
        width={360}
        className="filter-drawer"
      >
        <div className="filter-drawer-body">
          <div className="filter-row">
            <Text className="muted">Search</Text>
            <Input
              value={filters.search}
              onChange={(event) => setFilters((prev) => ({ ...prev, search: event.target.value }))}
              placeholder="Name, ID, description"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Coding Agent</Text>
            <Select
              allowClear
              value={filters.codingAgent}
              onChange={(value) => setFilters((prev) => ({ ...prev, codingAgent: value || null }))}
              options={codingAgents.map((agent) => ({ value: agent, label: agent }))}
              placeholder="Select agent"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Status</Text>
            <Select
              allowClear
              value={filters.status}
              onChange={(value) => setFilters((prev) => ({ ...prev, status: value || null }))}
              options={statuses.map((status) => ({ value: status, label: formatStatusLabel(status) }))}
              placeholder="Select status"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Approval</Text>
            <Select
              allowClear
              value={filters.approvalStatus}
              onChange={(value) => setFilters((prev) => ({ ...prev, approvalStatus: value || null }))}
              options={approvalStatuses.map((status) => ({ value: status, label: formatStatusLabel(status) }))}
              placeholder="Select approval status"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Environment</Text>
            <Select
              allowClear
              value={filters.environment}
              onChange={(value) => setFilters((prev) => ({ ...prev, environment: value || null }))}
              options={environments.map((value) => ({ value, label: value }))}
              placeholder="Select environment"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Platform</Text>
            <Select
              allowClear
              value={filters.platformCode}
              onChange={(value) => setFilters((prev) => ({ ...prev, platformCode: value || null }))}
              options={platforms.map((value) => ({ value, label: value }))}
              placeholder="Select platform"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Content source</Text>
            <Select
              allowClear
              value={filters.contentSource}
              onChange={(value) => setFilters((prev) => ({ ...prev, contentSource: value || null }))}
              options={contentSources.map((value) => ({ value, label: value }))}
              placeholder="Select content source"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Version</Text>
            <Input
              value={filters.version}
              onChange={(event) => setFilters((prev) => ({ ...prev, version: event.target.value }))}
              placeholder="For example 1.0.0"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Tag</Text>
            <Input
              value={filters.tag}
              onChange={(event) => setFilters((prev) => ({ ...prev, tag: event.target.value }))}
              placeholder="architecture"
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
            onClick={() => setFilters({
              search: '',
              codingAgent: null,
              status: null,
              approvalStatus: null,
              environment: null,
              platformCode: null,
              contentSource: null,
              version: '',
              tag: '',
              hasDescription: null,
            })}
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
