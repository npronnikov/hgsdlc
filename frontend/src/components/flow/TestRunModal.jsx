import { useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Select, message } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { apiRequest } from '../../api/request.js';

/** @deprecated Use DebugRunDrawer instead. Will be removed in next iteration. */
export function TestRunModal({ open, onClose, canonicalName }) {
  useEffect(() => {
    if (open) console.warn('[TestRunModal] Deprecated: use DebugRunDrawer instead');
  }, [open]);
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    apiRequest('/projects')
      .then((data) => {
        setProjects(data || []);
        if (data?.length > 0 && !form.getFieldValue('project_id')) {
          const first = data[0];
          form.setFieldsValue({
            project_id: first.id,
            target_branch: first.default_branch || 'main',
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
          ai_session_mode: 'isolated_attempt_sessions',
          publish_mode: 'local',
          idempotency_key: crypto.randomUUID(),
        }),
      });
      message.success(`Test run started: ${response.run_id}`);
      onClose();
      navigate(`/run-console?runId=${response.run_id}`);
    } catch (err) {
      if (err?.errorFields) {
        return;
      }
      message.error(err.message || 'Failed to start test run');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onClose();
  };

  const projectOptions = projects.map((p) => ({ value: p.id, label: p.name }));

  return (
    <Modal
      title="Test Run (dry run)"
      open={open}
      onCancel={handleCancel}
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          Cancel
        </Button>,
        <Button
          key="launch"
          type="primary"
          icon={<PlayCircleOutlined />}
          loading={loading}
          onClick={handleLaunch}
        >
          Launch
        </Button>,
      ]}
      width={520}
    >
      <Form layout="vertical" form={form}>
        <Form.Item
          label="Project"
          name="project_id"
          rules={[{ required: true, message: 'Select a project' }]}
        >
          <Select
            options={projectOptions}
            placeholder="Select project"
            onChange={(value) => {
              const project = projects.find((p) => p.id === value);
              if (project) {
                form.setFieldValue('target_branch', project.default_branch || 'main');
              }
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
          <Input.TextArea rows={4} placeholder="Describe the change to test" />
        </Form.Item>
      </Form>
      <div style={{ fontSize: 12, color: 'var(--color-text-tertiary, #999)', marginTop: 4 }}>
        Flow: <code>{canonicalName}</code> &middot; publish_mode: <code>local</code> (no git push, no PR)
      </div>
    </Modal>
  );
}
