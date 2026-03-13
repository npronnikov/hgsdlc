import React from 'react';
import { Card, Col, Descriptions, List, Row, Space, Tabs, Typography } from 'antd';
import StatusTag from '../components/StatusTag.jsx';

const { Title, Text } = Typography;

export default function RunConsole() {
  return (
    <div>
      <Title level={3}>Run Console</Title>
      <div className="summary-bar">
        <div>
          <Text className="muted">run status</Text>
          <div><StatusTag value="waiting_gate" /></div>
        </div>
        <div>
          <Text className="muted">current node</Text>
          <div className="mono">approve-requirements</div>
        </div>
        <div>
          <Text className="muted">current gate</Text>
          <div><StatusTag value="awaiting_decision" /></div>
        </div>
        <div>
          <Text className="muted">flow_canonical_name</Text>
          <div className="mono">feature-change-flow@1.0.3</div>
        </div>
        <div>
          <Text className="muted">run branch</Text>
          <div className="mono">feature/hgsdlc/run-0041</div>
        </div>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={6}>
          <Card title="Node Timeline" className="timeline-card">
            <List
              dataSource={[
                { title: 'intake-analysis', status: 'succeeded', time: '09:40' },
                { title: 'collect-answers', status: 'submitted', time: '10:05' },
                { title: 'process-answers', status: 'succeeded', time: '10:21' },
                { title: 'approve-requirements', status: 'awaiting decision', time: 'now' },
                { title: 'build-plan', status: 'queued', time: '-' },
              ]}
              renderItem={(item) => (
                <List.Item>
                  <Space direction="vertical" size={0}>
                    <Text strong>{item.title}</Text>
                    <Text type="secondary">{item.status} · {item.time}</Text>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card>
            <Tabs
              defaultActiveKey="overview"
              items={[
                {
                  key: 'overview',
                  label: 'Overview',
                  children: (
                    <Row gutter={[12, 12]}>
                      <Col span={8}>
                        <Card className="metric-card">
                          <Text className="card-label" type="secondary">Changed files</Text>
                          <div className="metric-value">12</div>
                        </Card>
                      </Col>
                      <Col span={8}>
                        <Card className="metric-card">
                          <Text className="card-label" type="secondary">Added lines</Text>
                          <div className="metric-value">186</div>
                        </Card>
                      </Col>
                      <Col span={8}>
                        <Card className="metric-card">
                          <Text className="card-label" type="secondary">Removed lines</Text>
                          <div className="metric-value">41</div>
                        </Card>
                      </Col>
                      <Col span={24}>
                        <Card>
                          <Title level={5}>Latest artifacts</Title>
                          <List
                            dataSource={[
                              { name: 'requirements-draft.md', version: 'v3' },
                              { name: 'questions.md', version: 'v2' },
                            ]}
                            renderItem={(item) => (
                              <List.Item>
                                <Text className="mono">{item.name}</Text>
                                <StatusTag value={item.version} />
                              </List.Item>
                            )}
                          />
                        </Card>
                      </Col>
                    </Row>
                  ),
                },
                {
                  key: 'timeline',
                  label: 'Timeline',
                  children: (
                    <List
                      dataSource={[
                        { event: 'NODE_SUCCEEDED', detail: 'process-answers · 10:21', type: 'AI' },
                        { event: 'GATE_OPENED', detail: 'approve-requirements · 10:24', type: 'human_approval' },
                      ]}
                      renderItem={(item) => (
                        <List.Item>
                          <Space direction="vertical" size={0}>
                            <Text strong>{item.event}</Text>
                            <Text type="secondary">{item.detail}</Text>
                          </Space>
                          <StatusTag value={item.type} />
                        </List.Item>
                      )}
                    />
                  ),
                },
                {
                  key: 'artifacts',
                  label: 'Artifacts',
                  children: (
                    <Descriptions bordered size="small" column={1}>
                      <Descriptions.Item label="requirements-draft.md">
                        Node: process-answers · Version v3 · Checksum d9f8a...12c
                      </Descriptions.Item>
                      <Descriptions.Item label="questions.md">
                        Node: intake-analysis · Version v2 · Checksum a0c1f...9e2
                      </Descriptions.Item>
                    </Descriptions>
                  ),
                },
                {
                  key: 'prompt',
                  label: 'Prompt Package',
                  children: (
                    <div>
                      <div className="prompt-section">
                        <div className="prompt-title">1. system_header</div>
                        <pre className="code-block">You are HGSDLC agent...</pre>
                      </div>
                      <div className="prompt-section">
                        <div className="prompt-title">2. flow_identity</div>
                        <pre className="code-block">feature-change-flow@1.0.3</pre>
                      </div>
                    </div>
                  ),
                },
                {
                  key: 'skills',
                  label: 'Used Skills',
                  children: (
                    <Row gutter={[12, 12]}>
                      <Col span={12}>
                        <Card>
                          <Title level={5}>Declared skills</Title>
                          <Space wrap>
                            <StatusTag value="feature-intake@1.0.0" />
                            <StatusTag value="update-requirements@1.2.0" />
                          </Space>
                        </Card>
                      </Col>
                      <Col span={12}>
                        <Card>
                          <Title level={5}>Applied skills</Title>
                          <Space wrap>
                            <StatusTag value="update-requirements@1.2.0" />
                          </Space>
                        </Card>
                      </Col>
                    </Row>
                  ),
                },
                {
                  key: 'delta',
                  label: 'Delta Summary',
                  children: (
                    <Card>
                      <Text>Agent summary: Implemented partial refund support and updated tests.</Text>
                      <div style={{ marginTop: 12 }}>
                        <Text type="secondary">touched_areas</Text>
                        <div>src/main/java, src/test/java, docs</div>
                      </div>
                    </Card>
                  ),
                },
              ]}
            />
          </Card>
        </Col>

        <Col xs={24} lg={6}>
          <Card title="Current Gate">
            <div className="card-muted">Approve requirements draft</div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Gate status</Text>
              <div><StatusTag value="awaiting_decision" /></div>
            </div>
            <div style={{ marginTop: 12 }}>
              <Text className="muted">Prompt checksum</Text>
              <div className="mono">9bc12d...1af</div>
            </div>
            <div style={{ marginTop: 16 }}>
              <Title level={5}>Latest artifacts</Title>
              <List
                dataSource={[{ name: 'requirements-draft.md', version: 'v3' }]}
                renderItem={(item) => (
                  <List.Item>
                    <Text className="mono">{item.name}</Text>
                    <StatusTag value={item.version} />
                  </List.Item>
                )}
              />
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
