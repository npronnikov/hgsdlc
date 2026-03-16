export const flows = [
  {
    key: 'f1',
    name: 'feature-change-flow',
    flowId: 'feature-change-flow',
    description: 'Existing project feature implementation flow',
    status: 'published',
    version: '1.0.3',
    canonical: 'feature-change-flow@1.0.3',
  },
  {
    key: 'f2',
    name: 'refund-flow',
    flowId: 'refund-flow',
    description: 'Refund approval pipeline',
    status: 'draft',
    version: '0.9.2',
    canonical: 'refund-flow@0.9.2',
  },
];

export const rules = [
  {
    key: 'r1',
    name: 'project-rule',
    description: 'Execution constraints',
    codingAgent: 'qwen',
    status: 'published',
    version: '1.0.2',
    canonical: 'project-rule@1.0.2',
  },
  {
    key: 'r3',
    name: 'security-rule',
    description: 'Security hardening checks',
    codingAgent: 'qwen',
    status: 'published',
    version: '1.1.0',
    canonical: 'security-rule@1.1.0',
  },
  {
    key: 'r4',
    name: 'docs-rule',
    description: 'Documentation update guardrails',
    codingAgent: 'qwen',
    status: 'draft',
    version: '0.3.0',
    canonical: 'docs-rule@0.3.0',
  },
  {
    key: 'r2',
    name: 'sandbox-rule',
    description: 'Restricted experimentation',
    codingAgent: 'claude',
    status: 'draft',
    version: '0.4.0',
    canonical: 'sandbox-rule@0.4.0',
  },
];

export const skills = [
  {
    key: 's1',
    name: 'update-requirements',
    description: 'Update requirements from answers',
    codingAgent: 'qwen',
    status: 'published',
    version: '1.2.0',
    canonical: 'update-requirements@1.2.0',
  },
  {
    key: 's3',
    name: 'trace-changes',
    description: 'Summarize code changes with rationale',
    codingAgent: 'qwen',
    status: 'published',
    version: '1.0.1',
    canonical: 'trace-changes@1.0.1',
  },
  {
    key: 's4',
    name: 'validate-constraints',
    description: 'Check against rule constraints before delivery',
    codingAgent: 'qwen',
    status: 'draft',
    version: '0.2.0',
    canonical: 'validate-constraints@0.2.0',
  },
  {
    key: 's2',
    name: 'java-spring-coding',
    description: 'Implement in Spring Boot',
    codingAgent: 'cursor',
    status: 'draft',
    version: '0.9.1',
    canonical: 'java-spring-coding@0.9.1',
  },
];

export const recentRuns = [
  {
    key: 'run-0041',
    project: 'checkout-service',
    flow: 'feature-change-flow@1.0.0',
    status: 'waiting_gate',
  },
  {
    key: 'run-0040',
    project: 'billing-core',
    flow: 'refund-flow@1.2.3',
    status: 'completed',
  },
  {
    key: 'run-0039',
    project: 'admin-portal',
    flow: 'access-audit@0.9.1',
    status: 'failed',
  },
];

export const gates = [
  {
    key: 'g1',
    title: 'Approve requirements',
    run: 'run-0041',
    status: 'awaiting_decision',
    role: 'TECH_APPROVER',
  },
  {
    key: 'g2',
    title: 'Provide answers',
    run: 'run-0038',
    status: 'awaiting_input',
    role: 'PRODUCT_OWNER',
  },
];

export const auditEvents = [
  {
    key: 'a1',
    event: 'RUN_STARTED',
    detail: 'run-0041 · product_owner · 09:30',
    type: 'runtime',
  },
  {
    key: 'a2',
    event: 'NODE_SUCCEEDED',
    detail: 'intake-analysis · 09:40',
    type: 'AI',
  },
];
