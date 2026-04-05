import React from 'react';
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Spin } from 'antd';
import AppShell from './components/AppShell.jsx';
import Login from './pages/Login.jsx';
import Overview from './pages/Overview.jsx';
import Projects from './pages/Projects.jsx';
import Flows from './pages/Flows.jsx';
import FlowEditor from './pages/FlowEditor.jsx';
import Rules from './pages/Rules.jsx';
import RuleEditor from './pages/RuleEditor.jsx';
import Skills from './pages/Skills.jsx';
import SkillEditor from './pages/SkillEditor.jsx';
import Requests from './pages/Requests.jsx';
import RunLaunch from './pages/RunLaunch.jsx';
import RunConsole from './pages/RunConsole.jsx';
import GatesInbox from './pages/GatesInbox.jsx';
import HumanGate from './pages/HumanGate.jsx';
import Settings from './pages/Settings.jsx';
import AuditRuntime from './pages/AuditRuntime.jsx';
import AuditAgent from './pages/AuditAgent.jsx';
import AuditReview from './pages/AuditReview.jsx';
import PromptPackage from './pages/PromptPackage.jsx';
import Artifacts from './pages/Artifacts.jsx';
import DeltaSummary from './pages/DeltaSummary.jsx';
import Users from './pages/Users.jsx';
import ProductPipelineMvp from './pages/ProductPipelineMvp.jsx';
import { AuthProvider, useAuth } from './auth/AuthContext.jsx';

function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireRole({ role, children }) {
  const { user } = useAuth();
  if (!user?.roles?.includes(role)) {
    return <Navigate to="/overview" replace />;
  }
  return children;
}

function RequireAnyRole({ roles, children }) {
  const { user } = useAuth();
  const hasAllowedRole = roles.some((role) => user?.roles?.includes(role));
  if (!hasAllowedRole) {
    return <Navigate to="/overview" replace />;
  }
  return children;
}

export default function App() {
  return (
    <AuthProvider>
      <HashRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <AppShell />
              </RequireAuth>
            }
          >
            <Route index element={<Navigate to="/overview" replace />} />
            <Route path="overview" element={<Overview />} />
            <Route path="projects" element={<Projects />} />
            <Route path="flows" element={<Flows />} />
            <Route
              path="flows/create"
              element={(
                <RequireAnyRole roles={['ADMIN', 'FLOW_CONFIGURATOR']}>
                  <FlowEditor />
                </RequireAnyRole>
              )}
            />
            <Route path="flows/:flowId" element={<FlowEditor />} />
            <Route path="flow-editor" element={<FlowEditor />} />
            <Route path="rules" element={<Rules />} />
            <Route
              path="rules/create"
              element={(
                <RequireAnyRole roles={['ADMIN', 'FLOW_CONFIGURATOR']}>
                  <RuleEditor />
                </RequireAnyRole>
              )}
            />
            <Route path="rules/:ruleId" element={<RuleEditor />} />
            <Route path="rule-editor" element={<RuleEditor />} />
            <Route path="skills" element={<Skills />} />
            <Route
              path="skills/create"
              element={(
                <RequireAnyRole roles={['ADMIN', 'FLOW_CONFIGURATOR']}>
                  <SkillEditor />
                </RequireAnyRole>
              )}
            />
            <Route path="skills/:skillId" element={<SkillEditor />} />
            <Route path="skill-editor" element={<SkillEditor />} />
            <Route path="requests" element={<Requests />} />
            <Route path="run-launch" element={<RunLaunch />} />
            <Route path="run-console" element={<RunConsole />} />
            <Route path="settings" element={<Settings />} />
            <Route path="gates-inbox" element={<GatesInbox />} />
            <Route path="gate-input" element={<HumanGate />} />
            <Route path="gate-approval" element={<HumanGate />} />
            <Route path="human-gate" element={<HumanGate />} />
            <Route path="audit-runtime" element={<AuditRuntime />} />
            <Route path="audit-agent" element={<AuditAgent />} />
            <Route path="audit-review" element={<AuditReview />} />
            <Route path="prompt-package" element={<PromptPackage />} />
            <Route path="artifacts" element={<Artifacts />} />
            <Route path="delta-summary" element={<DeltaSummary />} />
            <Route path="product-pipeline" element={<ProductPipelineMvp />} />
            <Route
              path="users"
              element={
                <RequireRole role="ADMIN">
                  <Users />
                </RequireRole>
              }
            />
            <Route path="publication-queue" element={<Navigate to="/requests" replace />} />
          </Route>
          <Route path="*" element={<Navigate to="/overview" replace />} />
        </Routes>
      </HashRouter>
    </AuthProvider>
  );
}
