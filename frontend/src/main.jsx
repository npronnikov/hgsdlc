import React from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigProvider, theme as antdTheme } from 'antd';
import App from './App.jsx';
import 'antd/dist/reset.css';
import './styles.css';
import { ThemeModeProvider, useThemeMode } from './theme/ThemeContext.jsx';

function ThemedApp() {
  const { isDark } = useThemeMode();

  const theme = {
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorPrimary: '#0f766e',
      colorInfo: '#0ea5e9',
      colorSuccess: '#16a34a',
      colorWarning: '#d97706',
      colorError: '#dc2626',
      colorText: isDark ? '#e5e7eb' : '#111827',
      colorTextSecondary: isDark ? '#9ca3af' : '#4b5563',
      colorBgLayout: isDark ? '#0b1220' : '#f6f7fb',
      colorBgContainer: isDark ? '#111827' : '#ffffff',
      borderRadius: 0,
      borderRadiusLG: 0,
      borderRadiusSM: 0,
      fontFamily: 'Space Grotesk, Segoe UI, sans-serif',
    },
    components: {
      Button: {
        borderRadius: 0,
      },
      Card: {
        borderRadiusLG: 0,
      },
      Modal: {
        borderRadiusLG: 0,
        borderRadiusSM: 0,
      },
      Drawer: {
        borderRadiusLG: 0,
      },
      Input: {
        borderRadius: 0,
      },
      Select: {
        borderRadius: 0,
      },
    },
  };

  return (
    <ConfigProvider theme={theme}>
      <App />
    </ConfigProvider>
  );
}

const root = document.getElementById('app');
if (root) {
  createRoot(root).render(
    <ThemeModeProvider>
      <ThemedApp />
    </ThemeModeProvider>
  );
}
