const DEFAULT_FALLBACK = 'An error occurred while saving. Check your data and try again.';

const RULES = [
  { re: /resource_version mismatch for publish/i, msg: 'Resource version mismatch for publish' },
  { re: /resource_version mismatch for draft/i, msg: 'Resource version mismatch for draft' },
  { re: /Request body is required/i, msg: 'Request body is required' },
  { re: /Authenticated user is required/i, msg: 'Authenticated user is required' },
  { re: /Unsupported coding_agent: (.+)/i, msg: (_, agent) => `Unsupported coding_agent: ${agent}` },

  { re: /rule_markdown is required/i, msg: 'Rule markdown is required' },
  { re: /title is required/i, msg: 'Title is required' },
  { re: /rule_id is required/i, msg: 'Rule ID is required' },
  { re: /rule_id has invalid format/i, msg: 'Rule ID has an invalid format' },
  { re: /Path ruleId does not match request rule_id/i, msg: 'Path ruleId does not match request rule_id' },
  { re: /coding_agent is required/i, msg: 'Coding agent is required' },
  { re: /Frontmatter is required for publish/i, msg: 'Frontmatter is required for publish' },
  {
    re: /Frontmatter field '(.+)' is required for coding_agent (.+)/i,
    msg: (_, field, agent) => `Frontmatter field '${field}' is required for coding_agent ${agent}`,
  },
  { re: /Frontmatter field '(.+)' must not be blank/i, msg: (_, field) => `Frontmatter field '${field}' must not be blank` },
  { re: /Frontmatter field '(.+)' must not be empty/i, msg: (_, field) => `Frontmatter field '${field}' must not be empty` },
  { re: /Rule not found: (.+)/i, msg: (_, id) => `Rule not found: ${id}` },
  { re: /Rule version not found: (.+)/i, msg: (_, id) => `Rule version not found: ${id}` },

  { re: /files are required/i, msg: 'Skill files are required' },
  { re: /package must contain exactly one SKILL\.md/i, msg: 'Package must contain exactly one SKILL.md' },
  { re: /duplicate file path: (.+)/i, msg: (_, path) => `Duplicate file path: ${path}` },
  { re: /name is required/i, msg: 'Name is required' },
  { re: /description is required/i, msg: 'Description is required' },
  { re: /skill_id is required/i, msg: 'Skill ID is required' },
  { re: /skill_id has invalid format/i, msg: 'Skill ID has an invalid format' },
  { re: /Path skillId does not match request skill_id/i, msg: 'Path skillId does not match request skill_id' },
  { re: /Skill not found: (.+)/i, msg: (_, id) => `Skill not found: ${id}` },
  { re: /Skill version not found: (.+)/i, msg: (_, id) => `Skill version not found: ${id}` },

  { re: /flow_yaml is required/i, msg: 'Flow YAML is required' },
  { re: /flow_id is required/i, msg: 'Flow ID is required' },
  { re: /flow_id has invalid format/i, msg: 'Flow ID has an invalid format' },
  { re: /Path flowId does not match request flow_id/i, msg: 'Path flowId does not match request flow_id' },
  { re: /flow id is required in flow_yaml/i, msg: 'flow_yaml must contain id' },
  { re: /flow_yaml id does not match path flowId/i, msg: 'flow_yaml id does not match path flowId' },
  { re: /title is required in flow_yaml/i, msg: 'flow_yaml must contain title' },
  { re: /status is required in flow_yaml/i, msg: 'flow_yaml must contain status' },
  { re: /start_node_id is required in flow_yaml/i, msg: 'flow_yaml must contain start_node_id' },
  { re: /coding_agent is required in flow_yaml/i, msg: 'flow_yaml must contain coding_agent' },
  { re: /flow_yaml status must be published for publish/i, msg: 'flow_yaml status must be published for publish' },
  { re: /flow_yaml status must be draft for save/i, msg: 'flow_yaml status must be draft for save' },
  { re: /Referenced rules not published: (.+)/i, msg: (_, refs) => `Referenced rules are not published: ${refs}` },
  { re: /Referenced skills not published: (.+)/i, msg: (_, refs) => `Referenced skills are not published: ${refs}` },
  { re: /Referenced rules mismatch coding_agent: (.+)/i, msg: (_, refs) => `Referenced rules mismatch coding_agent: ${refs}` },
  { re: /Referenced skills mismatch coding_agent: (.+)/i, msg: (_, refs) => `Referenced skills mismatch coding_agent: ${refs}` },
  { re: /Flow not found: (.+)/i, msg: (_, id) => `Flow not found: ${id}` },
  { re: /Flow version not found: (.+)/i, msg: (_, id) => `Flow version not found: ${id}` },
  { re: /Invalid flow_yaml: (.+)/i, msg: (_, detail) => `Invalid flow_yaml: ${detail}` },
  { re: /Unable to parse flow_yaml: (.+)/i, msg: (_, detail) => `Unable to parse flow_yaml: ${detail}` },
  {
    re: /Flow coding_agent does not match runtime settings: flow=(.+), runtime=(.+)/i,
    msg: (_, flowAgent, runtimeAgent) => `Flow coding_agent (${flowAgent}) does not match runtime settings (${runtimeAgent})`,
  },
  { re: /Invalid version: (.+)/i, msg: (_, version) => `Invalid version: ${version}` },
  { re: /Unsupported node type or kind/i, msg: 'Unsupported node type or kind' },
  { re: /Flow model is required/i, msg: 'Flow model is required' },
  { re: /Flow has no nodes/i, msg: 'Flow has no nodes' },
  { re: /Node id is required/i, msg: 'Node ID is required' },
  { re: /Duplicate node id: (.+)/i, msg: (_, id) => `Duplicate node ID: ${id}` },
  { re: /start_node_id does not exist: (.+)/i, msg: (_, id) => `start_node_id does not exist: ${id}` },
  { re: /Unreachable node: (.+)/i, msg: (_, id) => `Unreachable node: ${id}` },
  { re: /Node type is required: (.+)/i, msg: (_, id) => `Node type is required: ${id}` },
  { re: /node_kind is required: (.+)/i, msg: (_, id) => `node_kind is required: ${id}` },
  { re: /Unsupported node_kind for executor: (.+)/i, msg: (_, id) => `Unsupported node_kind for executor: ${id}` },
  { re: /Unsupported node_kind for gate: (.+)/i, msg: (_, id) => `Unsupported node_kind for gate: ${id}` },
  { re: /Unsupported node type: (.+)/i, msg: (_, id) => `Unsupported node type: ${id}` },
  { re: /execution_context is required: (.+)/i, msg: (_, id) => `execution_context is required: ${id}` },
  { re: /execution_context entry is required: (.+)/i, msg: (_, id) => `execution_context entry is required: ${id}` },
  { re: /execution_context type is required: (.+)/i, msg: (_, id) => `execution_context type is required: ${id}` },
  { re: /Unsupported execution_context type: (.+)/i, msg: (_, id) => `Unsupported execution_context type: ${id}` },
  { re: /execution_context required flag is missing: (.+)/i, msg: (_, id) => `execution_context required flag is missing: ${id}` },
  { re: /human_input execution_context modifiable flag is missing: (.+)/i, msg: (_, id) => `human_input execution_context modifiable flag is missing: ${id}` },
  { re: /produced_artifacts modifiable=true is not supported for ai\/command nodes: (.+)/i, msg: (_, id) => `produced_artifacts modifiable=true is not supported for ai/command nodes: ${id}` },
  { re: /user_request must not define path: (.+)/i, msg: (_, id) => `user_request must not define path: ${id}` },
  { re: /execution_context path is required: (.+)/i, msg: (_, id) => `execution_context path is required: ${id}` },
  { re: /produced_artifacts entry is required: (.+)/i, msg: (_, id) => `produced_artifacts entry is required: ${id}` },
  { re: /produced_artifacts required flag is missing: (.+)/i, msg: (_, id) => `produced_artifacts required flag is missing: ${id}` },
  { re: /produced_artifacts path is required: (.+)/i, msg: (_, id) => `produced_artifacts path is required: ${id}` },
  { re: /expected_mutations entry is required: (.+)/i, msg: (_, id) => `expected_mutations entry is required: ${id}` },
  { re: /expected_mutations required flag is missing: (.+)/i, msg: (_, id) => `expected_mutations required flag is missing: ${id}` },
  { re: /expected_mutations path is required: (.+)/i, msg: (_, id) => `expected_mutations path is required: ${id}` },
  { re: /skill_refs only allowed for AI nodes: (.+)/i, msg: (_, id) => `skill_refs are allowed only for AI nodes: ${id}` },
  { re: /Start AI node requires instruction: (.+)/i, msg: (_, id) => `Start AI node requires instruction: ${id}` },
  { re: /Executor node requires on_success: (.+)/i, msg: (_, id) => `Executor node requires on_success: ${id}` },
  { re: /human_input gate requires on_submit: (.+)/i, msg: (_, id) => `human_input gate requires on_submit: ${id}` },
  {
    re: /human_input requires at least one execution_context artifact_ref with modifiable=true from predecessor nodes: (.+)/i,
    msg: (_, detail) => `human_input requires at least one execution_context artifact_ref with modifiable=true from predecessor nodes: ${detail}`,
  },
  {
    re: /human_input produced_artifacts missing required artifact from execution_context modifiable set: (.+)/i,
    msg: (_, detail) => `human_input produced_artifacts missing required artifact from execution_context modifiable set: ${detail}`,
  },
  {
    re: /human_input produced_artifacts extra artifact not found in execution_context modifiable set: (.+)/i,
    msg: (_, detail) => `human_input produced_artifacts extra artifact not found in execution_context modifiable set: ${detail}`,
  },
  {
    re: /human_input produced_artifacts collision in execution_context modifiable artifacts: (.+)/i,
    msg: (_, detail) => `human_input produced_artifacts collision in execution_context modifiable artifacts: ${detail}`,
  },
  { re: /human_approval gate requires on_approve: (.+)/i, msg: (_, id) => `human_approval gate requires on_approve: ${id}` },
  { re: /human_approval gate requires on_rework: (.+)/i, msg: (_, id) => `human_approval gate requires on_rework: ${id}` },
  { re: /checkpoint_before_run is only allowed for ai\/command nodes: (.+)/i, msg: (_, id) => `checkpoint_before_run is allowed only for ai/command nodes: ${id}` },
  { re: /CHECKPOINT_NOT_FOUND_FOR_REWORK:(.+)/i, msg: (_, detail) => `Checkpoint not found for rework: ${detail}` },
  { re: /REWORK_RESET_FAILED:(.+)/i, msg: (_, detail) => `Failed to roll back changes before rework: ${detail}` },
  { re: /Invalid transition target for (.+) on node: (.+)/i, msg: (_, route, id) => `Invalid transition ${route} on node: ${id}` },
  {
    re: /Transition target not found for (.+) on node: (.+) -> (.+)/i,
    msg: (_, route, id, target) => `Transition target not found for ${route} on node: ${id} -> ${target}`,
  },
];

export function toRussianError(raw, fallback = DEFAULT_FALLBACK) {
  const text = raw == null ? '' : String(raw).trim();
  if (!text) {
    return fallback;
  }
  if (/[\u0400-\u04FF]/.test(text)) {
    return text;
  }
  for (const entry of RULES) {
    const match = text.match(entry.re);
    if (match) {
      return typeof entry.msg === 'function' ? entry.msg(...match) : entry.msg;
    }
  }
  return fallback;
}
