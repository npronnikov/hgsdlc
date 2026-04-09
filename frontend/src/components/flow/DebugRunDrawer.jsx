import { useEffect, useState } from 'react';
import { Button, Drawer, Form, Input, Radio, Select, message } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { apiRequest } from '../../api/request.js';

export function DebugRunDrawer({ open, onClose, canonicalName }) {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return;
    apiRequest('/projects')
      .then((data) => {
        setProjects(data || []);
        if (data?.length > 0 && !form.getFieldValue('project_id')) {
          const first = data[0];
          form.setFieldsValue({
            project_id: first.id,
            target_branch: first.default_branch || 'main',
            publish_mode: 'local',
          });
        }
      })
      .catch((err) => message.error(err.message || 'Failed to load projects'));
  }, [open, form]);

  const handleLaunch = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      const response = await apiRequest('/runs', {
        method: 'POST',
        body: JSON.stringify({
          project_id: values.project_id,
          target_branch: values.target_branch,
          flow_canonical_name: canonicalName,
          feature_request: values.feature_request,
          publish_mode: values.publish_mode,
          debug_mode: true,
          idempotency_key: crypto.randomUUID(),
        }),
      });
      onClose();
      navigate(`/run-workspace?runId=${response.run_id}`);
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err.message || 'Failed to start debug run');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Drawer
      title="Debug Run"
      placement="right"
      width={360}
      open={open}
      onClose={onClose}
      footer={
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          loading={loading}
          onClick={handleLaunch}
          block
        >
          Run Debug
        </Button>
      }
    >
      <Form layout="vertical" form={form}>
        <Form.Item
          label="Project"
          name="project_id"
          rules={[{ required: true, message: 'Select a project' }]}
        >
          <Select
            options={projects.map((p) => ({ value: p.id, label: p.name }))}
            placeholder="Select project"
            onChange={(value) => {
              const project = projects.find((p) => p.id === value);
              if (project) form.setFieldValue('target_branch', project.default_branch || 'main');
            }}
          />
        </Form.Item>
        <Form.Item
          label="Target branch"
          name="target_branch"
          rules={[{ required: true, message: 'Specify target branch' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          label="Feature request"
          name="feature_request"
          rules={[{ required: true, message: 'Describe the feature request' }]}
        >
          <Input.TextArea rows={4} placeholder="Describe the change to debug" />
        </Form.Item>
        <Form.Item label="Publish mode" name="publish_mode" initialValue="local">
          <Radio.Group>
            <Radio value="local">Local</Radio>
            <Radio value="branch">Branch</Radio>
            <Radio value="pr">PR</Radio>
          </Radio.Group>
        </Form.Item>
      </Form>
      <div style={{ fontSize: 12, color: 'var(--color-text-tertiary, #999)', marginTop: 4 }}>
        Flow: <code>{canonicalName}</code>
      </div>
    </Drawer>
  );
}
