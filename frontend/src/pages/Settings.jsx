import React, { useEffect, useState } from 'react';
import { Button, Card, Collapse, Form, Input, InputNumber, Modal, Select, Space, Switch, Typography, message } from 'antd';
import Editor from '@monaco-editor/react';
import { apiRequest } from '../api/request.js';
import { useThemeMode } from '../theme/ThemeContext.jsx';
import { configureMonacoThemes, getMonacoThemeName } from '../utils/monacoTheme.js';

const { Title, Text } = Typography;
const DEFAULT_CATALOG_REPO_URL = 'https://github.com/npronnikov/catalog.git';
const BASE_EDITOR_OPTIONS = {
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  automaticLayout: true,
  wordWrap: 'on',
  fontFamily: "'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace",
  fontSize: 13,
  lineHeight: 20,
};
const SHELL_EDITOR_OPTIONS = {
  ...BASE_EDITOR_OPTIONS,
};
const JSON_EDITOR_OPTIONS = {
  ...BASE_EDITOR_OPTIONS,
};

function defaultAgentLaunchCommand(agent) {
  const normalized = String(agent || '').trim().toLowerCase();
  if (normalized === 'claude') {
    return 'claude --dangerously-skip-permissions --output-format stream-json -p {{PROMPT}}';
  }
  if (normalized === 'gigacode') {
    return 'gigacode -p {{PROMPT}} --approval-mode auto-edit --output-format stream-json --include-partial-messages';
  }
  return 'qwen --approval-mode yolo --channel CI --output-format stream-json --include-partial-messages {{PROMPT}}';
}

function defaultAgentInitCommand(agent) {
  const normalized = String(agent || '').trim().toLowerCase();
  if (normalized === 'claude') {
    return 'claude -p "/init" --permission-mode acceptEdits';
  }
  if (normalized === 'gigacode') {
    return 'gigacode -p "/init" --approval-mode auto-edit';
  }
  return 'qwen -p "/init" --approval-mode yolo';
}

function defaultAgentSettingsJsonTemplate(agent) {
  const normalized = String(agent || '').trim().toLowerCase();
  if (normalized === 'claude') {
    return '{\n  "agent": "claude",\n  "dangerously_skip_permissions": true,\n  "output_format": "stream-json"\n}\n';
  }
  if (normalized === 'gigacode') {
    return '{\n  "agent": "gigacode",\n  "approval_mode": "auto-edit",\n  "output_format": "stream-json",\n  "include_partial_messages": true\n}\n';
  }
  return '{\n  "agent": "qwen",\n  "approval_mode": "yolo",\n  "channel": "CI",\n  "output_format": "stream-json",\n  "include_partial_messages": true\n}\n';
}

