import React, { useState } from 'react';
import { Layout, Menu, Input, Space, Tag, Avatar, Typography, Button, Dropdown } from 'antd';
import {
  AppstoreOutlined,
  BranchesOutlined,
  DeploymentUnitOutlined,
  FileTextOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LogoutOutlined,
  NodeIndexOutlined,
  PlayCircleOutlined,
  ProjectOutlined,
  SafetyOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

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
  const { user, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(true);
  const ruleIdFromPath = location.pathname.startsWith('/rules/') ? location.pathname.split('/')[2] : null;
  const skillIdFromPath = location.pathname.startsWith('/skills/') ? location.pathname.split('/')[2] : null;
  const flowIdFromPath = location.pathname.startsWith('/flows/') ? location.pathname.split('/')[2] : null;
  const isRuleEditorRoute = location.pathname.startsWith('/rules/');
  const isSkillEditorRoute = location.pathname.startsWith('/skills/');
  const isFlowEditorRoute = location.pathname.startsWith('/flows/');
  const meta = routeMeta[location.pathname]
    || (isRuleEditorRoute ? { title: 'Rules', menuKey: '/rules' } : null)
    || (isSkillEditorRoute ? { title: 'Skills', menuKey: '/skills' } : null)
    || (isFlowEditorRoute ? { title: 'Flows', menuKey: '/flows' } : null)
    || { title: 'Overview', menuKey: '/overview' };
  const selectedKey = meta.menuKey;
  const title = meta.title;
  const initials = user?.username ? user.username.slice(0, 2).toUpperCase() : 'HG';
  const showRuleIdCrumb = isRuleEditorRoute && ruleIdFromPath && ruleIdFromPath !== 'create';
  const showSkillIdCrumb = isSkillEditorRoute && skillIdFromPath && skillIdFromPath !== 'create';
  const showFlowIdCrumb = isFlowEditorRoute && flowIdFromPath && flowIdFromPath !== 'create';
  const displayName = user?.username || 'Пользователь';
  const userMenuItems = [
    { key: 'settings', icon: <SettingOutlined />, label: 'Настройки' },
    { key: 'logout', icon: <LogoutOutlined />, label: 'Выход' },
  ];

  return (
    <Layout className="hg-layout">
      <Sider
        width={240}
        collapsedWidth={72}
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        trigger={null}
        className="hg-sider"
      >
        <div className={`brand ${collapsed ? 'brand-collapsed' : ''}`}>
          <button
            className="sider-brand-link"
            type="button"
            onClick={() => navigate('/overview')}
          >
            <div className="brand-mark">HG</div>
            {!collapsed && (
            <div>
              <Text strong>SDLC</Text>
              <div className="muted">Control Tower</div>
            </div>
            )}
          </button>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={navItems}
          onClick={({ key }) => navigate(key)}
          inlineCollapsed={collapsed}
        />
        <div className={`card-muted sider-footer ${collapsed ? 'sider-footer-collapsed' : ''}`}>
          <Tag color="#94a3b8">v0.1</Tag>
          {!collapsed && <Text className="muted">staging</Text>}
        </div>
      </Sider>
      <Layout>
        <Header className="hg-header">
          <Space size="middle">
            <button
              className="header-drawer-toggle"
              type="button"
              onClick={() => setCollapsed((value) => !value)}
              aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            >
              {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            </button>
            <div className="hg-breadcrumbs">
              <button type="button" onClick={() => navigate('/overview')}>SDLC</button>
              <span>/</span>
              <button type="button" onClick={() => navigate(selectedKey)}>{title}</button>
              {showRuleIdCrumb && (
                <>
                  <span>/</span>
                  <button type="button" onClick={() => navigate(location.pathname)}>{ruleIdFromPath}</button>
                </>
              )}
              {showSkillIdCrumb && (
                <>
                  <span>/</span>
                  <button type="button" onClick={() => navigate(location.pathname)}>{skillIdFromPath}</button>
                </>
              )}
              {showFlowIdCrumb && (
                <>
                  <span>/</span>
                  <button type="button" onClick={() => navigate(location.pathname)}>{flowIdFromPath}</button>
                </>
              )}
            </div>
          </Space>
          <Space size="middle">
            <Input placeholder="Search" allowClear />
            <Avatar style={{ backgroundColor: '#4f46e5' }}>{initials}</Avatar>
            <Dropdown
              trigger={['hover']}
              menu={{
                items: userMenuItems,
                onClick: ({ key }) => {
                  if (key === 'logout') {
                    logout();
                  }
                },
              }}
            >
              <Button type="text" className="profile-name-trigger">
                {displayName}
              </Button>
            </Dropdown>
          </Space>
        </Header>
        <Content className="hg-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
