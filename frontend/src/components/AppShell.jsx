import React from 'react';
import { Layout, Menu, Input, Space, Tag, Avatar, Typography } from 'antd';
import {
  AppstoreOutlined,
  BranchesOutlined,
  DeploymentUnitOutlined,
  FileTextOutlined,
  NodeIndexOutlined,
  PlayCircleOutlined,
  ProjectOutlined,
  SafetyOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const navItems = [
  { key: '/overview', icon: <AppstoreOutlined />, label: 'Overview' },
  { key: '/projects', icon: <ProjectOutlined />, label: 'Projects' },
  { key: '/flows', icon: <BranchesOutlined />, label: 'Flows' },
  { key: '/rules', icon: <FileTextOutlined />, label: 'Rules' },
  { key: '/skills', icon: <NodeIndexOutlined />, label: 'Skills' },
  { key: '/run-launch', icon: <PlayCircleOutlined />, label: 'Run Launch' },
  { key: '/run-console', icon: <DeploymentUnitOutlined />, label: 'Runs' },
  { key: '/gates-inbox', icon: <SafetyOutlined />, label: 'Gates Inbox' },
  { key: '/audit-runtime', icon: <SettingOutlined />, label: 'Audit' },
  { key: '/versions', icon: <FileTextOutlined />, label: 'Versions / Snapshots' },
];

const routeMeta = {
  '/overview': { title: 'Overview', menuKey: '/overview' },
  '/projects': { title: 'Projects', menuKey: '/projects' },
  '/flows': { title: 'Flows', menuKey: '/flows' },
  '/flow-editor': { title: 'Flow Editor', menuKey: '/flows' },
  '/rules': { title: 'Rules', menuKey: '/rules' },
  '/rule-editor': { title: 'Rule Editor', menuKey: '/rules' },
  '/skills': { title: 'Skills', menuKey: '/skills' },
  '/skill-editor': { title: 'Skill Editor', menuKey: '/skills' },
  '/run-launch': { title: 'Run Launch', menuKey: '/run-launch' },
  '/run-console': { title: 'Run Console', menuKey: '/run-console' },
  '/gates-inbox': { title: 'Gates Inbox', menuKey: '/gates-inbox' },
  '/gate-input': { title: 'Human Input Gate', menuKey: '/gates-inbox' },
  '/gate-approval': { title: 'Human Approval Gate', menuKey: '/gates-inbox' },
  '/audit-runtime': { title: 'Runtime Audit', menuKey: '/audit-runtime' },
  '/audit-agent': { title: 'Agent Audit', menuKey: '/audit-runtime' },
  '/audit-review': { title: 'Review Audit', menuKey: '/audit-runtime' },
  '/prompt-package': { title: 'Prompt Package', menuKey: '/run-console' },
  '/artifacts': { title: 'Artifacts', menuKey: '/run-console' },
  '/delta-summary': { title: 'Delta Summary', menuKey: '/run-console' },
  '/versions': { title: 'Versions / Snapshots', menuKey: '/versions' },
};

export default function AppShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const meta = routeMeta[location.pathname] || { title: 'Overview', menuKey: '/overview' };
  const selectedKey = meta.menuKey;
  const title = meta.title;

  return (
    <Layout className="hg-layout">
      <Sider width={240} className="hg-sider">
        <div className="brand">
          <div className="brand-mark">HG</div>
          <div>
            <Text strong>HGSDLC</Text>
            <div className="muted">Control Tower</div>
          </div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={navItems}
          onClick={({ key }) => navigate(key)}
        />
        <div className="card-muted" style={{ marginTop: 'auto' }}>
          <Tag color="#94a3b8">v0.1</Tag>
          <Text className="muted">staging</Text>
        </div>
      </Sider>
      <Layout>
        <Header className="hg-header">
          <Space size="middle">
            <Text type="secondary">HGSDLC</Text>
            <Text type="secondary">/</Text>
            <Text strong>{title}</Text>
          </Space>
          <Space size="middle">
            <Input placeholder="Search" allowClear />
            <Tag color="#2563eb">STAGING</Tag>
            <Tag color="#4f46e5">FLOW_CONFIGURATOR</Tag>
            <Avatar style={{ backgroundColor: '#4f46e5' }}>NC</Avatar>
          </Space>
        </Header>
        <Content className="hg-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