export default function Settings() {
  const [form] = Form.useForm();
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [initialValues, setInitialValues] = useState(null);
  const [repairMode, setRepairMode] = useState('pull_remote');
  const [commandAgent, setCommandAgent] = useState(null);
  const [agentSettingsDefaults, setAgentSettingsDefaults] = useState(defaultAgentSettingsJsonTemplate('qwen'));
  const codingAgentValue = Form.useWatch('coding_agent', form);

  const load = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/settings/runtime');
      const values = {
        workspace_root: data?.workspace_root || '/tmp/workspace',
        coding_agent: data?.coding_agent || 'qwen',
        ai_timeout_seconds: data?.ai_timeout_seconds ?? 900,
        prompt_language: data?.prompt_language || 'en',
        agent_launch_command: data?.agent_launch_command || defaultAgentLaunchCommand(data?.coding_agent || 'qwen'),
        agent_init_command: data?.agent_init_command || defaultAgentInitCommand(data?.coding_agent || 'qwen'),
        auto_init_when_no_rule: Boolean(data?.auto_init_when_no_rule),
        agent_settings_json: typeof data?.agent_settings_json === 'string' ? data.agent_settings_json : '',
        agent_settings_json_enabled: Boolean(data?.agent_settings_json_enabled),
        catalog_repo_url: data?.catalog_repo_url || DEFAULT_CATALOG_REPO_URL,
        catalog_default_branch: data?.catalog_default_branch || 'main',
        catalog_verify_checksum: data?.catalog_verify_checksum ?? true,
        git_username: data?.git_username || '',
        git_password_or_pat: data?.git_password_or_pat || '',
        local_git_username: data?.local_git_username || '',
        local_git_email: data?.local_git_email || '',
      };
      setInitialValues(values);
      setCommandAgent(values.coding_agent);
      setAgentSettingsDefaults(data?.agent_settings_json_template || defaultAgentSettingsJsonTemplate(values.coding_agent));
      form.setFieldsValue(values);
    } catch (err) {
      message.error(err.message || 'Failed to load settings');
      setInitialValues({
        workspace_root: '/tmp/workspace',
        coding_agent: 'qwen',
        ai_timeout_seconds: 900,
        prompt_language: 'en',
        agent_launch_command: defaultAgentLaunchCommand('qwen'),
        agent_init_command: defaultAgentInitCommand('qwen'),
        auto_init_when_no_rule: false,
        agent_settings_json: '',
        agent_settings_json_enabled: false,
        catalog_repo_url: DEFAULT_CATALOG_REPO_URL,
        catalog_default_branch: 'main',
        catalog_verify_checksum: true,
        git_username: '',
        git_password_or_pat: '',
        local_git_username: '',
        local_git_email: '',
      });
      setCommandAgent('qwen');
      setAgentSettingsDefaults(defaultAgentSettingsJsonTemplate('qwen'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    if (initialValues) {
      form.setFieldsValue(initialValues);
    }
  }, [initialValues]);

  useEffect(() => {
    let active = true;
    const loadAgentCommand = async () => {
      if (!codingAgentValue || !initialValues) {
        return;
      }
      if (codingAgentValue === commandAgent) {
        return;
      }
      try {
        const data = await apiRequest(`/settings/runtime/agent-command?coding_agent=${encodeURIComponent(codingAgentValue)}`);
        if (!active) {
          return;
        }
        const launchCommand = data?.agent_launch_command || defaultAgentLaunchCommand(codingAgentValue);
        const initCommand = data?.agent_init_command || defaultAgentInitCommand(codingAgentValue);
        const autoInitWhenNoRule = Boolean(data?.auto_init_when_no_rule);
        const settingsJsonTemplate = data?.agent_settings_json_template || defaultAgentSettingsJsonTemplate(codingAgentValue);
        const settingsJson = typeof data?.agent_settings_json === 'string' ? data.agent_settings_json : '';
        const settingsJsonEnabled = Boolean(data?.agent_settings_json_enabled);
        form.setFieldValue('agent_launch_command', launchCommand);
        form.setFieldValue('agent_init_command', initCommand);
        form.setFieldValue('auto_init_when_no_rule', autoInitWhenNoRule);
        form.setFieldValue('agent_settings_json', settingsJson);
        form.setFieldValue('agent_settings_json_enabled', settingsJsonEnabled);
        setAgentSettingsDefaults(settingsJsonTemplate);
        setCommandAgent(String(data?.coding_agent || codingAgentValue).trim().toLowerCase());
      } catch (err) {
        if (!active) {
          return;
        }
        form.setFieldValue('agent_launch_command', defaultAgentLaunchCommand(codingAgentValue));
        form.setFieldValue('agent_init_command', defaultAgentInitCommand(codingAgentValue));
        form.setFieldValue('auto_init_when_no_rule', false);
        form.setFieldValue('agent_settings_json', '');
        form.setFieldValue('agent_settings_json_enabled', false);
        setAgentSettingsDefaults(defaultAgentSettingsJsonTemplate(codingAgentValue));
        setCommandAgent(String(codingAgentValue).trim().toLowerCase());
        message.error(err.message || 'Failed to load agent launch command');
      }
    };
    loadAgentCommand();
    return () => {
      active = false;
    };
  }, [codingAgentValue, commandAgent, form, initialValues]);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const resolvedCodingAgent = values.coding_agent || form.getFieldValue('coding_agent') || 'qwen';
      const resolvedAgentLaunchCommand = (
        values.agent_launch_command
        ?? form.getFieldValue('agent_launch_command')
        ?? defaultAgentLaunchCommand(resolvedCodingAgent)
      );
      const resolvedAgentInitCommand = (
        values.agent_init_command
        ?? form.getFieldValue('agent_init_command')
        ?? defaultAgentInitCommand(resolvedCodingAgent)
      );
      const resolvedAutoInitWhenNoRule = (
        values.auto_init_when_no_rule
        ?? form.getFieldValue('auto_init_when_no_rule')
      );
      setSaving(true);
      await apiRequest('/settings/runtime', {
        method: 'PUT',
        body: JSON.stringify({
          workspace_root: values.workspace_root,
          coding_agent: resolvedCodingAgent,
          ai_timeout_seconds: values.ai_timeout_seconds,
          prompt_language: values.prompt_language,
          agent_launch_command: resolvedAgentLaunchCommand,
          agent_init_command: resolvedAgentInitCommand,
          auto_init_when_no_rule: resolvedAutoInitWhenNoRule ?? null,
          agent_settings_json: values.agent_settings_json,
          agent_settings_json_enabled: values.agent_settings_json_enabled ?? null,
        }),
      });
      await apiRequest('/settings/catalog', {
        method: 'PUT',
        body: JSON.stringify({
          catalog_repo_url: values.catalog_repo_url,
          catalog_default_branch: values.catalog_default_branch,
          catalog_verify_checksum: values.catalog_verify_checksum ?? true,
          git_username: values.git_username,
          git_password_or_pat: values.git_password_or_pat,
          local_git_username: values.local_git_username,
          local_git_email: values.local_git_email,
        }),
      });
      message.success('Runtime Settings saved');
    } catch (err) {
      if (err?.errorFields) {
        return;
      }
      message.error(err.message || 'Failed to save settings');
    } finally {
      setSaving(false);
    }
  };

  const runRepair = async (mode) => {
    try {
      setSaving(true);
      const result = await apiRequest('/settings/catalog/repair', {
        method: 'PUT',
        body: JSON.stringify({ mode }),
      });
      const summary = [
        `Status: ${result?.status || 'unknown'}`,
        `Mode: ${result?.mode || 'unknown'}`,
        `Scanned: rules=${result?.scanned_rules ?? 0}, skills=${result?.scanned_skills ?? 0}, flows=${result?.scanned_flows ?? 0}`,
        `Merge: inserted=${result?.inserted ?? 0}, updated=${result?.updated ?? 0}, skipped=${result?.skipped ?? 0}`,
      ];
      const errors = Array.isArray(result?.errors) ? result.errors : [];
      if (errors.length > 0) {
        const errorPreview = errors.slice(0, 20).map((err) => `- ${err.path}: ${err.message}`).join('\n');
        Modal.warning({
          title: 'Catalog repair completed with errors',
          width: 880,
          content: (
            <div>
              <div>{summary.join(' | ')}</div>
              <pre style={{ whiteSpace: 'pre-wrap', marginTop: 12, maxHeight: 320, overflow: 'auto' }}>{errorPreview}</pre>
              {errors.length > 20 ? <Text type="secondary">Showing first 20 of {errors.length} errors.</Text> : null}
            </div>
          ),
        });
      } else {
        message.success(result?.message || 'Catalog repair completed');
      }
    } catch (err) {
      message.error(err.message || 'Failed to repair catalog');
    } finally {
      setSaving(false);
    }
  };

  const handleRepair = async () => {
    if (repairMode === 'full_repair') {
      Modal.confirm({
        title: 'Run Full Repair?',
        content: 'Full Repair will delete all local catalog items (team/non-remote), then rebuild the index from the remote git mirror.',
        okText: 'Run Full Repair',
        okButtonProps: { danger: true },
        onOk: () => runRepair('full_repair'),
      });
      return;
    }
    await runRepair('pull_remote');
  };

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Settings</Title>
        <Space>
          <Button onClick={load} loading={loading}>Refresh</Button>
          <Button type="default" onClick={handleSave} loading={saving}>Save</Button>
        </Space>
      </div>
      <div className="settings-layout">
      <Card className="runtime-settings-card" title="Runtime Settings" loading={loading && !initialValues}>
        {initialValues && (
        <Form layout="vertical" form={form} initialValues={initialValues}>
          <div className="catalog-settings-grid">
            <Form.Item
              label="Runtime workspace root"
              name="workspace_root"
              rules={[{ required: true, message: 'Specify absolute path' }]}
              extra="Absolute path on the server where runtime creates the run workspace. Default is /tmp/workspace."
            >
              <Input placeholder="/tmp/workspace" />
            </Form.Item>
            <Form.Item
              label="Runtime coding agent"
              name="coding_agent"
              rules={[{ required: true, message: 'Select coding agent' }]}
              extra="Selected coding_agent must match the flow coding_agent."
            >
              <Select
                options={[
                  { value: 'qwen', label: 'qwen' },
                  { value: 'gigacode', label: 'gigacode' },
                  { value: 'claude', label: 'claude' },
                ]}
              />
            </Form.Item>
            <Form.Item
              label="AI timeout (seconds)"
              name="ai_timeout_seconds"
              rules={[{ required: true, message: 'Specify timeout' }]}
              extra="Maximum wait time for AI node and command execution (in seconds). Default is 900 (15 minutes)."
            >
              <InputNumber min={10} max={7200} style={{ width: '100%' }} placeholder="900" />
            </Form.Item>
            <Form.Item
              label="Prompt language"
              name="prompt_language"
              rules={[{ required: true, message: 'Select prompt language' }]}
              extra="Language used for AI prompt texts (headers, instructions, structured output format)."
            >
              <Select
                options={[
                  { value: 'en', label: 'English' },
                  { value: 'ru', label: 'Russian' },
                ]}
              />
            </Form.Item>
            <div className="catalog-settings-span-2">
              <Title level={5} style={{ marginTop: 8, marginBottom: 8 }}>Danger Zone</Title>
              <Collapse
                items={[
                  {
                    key: 'agent-launch-command',
                    forceRender: true,
                    label: (
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
                        <span>Agent Commands</span>
                        <Button
                          size="small"
                          type="default"
                          onMouseDown={(event) => event.stopPropagation()}
                          onClick={(event) => {
                            event.stopPropagation();
                            form.setFieldValue('agent_launch_command', defaultAgentLaunchCommand(codingAgentValue || 'qwen'));
                            form.setFieldValue('agent_init_command', defaultAgentInitCommand(codingAgentValue || 'qwen'));
                          }}
                        >
                          Load Defaults
                        </Button>
                      </div>
                    ),
                    children: (
                      <>
                        <Form.Item
                          style={{ marginBottom: 12 }}
                          name="agent_launch_command"
                          trigger="onChange"
                          getValueFromEvent={(value) => value ?? ''}
                          getValueProps={(value) => ({ value: value ?? '' })}
                          rules={[{ required: true, message: 'Specify launch command' }]}
                          extra="Editable shell command used to start the selected coding agent. Supported placeholders: {{PROMPT}} and {{PROMPT_FILE}}."
                        >
                          <Editor
                            height="95px"
                            language="shell"
                            beforeMount={configureMonacoThemes}
                            theme={monacoTheme}
                            options={SHELL_EDITOR_OPTIONS}
                          />
                        </Form.Item>
                        <Form.Item
                          style={{ marginBottom: 12 }}
                          name="agent_init_command"
                          trigger="onChange"
                          getValueFromEvent={(value) => value ?? ''}
                          getValueProps={(value) => ({ value: value ?? '' })}
                          rules={[{ required: true, message: 'Specify init command' }]}
                          extra="Bootstrap command executed before launch when auto init is enabled and flow has no rules. Supported placeholders: {{PROMPT}}, {{PROMPT_FILE}}, {{PROJECT_ROOT}}."
                        >
                          <Editor
                            height="65px"
                            language="shell"
                            beforeMount={configureMonacoThemes}
                            theme={monacoTheme}
                            options={SHELL_EDITOR_OPTIONS}
                          />
                        </Form.Item>
                        <Form.Item
                          style={{ marginBottom: 0 }}
                          label="Auto init when flow has no rules"
                          name="auto_init_when_no_rule"
                          valuePropName="checked"
                          extra="If enabled, runtime runs Agent Init Command before Agent Launch Command only when flow.rule_refs is empty."
                        >
                          <Switch />
                        </Form.Item>
                      </>
                    ),
                  },
                  {
                    key: 'agent-settings-json',
                    label: (
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
                        <span>Agent Settings</span>
                        <Button
                          size="small"
                          type="default"
                          onMouseDown={(event) => event.stopPropagation()}
                          onClick={(event) => {
                            event.stopPropagation();
                            form.setFieldValue('agent_settings_json', agentSettingsDefaults || '');
                          }}
                        >
                          Load Defaults
                        </Button>
                      </div>
                    ),
                    children: (
                      <>
                        <Form.Item
                          style={{ marginBottom: 12 }}
                          name="agent_settings_json"
                          trigger="onChange"
                          getValueFromEvent={(value) => value ?? ''}
                          getValueProps={(value) => ({ value: value ?? '' })}
                        >
                          <Editor
                            height="320px"
                            language="json"
                            beforeMount={configureMonacoThemes}
                            theme={monacoTheme}
                            options={JSON_EDITOR_OPTIONS}
                          />
                        </Form.Item>
                        <Form.Item
                          style={{ marginBottom: 0 }}
                          label="Enable settings.json initialization"
                          name="agent_settings_json_enabled"
                          valuePropName="checked"
                          extra="Default is disabled. When enabled, runtime copies Agent Settings from DB to workspace/runId/.qwen/settings.json or .claude/settings.json."
                        >
                          <Switch />
                        </Form.Item>
                      </>
                    ),
                  },
                ]}
              />
            </div>
          </div>
        </Form>
        )}
      </Card>
      <Card
        className="catalog-settings-card"
        title="Catalog repository settings"
        loading={loading && !initialValues}
        extra={(
          <Space>
            <Select
              style={{ width: 170 }}
              value={repairMode}
              onChange={setRepairMode}
              options={[
                { value: 'pull_remote', label: 'Pull Remote' },
                { value: 'full_repair', label: 'Full Repair' },
              ]}
            />
            <Button onClick={handleRepair} loading={saving}>Repair catalog</Button>
          </Space>
        )}
      >
        {initialValues && (
          <Form layout="vertical" form={form} initialValues={initialValues}>
            <div className="catalog-settings-grid">
              <Form.Item
                className="catalog-settings-span-2"
                label="Catalog repository URL"
                name="catalog_repo_url"
                extra="Remote git repository that stores published catalog versions."
              >
                <Input placeholder="git@github.com:org/catalog.git" />
              </Form.Item>
              <Form.Item
                label="Catalog default branch"
                name="catalog_default_branch"
                extra="Branch used for catalog synchronization and publication."
              >
                <Input placeholder="main" />
              </Form.Item>
              <Form.Item
                label="Checksum validation"
                extra="If Off, checksum mismatches are ignored during catalog repair; only schema-required fields are validated."
              >
                <Space size={8}>
                  <Text type="secondary">Verify Checksum</Text>
                  <Form.Item name="catalog_verify_checksum" valuePropName="checked" noStyle>
                    <Switch checkedChildren="On" unCheckedChildren="Off" />
                  </Form.Item>
                </Space>
              </Form.Item>
            </div>
            <Title level={5} style={{ marginTop: 8 }}>Remote Authentication Settings</Title>
            <div className="catalog-settings-grid">
              <Form.Item label="Git username" name="git_username">
                <Input placeholder="git-bot" />
              </Form.Item>
              <Form.Item label="Git password / PAT" name="git_password_or_pat">
                <Input.Password placeholder="Personal access token" />
              </Form.Item>
            </div>
            <Title level={5} style={{ marginTop: 8 }}>Local Authentication Settings</Title>
            <div className="catalog-settings-grid">
              <Form.Item label="Username" name="local_git_username">
                <Input placeholder="hgsdlc" />
              </Form.Item>
              <Form.Item label="User email" name="local_git_email">
                <Input placeholder="hgsdlc@email.com" />
              </Form.Item>
            </div>
          </Form>
        )}
      </Card>
      </div>
    </div>
  );
}
