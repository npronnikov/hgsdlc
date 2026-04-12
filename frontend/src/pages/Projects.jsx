import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Dropdown, Form, Input, Modal, Space, Switch, Typography, message } from 'antd';
import { DeleteOutlined, EditOutlined, MoreOutlined, PlusOutlined } from '@ant-design/icons';
import StatusTag from '../components/StatusTag.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import { apiRequest } from '../api/request.js';

const { Title, Text } = Typography;
const canCreateProject = (role) => role === 'ADMIN' || role === 'FLOW_CONFIGURATOR' || role === 'PRODUCT_OWNER';

export default function Projects() {
  const { user } = useAuth();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingProject, setEditingProject] = useState(null);
  const [viewingProject, setViewingProject] = useState(null);
  const [showInactive, setShowInactive] = useState(false);
  const [form] = Form.useForm();

  const loadProjects = async () => {
    setLoading(true);
    try {
      const data = await apiRequest('/projects');
      setProjects(data || []);
    } catch (err) {
      message.error(err.message || 'Failed to load projects');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProjects();
  }, []);

  const openCreate = () => {
    setEditingProject(null);
    form.resetFields();
    setIsModalOpen(true);
  };

  const openEdit = (project) => {
    setEditingProject(project);
    form.setFieldsValue({
      name: project.name,
      repo_url: project.repo_url,
      default_branch: project.default_branch,
    });
    setIsModalOpen(true);
  };

  const openView = (project) => {
    setViewingProject(project);
  };

  const closeView = () => {
    setViewingProject(null);
  };

  const submitForm = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload = {
        name: values.name?.trim(),
        repo_url: values.repo_url?.trim(),
      };
      const trimmedBranch = values.default_branch?.trim();
      if (trimmedBranch) {
        payload.default_branch = trimmedBranch;
      }

      if (editingProject) {
        const response = await apiRequest(`/projects/${editingProject.id}`, {
          method: 'PATCH',
          body: JSON.stringify({
            ...payload,
            resource_version: editingProject.resource_version,
          }),
        });
        setProjects((prev) => prev.map((item) => (item.id === response.id ? response : item)));
        message.success('Project updated');
      } else {
        const response = await apiRequest('/projects', {
          method: 'POST',
          body: JSON.stringify(payload),
        });
        setProjects((prev) => [response, ...prev]);
        message.success('Project created');
      }
      setIsModalOpen(false);
      setEditingProject(null);
      form.resetFields();
    } catch (err) {
      if (err?.errorFields) {
        return;
      }
      message.error(err.message || 'Failed to save project');
    } finally {
      setSubmitting(false);
    }
  };

  const deleteProject = async (project) => {
    try {
      await apiRequest(`/projects/${project.id}`, {
        method: 'DELETE',
      });
      setProjects((prev) => prev.map((item) => (
        item.id === project.id
          ? { ...item, status: 'archived' }
          : item
      )));
      message.success('Project marked inactive');
    } catch (err) {
      message.error(err.message || 'Failed to mark project inactive');
    }
  };

  const projectMenuItems = (project) => ([
    { key: 'edit', label: 'Edit', icon: <EditOutlined /> },
    {
      key: 'delete',
      label: 'Delete',
      icon: <DeleteOutlined />,
      danger: true,
      disabled: project.status === 'archived',
    },
  ]);

  const handleMenuClick = (project, key) => {
    if (key === 'edit') {
      openEdit(project);
      return;
    }
    if (key === 'delete') {
      Modal.confirm({
        title: `Mark project "${project.name}" inactive?`,
        content: 'Project will be hidden from active list but preserved in history.',
        okText: 'Mark inactive',
        okButtonProps: { danger: true },
        cancelText: 'Cancel',
        onOk: () => deleteProject(project),
      });
    }
  };

  const visibleProjects = useMemo(
    () => (showInactive ? projects : projects.filter((project) => project.status === 'active')),
    [projects, showInactive]
  );


  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Projects</Title>
        <Space size={12}>
          <Space size={8}>
            <Text type="secondary">Show inactive</Text>
            <Switch
              checked={showInactive}
              onChange={setShowInactive}
              checkedChildren="On"
              unCheckedChildren="Off"
            />
          </Space>
          {canCreateProject(user?.role) ? (
            <Button type="default" icon={<PlusOutlined />} onClick={openCreate}>New project</Button>
          ) : null}
        </Space>
      </div>
      <div className="cards-fullscreen">
        {loading ? (
          <div className="card-muted">Loading...</div>
        ) : (
          <div className="cards-grid">
            {visibleProjects.map((project) => (
              <Card
                key={project.id}
                className={`resource-card project-card status-${(project.status || 'unknown').toLowerCase()}`}
                hoverable
                onClick={() => openView(project)}
              >
                <div className="resource-card-header">
                  <div className="resource-card-title">
                    <span className="resource-card-name">{project.name}</span>
                  </div>
                  <div className="resource-card-actions" onClick={(event) => event.stopPropagation()}>
                    <StatusTag value={project.status} />
                    <Dropdown
                      trigger={['click']}
                      menu={{
                        items: projectMenuItems(project),
                        onClick: ({ key }) => handleMenuClick(project, key),
                      }}
                    >
                      <Button
                        type="text"
                        size="small"
                        icon={<MoreOutlined />}
                        className="resource-card-menu"
                        onClick={(event) => event.stopPropagation()}
                      />
                    </Dropdown>
                  </div>
                </div>
                <div className="resource-card-description mono project-repo-url">{project.repo_url}</div>
                <div className="resource-card-footer resource-card-footer-stack">
                  <span className="resource-canonical mono">branch: {project.default_branch}</span>
                  <span className="resource-canonical mono">last run: {project.last_run_id || '—'}</span>
                </div>
              </Card>
            ))}
            {visibleProjects.length === 0 && (
              <div className="card-muted">No projects found.</div>
            )}
          </div>
        )}
      </div>

      <Modal
        title={editingProject ? 'Edit project' : 'Add project'}
        open={isModalOpen}
        onOk={submitForm}
        confirmLoading={submitting}
        onCancel={() => {
          setIsModalOpen(false);
          setEditingProject(null);
        }}
        okText={editingProject ? 'Save' : 'Create'}
        cancelText="Cancel"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="Name"
            name="name"
            rules={[{ required: true, message: 'Enter project name' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="Repository URL"
            name="repo_url"
            rules={[{ required: true, message: 'Enter repository URL' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item label="Default branch" name="default_branch">
            <Input placeholder="main" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Project parameters"
        open={!!viewingProject}
        onCancel={closeView}
        footer={null}
      >
        {viewingProject && (
          <div className="form-stack">
            <div>
              <div className="muted">Name</div>
              <div>{viewingProject.name}</div>
            </div>
            <div>
              <div className="muted">Repository</div>
              <div className="mono project-repo-url">{viewingProject.repo_url}</div>
            </div>
            <div>
              <div className="muted">Default branch</div>
              <div className="mono">{viewingProject.default_branch}</div>
            </div>
            <div>
              <div className="muted">Status</div>
              <StatusTag value={viewingProject.status} />
            </div>
            <div>
              <div className="muted">Last run</div>
              <div className="mono">{viewingProject.last_run_id || '—'}</div>
            </div>
          </div>
        )}
      </Modal>

    </div>
  );
}
