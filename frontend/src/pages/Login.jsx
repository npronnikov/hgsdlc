import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

const { Title, Text } = Typography;

export default function Login() {
  const navigate = useNavigate();
  const { user, login } = useAuth();
  const [error, setError] = useState(null);

  useEffect(() => {
    if (user) {
      navigate('/overview', { replace: true });
    }
  }, [user, navigate]);

  const handleFinish = async (values) => {
    setError(null);
    try {
      await login(values.username, values.password);
      navigate('/overview', { replace: true });
    } catch (err) {
      setError(err.message || 'Login failed');
    }
  };

  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="brand">
          <div className="brand-row">
            <div className="brand-mark">HG</div>
            <Title level={2} style={{ margin: 0 }}>SDLC Platform</Title>
          </div>
          <Text type="secondary">Managed flow execution for Git-based engineering work</Text>
        </div>
        <Card>
          <Form layout="vertical" onFinish={handleFinish}>
            <Form.Item
              label="Username"
              name="username"
              rules={[{ required: true, message: 'Enter username' }]}
            >
              <Input placeholder="admin" />
            </Form.Item>
            <Form.Item
              label="Password"
              name="password"
              rules={[{ required: true, message: 'Enter password' }]}
            >
              <Input.Password placeholder="admin" />
            </Form.Item>
            {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
            <Button type="primary" htmlType="submit" block>
              Sign in
            </Button>
          </Form>
        </Card>
      </div>
      <div className="login-bg">
        <div className="login-grid"></div>
      </div>
    </div>
  );
}
