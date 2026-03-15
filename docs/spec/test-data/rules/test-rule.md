---
id: test-rule
version: 1.0.0
canonical_name: test-rule@1.0.0
title: Test execution rule
response_schema_id: agent-response-v1
allowed_paths:
  - src/**
  - docs/**
  - .hgwork/**
forbidden_paths:
  - .git/**
  - .qwen/**
  - .hgwork/system/**
allowed_commands:
  - git_commit
require_structured_response: true
---

# Test rule

Keep changes limited to the allowed paths, and return structured agent responses.
