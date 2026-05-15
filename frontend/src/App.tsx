import {
  ApartmentOutlined,
  AuditOutlined,
  BookOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  MessageOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined
} from '@ant-design/icons';
import { Badge, Layout, Menu, Space, Tag, Typography } from 'antd';
import type { MenuProps } from 'antd';
import type { ReactNode } from 'react';
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
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

type NavEntry = {
  key: string;
  icon: ReactNode;
  label: string;
  role: '销售' | '主管' | '系统管理';
};

const salesNav: NavEntry[] = [
  { key: '/', icon: <DashboardOutlined />, label: '今日工作台', role: '销售' },
  { key: '/agent', icon: <MessageOutlined />, label: 'Agent 工作台', role: '销售' },
  { key: '/customers', icon: <TeamOutlined />, label: '客户 360', role: '销售' },
  { key: '/knowledge', icon: <BookOutlined />, label: '知识库问答', role: '销售' },
  { key: '/callcenter', icon: <PhoneOutlined />, label: '呼叫中心', role: '销售' }
];

const managerNav: NavEntry[] = [
  { key: '/leads', icon: <ThunderboltOutlined />, label: '商机优先级', role: '主管' }
];

const adminNav: NavEntry[] = [
  { key: '/runs', icon: <AuditOutlined />, label: '运行审计', role: '系统管理' },
  { key: '/evaluation', icon: <ExperimentOutlined />, label: '质量评估', role: '系统管理' }
];

const flatNavItems = [...salesNav, ...managerNav, ...adminNav];

const menuItems: MenuProps['items'] = [
  {
    key: 'group-sales',
    type: 'group',
    label: '销售每日作业',
    children: salesNav.map((item) => ({
      key: item.key,
      icon: item.icon,
      label: <NavLink to={item.key}>{item.label}</NavLink>
    }))
  },
  {
    key: 'group-manager',
    type: 'group',
    label: '销售主管',
    children: managerNav.map((item) => ({
      key: item.key,
      icon: item.icon,
      label: <NavLink to={item.key}>{item.label}</NavLink>
    }))
  },
  {
    key: 'group-admin',
    type: 'group',
    label: '系统管理',
    children: adminNav.map((item) => ({
      key: item.key,
      icon: item.icon,
      label: <NavLink to={item.key}>{item.label}</NavLink>
    }))
  }
];

const pageMeta: Record<string, { title: string; subtitle: string; role: string }> = {
  '/': {
    title: '今日销售工作台',
    subtitle: '把客户、商机、Agent 建议和待确认动作收在一个业务入口里',
    role: '销售 / 主管'
  },
  '/agent': {
    title: 'Agent 工作台',
    subtitle: '用自然语言完成客户分析、知识检索、任务建议和写入确认',
    role: '销售'
  },
  '/customers': {
    title: '客户 360',
    subtitle: '查看客户画像、风险标签、跟进时间线和下一步动作',
    role: '销售'
  },
  '/leads': {
    title: '商机优先级',
    subtitle: '销售主管查看高优先级商机、评分解释和跟进建议',
    role: '主管'
  },
  '/knowledge': {
    title: '知识库问答',
    subtitle: '查询销售 SOP、套餐政策、质检规则，并查看引用来源',
    role: '销售'
  },
  '/callcenter': {
    title: '呼叫中心',
    subtitle: '生成通话摘要、识别质检风险，并确认写入联系记录',
    role: '销售'
  },
  '/runs': {
    title: '运行审计',
    subtitle: '系统管理员排查 Agent Run、Tool Call、Confirmation 全链路',
    role: '系统管理'
  },
  '/evaluation': {
    title: '质量评估',
    subtitle: '系统管理员查看 JSONL 用例、工具命中、RAG 引用和延迟指标',
    role: '系统管理'
  }
};

function resolveSelectedKey(pathname: string) {
  return (
    flatNavItems
      .map((item) => item.key)
      .filter((key) => key !== '/')
      .find((key) => pathname.startsWith(key)) ?? '/'
  );
}

function Shell() {
  const location = useLocation();
  const selectedKey = resolveSelectedKey(location.pathname);
  const meta = pageMeta[selectedKey] ?? pageMeta['/'];
  const isAdminPage = meta.role === '系统管理';

  return (
    <Layout className="app-shell">
      <Sider width={260} className="app-sider">
        <div className="brand">
          <div className="brand-icon">
            <ApartmentOutlined />
          </div>
          <div>
            <Title level={5}>CRM-AgentPilot</Title>
            <Text>销售作业 AI 工作台</Text>
          </div>
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]} items={menuItems} />
        <div className="sider-footer">
          {isAdminPage ? <SettingOutlined /> : <SafetyCertificateOutlined />}
          <div>
            <Text strong>{isAdminPage ? '系统管理区' : '写入安全'}</Text>
            <span>{isAdminPage ? '模型、审计、评测集中管理' : '写 CRM 前必须确认'}</span>
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
            <Tag color={isAdminPage ? 'purple' : 'blue'} icon={isAdminPage ? <SettingOutlined /> : <UserOutlined />}>
              {meta.role}
            </Tag>
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
