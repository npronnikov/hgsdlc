import { useEffect, useState } from 'react';
import {
  Button,
  Dropdown,
  Form,
  Input,
  Modal,
  Select,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  MoreOutlined,
  PlusOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAuth } from '../auth/AuthContext.jsx';
import { listUsers, createUser, updateUser, changePassword, deleteUser } from '../api/users.js';

const { Title } = Typography;

const ALL_ROLES = ['ADMIN', 'FLOW_CONFIGURATOR', 'PRODUCT_OWNER', 'TECH_APPROVER'];

const ROLE_COLORS = {
  ADMIN: 'red',
  FLOW_CONFIGURATOR: 'blue',
  PRODUCT_OWNER: 'green',
  TECH_APPROVER: 'orange',
};

export default function Users() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [pwdModalOpen, setPwdModalOpen] = useState(false);
  const [pwdTarget, setPwdTarget] = useState(null);
  const [form] = Form.useForm();
  const [pwdForm] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const data = await listUsers();
      setUsers(data || []);
    } catch (err) {
      message.error(err.message || 'Failed to load users');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openCreate = () => {
    setEditingUser(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (target) => {
    setEditingUser(target);
    form.setFieldsValue({
      display_name: target.display_name,
      roles: target.roles,
      enabled: target.enabled,
    });
    setModalOpen(true);
  };

  const openPwd = (target) => {
    setPwdTarget(target);
    pwdForm.resetFields();
    setPwdModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      if (editingUser) {
        await updateUser(editingUser.id, {
          display_name: values.display_name?.trim(),
          roles: values.roles,
          enabled: values.enabled,
        });
        message.success('User updated');
      } else {
        await createUser({
          username: values.username?.trim().toLowerCase(),
          display_name: values.display_name?.trim(),
          password: values.password,
          roles: values.roles,
        });
        message.success('User created');
      }

      setModalOpen(false);
      setEditingUser(null);
      form.resetFields();
      load();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err.message || 'Failed to save user');
    } finally {
      setSubmitting(false);
    }
  };

  const handleChangePassword = async () => {
    try {
      const values = await pwdForm.validateFields();
      setSubmitting(true);
      await changePassword(pwdTarget.id, values.password);
      message.success('Password changed');
      setPwdModalOpen(false);
      setPwdTarget(null);
      pwdForm.resetFields();
    } catch (err) {
      if (err?.errorFields) return;
      message.error(err.message || 'Failed to change password');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = (target) => {
    Modal.confirm({
      title: `Delete user "${target.username}"?`,
      content: 'This action cannot be undone.',
      okText: 'Delete',
      okButtonProps: { danger: true },
      cancelText: 'Cancel',
      onOk: async () => {
        try {
          await deleteUser(target.id);
          message.success('User deleted');
          load();
        } catch (err) {
          message.error(err.message || 'Failed to delete user');
        }
      },
    });
  };

  const isSelf = (target) => target.id === currentUser?.id;

  const menuItems = (target) => [
    { key: 'edit', label: 'Edit', icon: <EditOutlined /> },
    { key: 'password', label: 'Change password', icon: <KeyOutlined /> },
    {
      key: 'delete',
      label: (
        <Tooltip title={isSelf(target) ? 'Cannot delete your own account' : ''}>
          Delete
        </Tooltip>
      ),
      icon: <DeleteOutlined />,
      danger: true,
      disabled: isSelf(target),
    },
  ];

  const columns = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      render: (val) => <span className="mono">{val}</span>,
    },
    {
      title: 'Display name',
      dataIndex: 'display_name',
      key: 'display_name',
    },
    {
      title: 'Roles',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles) => (
        <>
          {(roles || []).map((r) => (
            <Tag key={r} color={ROLE_COLORS[r] || 'default'}>
              {r}
            </Tag>
          ))}
        </>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled) =>
        enabled ? (
          <Tag color="success">Active</Tag>
        ) : (
          <Tag color="default">Disabled</Tag>
        ),
    },
    {
      title: 'Created',
      dataIndex: 'created_at',
      key: 'created_at',
      render: (val) => val ? new Date(val).toLocaleDateString() : '—',
    },
    {
      title: '',
      key: 'actions',
      width: 48,
      render: (_, record) => (
        <Dropdown
          trigger={['click']}
          menu={{
            items: menuItems(record),
            onClick: ({ key }) => {
              if (key === 'edit') openEdit(record);
              else if (key === 'password') openPwd(record);
              else if (key === 'delete') handleDelete(record);
            },
          }}
        >
          <Button type="text" size="small" icon={<MoreOutlined />} />
        </Dropdown>
      ),
    },
  ];

  return (
    <div className="cards-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Users</Title>
        <Button type="default" icon={<PlusOutlined />} onClick={openCreate}>
          New user
        </Button>
      </div>

      <div className="cards-fullscreen">
        <Table
          rowKey="id"
          dataSource={users}
          columns={columns}
          loading={loading}
          pagination={false}
          size="middle"
        />
      </div>

      {/* Create / Edit modal */}
      <Modal
        title={editingUser ? 'Edit user' : 'New user'}
        open={modalOpen}
        onOk={handleSubmit}
        confirmLoading={submitting}
        onCancel={() => {
          setModalOpen(false);
          setEditingUser(null);
          form.resetFields();
        }}
        okText={editingUser ? 'Save' : 'Create'}
        cancelText="Cancel"
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          {!editingUser && (
            <Form.Item
              label="Username"
              name="username"
              rules={[{ required: true, message: 'Enter username' }]}
            >
              <Input prefix={<UserOutlined />} autoComplete="off" />
            </Form.Item>
          )}
          <Form.Item
            label="Display name"
            name="display_name"
            rules={[{ required: true, message: 'Enter display name' }]}
          >
            <Input autoComplete="off" />
          </Form.Item>
          {!editingUser && (
            <Form.Item
              label="Password"
              name="password"
              rules={[
                { required: true, message: 'Enter password' },
                { min: 6, message: 'Password must be at least 6 characters' },
              ]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          )}
          <Form.Item
            label="Roles"
            name="roles"
            rules={[{ required: true, message: 'Select at least one role' }]}
          >
            <Select
              mode="multiple"
              options={ALL_ROLES.map((r) => ({ value: r, label: r }))}
              placeholder="Select roles"
            />
          </Form.Item>
          {editingUser && (
            <Form.Item label="Enabled" name="enabled" valuePropName="checked">
              <Tooltip
                title={isSelf(editingUser) ? 'Cannot disable your own account' : ''}
              >
                <Switch disabled={isSelf(editingUser)} />
              </Tooltip>
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* Change password modal */}
      <Modal
        title={`Change password — ${pwdTarget?.username}`}
        open={pwdModalOpen}
        onOk={handleChangePassword}
        confirmLoading={submitting}
        onCancel={() => {
          setPwdModalOpen(false);
          setPwdTarget(null);
          pwdForm.resetFields();
        }}
        okText="Change"
        cancelText="Cancel"
        destroyOnHidden
      >
        <Form form={pwdForm} layout="vertical">
          <Form.Item
            label="New password"
            name="password"
            rules={[
              { required: true, message: 'Enter new password' },
              { min: 6, message: 'Password must be at least 6 characters' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
