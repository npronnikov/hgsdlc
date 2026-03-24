import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Drawer, Input, Select, Space, Typography, message } from 'antd';
import {
  AlertOutlined,
  ApartmentOutlined,
  ClusterOutlined,
  EyeOutlined,
  FilterOutlined,
  NodeIndexOutlined,
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

export default function Flows() {
  const navigate = useNavigate();
  const [flows, setFlows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [filters, setFilters] = useState({
    search: '',
    status: null,
    version: '',
    hasDescription: null,
  });

  const loadFlows = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/flows');
      const mapped = data.map((flow) => ({
        key: flow.flow_id,
        name: flow.title || flow.flow_id,
        flowId: flow.flow_id,
        description: flow.description || '',
        codingAgent: flow.coding_agent,
        teamCode: flow.team_code,
        platformCode: flow.platform_code,
        tags: flow.tags || [],
        flowKind: flow.flow_kind,
        riskLevel: flow.risk_level,
        startNodeId: flow.start_node_id,
        approvalStatus: flow.approval_status,
        visibility: flow.visibility,
        status: flow.status,
        version: flow.version,
        canonical: flow.canonical_name,
      }));
      setFlows(mapped);
    } catch (err) {
      message.error(err.message || 'Failed to load Flows');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFlows();
  }, []);

  const statuses = useMemo(
    () => Array.from(new Set(flows.map((flow) => flow.status).filter(Boolean))),
    [flows]
  );

  const filteredFlows = useMemo(() => {
    const search = filters.search.trim().toLowerCase();
    const version = filters.version.trim().toLowerCase();
    return flows.filter((flow) => {
      if (filters.status && flow.status !== filters.status) {
        return false;
      }
      if (filters.hasDescription === true && !flow.description) {
        return false;
      }
      if (filters.hasDescription === false && flow.description) {
        return false;
      }
      if (version && (flow.version || '').toLowerCase() !== version) {
        return false;
      }
      if (!search) {
        return true;
      }
      return [flow.name, flow.flowId, flow.canonical, flow.description]
        .filter(Boolean)
        .some((value) => value.toLowerCase().includes(search));
    });
  }, [flows, filters]);

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Flows</Title>
        <Space>
          <Button type="default" icon={<PlusOutlined />} onClick={() => navigate('/flows/create')}>New Flow</Button>
          <Button type="default" icon={<FilterOutlined />} onClick={() => setIsFilterOpen(true)}>Filter</Button>
        </Space>
      </div>
      <div className="cards-fullscreen">
        {loading ? (
          <div className="card-muted">Loading...</div>
        ) : (
          <div className="cards-grid">
            {filteredFlows.map((flow) => (
              <Card
                key={flow.key}
                className={`resource-card flow-card status-${(flow.status || 'unknown').toLowerCase()}`}
                hoverable
                onClick={() => navigate(`/flows/${flow.flowId}`)}
              >
                <div className="resource-card-header">
                  <div className="resource-card-title">
                    <span className="resource-card-name" title={flow.name}>
                      {truncateCardName(flow.name)}
                    </span>
                    <span className="resource-card-subtitle mono">{flow.flowId}@{flow.version}</span>
                  </div>
                  <span className="resource-chip resource-chip-agent">
                    <RobotOutlined />
                    {flow.codingAgent || 'no agent'}
                  </span>
                </div>
                {flow.description && (
                  <Text type="secondary" className="resource-card-description">
                    {flow.description}
                  </Text>
                )}
                <div className="resource-meta-list">
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><ApartmentOutlined />Type</span>
                    <span className="resource-meta-value">{flow.flowKind || '—'}</span>
                  </div>
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><AlertOutlined />Risk</span>
                    <span className="resource-meta-value">{flow.riskLevel || '—'}</span>
                  </div>
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><ClusterOutlined />Platform</span>
                    <span className="resource-meta-value">{flow.platformCode || '—'}</span>
                  </div>
                  <div className="resource-meta-row">
                    <span className="resource-meta-key"><NodeIndexOutlined />Start node</span>
                    <span className="resource-meta-value">{flow.startNodeId || '—'}</span>
                  </div>
                </div>
                {(flow.tags || []).length > 0 && (
                  <div className="resource-tags-row">
                    {(flow.tags || []).slice(0, 5).map((tag) => (
                      <span key={`${flow.key}-${tag}`} className="resource-tag">#{tag}</span>
                    ))}
                  </div>
                )}
                <div className="resource-card-footer">
                  <div className="resource-card-chips">
                    {flow.visibility && <span className="resource-chip"><EyeOutlined />{flow.visibility}</span>}
                    {flow.approvalStatus && <span className="resource-chip"><SafetyCertificateOutlined />{flow.approvalStatus}</span>}
                    {flow.teamCode && <span className="resource-chip resource-chip-team"><TeamOutlined />{flow.teamCode}</span>}
                  </div>
                </div>
              </Card>
            ))}
            {filteredFlows.length === 0 && (
              <div className="card-muted">Flows not found.</div>
            )}
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
            onClick={() => setFilters({ search: '', status: null, version: '', hasDescription: null })}
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
