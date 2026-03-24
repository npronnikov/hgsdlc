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
const truncateCardName = (value, max = 26) => {
  if (!value) return '';
  return value.length > max ? `${value.slice(0, max)}...` : value;
};

export default function Rules() {
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [filters, setFilters] = useState({
    search: '',
    codingAgent: null,
    status: null,
    version: '',
  });
  const navigate = useNavigate();

  const loadRules = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/rules');
      const mapped = data.map((rule) => ({
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
      setRules(mapped);
    } catch (err) {
      message.error(err.message || 'Failed to load Rules');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRules();
  }, []);
  const codingAgents = useMemo(
    () => Array.from(new Set(rules.map((rule) => rule.codingAgent).filter(Boolean))),
    [rules]
  );
  const statuses = useMemo(
    () => Array.from(new Set(rules.map((rule) => rule.status).filter(Boolean))),
    [rules]
  );

  const filteredRules = useMemo(() => {
    const search = filters.search.trim().toLowerCase();
    const version = filters.version.trim().toLowerCase();
    return rules.filter((rule) => {
      if (filters.codingAgent && rule.codingAgent !== filters.codingAgent) {
        return false;
      }
      if (filters.status && rule.status !== filters.status) {
        return false;
      }
      if (version && (rule.version || '').toLowerCase() !== version) {
        return false;
      }
      if (!search) {
        return true;
      }
      return [rule.name, rule.ruleId, rule.canonical, rule.codingAgent, rule.description]
        .filter(Boolean)
        .some((value) => value.toLowerCase().includes(search));
    });
  }, [rules, filters]);

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Rules</Title>
        <Space>
          <Button type="default" icon={<PlusOutlined />} onClick={() => navigate('/rules/create')}>New Rule</Button>
          <Button type="default" icon={<FilterOutlined />} onClick={() => setIsFilterOpen(true)}>Filter</Button>
        </Space>
      </div>
      <div className="cards-fullscreen">
        {loading ? (
          <div className="card-muted">Loading...</div>
        ) : (
          <div className="cards-grid">
            {filteredRules.map((rule) => (
              <Card
                key={rule.key}
                className={`resource-card rule-card status-${(rule.status || 'unknown').toLowerCase()}`}
                hoverable
                onClick={() => navigate(`/rules/${rule.ruleId}`)}
              >
                <div className="resource-card-header">
                  <div className="resource-card-title">
                    <span className="resource-card-name" title={rule.name}>
                      {truncateCardName(rule.name)}
                    </span>
                    <span className="resource-card-subtitle mono">{rule.ruleId}@{rule.version}</span>
                  </div>
                  <span className="resource-chip resource-chip-agent">
                    <RobotOutlined />
                    {rule.codingAgent || 'no agent'}
                  </span>
                </div>
                {rule.description && (
                  <Text type="secondary" className="resource-card-description">
                    {rule.description}
                  </Text>
                )}
                <div className="resource-meta-list">
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><ApartmentOutlined />Type</span>
                    <span className="resource-meta-value">{rule.ruleKind || '—'}</span>
                  </div>
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><BranchesOutlined />Scope</span>
                    <span className="resource-meta-value">{rule.scope || '—'}</span>
                  </div>
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><ClusterOutlined />Platform</span>
                    <span className="resource-meta-value">{rule.platformCode || '—'}</span>
                  </div>
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><EyeOutlined />Visibility</span>
                    <span className="resource-meta-value">{rule.visibility || '—'}</span>
                  </div>
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
            {filteredRules.length === 0 && (
              <div className="card-muted">Rules not found.</div>
            )}
          </div>
        )}
      </div>

      <Drawer
        title="Rules filter"
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
              placeholder="Name, ID, canonical"
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
            <Text className="muted">Version</Text>
            <Input
              value={filters.version}
              onChange={(event) => setFilters((prev) => ({ ...prev, version: event.target.value }))}
              placeholder="For example 1.0.0"
            />
          </div>
        </div>
        <div className="filter-drawer-footer">
          <Button
            type="default"
            onClick={() => setFilters({ search: '', codingAgent: null, status: null, version: '' })}
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
