import {
  ApartmentOutlined,
  AuditOutlined,
  BookOutlined,
  DashboardOutlined,
  ExperimentOutlined,
  LoginOutlined,
  LogoutOutlined,
  MessageOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined
} from '@ant-design/icons';
import { Alert, Badge, Button, Card, Form, Input, Layout, Menu, Space, Spin, Tag, Typography } from 'antd';
import type { MenuProps } from 'antd';
import type { ErrorInfo, ReactNode } from 'react';
import { Component, lazy, Suspense, useEffect, useState } from 'react';
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { fetchCurrentUser, type AuthProfile } from './api/client';

const AgentChat = lazy(() => import('./pages/AgentChat/AgentChat'));
const AgentRuns = lazy(() => import('./pages/AgentRuns/AgentRuns'));
const CallCenter = lazy(() => import('./pages/CallCenter/CallCenter'));
const Customers = lazy(() => import('./pages/Customers/Customers'));
const Dashboard = lazy(() => import('./pages/Dashboard/Dashboard'));
const Evaluation = lazy(() => import('./pages/Evaluation/Evaluation'));
const KnowledgeBase = lazy(() => import('./pages/KnowledgeBase/KnowledgeBase'));
const Leads = lazy(() => import('./pages/Leads/Leads'));
const SystemAdmin = lazy(() => import('./pages/SystemAdmin/SystemAdmin'));

const { Header, Sider, Content } = Layout;
const { Paragraph, Text, Title } = Typography;

type WorkspaceRole = AuthProfile['primaryRole'];

type NavEntry = {
  key: string;
  icon: ReactNode;
  label: string;
  group: 'sales' | 'manager' | 'admin';
  visibleFor: WorkspaceRole[];
};

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

const roleTitles: Record<WorkspaceRole, string> = {
  sales: '销售代表',
  manager: '销售主管',
  admin: '系统管理员'
};

const roleColors: Record<WorkspaceRole, string> = {
  sales: 'blue',
  manager: 'purple',
  admin: 'geekblue'
};

type WorkspaceErrorBoundaryProps = {
  children: ReactNode;
};

type WorkspaceErrorBoundaryState = {
  hasError: boolean;
};

class WorkspaceErrorBoundary extends Component<WorkspaceErrorBoundaryProps, WorkspaceErrorBoundaryState> {
  state: WorkspaceErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): WorkspaceErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Workspace page crashed', error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      return (
        <Card className="workspace-error-card">
          <Space direction="vertical" size={12}>
            <Tag color="red">页面异常</Tag>
            <Title level={4}>当前工作区加载失败</Title>
            <Paragraph type="secondary">
              页面组件出现异常，但登录状态和后端服务仍保留。可以刷新页面重新进入，或切换到其他菜单继续处理业务。
            </Paragraph>
            <Button type="primary" onClick={() => window.location.reload()}>
              刷新页面
            </Button>
          </Space>
        </Card>
      );
    }
    return this.props.children;
  }
}

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

function persistUser(profile: AuthProfile) {
  window.localStorage.setItem('agentpilot.currentUser', JSON.stringify(profile));
}

function clearSession() {
  window.localStorage.removeItem('agentpilot.currentUser');
  window.localStorage.removeItem('agentpilot.apiToken');
}

function visibleNavFor(user: AuthProfile) {
  return navItems.filter((item) => item.visibleFor.includes(user.primaryRole));
}

function resolveSelectedKey(pathname: string) {
  return (
    navItems
      .map((item) => item.key)
      .filter((key) => key !== '/')
      .find((key) => pathname.startsWith(key)) ?? '/'
  );
}

