import {
  ApartmentOutlined,
  AuditOutlined,
  BookOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  MessageOutlined,
  PhoneOutlined,
  TeamOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import { Badge, Layout, Menu, Space, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { fetchEventStatus, fetchModelStatus, ModelStatus } from './api/client';
import AgentChat from './pages/AgentChat/AgentChat';
import AgentRuns from './pages/AgentRuns/AgentRuns';
import CallCenter from './pages/CallCenter/CallCenter';
import Customers from './pages/Customers/Customers';
import Dashboard from './pages/Dashboard/Dashboard';
import Evaluation from './pages/Evaluation/Evaluation';
import KnowledgeBase from './pages/KnowledgeBase/KnowledgeBase';
import Leads from './pages/Leads/Leads';

const { Header, Sider, Content } = Layout;
const { Text, Title } = Typography;

const navItems = [
  { key: '/', icon: <DashboardOutlined />, label: <NavLink to="/">总览</NavLink> },
  { key: '/agent', icon: <MessageOutlined />, label: <NavLink to="/agent">Agent 工作台</NavLink> },
  { key: '/customers', icon: <TeamOutlined />, label: <NavLink to="/customers">客户 360</NavLink> },
  { key: '/leads', icon: <ThunderboltOutlined />, label: <NavLink to="/leads">商机推荐</NavLink> },
  { key: '/knowledge', icon: <BookOutlined />, label: <NavLink to="/knowledge">知识库</NavLink> },
  { key: '/callcenter', icon: <PhoneOutlined />, label: <NavLink to="/callcenter">呼叫中心</NavLink> },
  { key: '/runs', icon: <AuditOutlined />, label: <NavLink to="/runs">运行审计</NavLink> },
  { key: '/evaluation', icon: <ExperimentOutlined />, label: <NavLink to="/evaluation">评测报告</NavLink> }
];

function Shell() {
  const location = useLocation();
  const [modelStatus, setModelStatus] = useState<ModelStatus | null>(null);
  const [eventMode, setEventMode] = useState('log-only');
  const selectedKey =
    navItems
      .map((item) => item.key)
      .filter((key) => key !== '/')
      .find((key) => location.pathname.startsWith(key)) ?? '/';

  useEffect(() => {
    fetchModelStatus().then(setModelStatus).catch(() => undefined);
    fetchEventStatus().then((status) => setEventMode(status.mode)).catch(() => undefined);
  }, []);

  const modelText = modelStatus?.configured
    ? `${modelStatus.vendor ?? modelStatus.provider} / ${modelStatus.model}`
    : 'deterministic mock';
  const embeddingText = modelStatus?.embedding?.configured
    ? `${modelStatus.embedding.vendor ?? modelStatus.embedding.provider} / ${modelStatus.embedding.model} / ${modelStatus.embedding.dimension}d`
    : 'embedding: mock';

  return (
    <Layout className="app-shell">
      <Sider width={232} className="app-sider">
        <div className="brand">
          <ApartmentOutlined className="brand-icon" />
          <div>
            <Title level={5}>CRM-AgentPilot</Title>
            <Text>销售作业 AI Agent</Text>
          </div>
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]} items={navItems} />
      </Sider>
      <Layout>
        <Header className="app-header">
          <Space direction="vertical" size={0}>
            <Text className="eyebrow">Interview Demo</Text>
            <Title level={4}>CRM AI Agent 全栈平台</Title>
          </Space>
          <Space size={10} wrap className="header-status">
            <Tag color={modelStatus?.configured ? 'blue' : 'default'}>{modelText}</Tag>
            <Tag color={modelStatus?.embedding?.configured ? 'green' : 'default'}>{embeddingText}</Tag>
            <Tag color={eventMode === 'log-only' ? 'default' : 'green'}>events: {eventMode}</Tag>
            <Badge status="success" text="Demo Ready" />
          </Space>
        </Header>
        <Content className="app-content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/agent" element={<AgentChat />} />
            <Route path="/customers" element={<Customers />} />
            <Route path="/leads" element={<Leads />} />
            <Route path="/knowledge" element={<KnowledgeBase />} />
            <Route path="/callcenter" element={<CallCenter />} />
            <Route path="/runs" element={<AgentRuns />} />
            <Route path="/evaluation" element={<Evaluation />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Shell />
    </BrowserRouter>
  );
}
