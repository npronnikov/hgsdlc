import React, { useEffect, useState } from 'react';
import { Button, Card, Form, Input, Select, Space, Typography, message } from 'antd';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;

export default function Settings() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/settings/runtime');
      form.setFieldsValue({
        workspace_root: data?.workspace_root || '/tmp',
        coding_agent: data?.coding_agent || 'qwen',
      });
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить настройки');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      await apiRequest('/settings/runtime', {
        method: 'PUT',
        body: JSON.stringify({
          workspace_root: values.workspace_root,
          coding_agent: values.coding_agent,
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
      <Card>
        <Form layout="vertical" form={form}>
          <Form.Item
            label="Runtime workspace root"
            name="workspace_root"
            rules={[{ required: true, message: 'Укажите абсолютный путь' }]}
          >
            <Input placeholder="/tmp" />
            <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
              Абсолютный путь на сервере, где runtime создаёт run workspace. По умолчанию используется `/tmp`.
            </Text>
          </Form.Item>
          <Form.Item
            label="Runtime coding agent"
            name="coding_agent"
            rules={[{ required: true, message: 'Выберите coding agent' }]}
          >
            <Select
              options={[
                { value: 'qwen', label: 'qwen' },
                { value: 'claude', label: 'claude' },
                { value: 'cursor', label: 'cursor' },
              ]}
            />
            <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
              Выбранный `coding_agent` должен совпадать с `coding_agent` flow. Сейчас реальное выполнение реализовано только для `qwen`.
            </Text>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
