import React, { useEffect, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Select, Space, Typography, message } from 'antd';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function Settings() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [initialValues, setInitialValues] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/settings/runtime');
      const values = {
        workspace_root: data?.workspace_root || '/tmp/workspace',
        coding_agent: data?.coding_agent || 'qwen',
        ai_timeout_seconds: data?.ai_timeout_seconds ?? 900,
      };
      setInitialValues(values);
      form.setFieldsValue(values);
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить настройки');
      setInitialValues({ workspace_root: '/tmp/workspace', coding_agent: 'qwen', ai_timeout_seconds: 900 });
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
      message.success('Runtime Settings сохранены');
    } catch (err) {
      if (err?.errorFields) {
        return;
      }
      message.error(err.message || 'Не удалось сохранить настройки');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Runtime Settings</Title>
        <Space>
          <Button onClick={load} loading={loading}>Обновить</Button>
          <Button type="primary" onClick={handleSave} loading={saving}>Сохранить</Button>
        </Space>
      </div>
      <Card loading={loading && !initialValues}>
        {initialValues && (
        <Form layout="vertical" form={form} initialValues={initialValues}>
          <Form.Item
            label="Runtime workspace root"
            name="workspace_root"
            rules={[{ required: true, message: 'Укажите абсолютный путь' }]}
            extra="Абсолютный путь на сервере, где runtime создаёт run workspace. По умолчанию /tmp/workspace."
          >
            <Input placeholder="/tmp/workspace" />
          </Form.Item>
          <Form.Item
            label="Runtime coding agent"
            name="coding_agent"
            rules={[{ required: true, message: 'Выберите coding agent' }]}
            extra="Выбранный coding_agent должен совпадать с coding_agent flow. Сейчас реальное выполнение реализовано только для qwen."
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
            rules={[{ required: true, message: 'Укажите таймаут' }]}
            extra="Максимальное время ожидания выполнения AI-ноды и команд (в секундах). По умолчанию 900 (15 минут)."
          >
            <InputNumber min={10} max={7200} style={{ width: '100%' }} placeholder="900" />
          </Form.Item>
        </Form>
        )}
      </Card>
    </div>
  );
}
