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
import RunLaunch from './pages/RunLaunch.jsx';
import RunConsole from './pages/RunConsole.jsx';
import GatesInbox from './pages/GatesInbox.jsx';
import GateInput from './pages/GateInput.jsx';
import GateApproval from './pages/GateApproval.jsx';
import AuditRuntime from './pages/AuditRuntime.jsx';
import AuditAgent from './pages/AuditAgent.jsx';
import AuditReview from './pages/AuditReview.jsx';
import PromptPackage from './pages/PromptPackage.jsx';
import Artifacts from './pages/Artifacts.jsx';
import DeltaSummary from './pages/DeltaSummary.jsx';
import Versions from './pages/Versions.jsx';
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
            <Route path="flow-editor" element={<FlowEditor />} />
            <Route path="rules" element={<Rules />} />
            <Route path="rules/create" element={<RuleEditor />} />
            <Route path="rules/:ruleId" element={<RuleEditor />} />
            <Route path="rule-editor" element={<RuleEditor />} />
            <Route path="skills" element={<Skills />} />
            <Route path="skills/create" element={<SkillEditor />} />
            <Route path="skills/:skillId" element={<SkillEditor />} />
            <Route path="skill-editor" element={<SkillEditor />} />
            <Route path="run-launch" element={<RunLaunch />} />
            <Route path="run-console" element={<RunConsole />} />
            <Route path="gates-inbox" element={<GatesInbox />} />
            <Route path="gate-input" element={<GateInput />} />
            <Route path="gate-approval" element={<GateApproval />} />
            <Route path="audit-runtime" element={<AuditRuntime />} />
            <Route path="audit-agent" element={<AuditAgent />} />
            <Route path="audit-review" element={<AuditReview />} />
            <Route path="prompt-package" element={<PromptPackage />} />
            <Route path="artifacts" element={<Artifacts />} />
            <Route path="delta-summary" element={<DeltaSummary />} />
            <Route path="versions" element={<Versions />} />
          </Route>
          <Route path="*" element={<Navigate to="/overview" replace />} />
        </Routes>
      </HashRouter>
    </AuthProvider>
  );
}
