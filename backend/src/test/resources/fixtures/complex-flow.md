---
id: complex-flow
name: Complex Flow
version: 2.0.0
description: A complex flow demonstrating all available features
startRoles:
  - developer
  - tech_lead
  - product_owner
resumePolicy: FROM_CHECKPOINT
phases:
  - id: initialization
    name: Initialization
    order: 0
  - id: development
    name: Development
    order: 1
  - id: review
    name: Review
    order: 2
  - id: deployment
    name: Deployment
    order: 3
artifacts:
  - id: source-code
    name: Source Code
    logicalRole: SOURCE_CODE
    required: true
  - id: test-results
    name: Test Results
    logicalRole: TEST_ARTIFACT
    required: false
  - id: coverage-report
    name: Coverage Report
    logicalRole: TEST_ARTIFACT
    required: false
  - id: deployment-package
    name: Deployment Package
    logicalRole: BUILD_ARTIFACT
    required: true
---

# Complex Flow

A comprehensive flow demonstrating all available features of the Human-Guided SDLC platform.

## Overview

This flow showcases:

- Multiple phases with different purposes
- Gate nodes for approvals
- Executor nodes with different handler types
- Artifact bindings
- Conditional transitions

## Initialization Phase

Set up the environment and prepare for development.

### Steps

1. Initialize project structure
2. Configure dependencies
3. Set up CI/CD pipeline

## Development Phase

Implement the required features.

### Steps

1. Create feature branches
2. Implement changes
3. Write tests
4. Run local validation

### Artifacts Produced

- `source-code`: The implementation
- `test-results`: Unit test results
- `coverage-report`: Code coverage metrics

## Review Phase

Review and approve the implementation.

### Gates

- **Code Review Gate**: Requires tech lead approval
- **QA Gate**: Requires QA sign-off

### Activities

1. Automated code analysis
2. Peer review
3. QA testing
4. Security review

## Deployment Phase

Deploy to production.

### Steps

1. Deploy to staging
2. Run smoke tests
3. Get production approval
4. Deploy to production
5. Monitor deployment

### Artifacts Produced

- `deployment-package`: The deployable artifact

## Transitions

| From | To | Condition | Type |
|------|-----|-----------|------|
| init | develop | - | forward |
| develop | code-review | - | forward |
| code-review | qa-gate | approved | forward |
| code-review | develop | rejected | rework |
| qa-gate | deploy-staging | approved | forward |
| qa-gate | develop | issues_found | rework |
| deploy-staging | smoke-test | - | forward |
| smoke-test | deploy-prod | passed | forward |
| smoke-test | develop | failed | rework |
| deploy-prod | monitor | - | forward |

## Best Practices

1. Always run tests before requesting review
2. Address all review feedback before proceeding
3. Monitor deployment closely after promotion
4. Roll back quickly if issues are detected
