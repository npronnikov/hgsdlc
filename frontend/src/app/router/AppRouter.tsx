import { Routes, Route, Navigate } from 'react-router-dom';

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/flows" replace />} />
      <Route path="/flows" element={<div>Flow Catalog</div>} />
      <Route path="/flows/:flowId" element={<div>Flow Details</div>} />
      <Route path="/runs" element={<div>Run List</div>} />
      <Route path="/runs/:runId" element={<div>Run Details</div>} />
      <Route path="/skills" element={<div>Skill Catalog</div>} />
      <Route path="*" element={<Navigate to="/flows" replace />} />
    </Routes>
  );
}
