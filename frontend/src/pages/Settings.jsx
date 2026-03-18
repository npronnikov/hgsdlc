import React, { useEffect, useState } from 'react';
import { Button, Card, Form, Input, Space, Typography, message } from 'antd';
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
        body: JSON.stringify({ workspace_root: values.workspace_root }),
      });
      message.success('Настройки сохранены');
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
        <Title level={3} style={{ margin: 0 }}>Настройки</Title>
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
          </Form.Item>
          <Text type="secondary">
            Абсолютный путь на сервере, где runtime создаёт run workspace. По умолчанию используется `/tmp`.
          </Text>
        </Form>
      </Card>
    </div>
  );
}
