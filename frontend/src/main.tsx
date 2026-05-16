import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2563eb',
          colorSuccess: '#16a34a',
          colorWarning: '#d97706',
          colorError: '#dc2626',
          colorInfo: '#0f766e',
          colorText: '#162033',
          colorTextSecondary: '#65758b',
          colorBgLayout: '#eef2f6',
          colorBorder: '#dbe4ef',
          controlHeight: 36,
          borderRadius: 7,
          fontFamily:
            '-apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif'
        },
        components: {
          Button: {
            borderRadius: 7,
            controlHeight: 36,
            fontWeight: 600
          },
          Layout: {
            headerBg: '#ffffff',
            siderBg: '#111827',
            bodyBg: '#f5f7fb'
          },
          Card: {
            borderRadiusLG: 8,
            headerFontSize: 15
          },
          Table: {
            headerBg: '#f6f8fb',
            headerColor: '#475569',
            rowHoverBg: '#f7fbff'
          },
          Tag: {
            borderRadiusSM: 6
          }
        }
      }}
    >
      <App />
    </ConfigProvider>
  </React.StrictMode>
);
