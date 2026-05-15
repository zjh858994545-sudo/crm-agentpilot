import {
  ApartmentOutlined,
  AuditOutlined,
  BookOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  MessageOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import { Badge, Layout, Menu, Space, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
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
  { key: '/', icon: <DashboardOutlined />, label: <NavLink to="/">运营总览</NavLink> },
  { key: '/agent', icon: <MessageOutlined />, label: <NavLink to="/agent">Agent 工作台</NavLink> },
  { key: '/customers', icon: <TeamOutlined />, label: <NavLink to="/customers">客户 360</NavLink> },
  { key: '/leads', icon: <ThunderboltOutlined />, label: <NavLink to="/leads">商机推荐</NavLink> },
  { key: '/knowledge', icon: <BookOutlined />, label: <NavLink to="/knowledge">知识库</NavLink> },
  { key: '/callcenter', icon: <PhoneOutlined />, label: <NavLink to="/callcenter">呼叫中心</NavLink> },
  { key: '/runs', icon: <AuditOutlined />, label: <NavLink to="/runs">运行审计</NavLink> },
  { key: '/evaluation', icon: <ExperimentOutlined />, label: <NavLink to="/evaluation">评测报告</NavLink> }
];

const pageMeta: Record<string, { title: string; subtitle: string }> = {
  '/': {
    title: '运营总览',
    subtitle: '销售作业、知识检索、写入确认与系统运行状态'
  },
  '/agent': {
    title: 'Agent 工作台',
    subtitle: '用自然语言驱动客户分析、知识检索和 CRM 写操作确认'
  },
  '/customers': {
    title: '客户 360',
    subtitle: '客户画像、跟进记录、风险标签和价值分层'
  },
  '/leads': {
    title: '商机推荐',
    subtitle: '基于客户价值、跟进历史和成交窗口的可解释排序'
  },
  '/knowledge': {
    title: '销售知识库',
    subtitle: 'SOP、套餐政策和质检规则的 RAG 检索'
  },
  '/callcenter': {
    title: '呼叫中心',
    subtitle: '通话摘要、质检风险和联系记录确认'
  },
  '/runs': {
    title: '运行审计',
    subtitle: 'Agent Run、Tool Call 与 Confirmation 全链路追踪'
  },
  '/evaluation': {
    title: '评测报告',
    subtitle: 'JSONL 用例、工具调用命中、RAG 引用和延迟指标'
  }
};

function resolveSelectedKey(pathname: string) {
  return (
    navItems
      .map((item) => item.key)
      .filter((key) => key !== '/')
      .find((key) => pathname.startsWith(key)) ?? '/'
  );
}

function Shell() {
  const location = useLocation();
  const [modelStatus, setModelStatus] = useState<ModelStatus | null>(null);
  const [eventMode, setEventMode] = useState('log-only');
  const selectedKey = resolveSelectedKey(location.pathname);
  const meta = pageMeta[selectedKey] ?? pageMeta['/'];

  useEffect(() => {
    fetchModelStatus().then(setModelStatus).catch(() => undefined);
    fetchEventStatus().then((status) => setEventMode(status.mode)).catch(() => undefined);
  }, []);

  const modelText = useMemo(() => {
    if (!modelStatus?.configured) {
      return 'Mock Model';
    }
    return `${modelStatus.vendor ?? modelStatus.provider} / ${modelStatus.model}`;
  }, [modelStatus]);

  const embeddingText = useMemo(() => {
    const embedding = modelStatus?.embedding;
    if (!embedding?.configured) {
      return 'Embedding Mock';
    }
    return `${embedding.vendor ?? embedding.provider} / ${embedding.model} / ${embedding.dimension}d`;
  }, [modelStatus]);

  return (
    <Layout className="app-shell">
      <Sider width={252} className="app-sider">
        <div className="brand">
          <div className="brand-icon">
            <ApartmentOutlined />
          </div>
          <div>
            <Title level={5}>CRM-AgentPilot</Title>
            <Text>销售作业 AI 工作台</Text>
          </div>
        </div>
        <div className="sider-section-label">Workspace</div>
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]} items={navItems} />
        <div className="sider-footer">
          <SafetyCertificateOutlined />
          <div>
            <Text strong>写入安全</Text>
            <span>写 CRM 前必须确认</span>
          </div>
        </div>
      </Sider>
      <Layout className="app-main">
        <Header className="app-header">
          <Space direction="vertical" size={2} className="header-title">
            <Text className="eyebrow">CRM AI Agent Platform</Text>
            <Title level={3}>{meta.title}</Title>
            <Text className="page-subtitle">{meta.subtitle}</Text>
          </Space>
          <Space size={8} wrap className="header-status">
            <Tag color={modelStatus?.configured ? 'blue' : 'default'}>{modelText}</Tag>
            <Tag color={modelStatus?.embedding?.configured ? 'green' : 'default'}>{embeddingText}</Tag>
            <Tag color={eventMode === 'log-only' ? 'default' : 'cyan'}>Events / {eventMode}</Tag>
            <Badge status="success" text="工作台在线" />
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
