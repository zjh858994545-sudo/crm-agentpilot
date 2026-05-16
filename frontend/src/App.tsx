import {
  ApartmentOutlined,
  AuditOutlined,
  BookOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  LoginOutlined,
  MessageOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined
} from '@ant-design/icons';
import { Badge, Button, Card, Col, Layout, Menu, Row, Select, Space, Tag, Typography } from 'antd';
import type { MenuProps } from 'antd';
import type { ReactNode } from 'react';
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import AgentChat from './pages/AgentChat/AgentChat';
import AgentRuns from './pages/AgentRuns/AgentRuns';
import CallCenter from './pages/CallCenter/CallCenter';
import Customers from './pages/Customers/Customers';
import Dashboard from './pages/Dashboard/Dashboard';
import Evaluation from './pages/Evaluation/Evaluation';
import KnowledgeBase from './pages/KnowledgeBase/KnowledgeBase';
import Leads from './pages/Leads/Leads';
import SystemAdmin from './pages/SystemAdmin/SystemAdmin';
import { useState } from 'react';

const { Header, Sider, Content } = Layout;
const { Paragraph, Text, Title } = Typography;

type WorkspaceRole = 'sales' | 'manager' | 'admin';

type WorkspaceUser = {
  id: string;
  userId: number;
  salesRepId: number;
  name: string;
  title: string;
  role: WorkspaceRole;
  token: string;
  defaultPath: string;
  description: string;
  color: string;
};

type NavEntry = {
  key: string;
  icon: ReactNode;
  label: string;
  group: 'sales' | 'manager' | 'admin';
  visibleFor: WorkspaceRole[];
};

const workspaceUsers: WorkspaceUser[] = [
  {
    id: 'sales-1',
    userId: 1,
    salesRepId: 1,
    name: '林晓峰',
    title: '销售代表',
    role: 'sales',
    token: 'agentpilot-sales-1',
    defaultPath: '/',
    description: '处理客户跟进、知识问答、通话摘要和 CRM 写入确认。',
    color: 'blue'
  },
  {
    id: 'manager',
    userId: 100,
    salesRepId: 1,
    name: '陈明',
    title: '销售主管',
    role: 'manager',
    token: 'agentpilot-manager',
    defaultPath: '/',
    description: '查看高优商机、风险客户、趋势指标和团队作业重点。',
    color: 'purple'
  },
  {
    id: 'admin',
    userId: 900,
    salesRepId: 1,
    name: '系统管理员',
    title: '系统管理员',
    role: 'admin',
    token: 'agentpilot-admin',
    defaultPath: '/system',
    description: '管理系统能力、运行审计、质量评估和事件可靠性。',
    color: 'geekblue'
  }
];

const navItems: NavEntry[] = [
  { key: '/', icon: <DashboardOutlined />, label: '今日工作台', group: 'sales', visibleFor: ['sales', 'manager'] },
  { key: '/agent', icon: <MessageOutlined />, label: 'AI 助手', group: 'sales', visibleFor: ['sales', 'manager'] },
  { key: '/customers', icon: <TeamOutlined />, label: '客户 360', group: 'sales', visibleFor: ['sales', 'manager'] },
  { key: '/leads', icon: <ThunderboltOutlined />, label: '商机优先级', group: 'manager', visibleFor: ['sales', 'manager'] },
  { key: '/knowledge', icon: <BookOutlined />, label: '知识库问答', group: 'sales', visibleFor: ['sales', 'manager', 'admin'] },
  { key: '/callcenter', icon: <PhoneOutlined />, label: '呼叫中心', group: 'sales', visibleFor: ['sales'] },
  { key: '/system', icon: <SettingOutlined />, label: '系统能力', group: 'admin', visibleFor: ['admin'] },
  { key: '/runs', icon: <AuditOutlined />, label: '运行审计', group: 'admin', visibleFor: ['admin'] },
  { key: '/evaluation', icon: <ExperimentOutlined />, label: '质量评估', group: 'admin', visibleFor: ['admin'] }
];

const groupLabels: Record<NavEntry['group'], string> = {
  sales: '销售每日作业',
  manager: '销售主管',
  admin: '系统管理'
};

