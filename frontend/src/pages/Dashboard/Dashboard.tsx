import {
  AuditOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  MessageOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserSwitchOutlined
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Progress,
  Row,
  Space,
  Statistic,
  Tag,
  Typography,
  message as antdMessage
} from 'antd';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AgentConfirmation,
  confirmAgentAction,
  CrmTask,
  Customer,
  EventStatus,
  fetchAgentConfirmations,
  fetchCustomers,
  fetchEventStatus,
  fetchHealth,
  fetchKnowledgeStatus,
  fetchLeadRecommendations,
  fetchModelStatus,
  fetchSecurityStatus,
  fetchTasks,
  HealthView,
  KnowledgeStatus,
  LeadRecommendation,
  ModelStatus,
  rejectAgentAction,
  SecurityStatus
} from '../../api/client';

const { Paragraph, Text, Title } = Typography;

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

const fallbackCustomers: Customer[] = [
  {
    id: 1001,
    name: '美家房产',
    industry: '房产',
    city: '北京',
    valueLevel: 'A',
    riskLevel: 'MEDIUM',
    lifecycleStage: '续费期',
    packageExpireAt: '2026-05-31T00:00:00',
    tags: '续费,价格敏感,ROI关注'
  },
  {
    id: 1002,
    name: '快招人力',
    industry: '招聘',
    city: '天津',
    valueLevel: 'A',
    riskLevel: 'LOW',
    lifecycleStage: '高意向',
    tags: '招聘旺季,升级套餐'
  },
  {
    id: 1003,
    name: '老街火锅',
    industry: '餐饮',
    city: '石家庄',
    valueLevel: 'B',
    riskLevel: 'HIGH',
    lifecycleStage: '异议处理',
    tags: '价格异议,效果担忧'
  }
];

const fallbackLeads: LeadRecommendation[] = [
  {
    leadId: 3001,
    customerId: 1001,
    customerName: '美家房产',
    industry: '房产',
    estimatedAmount: 8999,
    expectedCloseDate: '2026-05-20',
    score: 86.5,
    priority: 'HIGH',
    reasons: ['套餐即将到期', 'A 类客户', '最近 10 天未联系'],
    suggestedAction: '优先电话跟进，准备 ROI 和曝光数据复盘。'
  },
  {
    leadId: 3002,
    customerId: 1002,
    customerName: '快招人力',
    industry: '招聘',
    estimatedAmount: 6999,
    expectedCloseDate: '2026-05-22',
    score: 81,
    priority: 'HIGH',
    reasons: ['招聘旺季', '高意向线索'],
    suggestedAction: '输出套餐对比和招聘效果案例。'
  },
  {
    leadId: 3003,
    customerId: 1003,
    customerName: '老街火锅',
    industry: '餐饮',
    estimatedAmount: 4999,
    expectedCloseDate: '2026-05-18',
    score: 73.5,
    priority: 'MEDIUM',
    reasons: ['存在价格异议', '续费风险较高'],
    suggestedAction: '处理价格异议，约定复盘时间。'
  }
];

const fallbackTasks: CrmTask[] = [
  {
    id: 1,
    customerId: 1001,
    salesRepId: 1,
    title: '复盘曝光数据并推进续费',
    content: '准备最近 30 天曝光和咨询数据，处理价格异议。',
    dueTime: '2026-05-16T10:00:00',
    status: 'TODO',
    source: 'agent'
  },
  {
    id: 2,
    customerId: 1003,
    salesRepId: 1,
    title: '回访价格异议客户',
    content: '确认客户预算区间，给出轻量套餐方案。',
    dueTime: '2026-05-16T15:30:00',
    status: 'TODO',
    source: 'manual'
  }
];

const priorityColor: Record<string, string> = {
  HIGH: 'red',
  MEDIUM: 'orange',
  LOW: 'default'
};

function currency(value?: number) {
  if (value == null) {
    return '-';
  }
  return `¥${Number(value).toLocaleString('zh-CN')}`;
}

