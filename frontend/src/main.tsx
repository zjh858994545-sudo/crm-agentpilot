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
          borderRadius: 6,
          fontFamily:
            '-apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif'
        },
        components: {
          Layout: {
            headerBg: '#ffffff',
            siderBg: '#111827',
            bodyBg: '#f5f7fb'
          },
          Card: {
            borderRadiusLG: 8
          }
        }
      }}
    >
      <App />
    </ConfigProvider>
  </React.StrictMode>
);

