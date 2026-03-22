import React, { useEffect, useState } from 'react';
import { Button, Card, Space, Typography, message } from 'antd';
import { useAuth } from '../auth/AuthContext.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

const canModerateRequests = (role) => role === 'TECH_APPROVER' || role === 'ADMIN';

export default function Requests() {
  const { user } = useAuth();
  const [pendingSkills, setPendingSkills] = useState([]);
  const [loading, setLoading] = useState(false);

  const loadPending = async () => {
    if (!canModerateRequests(user?.role)) {
      setPendingSkills([]);
      return;
    }
    setLoading(true);
    try {
      const data = await apiRequest('/skills/pending-publication');
      setPendingSkills(data || []);
    } catch (err) {
      message.error(err.message || 'Failed to load requests');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPending();
  }, [user?.role]);

  const approve = async (skillId, version) => {
    try {
      await apiRequest(`/skills/${skillId}/versions/${version}/approve`, { method: 'POST' });
      message.success('Request approved, skill published');
      await loadPending();
    } catch (err) {
      message.error(err.message || 'Failed to approve request');
    }
  };

  const reject = async (skillId, version) => {
    try {
      await apiRequest(`/skills/${skillId}/versions/${version}/reject`, { method: 'POST' });
      message.success('Request rejected');
      await loadPending();
    } catch (err) {
      message.error(err.message || 'Failed to reject request');
    }
  };

  if (!canModerateRequests(user?.role)) {
    return (
      <div className="cards-page">
        <div className="page-header">
          <Title level={3} style={{ margin: 0 }}>Requests</Title>
        </div>
        <Card>
          <Text type="secondary">Access denied. Approver role required.</Text>
        </Card>
      </div>
    );
  }

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Requests</Title>
      </div>
      <Card>
        <Title level={5} style={{ marginTop: 0 }}>Skills pending publication</Title>
        {loading && <div className="card-muted">Loading...</div>}
        {!loading && pendingSkills.length === 0 && (
          <Text type="secondary">No pending requests.</Text>
        )}
        {!loading && pendingSkills.length > 0 && (
          <Space direction="vertical" style={{ width: '100%' }}>
            {pendingSkills.map((item) => (
              <div
                key={`${item.skill_id}-${item.version}`}
                style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}
              >
                <div>
                  <div><b>{item.name || item.skill_id}</b> <span className="mono">v{item.version}</span></div>
                  <Text type="secondary">{item.description}</Text>
                </div>
                <Space>
                  <Button type="default" onClick={() => reject(item.skill_id, item.version)}>Reject</Button>
                  <Button type="default" onClick={() => approve(item.skill_id, item.version)}>Approve</Button>
                </Space>
              </div>
            ))}
          </Space>
        )}
      </Card>
    </div>
  );
}
