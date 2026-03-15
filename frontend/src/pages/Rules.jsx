import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Drawer, Input, Select, Space, Typography, message } from 'antd';
import { FilterOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import StatusTag from '../components/StatusTag.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

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
        status: rule.status,
        version: rule.version,
        canonical: rule.canonical_name,
      }));
      setRules(mapped);
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить Rules');
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
          <Button type="default" icon={<PlusOutlined />} onClick={() => navigate('/rules/create')}>Новый Rule</Button>
          <Button type="default" icon={<FilterOutlined />} onClick={() => setIsFilterOpen(true)}>Фильтр</Button>
        </Space>
      </div>
      <div className="cards-fullscreen">
        {loading ? (
          <div className="card-muted">Загрузка...</div>
        ) : (
          <div className="cards-grid">
            {filteredRules.map((rule) => (
              <Card
                key={rule.key}
                className="resource-card"
                hoverable
                onClick={() => navigate(`/rules/${rule.ruleId}`)}
              >
                <div className="resource-card-header">
                  <Text strong>{rule.name}</Text>
                  <StatusTag value={rule.status} />
                </div>
                <div className="resource-card-meta">
                  <span className="mono">{rule.ruleId}</span>
                  {rule.description && <Text type="secondary">{rule.description}</Text>}
                  <span className="mono">{rule.canonical}</span>
                </div>
                <div className="resource-card-footer">
                  <span className="mono">{rule.version}</span>
                  <span className="mono">{rule.codingAgent || '-'}</span>
                </div>
              </Card>
            ))}
            {filteredRules.length === 0 && (
              <div className="card-muted">Правила не найдены.</div>
            )}
          </div>
        )}
      </div>

      <Drawer
        title="Фильтр Rules"
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
              placeholder="Название, ID, canonical"
            />
          </div>
          <div className="filter-row">
            <Text className="muted">Coding Agent</Text>
            <Select
              allowClear
              value={filters.codingAgent}
              onChange={(value) => setFilters((prev) => ({ ...prev, codingAgent: value || null }))}
              options={codingAgents.map((agent) => ({ value: agent, label: agent }))}
              placeholder="Выберите агент"
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
        </div>
        <div className="filter-drawer-footer">
          <Button
            type="default"
            onClick={() => setFilters({ search: '', codingAgent: null, status: null, version: '' })}
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
