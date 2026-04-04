import React, { useState } from 'react';
import { Layout, Menu, Input, Space, Tag, Avatar, Typography, Button, Dropdown } from 'antd';
import {
  AppstoreOutlined,
  ApartmentOutlined,
  AuditOutlined,
  DeploymentUnitOutlined,
  GithubOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MoonOutlined,
  LogoutOutlined,
  PlayCircleOutlined,
  ProjectOutlined,
  RobotOutlined,
  SettingOutlined,
  SunOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { useThemeMode } from '../theme/ThemeContext.jsx';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const BASE_NAV_ITEMS = [
  { key: '/overview', icon: <AppstoreOutlined />, label: 'Overview' },
  { key: '/projects', icon: <ProjectOutlined />, label: 'Projects' },
  { key: '/flows', icon: <ApartmentOutlined />, label: 'Flows' },
  { key: '/rules', icon: <AuditOutlined />, label: 'Rules' },
  { key: '/skills', icon: <RobotOutlined />, label: 'Skills' },
  { key: '/requests', icon: <GithubOutlined />, label: 'Requests' },
  { key: '/run-launch', icon: <PlayCircleOutlined />, label: 'Run Launch' },
  { key: '/run-console', icon: <DeploymentUnitOutlined />, label: 'Runs' },
  { key: '/settings', icon: <SettingOutlined />, label: 'Runtime Settings' },
];

const NAV_ITEM_ALLOWED_ROLES = {
  '/rules': ['ADMIN', 'FLOW_CONFIGURATOR', 'TECH_APPROVER'],
  '/skills': ['ADMIN', 'FLOW_CONFIGURATOR', 'TECH_APPROVER'],
  '/requests': ['ADMIN', 'FLOW_CONFIGURATOR', 'TECH_APPROVER'],
  '/settings': ['ADMIN', 'FLOW_CONFIGURATOR', 'TECH_APPROVER'],
};

const routeMeta = {
  '/overview': { title: 'Overview', menuKey: '/overview' },
  '/projects': { title: 'Projects', menuKey: '/projects' },
  '/flows': { title: 'Flows', menuKey: '/flows' },
  '/flow-editor': { title: 'Flow Editor', menuKey: '/flows' },
  '/rules': { title: 'Rules', menuKey: '/rules' },
  '/rule-editor': { title: 'Rule Editor', menuKey: '/rules' },
  '/skills': { title: 'Skills', menuKey: '/skills' },
  '/skill-editor': { title: 'Skill Editor', menuKey: '/skills' },
  '/requests': { title: 'Requests', menuKey: '/requests' },
  '/run-launch': { title: 'Run Launch', menuKey: '/run-launch' },
  '/run-console': { title: 'Run Console', menuKey: '/run-console' },
  '/settings': { title: 'Runtime Settings', menuKey: '/settings' },
  '/gates-inbox': { title: 'Gates Inbox', menuKey: '/run-console' },
  '/human-gate': { title: 'Human Gate', menuKey: '/run-console' },
  '/gate-input': { title: 'Human Input Gate', menuKey: '/run-console' },
  '/gate-approval': { title: 'Human Approval Gate', menuKey: '/run-console' },
  '/audit-runtime': { title: 'Runtime Audit', menuKey: '/run-console' },
  '/audit-agent': { title: 'Agent Audit', menuKey: '/run-console' },
  '/audit-review': { title: 'Review Audit', menuKey: '/run-console' },
  '/prompt-package': { title: 'Prompt Package', menuKey: '/run-console' },
  '/artifacts': { title: 'Artifacts', menuKey: '/run-console' },
  '/delta-summary': { title: 'Delta Summary', menuKey: '/run-console' },
  '/users': { title: 'Users', menuKey: '/users' },
};

export default function AppShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { isDark, toggleMode } = useThemeMode();
  const [collapsed, setCollapsed] = useState(true);
  const userRoles = user?.roles || [];
  const navItems = [
    ...BASE_NAV_ITEMS.filter((item) => {
      const allowedRoles = NAV_ITEM_ALLOWED_ROLES[item.key];
      if (!allowedRoles) {
        return true;
      }
      return allowedRoles.some((role) => userRoles.includes(role));
    }),
    ...(userRoles.includes('ADMIN')
      ? [{ key: '/users', icon: <TeamOutlined />, label: 'Users' }]
      : []),
  ];
  const ruleIdFromPath = location.pathname.startsWith('/rules/') ? location.pathname.split('/')[2] : null;
  const skillIdFromPath = location.pathname.startsWith('/skills/') ? location.pathname.split('/')[2] : null;
  const flowIdFromPath = location.pathname.startsWith('/flows/') ? location.pathname.split('/')[2] : null;
  const isRuleEditorRoute = location.pathname.startsWith('/rules/');
  const isSkillEditorRoute = location.pathname.startsWith('/skills/');
  const isFlowEditorRoute = location.pathname.startsWith('/flows/');
  const isHumanGateRoute = ['/human-gate', '/gate-approval', '/gate-input'].includes(location.pathname);
  const searchParams = new URLSearchParams(location.search || '');
  const runIdFromQuery = searchParams.get('runId');
  const gateKindFromQuery = (searchParams.get('gateKind') || '').toLowerCase();
  const humanGateTitle = gateKindFromQuery === 'human_approval'
    ? 'Human Approval Gate'
    : gateKindFromQuery === 'human_input'
      ? 'Human Input Gate'
      : location.pathname === '/gate-approval'
        ? 'Human Approval Gate'
        : location.pathname === '/gate-input'
          ? 'Human Input Gate'
          : 'Human Gate';
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
  const displayName = user?.username || 'User';
  const userMenuItems = [
    { key: 'settings', icon: <SettingOutlined />, label: 'Runtime Settings' },
    { key: 'logout', icon: <LogoutOutlined />, label: 'Logout' },
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
              {isHumanGateRoute ? (
                <>
                  <button
                    type="button"
                    onClick={() => navigate(runIdFromQuery ? `/run-console?runId=${runIdFromQuery}` : '/run-console')}
                  >
                    Run Console
                  </button>
                  <span>/</span>
                  <button type="button" onClick={() => navigate(location.pathname + location.search)}>
                    {humanGateTitle}
                  </button>
                </>
              ) : (
                <>
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
                </>
              )}
            </div>
          </Space>
          <Space size="middle" className="hg-header-actions">
            <Input placeholder="Search" allowClear />
            <span className="theme-toggle-wrap">
              <Button
                type="text"
                shape="circle"
                className="theme-toggle-btn"
                icon={isDark ? <SunOutlined /> : <MoonOutlined />}
                onClick={toggleMode}
                aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
                title={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
              />
            </span>
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
              <Space className="profile-menu-trigger" size={8}>
                <Avatar style={{ backgroundColor: '#4f46e5' }}>{initials}</Avatar>
                <Button type="text" className="profile-name-trigger">
                  {displayName}
                </Button>
              </Space>
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
