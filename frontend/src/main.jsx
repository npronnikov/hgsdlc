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
      borderRadius: 4,
      borderRadiusLG: 6,
      borderRadiusSM: 4,
      fontFamily: 'Space Grotesk, Segoe UI, sans-serif',
    },
    components: {
      Button: {
        borderRadius: 4,
      },
      Card: {
        borderRadiusLG: 6,
      },
      Modal: {
        borderRadiusLG: 6,
        borderRadiusSM: 4,
      },
      Drawer: {
        borderRadiusLG: 6,
      },
      Input: {
        borderRadius: 4,
      },
      Select: {
        borderRadius: 4,
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
