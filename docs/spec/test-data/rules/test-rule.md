---
id: test-rule
version: 1.0.0
canonical_name: test-rule@1.0.0
title: Test execution rule
description: Rule for validating test flows in development.
response_schema: {}
allowed_paths:
  - src/**
  - docs/**
  - .hgsdlc/**
forbidden_paths:
  - .git/**
  - .qwen/**
  - .hgsdlc/system/**
allowed_commands:
  - git_commit
require_structured_response: true
---

# Test rule

Keep changes limited to the allowed paths, and return structured agent responses.
