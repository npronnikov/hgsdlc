import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Drawer, Input, Select, Space, Typography, message } from 'antd';
import { FilterOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function Skills() {
  const [skills, setSkills] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [filters, setFilters] = useState({
    search: '',
    codingAgent: null,
    status: null,
    version: '',
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
        status: skill.status,
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

  const filteredSkills = useMemo(() => {
    const search = filters.search.trim().toLowerCase();
    const version = filters.version.trim().toLowerCase();
    return skills.filter((skill) => {
      if (filters.codingAgent && skill.codingAgent !== filters.codingAgent) {
        return false;
      }
      if (filters.status && skill.status !== filters.status) {
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
      if (!search) {
        return true;
      }
      return [skill.name, skill.skillId, skill.canonical, skill.description, skill.codingAgent]
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
                    <span className="resource-card-name">{skill.name}</span>
                  </div>
                  <StatusTag value={skill.status} />
                </div>
                {skill.description && (
                  <Text type="secondary" className="resource-card-description">
                    {skill.description}
                  </Text>
                )}
                <div className="resource-card-footer">
                  <span className="resource-canonical mono">{skill.canonical}</span>
                  <div className="resource-card-chips">
                    <span className="resource-chip">{skill.codingAgent || 'no agent'}</span>
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
              options={statuses.map((status) => ({ value: status, label: status }))}
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
            onClick={() => setFilters({ search: '', codingAgent: null, status: null, version: '', hasDescription: null })}
          >
            Reset
          </Button>
          <Button type="primary" onClick={() => setIsFilterOpen(false)}>
            Apply
          </Button>
        </div>
      </Drawer>
    </div>
  );
}
