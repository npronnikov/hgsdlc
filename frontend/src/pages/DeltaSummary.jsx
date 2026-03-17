import React, { useEffect, useMemo, useState } from 'react';
import { Card, Col, Empty, Row, Typography, message } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function DeltaSummary() {
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');
  const [run, setRun] = useState(null);
  const [artifacts, setArtifacts] = useState([]);
  const [nodes, setNodes] = useState([]);

  useEffect(() => {
    const load = async () => {
      if (!runId) {
        return;
      }
      try {
        const [runData, artifactData, nodeData] = await Promise.all([
          apiRequest(`/runs/${runId}`),
          apiRequest(`/runs/${runId}/artifacts`),
          apiRequest(`/runs/${runId}/nodes`),
        ]);
        setRun(runData);
        setArtifacts(artifactData || []);
        setNodes(nodeData || []);
      } catch (err) {
        message.error(err.message || 'Не удалось загрузить delta summary');
      }
    };
    load();
  }, [runId]);

  const metrics = useMemo(() => {
    const kinds = (key) => artifacts.filter((item) => item.kind === key).length;
    return [
      { label: 'artifact_versions', value: String(artifacts.length) },
      { label: 'produced', value: String(kinds('produced')) },
      { label: 'mutations', value: String(kinds('mutation')) },
      { label: 'node_executions', value: String(nodes.length) },
    ];
  }, [artifacts, nodes]);

  if (!runId) {
    return <Empty description="Добавьте runId: /delta-summary?runId=..." />;
  }

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Delta Summary</Title>
      </div>
      <Row gutter={[16, 16]}>
        {metrics.map((item) => (
          <Col xs={24} sm={12} lg={6} key={item.label}>
            <Card className="metric-card">
              <Text className="card-label" type="secondary">{item.label}</Text>
              <div className="metric-value">{item.value}</div>
            </Card>
          </Col>
        ))}
      </Row>
      <Card style={{ marginTop: 16 }}>
        <div>
          <Text className="muted">run_status</Text>
          <div>{run?.status || '—'}</div>
        </div>
        <div style={{ marginTop: 12 }}>
          <Text className="muted">current_node_id</Text>
          <div>{run?.current_node_id || '—'}</div>
        </div>
      </Card>
    </div>
  );
}
