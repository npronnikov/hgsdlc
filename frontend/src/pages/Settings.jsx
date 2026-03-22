import React, { useEffect, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Modal, Select, Space, Tabs, Typography, message } from 'antd';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function Settings() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [initialValues, setInitialValues] = useState(null);
  const [repairMode, setRepairMode] = useState('upsert');

  const load = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/settings/runtime');
      const values = {
        workspace_root: data?.workspace_root || '/tmp/workspace',
        coding_agent: data?.coding_agent || 'qwen',
        ai_timeout_seconds: data?.ai_timeout_seconds ?? 900,
        catalog_repo_url: data?.catalog_repo_url || '',
        catalog_default_branch: data?.catalog_default_branch || 'main',
        publish_mode: data?.publish_mode || 'pr',
        git_ssh_private_key: data?.git_ssh_private_key || '',
        git_ssh_public_key: data?.git_ssh_public_key || '',
        git_ssh_passphrase: data?.git_ssh_passphrase || '',
        git_certificate: data?.git_certificate || '',
        git_certificate_key: data?.git_certificate_key || '',
        git_username: data?.git_username || '',
        git_password_or_pat: data?.git_password_or_pat || '',
      };
      setInitialValues(values);
      form.setFieldsValue(values);
    } catch (err) {
      message.error(err.message || 'Failed to load settings');
      setInitialValues({
        workspace_root: '/tmp/workspace',
        coding_agent: 'qwen',
        ai_timeout_seconds: 900,
        catalog_repo_url: '',
        catalog_default_branch: 'main',
        publish_mode: 'pr',
        git_ssh_private_key: '',
        git_ssh_public_key: '',
        git_ssh_passphrase: '',
        git_certificate: '',
        git_certificate_key: '',
        git_username: '',
        git_password_or_pat: '',
      });
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

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      await apiRequest('/settings/runtime', {
        method: 'PUT',
        body: JSON.stringify({
          workspace_root: values.workspace_root,
          coding_agent: values.coding_agent,
          ai_timeout_seconds: values.ai_timeout_seconds,
        }),
      });
      await apiRequest('/settings/catalog', {
        method: 'PUT',
        body: JSON.stringify({
          catalog_repo_url: values.catalog_repo_url,
          catalog_default_branch: values.catalog_default_branch,
          publish_mode: values.publish_mode,
          git_ssh_private_key: values.git_ssh_private_key,
          git_ssh_public_key: values.git_ssh_public_key,
          git_ssh_passphrase: values.git_ssh_passphrase,
          git_certificate: values.git_certificate,
          git_certificate_key: values.git_certificate_key,
          git_username: values.git_username,
          git_password_or_pat: values.git_password_or_pat,
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
        `Scanned: rules=${result?.scanned_rules ?? 0}, skills=${result?.scanned_skills ?? 0}, flows=${result?.scanned_flows ?? 0}`,
        `Upsert: inserted=${result?.inserted ?? 0}, updated=${result?.updated ?? 0}, skipped=${result?.skipped ?? 0}`,
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
    if (repairMode === 'from_scratch') {
      Modal.confirm({
        title: 'Rebuild from scratch?',
        content: 'This will delete all local catalog index entries for rules, skills, and flows, then rebuild from repository.',
        okText: 'Run from scratch',
        okButtonProps: { danger: true },
        onOk: () => runRepair('from_scratch'),
      });
      return;
    }
    await runRepair('upsert');
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
      <Card title="Runtime Settings" loading={loading && !initialValues}>
        {initialValues && (
        <Form layout="vertical" form={form} initialValues={initialValues}>
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
            extra="Selected coding_agent must match the flow coding_agent. Currently real execution is implemented only for qwen."
          >
            <Select
              options={[
                { value: 'qwen', label: 'qwen' },
                { value: 'claude', label: 'claude' },
                { value: 'cursor', label: 'cursor' },
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
        </Form>
        )}
      </Card>
      <Card
        title="Catalog repository settings"
        loading={loading && !initialValues}
        extra={(
          <Space>
            <Select
              style={{ width: 170 }}
              value={repairMode}
              onChange={setRepairMode}
              options={[
                { value: 'upsert', label: 'Upsert' },
                { value: 'from_scratch', label: 'From scratch' },
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
                label="Catalog publish mode"
                name="publish_mode"
                extra="local - direct local commit, pr - publish through feature branch and PR."
              >
                <Select
                  options={[
                    { value: 'local', label: 'local' },
                    { value: 'pr', label: 'pr' },
                  ]}
                />
              </Form.Item>
            </div>
            <Title level={5} style={{ marginTop: 8 }}>Authentication Settings</Title>
            <Tabs
              items={[
                {
                  key: 'key',
                  label: 'Account',
                  children: (
                    <>
                      <div className="catalog-settings-grid">
                        <Form.Item label="Git username" name="git_username">
                          <Input placeholder="git-bot" />
                        </Form.Item>
                        <Form.Item label="Git password / PAT" name="git_password_or_pat">
                          <Input.Password placeholder="Personal access token" />
                        </Form.Item>
                      </div>
                    </>
                  ),
                },
                {
                  key: 'cert',
                  label: 'Git client certificate',
                  children: (
                    <>
                      <div className="catalog-settings-grid">
                        <Form.Item className="catalog-settings-span-2" label="Git client certificate" name="git_certificate">
                          <Input.TextArea rows={4} placeholder="-----BEGIN CERTIFICATE-----" />
                        </Form.Item>
                        <Form.Item className="catalog-settings-span-2" label="Git certificate key" name="git_certificate_key">
                          <Input.TextArea rows={4} placeholder="-----BEGIN PRIVATE KEY-----" />
                        </Form.Item>
                      </div>
                    </>
                  ),
                },
              ]}
            />
            <Form.Item name="git_ssh_private_key" hidden>
              <Input />
            </Form.Item>
            <Form.Item name="git_ssh_public_key" hidden>
              <Input />
            </Form.Item>
            <Form.Item name="git_ssh_passphrase" hidden>
              <Input />
            </Form.Item>
          </Form>
        )}
      </Card>
      </div>
    </div>
  );
}
