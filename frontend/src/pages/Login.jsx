import React, { useEffect, useState, useRef } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

const { Title, Text } = Typography;

const HERO_PHRASES = [
  'A new era of human-AI collaboration',
  'Where humans and AI build together',
  'Intelligent flows, human control',
  'Develop smarter with AI at your side',
];

function useTypingAnimation(phrases, typeDelay = 120, deleteDelay = 60, pauseAfterType = 1200, pauseBeforeNext = 400) {
  const [phraseIndex, setPhraseIndex] = useState(0);
  const [charIndex, setCharIndex] = useState(0);
  const [isDeleting, setIsDeleting] = useState(false);
  const timeoutRef = useRef(null);

  useEffect(() => {
    const currentPhrase = phrases[phraseIndex];

    const schedule = (delay) => {
      timeoutRef.current = setTimeout(() => {
        if (isDeleting) {
          setCharIndex((prev) => {
            if (prev <= 0) {
              setIsDeleting(false);
              setPhraseIndex((pi) => (pi + 1) % phrases.length);
              return 0;
            }
            return prev - 1;
          });
        } else {
          setCharIndex((prev) => {
            if (prev >= currentPhrase.length) {
              setIsDeleting(true);
              return prev;
            }
            return prev + 1;
          });
        }
      }, delay);
    };

    if (isDeleting && charIndex === 0) {
      schedule(pauseBeforeNext);
    } else if (!isDeleting && charIndex === currentPhrase.length) {
      schedule(pauseAfterType);
    } else {
      schedule(isDeleting ? deleteDelay : typeDelay);
    }

    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, [phraseIndex, charIndex, isDeleting, phrases, typeDelay, deleteDelay, pauseAfterType, pauseBeforeNext]);

  return phrases[phraseIndex].slice(0, charIndex);
}

export default function Login() {
  const navigate = useNavigate();
  const { user, login } = useAuth();
  const [error, setError] = useState(null);
  const typedText = useTypingAnimation(HERO_PHRASES);

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
            <Button htmlType="submit" block>
              Sign in
            </Button>
            <Alert
              type="info"
              showIcon
              style={{ marginTop: 12 }}
              message="Тестовые пользователи"
              description={(
                <div>
                  <div><b>admin</b> (ADMIN) / <code>admin</code></div>
                  <div><b>flow_configurator</b> (FLOW_CONFIGURATOR) / <code>admin</code></div>
                  <div><b>product_owner</b> (PRODUCT_OWNER) / <code>admin</code></div>
                  <div><b>tech_approver</b> (TECH_APPROVER) / <code>admin</code></div>
                </div>
              )}
            />
          </Form>
        </Card>
      </div>
      <div className="login-bg">
        <div className="login-grid"></div>
        <div className="login-hero">
          <p className="login-hero-kicker">Human Guided Development</p>
          <h1 className="login-hero-title">
            <span className="login-hero-typing">{typedText}</span>
            <span className="login-hero-cursor" aria-hidden />
          </h1>
          <p className="login-hero-subtext">
            Controlled AI runtime with versioned flows, transparent execution, and human approval gates.
          </p>
          <div className="login-hero-points" aria-hidden>
            <span className="login-hero-point">Controlled runtime</span>
            <span className="login-hero-point">Versioned flows</span>
            <span className="login-hero-point">Human approval</span>
          </div>
        </div>
      </div>
    </div>
  );
}
