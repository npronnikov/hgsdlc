# Example Skills

This directory contains example skill definitions.

## Structure

Each skill is defined in a `.md` file following the skill specification.

## Example

```markdown
# Initialize Project

## Metadata
- ID: initialize-project
- Version: 1.0.0
- Handler: builtin://initialize

## Input Schema
- project_name: string (required)
- template: string (optional)

## Output Schema
- project_id: string
- status: string
```
