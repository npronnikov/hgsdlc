import React, { useEffect, useMemo, useState } from 'react';
import { Card, Col, Empty, List, Row, Typography, message } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { apiRequest } from '../api/request.js';

const { Title } = Typography;

export default function Artifacts() {
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId');
  const [artifacts, setArtifacts] = useState([]);
  const [selectedArtifactId, setSelectedArtifactId] = useState(null);

  useEffect(() => {
    const load = async () => {
      if (!runId) {
        return;
      }
      try {
        const data = await apiRequest(`/runs/${runId}/artifacts`);
        setArtifacts(data || []);
        setSelectedArtifactId((data || [])[0]?.artifact_version_id || null);
      } catch (err) {
        message.error(err.message || 'Failed to load artifacts');
      }
    };
    load();
  }, [runId]);

  const selected = useMemo(
    () => artifacts.find((item) => item.artifact_version_id === selectedArtifactId) || null,
    [artifacts, selectedArtifactId]
  );

  if (!runId) {
    return <Empty description="Add runId: /artifacts?runId=..." />;
  }

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Artifacts</Title>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={10}>
          <Card>
            <List
              dataSource={artifacts}
              renderItem={(item) => (
                <List.Item onClick={() => setSelectedArtifactId(item.artifact_version_id)} style={{ cursor: 'pointer' }}>
                  <List.Item.Meta
                    title={item.artifact_key}
                    description={`${item.node_id} · ${item.kind}`}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card>
            <Title level={5}>Preview</Title>
            <pre className="code-block">{JSON.stringify(selected || {}, null, 2)}</pre>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
