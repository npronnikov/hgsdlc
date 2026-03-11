---
id: multi-phase-flow
name: Multi-Phase Flow
version: 1.0.0
description: A flow with multiple sequential phases
startRoles:
  - developer
  - tech_lead
phases:
  - id: development
    name: Development
    order: 0
  - id: review
    name: Code Review
    order: 1
  - id: deployment
    name: Deployment
    order: 2
---

# Multi-Phase Flow

This flow demonstrates a typical SDLC process with multiple phases.

## Development Phase

In this phase, developers write and test code locally.

### Activities

1. Create feature branch
2. Implement changes
3. Write unit tests
4. Run local tests

## Review Phase

Code review and quality assurance.

### Activities

1. Create pull request
2. Automated checks run
3. Peer review
4. Address feedback

## Deployment Phase

Deploy to production after approval.

### Activities

1. Merge to main branch
2. Deploy to staging
3. Run integration tests
4. Deploy to production
