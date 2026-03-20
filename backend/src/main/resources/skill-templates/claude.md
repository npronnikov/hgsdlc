---
# Field: name
# Why: skill name in Claude Code; if omitted, folder name is used.
# Example: release-notes
name: <skill-name-in-kebab-case>

# Field: description
# Why: describes what the skill does and when to use it.
# Example:
# Prepares release notes from git history and PRs. Use before release,
# while preparing changelog and version description.
description: >-
  <what this skill does and in which scenarios it should be used>

# Field: argument-hint
# Why: suggests argument format in autocomplete.
# Example: [version] [from-ref] [to-ref]
argument-hint: <optional>

# Field: disable-model-invocation
# Why: true = Claude does NOT auto-call this skill; manual only via /skill-name.
# Example: true
disable-model-invocation: <true|false>

# Field: user-invocable
# Why: false = hide skill from user slash-menu.
# Example: true
user-invocable: <true|false>

# Field: allowed-tools
# Why: restrict/allow tools during skill execution.
# Example: Bash(git *), Read, Glob, Grep
allowed-tools: <optional>

# Field: model
# Why: choose model for this skill.
# Example: sonnet
model: <optional>

# Field: context
# Why: fork = run skill in isolated subagent context.
# Example: fork
context: <optional>

# Field: agent
# Why: choose subagent type when context: fork.
# Example: Explore
agent: <optional>

# Field: hooks
# Why: attach lifecycle hooks to this specific skill.
# Example: see block below
hooks:
  PreToolUse:
    - matcher: "Bash"
      hooks:
        - type: command
          command: "./scripts/precheck.sh"
---

# <Skill name>

## Goal
<!-- Why: briefly explain business outcome. -->
<!-- Example:
Assemble release notes from git history, PRs, conventional commits, and changelog files.
-->

## Inputs
<!-- Why: define expected inputs. -->
<!-- Example:
- release version;
- commit range;
- milestone id if needed.
-->

## Steps
<!-- Why: provide deterministic workflow for Claude. -->
<!-- Example:
1. Get commits in the range.
2. Group changes by category.
3. Highlight breaking changes.
4. Generate human-readable release notes.
-->

## Output format
<!-- Why: normalize response structure. -->
<!-- Example:
Return:
- release title;
- new features;
- fixes;
- breaking changes;
- migration notes.
-->

## Constraints
<!-- Why: prevent unsafe/unwanted actions. -->
<!-- Example:
- Do not publish releases automatically.
- Do not rewrite git history.
-->
