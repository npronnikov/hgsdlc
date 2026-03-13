import React from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigProvider } from 'antd';
import App from './App.jsx';
import 'antd/dist/reset.css';
import './styles.css';

const theme = {
  token: {
    colorPrimary: '#2563eb',
    colorInfo: '#0891b2',
    colorSuccess: '#16a34a',
    colorWarning: '#d97706',
    colorError: '#dc2626',
    colorText: '#111827',
    colorTextSecondary: '#4b5563',
    colorBgLayout: '#f7f8fa',
    colorBgContainer: '#ffffff',
    borderRadius: 12,
    fontFamily: 'Inter, Segoe UI, sans-serif',
  },
};

const root = document.getElementById('app');
if (root) {
  createRoot(root).render(
    <ConfigProvider theme={theme}>
      <App />
    </ConfigProvider>
  );
}