function isOpenTask(task: CrmTask) {
  return !['DONE', 'CANCELLED', 'CLOSED'].includes(String(task.status ?? '').toUpperCase());
}

function isDueSoon(value?: string) {
  if (!value) {
    return false;
  }
  const due = new Date(value).getTime();
  return Number.isFinite(due) && due <= Date.now() + 2 * 86400000;
}

function statusTag(value?: string) {
  const normalized = value ?? 'unknown';
  const color =
    normalized.includes('pgvector') || normalized === 'UP' || normalized === 'ready'
      ? 'green'
      : normalized.includes('fallback') || normalized.includes('mock')
        ? 'orange'
        : 'blue';
  return <Tag color={color}>{normalized}</Tag>;
}

function agentUrlForLead(lead: LeadRecommendation) {
  const reasons = lead.reasons.join('、');
  const prompt = `请基于商机 ${lead.leadId} 分析${lead.customerName}，推荐理由是：${reasons}。请给出跟进策略，并判断是否需要创建跟进任务。`;
  return `/agent?prompt=${encodeURIComponent(prompt)}&leadId=${lead.leadId}&customerId=${lead.customerId}&source=lead`;
}

export default function Dashboard() {
  const trendChartRef = useRef<HTMLDivElement | null>(null);
  const [customers, setCustomers] = useState<Customer[]>(fallbackCustomers);
  const [leads, setLeads] = useState<LeadRecommendation[]>(fallbackLeads);
  const [tasks, setTasks] = useState<CrmTask[]>(fallbackTasks);
  const [health, setHealth] = useState<HealthView | null>(null);
  const [modelStatus, setModelStatus] = useState<ModelStatus | null>(null);
  const [knowledgeStatus, setKnowledgeStatus] = useState<KnowledgeStatus | null>(null);
  const [securityStatus, setSecurityStatus] = useState<SecurityStatus | null>(null);
  const [eventStatus, setEventStatus] = useState<EventStatus | null>(null);
  const [pendingConfirmations, setPendingConfirmations] = useState<AgentConfirmation[]>([]);
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [businessDataMode, setBusinessDataMode] = useState<'real' | 'sample'>('sample');
  const [error, setError] = useState('');

  const refreshConfirmations = async () => {
    try {
      setPendingConfirmations(await fetchAgentConfirmations('PENDING'));
    } catch {
      setPendingConfirmations([]);
    }
  };

  useEffect(() => {
    Promise.allSettled([
      fetchAgentConfirmations('PENDING'),
      fetchCustomers(),
      fetchLeadRecommendations(20),
      fetchTasks(),
      fetchHealth(),
      fetchModelStatus(),
      fetchKnowledgeStatus(),
      fetchSecurityStatus(),
      fetchEventStatus()
    ]).then((results) => {
      const [
        confirmationsResult,
        customersResult,
        leadsResult,
        tasksResult,
        healthResult,
        modelResult,
        knowledgeResult,
        securityResult,
        eventResult
      ] =
        results;

      if (confirmationsResult.status === 'fulfilled') {
        setPendingConfirmations(confirmationsResult.value);
      }
      if (customersResult.status === 'fulfilled') {
        setCustomers(customersResult.value);
      }
      if (leadsResult.status === 'fulfilled') {
        setLeads(leadsResult.value);
      }
      if (tasksResult.status === 'fulfilled') {
        setTasks(tasksResult.value);
      }
      if (healthResult.status === 'fulfilled') {
        setHealth(healthResult.value);
      }
      if (modelResult.status === 'fulfilled') {
        setModelStatus(modelResult.value);
      }
      if (knowledgeResult.status === 'fulfilled') {
        setKnowledgeStatus(knowledgeResult.value);
      }
      if (securityResult.status === 'fulfilled') {
        setSecurityStatus(securityResult.value);
      }
      if (eventResult.status === 'fulfilled') {
        setEventStatus(eventResult.value);
      }

      const businessDataFailed = results.slice(1, 4).some((result) => result.status === 'rejected');
      setBusinessDataMode(businessDataFailed ? 'sample' : 'real');
      if (businessDataFailed) {
        setError('后端业务数据暂不可用，当前显示离线样例。');
      }
    });
  }, []);

  const metrics = useMemo(() => {
    const highLeads = leads.filter((item) => item.priority === 'HIGH');
    const riskCustomers = customers.filter((item) => item.riskLevel === 'HIGH');
    const dueTasks = tasks.filter((task) => isOpenTask(task) && isDueSoon(task.dueTime));
    const renewalCustomers = customers.filter((item) =>
      [item.lifecycleStage, item.tags].some((value) => String(value ?? '').includes('续费'))
    );
    const amount = highLeads.reduce((sum, item) => sum + Number(item.estimatedAmount || 0), 0);

    return {
      highLeads,
      riskCustomers,
      dueTasks,
      renewalCustomers,
      amount
    };
  }, [customers, leads, tasks]);

  const topLeads = useMemo(
    () => [...leads].sort((a, b) => Number(b.score || 0) - Number(a.score || 0)).slice(0, 4),
    [leads]
  );

  const leadTrend = useMemo(() => {
    const buckets = new Map<string, { date: string; amount: number; high: number; total: number }>();
    leads.forEach((lead) => {
      const date = String(lead.expectedCloseDate ?? '').slice(5, 10) || '未知';
      const item = buckets.get(date) ?? { date, amount: 0, high: 0, total: 0 };
      item.amount += Number(lead.estimatedAmount || 0);
      item.total += 1;
      if (lead.priority === 'HIGH') {
        item.high += 1;
      }
      buckets.set(date, item);
    });
    return [...buckets.values()].sort((a, b) => a.date.localeCompare(b.date)).slice(0, 7);
  }, [leads]);

  const riskHeatmap = useMemo(() => {
    const riskLevels = ['LOW', 'MEDIUM', 'HIGH'];
    const industries = [...new Set(customers.map((customer) => customer.industry || '其他'))].slice(0, 5);
    const max = Math.max(
      1,
      ...industries.flatMap((industry) =>
        riskLevels.map(
          (risk) => customers.filter((customer) => (customer.industry || '其他') === industry && customer.riskLevel === risk).length
        )
      )
    );
    return { industries, riskLevels, max };
  }, [customers]);

  const workItems = useMemo(
    () => [
      {
        title: '先处理高优商机',
        value: metrics.highLeads.length,
        desc: '主管关注成交窗口，销售优先跟进',
        icon: <ThunderboltOutlined />,
        to: '/leads',
        color: 'red'
      },
      {
        title: '查看风险客户',
        value: metrics.riskCustomers.length,
        desc: '价格异议、效果担忧、质检风险优先处理',
        icon: <ExclamationCircleOutlined />,
        to: '/customers',
        color: 'orange'
      },
      {
        title: '确认 CRM 写入',
        value: pendingConfirmations.length,
        desc: 'Agent 已生成但还未落库的写操作',
        icon: <SafetyCertificateOutlined />,
        to: '/agent',
        color: 'blue'
      },
      {
        title: '续费客户池',
        value: metrics.renewalCustomers.length,
        desc: '适合用数据复盘和套餐政策推进',
        icon: <UserSwitchOutlined />,
        to: '/customers',
        color: 'green'
      }
    ],
    [metrics, pendingConfirmations.length]
  );

  const confirmFromDashboard = async (confirmationId: number) => {
    setConfirmingId(confirmationId);
    try {
      await confirmAgentAction(confirmationId);
      antdMessage.success('已确认，CRM 写操作已执行');
      await refreshConfirmations();
    } catch {
      antdMessage.error('确认失败，请进入 Agent 工作台重试');
    } finally {
      setConfirmingId(null);
    }
  };

  const rejectFromDashboard = async (confirmationId: number) => {
    setConfirmingId(confirmationId);
    try {
      await rejectAgentAction(confirmationId);
      antdMessage.info('已拒绝，本次写操作未执行');
      await refreshConfirmations();
    } catch {
      antdMessage.error('拒绝失败，请进入 Agent 工作台重试');
    } finally {
      setConfirmingId(null);
    }
  };

  const vectorPercent = knowledgeStatus?.chunkCount
    ? Math.round((knowledgeStatus.vectorizedChunkCount / knowledgeStatus.chunkCount) * 100)
    : 0;

  useEffect(() => {
    if (!trendChartRef.current) {
      return;
    }
    const chart = echarts.init(trendChartRef.current);
    chart.setOption({
      animationDuration: 700,
      grid: { left: 34, right: 18, top: 34, bottom: 28 },
      tooltip: { trigger: 'axis' },
      legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: '#65758b' } },
      xAxis: {
        type: 'category',
        data: leadTrend.map((item) => item.date),
        axisLine: { lineStyle: { color: '#dbe4ef' } },
        axisTick: { show: false },
        axisLabel: { color: '#65758b' }
      },
      yAxis: [
        {
          type: 'value',
          axisLabel: { color: '#65758b' },
          splitLine: { lineStyle: { color: '#edf1f6' } }
        },
        {
          type: 'value',
          axisLabel: { color: '#65758b' },
          splitLine: { show: false }
        }
      ],
      series: [
        {
          name: '预计金额',
          type: 'bar',
          barWidth: 18,
          data: leadTrend.map((item) => item.amount),
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#2563eb' },
              { offset: 1, color: '#93c5fd' }
            ])
          }
        },
        {
          name: '高优商机',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          symbolSize: 7,
          data: leadTrend.map((item) => item.high),
          lineStyle: { width: 3, color: '#dc2626' },
          itemStyle: { color: '#dc2626' },
          areaStyle: { color: 'rgba(220, 38, 38, 0.08)' }
        }
      ]
    });
    const resize = () => chart.resize();
    window.addEventListener('resize', resize);
    return () => {
      window.removeEventListener('resize', resize);
      chart.dispose();
    };
  }, [leadTrend]);

  return (
    <Space direction="vertical" size={18} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <div className="business-hero">
        <div className="business-hero-main">
          <Text className="eyebrow">Sales Command Center</Text>
          <Title level={3}>今天先处理谁，下一步做什么</Title>
          <Paragraph className="overview-copy">
            首页只服务销售和销售主管：把高优商机、风险客户、待办跟进和 Agent 写入确认放在同一个业务入口里。
            技术审计、评测和系统状态已经收进系统管理区。
          </Paragraph>
          <div className="hero-kpi-strip">
            <div>
              <span>高优商机</span>
              <strong>{metrics.highLeads.length}</strong>
            </div>
            <div>
              <span>高优金额</span>
              <strong>{currency(metrics.amount)}</strong>
            </div>
            <div>
              <span>待确认</span>
              <strong>{pendingConfirmations.length}</strong>
            </div>
          </div>
          <Space wrap>
            <Link to="/leads">
              <Button type="primary" icon={<ThunderboltOutlined />}>查看今日优先级</Button>
            </Link>
            <Link to="/agent">
              <Button icon={<MessageOutlined />}>让 Agent 协助处理</Button>
            </Link>
            <Link to="/customers">
              <Button icon={<TeamOutlined />}>进入客户 360</Button>
            </Link>
          </Space>
        </div>
        <div className="business-hero-panel">
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Text strong>主管关注</Text>
            <Tag color={businessDataMode === 'real' ? 'green' : 'orange'}>
              {businessDataMode === 'real' ? '真实数据' : '离线样例'}
            </Tag>
          </Space>
          <div className="manager-focus">
            <div>
              <span>高优商机金额</span>
              <strong>{currency(metrics.amount)}</strong>
            </div>
            <div>
              <span>风险客户</span>
              <strong>{metrics.riskCustomers.length} 个</strong>
            </div>
            <div>
              <span>48 小时待办</span>
              <strong>{metrics.dueTasks.length} 个</strong>
            </div>
          </div>
        </div>
      </div>

      <Row gutter={[16, 16]}>
        {workItems.map((item) => (
          <Col xs={24} md={12} xl={6} key={item.title}>
            <Link to={item.to} className="work-metric-link">
              <Card className={`metric-card work-metric-card metric-${item.color}`}>
                <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Statistic title={item.title} value={item.value} />
                  <span className={`work-metric-icon ${item.color}`}>{item.icon}</span>
                </Space>
                <Text className="metric-label">{item.desc}</Text>
              </Card>
            </Link>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={15}>
          <Card
            className="command-card"
            title="今日优先队列"
            extra={
              <Link to="/leads">
                <Button size="small" icon={<ThunderboltOutlined />}>查看全部</Button>
              </Link>
            }
          >
            {topLeads.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无商机推荐" />
            ) : (
              <div className="priority-queue">
                {topLeads.map((lead, index) => (
                  <div className={`queue-card priority-${lead.priority.toLowerCase()}`} key={lead.leadId}>
                    <div className="queue-rank">{index + 1}</div>
                    <div className="queue-content">
                      <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                        <div>
                          <Text strong>{lead.customerName}</Text>
                          <div className="metric-label">
                            {lead.industry} · {currency(lead.estimatedAmount)} · {lead.expectedCloseDate}
                          </div>
                        </div>
                        <Tag color={priorityColor[lead.priority] ?? 'default'}>{lead.priority}</Tag>
                      </Space>
                      <Progress
                        percent={Math.round(Number(lead.score || 0))}
                        size="small"
                        strokeColor={lead.priority === 'HIGH' ? '#ef4444' : '#2563eb'}
                      />
                      <Space size={6} wrap>
                        {lead.reasons.slice(0, 3).map((reason) => (
                          <Tag key={reason}>{reason}</Tag>
                        ))}
                      </Space>
                      <div className="queue-actions">
                        <Link to={agentUrlForLead(lead)}>
                          <Button size="small" type="primary" icon={<MessageOutlined />}>让 Agent 分析</Button>
                        </Link>
                        <Link to={`/customers?customerId=${lead.customerId}`}>
                          <Button size="small" icon={<TeamOutlined />}>看客户</Button>
                        </Link>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} xl={9}>
          <Card
            className="command-card"
            title="待确认写入"
            extra={
              <Link to="/agent">
                <Button size="small" icon={<MessageOutlined />}>进入 Agent</Button>
              </Link>
            }
          >
            {pendingConfirmations.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待确认写操作" />
            ) : (
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                {pendingConfirmations.slice(0, 4).map((item) => (
                  <div className="task-card" key={item.id}>
                    <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                      <div>
                        <Text strong>{item.actionSummary}</Text>
                        <div className="metric-label">{item.actionType} · Run #{item.runId}</div>
                      </div>
                      <Tag color="orange">{item.status}</Tag>
                    </Space>
                    <Space style={{ marginTop: 10 }}>
                      <Button size="small" loading={confirmingId === item.id} onClick={() => rejectFromDashboard(item.id)}>
                        拒绝
                      </Button>
                      <Button
                        size="small"
                        type="primary"
                        loading={confirmingId === item.id}
                        onClick={() => confirmFromDashboard(item.id)}
                      >
                        确认写入
                      </Button>
                    </Space>
                  </div>
                ))}
              </Space>
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={15}>
          <Card className="command-card" title="商机趋势">
            <div ref={trendChartRef} className="chart-canvas" />
          </Card>
        </Col>
        <Col xs={24} xl={9}>
          <Card className="command-card" title="客户风险热力">
            <div className="risk-heatmap">
              <div className="risk-heatmap-head" />
              {riskHeatmap.riskLevels.map((risk) => (
                <div className="risk-heatmap-head" key={risk}>{risk}</div>
              ))}
              {riskHeatmap.industries.map((industry) => (
                <div className="risk-heatmap-row" key={industry}>
                  <div className="risk-heatmap-label">{industry}</div>
                  {riskHeatmap.riskLevels.map((risk) => {
                    const count = customers.filter((customer) => (customer.industry || '其他') === industry && customer.riskLevel === risk).length;
                    const intensity = count / riskHeatmap.max;
                    return (
                      <div
                        className={`risk-heatmap-cell risk-${risk.toLowerCase()}`}
                        style={{ opacity: 0.28 + intensity * 0.72 }}
                        key={`${industry}-${risk}`}
                      >
                        {count}
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          </Card>
        </Col>
      </Row>

      <Card className="command-card" title="业务闭环">
        <div className="business-flow">
          <Link to="/customers" className="flow-node">
            <TeamOutlined />
            <Text strong>客户</Text>
            <span>看画像、标签、时间线</span>
          </Link>
          <Link to="/leads" className="flow-node">
            <ThunderboltOutlined />
            <Text strong>商机</Text>
            <span>按优先级排序</span>
          </Link>
          <Link to="/agent" className="flow-node">
            <MessageOutlined />
            <Text strong>Agent</Text>
            <span>生成分析和建议动作</span>
          </Link>
          <Link to="/agent" className="flow-node">
            <SafetyCertificateOutlined />
            <Text strong>确认</Text>
            <span>写 CRM 前人工确认</span>
          </Link>
          <Link to="/runs" className="flow-node muted">
            <AuditOutlined />
            <Text strong>审计</Text>
            <span>系统管理区追踪</span>
          </Link>
        </div>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={15}>
          <Card className="command-card" title="角色边界">
            <div className="role-grid">
              <div className="role-card">
                <TeamOutlined />
                <Text strong>销售</Text>
                <span>处理客户、商机、知识问答、通话摘要和写入确认。</span>
              </div>
              <div className="role-card">
                <UserSwitchOutlined />
                <Text strong>销售主管</Text>
                <span>看高优商机、风险客户、跟进压力和团队质量。</span>
              </div>
              <div className="role-card muted">
                <SettingOutlined />
                <Text strong>系统管理员</Text>
                <span>负责运行审计、质量评估、模型、向量和事件状态。</span>
              </div>
            </div>
          </Card>
        </Col>
        <Col xs={24} xl={9}>
          <Card
            className="admin-summary-card"
            title="系统管理摘要"
            extra={
              <Space>
                <Link to="/runs">审计</Link>
                <Link to="/evaluation">评估</Link>
              </Space>
            }
          >
            <Descriptions column={1} size="small">
              <Descriptions.Item label="服务">{statusTag(health?.status ?? 'LOCAL')}</Descriptions.Item>
              <Descriptions.Item label="模型">
                {statusTag(modelStatus?.configured ? modelStatus.model : 'mock')}
              </Descriptions.Item>
              <Descriptions.Item label="向量库">
                <Space>
                  {statusTag(knowledgeStatus?.vectorStoreMode ?? 'java-fallback')}
                  <Text type="secondary">{vectorPercent}%</Text>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="权限">
                <Space>
                  {securityStatus?.strict ? <Tag color="green">strict</Tag> : <Tag color="orange">permissive</Tag>}
                  {securityStatus?.rbacEnabled ? <Tag color="blue">RBAC</Tag> : null}
                  <Text type="secondary">users={securityStatus?.rbacUserCount ?? 0}</Text>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="事件">
                <Space>
                  {statusTag(eventStatus?.mode ?? 'log-only')}
                  <Text type="secondary">pending={eventStatus?.outboxPending ?? 0}</Text>
                  <Text type="secondary">dispatching={eventStatus?.outboxDispatching ?? 0}</Text>
                  <Text type={(eventStatus?.outboxDeadLetters ?? 0) > 0 ? 'danger' : 'secondary'}>
                    dead={eventStatus?.outboxDeadLetters ?? 0}
                  </Text>
                </Space>
              </Descriptions.Item>
            </Descriptions>
            <div className="admin-note">
              <ClockCircleOutlined />
              <span>技术能力不再打扰销售首页，统一收在系统管理区排查。</span>
            </div>
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
