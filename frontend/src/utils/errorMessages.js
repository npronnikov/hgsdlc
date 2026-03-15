const DEFAULT_FALLBACK = 'Произошла ошибка при сохранении. Проверьте данные и попробуйте ещё раз.';

const RULES = [
  { re: /resource_version mismatch for publish/i, msg: 'Версия ресурса не совпадает для публикации' },
  { re: /resource_version mismatch for draft/i, msg: 'Версия ресурса не совпадает для черновика' },
  { re: /Request body is required/i, msg: 'Требуется тело запроса' },
  { re: /Authenticated user is required/i, msg: 'Нужна авторизация пользователя' },
  { re: /Unsupported coding_agent: (.+)/i, msg: (_, agent) => `Неподдерживаемый coding_agent: ${agent}` },

  { re: /rule_markdown is required/i, msg: 'Нужен markdown Rule' },
  { re: /title is required/i, msg: 'Нужно название' },
  { re: /rule_id is required/i, msg: 'Нужен ID Rule' },
  { re: /rule_id has invalid format/i, msg: 'ID Rule имеет неверный формат' },
  { re: /Path ruleId does not match request rule_id/i, msg: 'ID Rule в пути не совпадает с rule_id в запросе' },
  { re: /coding_agent is required/i, msg: 'Нужен Кодинг-агент' },
  { re: /Frontmatter is required for publish/i, msg: 'Для публикации нужен frontmatter' },
  {
    re: /Frontmatter field '(.+)' is required for coding_agent (.+)/i,
    msg: (_, field, agent) => `В frontmatter отсутствует поле '${field}' для coding_agent ${agent}`,
  },
  { re: /Frontmatter field '(.+)' must not be blank/i, msg: (_, field) => `Поле frontmatter '${field}' не должно быть пустым` },
  { re: /Frontmatter field '(.+)' must not be empty/i, msg: (_, field) => `Поле frontmatter '${field}' не должно быть пустым` },
  { re: /Rule not found: (.+)/i, msg: (_, id) => `Rule не найден: ${id}` },
  { re: /Rule version not found: (.+)/i, msg: (_, id) => `Версия Rule не найдена: ${id}` },

  { re: /skill_markdown is required/i, msg: 'Нужен markdown Skill' },
  { re: /name is required/i, msg: 'Нужно название' },
  { re: /description is required/i, msg: 'Нужно описание' },
  { re: /skill_id is required/i, msg: 'Нужен ID Skill' },
  { re: /skill_id has invalid format/i, msg: 'ID Skill имеет неверный формат' },
  { re: /Path skillId does not match request skill_id/i, msg: 'ID Skill в пути не совпадает с skill_id в запросе' },
  { re: /Skill not found: (.+)/i, msg: (_, id) => `Skill не найден: ${id}` },
  { re: /Skill version not found: (.+)/i, msg: (_, id) => `Версия Skill не найдена: ${id}` },

  { re: /flow_yaml is required/i, msg: 'Нужен YAML Flow' },
  { re: /flow_id is required/i, msg: 'Нужен ID Flow' },
  { re: /flow_id has invalid format/i, msg: 'ID Flow имеет неверный формат' },
  { re: /Path flowId does not match request flow_id/i, msg: 'ID Flow в пути не совпадает с flow_id в запросе' },
  { re: /flow id is required in flow_yaml/i, msg: 'В flow_yaml нужен id' },
  { re: /flow_yaml id does not match path flowId/i, msg: 'ID в flow_yaml не совпадает с ID в пути' },
  { re: /title is required in flow_yaml/i, msg: 'В flow_yaml нужно title' },
  { re: /status is required in flow_yaml/i, msg: 'В flow_yaml нужен status' },
  { re: /start_node_id is required in flow_yaml/i, msg: 'В flow_yaml нужен start_node_id' },
  { re: /coding_agent is required in flow_yaml/i, msg: 'В flow_yaml нужен coding_agent' },
  { re: /flow_yaml status must be published for publish/i, msg: 'Для публикации в flow_yaml должен быть status=published' },
  { re: /flow_yaml status must be draft for save/i, msg: 'Для сохранения в flow_yaml должен быть status=draft' },
  { re: /Referenced rules not published: (.+)/i, msg: (_, refs) => `Ссылки на Rule не опубликованы: ${refs}` },
  { re: /Referenced skills not published: (.+)/i, msg: (_, refs) => `Ссылки на Skill не опубликованы: ${refs}` },
  { re: /Flow not found: (.+)/i, msg: (_, id) => `Flow не найден: ${id}` },
  { re: /Flow version not found: (.+)/i, msg: (_, id) => `Версия Flow не найдена: ${id}` },
  { re: /Invalid flow_yaml: (.+)/i, msg: (_, detail) => `Некорректный flow_yaml: ${detail}` },
  { re: /Unable to parse flow_yaml: (.+)/i, msg: (_, detail) => `Не удалось разобрать flow_yaml: ${detail}` },
  { re: /Invalid version: (.+)/i, msg: (_, version) => `Некорректная версия: ${version}` },
  { re: /Unsupported node type or kind/i, msg: 'Неподдерживаемый тип или kind ноды' },
  { re: /Flow model is required/i, msg: 'Нужна модель Flow' },
  { re: /Flow has no nodes/i, msg: 'В Flow нет нод' },
  { re: /Node id is required/i, msg: 'Нужен ID ноды' },
  { re: /Duplicate node id: (.+)/i, msg: (_, id) => `Дубликат ID ноды: ${id}` },
  { re: /start_node_id does not exist: (.+)/i, msg: (_, id) => `start_node_id не существует: ${id}` },
  { re: /Unreachable node: (.+)/i, msg: (_, id) => `Недостижимая нода: ${id}` },
  { re: /Node type is required: (.+)/i, msg: (_, id) => `Нужен тип ноды: ${id}` },
  { re: /node_kind is required: (.+)/i, msg: (_, id) => `Нужен node_kind: ${id}` },
  { re: /Unsupported node_kind for executor: (.+)/i, msg: (_, id) => `Неподдерживаемый node_kind для executor: ${id}` },
  { re: /Unsupported node_kind for gate: (.+)/i, msg: (_, id) => `Неподдерживаемый node_kind для gate: ${id}` },
  { re: /Unsupported node type: (.+)/i, msg: (_, id) => `Неподдерживаемый тип ноды: ${id}` },
  { re: /execution_context is required: (.+)/i, msg: (_, id) => `Нужен execution_context: ${id}` },
  { re: /execution_context entry is required: (.+)/i, msg: (_, id) => `Нужна запись execution_context: ${id}` },
  { re: /execution_context type is required: (.+)/i, msg: (_, id) => `Нужен тип в execution_context: ${id}` },
  { re: /Unsupported execution_context type: (.+)/i, msg: (_, id) => `Неподдерживаемый тип execution_context: ${id}` },
  { re: /execution_context required flag is missing: (.+)/i, msg: (_, id) => `Не указан required в execution_context: ${id}` },
  { re: /user_request must not define path: (.+)/i, msg: (_, id) => `user_request не должен содержать path: ${id}` },
  { re: /execution_context path is required: (.+)/i, msg: (_, id) => `Нужен path в execution_context: ${id}` },
  { re: /produced_artifacts entry is required: (.+)/i, msg: (_, id) => `Нужна запись produced_artifacts: ${id}` },
  { re: /produced_artifacts required flag is missing: (.+)/i, msg: (_, id) => `Не указан required в produced_artifacts: ${id}` },
  { re: /produced_artifacts path is required: (.+)/i, msg: (_, id) => `Нужен path в produced_artifacts: ${id}` },
  { re: /expected_mutations entry is required: (.+)/i, msg: (_, id) => `Нужна запись expected_mutations: ${id}` },
  { re: /expected_mutations required flag is missing: (.+)/i, msg: (_, id) => `Не указан required в expected_mutations: ${id}` },
  { re: /expected_mutations path is required: (.+)/i, msg: (_, id) => `Нужен path в expected_mutations: ${id}` },
  { re: /skill_refs only allowed for AI nodes: (.+)/i, msg: (_, id) => `skill_refs разрешены только для AI-ноды: ${id}` },
  { re: /Executor node requires on_success: (.+)/i, msg: (_, id) => `Executor-нода требует on_success: ${id}` },
  { re: /human_input gate requires on_submit: (.+)/i, msg: (_, id) => `human_input gate требует on_submit: ${id}` },
  { re: /human_approval gate requires on_approve: (.+)/i, msg: (_, id) => `human_approval gate требует on_approve: ${id}` },
  { re: /human_approval gate requires on_rework_routes: (.+)/i, msg: (_, id) => `human_approval gate требует on_rework_routes: ${id}` },
  { re: /Invalid transition target for (.+) on node: (.+)/i, msg: (_, route, id) => `Некорректный переход ${route} в ноде: ${id}` },
  {
    re: /Transition target not found for (.+) on node: (.+) -> (.+)/i,
    msg: (_, route, id, target) => `Целевая нода для ${route} не найдена: ${id} -> ${target}`,
  },
];

export function toRussianError(raw, fallback = DEFAULT_FALLBACK) {
  const text = raw == null ? '' : String(raw).trim();
  if (!text) {
    return fallback;
  }
  if (/[А-Яа-яЁё]/.test(text)) {
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
