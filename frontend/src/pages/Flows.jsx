import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Drawer, Input, Select, Space, Typography, message } from 'antd';
import { FilterOutlined, PlusOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

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
        status: flow.status,
        version: flow.version,
        canonical: flow.canonical_name,
        savedBy: flow.saved_by || '',
      }));
      setFlows(mapped);
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить Flows');
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
          <Button type="default" icon={<PlusOutlined />} onClick={() => navigate('/flows/create')}>Новый Flow</Button>
          <Button type="default" icon={<FilterOutlined />} onClick={() => setIsFilterOpen(true)}>Фильтр</Button>
        </Space>
      </div>
      <div className="cards-fullscreen">
        {loading ? (
          <div className="card-muted">Загрузка...</div>
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
                    <span className="resource-card-name">{flow.name}</span>
                  </div>
                  <StatusTag value={flow.status} />
                </div>
                {flow.description && (
                  <Text type="secondary" className="resource-card-description">
                    {flow.description}
                  </Text>
                )}
                <div className="resource-card-footer resource-card-footer-stack">
                  <span className="resource-canonical mono">{flow.canonical}</span>
                  <div className="resource-card-chips resource-card-chips-right">
                    <span className="resource-chip">
                      <UserOutlined />
                      {flow.savedBy || 'unknown'}
                    </span>
                  </div>
                </div>
              </Card>
            ))}
            {filteredFlows.length === 0 && (
              <div className="card-muted">Flows не найдены.</div>
            )}
          </div>
        )}
      </div>

      <Drawer
        title="Фильтр Flows"
        placement="right"
        open={isFilterOpen}
        onClose={() => setIsFilterOpen(false)}
        width={360}
        className="filter-drawer"
      >
        <div className="filter-drawer-body">
          <div className="filter-row">
            <Text className="muted">Поиск</Text>
            <Input
              value={filters.search}
              onChange={(event) => setFilters((prev) => ({ ...prev, search: event.target.value }))}
              placeholder="Название, ID, описание"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Статус</Text>
            <Select
              allowClear
              value={filters.status}
              onChange={(value) => setFilters((prev) => ({ ...prev, status: value || null }))}
              options={statuses.map((status) => ({ value: status, label: status }))}
              placeholder="Выберите статус"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Версия</Text>
            <Input
              value={filters.version}
              onChange={(event) => setFilters((prev) => ({ ...prev, version: event.target.value }))}
              placeholder="Например 1.0.0"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Описание</Text>
            <Select
              allowClear
              value={filters.hasDescription}
              onChange={(value) => setFilters((prev) => ({ ...prev, hasDescription: value ?? null }))}
              options={[
                { value: true, label: 'Есть описание' },
                { value: false, label: 'Без описания' },
              ]}
              placeholder="Любое"
            />
          </div>
        </div>
        <div className="filter-drawer-footer">
          <Button
            type="default"
            onClick={() => setFilters({ search: '', status: null, version: '', hasDescription: null })}
          >
            Сбросить
          </Button>
          <Button type="primary" onClick={() => setIsFilterOpen(false)}>
            Применить
          </Button>
        </div>
      </Drawer>
    </div>
  );
}
