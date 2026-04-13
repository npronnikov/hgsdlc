---
# Field: name
# Why: unique skill slug.
# Example: sql-query-audit
name: <skill-name-in-kebab-case>

# Field: description
# Why: Gigacode uses this to decide when the skill should be auto-applied.
# Example:
# Reviews SQL queries for correctness, readability, and performance risks.
# Use for SELECT/JOIN/CTE/index related work and SQL reviews.
description: >-
  <what this skill does and for which requests/tasks to apply it>
---

# <Skill name>

## Purpose
<!-- Why: describe the goal in one paragraph. -->
<!-- Example:
This skill audits SQL queries, finds heavy joins, redundant subqueries,
and full-scan risks.
-->

## Workflow
<!-- Why: give Gigacode a clear sequence of actions. -->
<!-- Example:
1. Find SQL files, ORM queries, and migrations.
2. Identify tables, joins, filters, and sorting.
3. Check indexes and potential full scans.
4. Produce recommendations.
-->

## What to check
<!-- Why: list evaluation criteria. -->
<!-- Example:
- join correctness;
- filtering on indexed columns;
- LIMIT/OFFSET use;
- N+1 patterns;
- CTE readability;
- parameter safety.
-->

## Constraints
<!-- Why: prevent unnecessary assumptions. -->
<!-- Example:
- Do not invent DB schema if it is not present in the project.
- If no execution plan is available, avoid hard claims.
-->

## Response format
<!-- Why: standardize output. -->
<!-- Example:
Return sections:
- summary;
- issues found;
- priority;
- proposed rewritten query.
-->

## Request examples
<!-- Example:
- "Review this SQL for performance"
- "Review a complex SELECT with multiple JOINs"
-->