const pageMeta: Record<string, { title: string; subtitle: string; role: string }> = {
  '/': {
    title: '今日销售工作台',
    subtitle: '把客户、商机、AI 建议和待确认动作收在一个业务入口里',
    role: '销售 / 主管'
  },
  '/agent': {
    title: 'AI 助手',
    subtitle: '用自然语言完成客户分析、知识查询、任务建议和写入确认',
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
  '/system': {
    title: '系统能力',
    subtitle: '系统管理员集中查看限流、RBAC、Outbox、模型工具和向量检索状态',
    role: '系统管理'
  },
  '/runs': {
    title: '运行审计',
    subtitle: '系统管理员排查每一次 AI 处理、工具调用和确认链路',
    role: '系统管理'
  },
  '/evaluation': {
    title: '质量评估',
    subtitle: '系统管理员查看评测用例、知识引用、工具命中和延迟指标',
    role: '系统管理'
  }
};

function readStoredUser() {
  try {
    const stored = JSON.parse(
      window.localStorage.getItem('agentpilot.currentUser') || window.localStorage.getItem('agentpilot.demoUser') || '{}'
    ) as Pick<WorkspaceUser, 'id'>;
    return workspaceUsers.find((user) => user.id === stored.id) ?? null;
  } catch {
    return null;
  }
}

function persistUser(user: WorkspaceUser) {
  window.localStorage.setItem('agentpilot.currentUser', JSON.stringify(user));
  window.localStorage.setItem('agentpilot.apiToken', user.token);
}

function visibleNavFor(user: WorkspaceUser) {
  return navItems.filter((item) => item.visibleFor.includes(user.role));
}

function resolveSelectedKey(pathname: string) {
  return (
    navItems
      .map((item) => item.key)
      .filter((key) => key !== '/')
      .find((key) => pathname.startsWith(key)) ?? '/'
  );
}

function buildMenuItems(user: WorkspaceUser): MenuProps['items'] {
  const visibleNav = visibleNavFor(user);
  return (['sales', 'manager', 'admin'] as NavEntry['group'][]).flatMap((group) => {
    const children = visibleNav
      .filter((item) => item.group === group)
      .map((item) => ({
        key: item.key,
        icon: item.icon,
        label: <NavLink to={item.key}>{item.label}</NavLink>
      }));
    return children.length
      ? [
          {
            key: `group-${group}`,
            type: 'group' as const,
            label: groupLabels[group],
            children
          }
        ]
      : [];
  });
}

function LoginPage({ onLogin }: { onLogin: (user: WorkspaceUser) => void }) {
  return (
    <div className="login-shell">
      <div className="login-panel">
        <Space direction="vertical" size={8} className="login-heading">
          <Text className="eyebrow">CRM-AgentPilot</Text>
          <Title level={2}>选择工作身份</Title>
          <Paragraph>
            用销售、主管、系统管理员三个视角进入系统。每个身份都会写入对应权限上下文，
            前端菜单和后端数据范围一起切换。
          </Paragraph>
        </Space>
        <Row gutter={[16, 16]}>
          {workspaceUsers.map((user) => (
            <Col xs={24} md={8} key={user.id}>
              <Card className={`login-role-card role-${user.role}`}>
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Space align="center">
                    <span className={`role-avatar role-avatar-${user.role}`}>
                      {user.role === 'admin' ? <SettingOutlined /> : user.role === 'manager' ? <ThunderboltOutlined /> : <UserOutlined />}
                    </span>
                    <div>
                      <Text strong>{user.name}</Text>
                      <div className="metric-label">{user.title}</div>
                    </div>
                  </Space>
                  <Paragraph className="login-role-desc">{user.description}</Paragraph>
                  <Button
                    block
                    type="primary"
                    icon={<LoginOutlined />}
                    data-testid={`login-${user.role}`}
                    onClick={() => onLogin(user)}
                  >
                    进入{user.title}视角
                  </Button>
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
      </div>
    </div>
  );
}

function Shell({ user, onUserChange }: { user: WorkspaceUser; onUserChange: (user: WorkspaceUser) => void }) {
  const location = useLocation();
  const navigate = useNavigate();
  const selectedKey = resolveSelectedKey(location.pathname);
  const visibleNav = visibleNavFor(user);
  const allowedKeys = new Set(visibleNav.map((item) => item.key));
  const meta = pageMeta[selectedKey] ?? pageMeta[user.defaultPath] ?? pageMeta['/'];
  const isAdminUser = user.role === 'admin';

  const switchUser = (nextUserId: string) => {
    const nextUser = workspaceUsers.find((item) => item.id === nextUserId);
    if (!nextUser) {
      return;
    }
    persistUser(nextUser);
    onUserChange(nextUser);
    navigate(nextUser.defaultPath, { replace: true });
  };

  const route = (key: string, element: ReactNode) =>
    allowedKeys.has(key) ? element : <Navigate to={user.defaultPath} replace />;

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
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]} items={buildMenuItems(user)} />
        <div className="sider-footer">
          {isAdminUser ? <SettingOutlined /> : <SafetyCertificateOutlined />}
          <div>
            <Text strong>{isAdminUser ? '系统管理区' : '写入安全'}</Text>
            <span>{isAdminUser ? '模型、审计、评测集中管理' : '写 CRM 前必须确认'}</span>
          </div>
        </div>
      </Sider>
      <Layout className="app-main">
        <Header className="app-header">
          <Space direction="vertical" size={2} className="header-title">
            <Text className="eyebrow">CRM AI Workspace</Text>
            <Title level={3}>{meta.title}</Title>
            <Text className="page-subtitle">{meta.subtitle}</Text>
          </Space>
          <Space size={8} wrap className="header-status">
            <Tag color={user.color} icon={isAdminUser ? <SettingOutlined /> : <UserOutlined />}>
              {user.title} · {user.name}
            </Tag>
            <Select
              size="middle"
              className="identity-switcher"
              data-testid="identity-switcher"
              value={user.id}
              options={workspaceUsers.map((item) => ({ label: `${item.title} · ${item.name}`, value: item.id }))}
              onChange={switchUser}
            />
            <Badge status="success" text="工作台在线" />
          </Space>
        </Header>
        <Content className="app-content">
          <Routes>
            <Route path="/" element={route('/', <Dashboard />)} />
            <Route path="/agent" element={route('/agent', <AgentChat />)} />
            <Route path="/customers" element={route('/customers', <Customers />)} />
            <Route path="/leads" element={route('/leads', <Leads />)} />
            <Route path="/knowledge" element={route('/knowledge', <KnowledgeBase />)} />
            <Route path="/callcenter" element={route('/callcenter', <CallCenter />)} />
            <Route path="/system" element={route('/system', <SystemAdmin />)} />
            <Route path="/runs" element={route('/runs', <AgentRuns />)} />
            <Route path="/evaluation" element={route('/evaluation', <Evaluation />)} />
            <Route path="*" element={<Navigate to={user.defaultPath} replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

export default function App() {
  const [user, setUser] = useState<WorkspaceUser | null>(() => readStoredUser());

  const login = (nextUser: WorkspaceUser) => {
    persistUser(nextUser);
    setUser(nextUser);
  };

  return (
    <BrowserRouter>
      {user ? <Shell user={user} onUserChange={setUser} /> : <LoginPage onLogin={login} />}
    </BrowserRouter>
  );
}
