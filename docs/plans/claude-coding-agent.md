# Claude Coding Agent — план реализации

## Суть

Добавить поддержку Claude Code CLI как coding agent, параллельно существующему `QwenCodingAgentStrategy`. Реализация сводится к одному новому классу — всё остальное уже готово.

---

## Что уже готово

- `SkillProvider.CLAUDE` и `RuleProvider.CLAUDE` — есть
- Шаблоны `skill-templates/claude.md`, `rule-templates/claude.md` — есть
- `CodingAgentStrategy` интерфейс — есть
- Spring auto-registration через `@Component` — работает
- `ANTHROPIC_API_KEY` — предполагается настроенным в окружении; Claude Code подхватит его самостоятельно

---

## Изменения (1 новый файл)

### `ClaudeCodingAgentStrategy.java` ★ Вся работа здесь

Новый файл: `backend/src/main/java/ru/hgd/sdlc/runtime/application/ClaudeCodingAgentStrategy.java`

Зависимости — идентичны `QwenCodingAgentStrategy`:
- `RuleVersionRepository`
- `SkillVersionRepository`
- `SkillFileRepository`
- `RuntimeStepTxService`
- `AgentPromptBuilder`
- `CatalogContentResolver`
- `WorkspacePort`

#### `codingAgent()` → `"claude"`

#### Структура рабочего пространства

```
<project_root>/
  CLAUDE.md                       ← правила (Claude Code читает из корня автоматически)
  .claude/
    commands/
      <skill-name>.md             ← скилл без файлов
      <skill-name>/               ← скилл с несколькими файлами
        <file>
```

#### Логика `materializeWorkspace()`

1. Определить пути:
   - `rulesPath` = `projectRoot/CLAUDE.md`
   - `commandsRoot` = `projectRoot/.claude/commands`
   - `promptPath` = `nodeExecutionRoot/prompt.md`
   - `stdoutPath` / `stderrPath` = `nodeExecutionRoot/agent.stdout.log` / `agent.stderr.log`

2. Создать `.claude/commands/`, очистить содержимое

3. Разрезолвить rules (`RuleProvider.CLAUDE`), записать в `CLAUDE.md`:
   ```
   # HGSDLC Runtime Rules
   flow: <name>
   coding_agent: claude
   ## <rule-name>
   <rule-content>
   ```

4. Залогировать аудит `rules_materialized`

5. Разрезолвить skills (`SkillProvider.CLAUDE`), записать в `.claude/commands/`:
   - Если нет `SkillFileEntity` → `.claude/commands/<canonical-name>.md` из `resolveSkillMarkdown()`
   - Если есть файлы → директория `.claude/commands/<canonical-name>/`, файлы по путям (с executable bit)

6. Залогировать аудит `skills_materialized`

7. Собрать prompt через `agentPromptBuilder.build(...)`, записать в `prompt.md`

8. Вернуть `AgentInvocationContext` с командой:
   ```java
   List.of(
       "claude",
       "--dangerously-skip-permissions",
       "--output-format", "stream-json",
       "-p", promptPackage.prompt()
   )
   ```

---

## Соответствие Qwen → Claude

| Аспект | Qwen | Claude |
|--------|------|--------|
| Идентификатор | `"qwen"` | `"claude"` |
| Директория конфига | `.qwen/` | `.claude/` |
| Файл правил | `.qwen/QWEN.md` | `CLAUDE.md` (корень) |
| Директория скиллов | `.qwen/skills/<name>/` | `.claude/commands/<name>/` |
| Команда | `qwen --approval-mode yolo --channel CI --output-format stream-json --include-partial-messages <prompt>` | `claude --dangerously-skip-permissions --output-format stream-json -p <prompt>` |
| API ключ | не нужен | из окружения (`ANTHROPIC_API_KEY`) |

---

## Что менять не нужно

- `AgentInvocationContext` — не меняется
- `ProcessExecutionPort` / `DefaultProcessExecutionAdapter` — не меняются
- `SettingsService` — не меняется
- `RunStepService` — не меняется
- UI — не меняется