function buildMenuItems(user: AuthProfile): MenuProps['items'] {
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

function LoginPage({ onLogin }: { onLogin: (profile: AuthProfile) => void }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async ({ token }: { token: string }) => {
    setSubmitting(true);
    setError(null);
    try {
      window.localStorage.setItem('agentpilot.apiToken', token.trim());
      const profile = await fetchCurrentUser();
      persistUser(profile);
      onLogin(profile);
    } catch {
      clearSession();
      setError('访问令牌无效或系统暂不可用，请确认令牌后重试。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-shell">
      <div className="login-panel login-panel-narrow">
        <Space direction="vertical" size={8} className="login-heading">
          <Text className="eyebrow">CRM-AgentPilot</Text>
          <Title level={2}>登录销售作业平台</Title>
          <Paragraph>
            输入由系统管理员分配的访问令牌。系统会根据令牌识别销售、主管或管理员身份，并自动加载对应菜单和数据范围。
          </Paragraph>
        </Space>
        <Card className="login-role-card login-token-card">
          <Form layout="vertical" onFinish={submit}>
            <Form.Item
              label="访问令牌"
              name="token"
              rules={[{ required: true, message: '请输入访问令牌' }]}
            >
              <Input.Password
                size="large"
                autoFocus
                data-testid="token-login-input"
                placeholder="请输入 X-AgentPilot-Token"
              />
            </Form.Item>
            {error ? <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} /> : null}
            <Button
              block
              size="large"
              type="primary"
              htmlType="submit"
              icon={<LoginOutlined />}
              loading={submitting}
              data-testid="token-login-submit"
            >
              登录工作台
            </Button>
          </Form>
        </Card>
      </div>
    </div>
  );
}

function Shell({ user, onLogout }: { user: AuthProfile; onLogout: () => void }) {
  const location = useLocation();
  const navigate = useNavigate();
  const selectedKey = resolveSelectedKey(location.pathname);
  const visibleNav = visibleNavFor(user);
  const allowedKeys = new Set(visibleNav.map((item) => item.key));
  const defaultPath = user.primaryRole === 'admin' ? '/system' : '/';
  const meta = pageMeta[selectedKey] ?? pageMeta[defaultPath] ?? pageMeta['/'];
  const isAdminUser = user.primaryRole === 'admin';

  const logout = () => {
    clearSession();
    onLogout();
    navigate('/', { replace: true });
  };

  const route = (key: string, element: ReactNode) =>
    allowedKeys.has(key) ? element : <Navigate to={defaultPath} replace />;

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
            <Tag color={roleColors[user.primaryRole]} icon={isAdminUser ? <SettingOutlined /> : <UserOutlined />}>
              {roleTitles[user.primaryRole]} · {user.displayName}
            </Tag>
            <Badge status="success" text="工作台在线" />
            <Button icon={<LogoutOutlined />} onClick={logout}>
              退出
            </Button>
          </Space>
        </Header>
        <Content className="app-content">
          <WorkspaceErrorBoundary>
            <Suspense fallback={<PageLoading />}>
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
                <Route path="*" element={<Navigate to={defaultPath} replace />} />
              </Routes>
            </Suspense>
          </WorkspaceErrorBoundary>
        </Content>
      </Layout>
    </Layout>
  );
}

function PageLoading() {
  return (
    <div className="page-loading">
      <Spin size="large" />
      <Text type="secondary">正在加载工作区...</Text>
    </div>
  );
}

export default function App() {
  const [user, setUser] = useState<AuthProfile | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const token = window.localStorage.getItem('agentpilot.apiToken');
    if (!token) {
      clearSession();
      setReady(true);
      return;
    }
    fetchCurrentUser()
      .then((profile) => {
        persistUser(profile);
        setUser(profile);
      })
      .catch(() => {
        clearSession();
        setUser(null);
      })
      .finally(() => setReady(true));
  }, []);

  if (!ready) {
    return (
      <div className="login-shell">
        <Space direction="vertical" align="center" size={16} style={{ width: '100%', paddingTop: 160 }}>
          <Spin size="large" />
          <Text type="secondary">正在校验登录状态...</Text>
        </Space>
      </div>
    );
  }

  return (
    <BrowserRouter>
      {user ? <Shell user={user} onLogout={() => setUser(null)} /> : <LoginPage onLogin={setUser} />}
    </BrowserRouter>
  );
}
