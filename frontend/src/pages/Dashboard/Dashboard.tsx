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
import type { EChartsType } from 'echarts/core';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AgentConfirmation,
  confirmAgentAction,
  DashboardMetrics,
  EventStatus,
  fetchAgentConfirmationPage,
  fetchDashboardMetrics,
  fetchEventStatus,
  fetchHealth,
  fetchKnowledgeStatus,
  fetchLeadRecommendations,
  fetchModelStatus,
  fetchSecurityStatus,
  HealthView,
  KnowledgeStatus,
  LeadRecommendation,
  ModelStatus,
  rejectAgentAction,
  SecurityStatus,
  describeApiError
} from '../../api/client';
import ApiErrorNotice from '../../components/ApiErrorNotice';

const { Paragraph, Text, Title } = Typography;

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

function trendDateLabel(value?: string) {
  if (!value || value === 'unknown') {
    return '未知';
  }
  return /^\d{4}-\d{2}-\d{2}/.test(value) ? value.slice(5, 10) : value;
}

function statusTag(value?: string) {
  const normalized = value ?? 'unknown';
  const color =
    normalized.includes('pgvector') || normalized === 'UP' || normalized === 'ready'
      ? 'green'
      : normalized.includes('local') || normalized.includes('mock')
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
  const [leads, setLeads] = useState<LeadRecommendation[]>([]);
  const [health, setHealth] = useState<HealthView | null>(null);
  const [modelStatus, setModelStatus] = useState<ModelStatus | null>(null);
  const [knowledgeStatus, setKnowledgeStatus] = useState<KnowledgeStatus | null>(null);
  const [securityStatus, setSecurityStatus] = useState<SecurityStatus | null>(null);
  const [eventStatus, setEventStatus] = useState<EventStatus | null>(null);
  const [dashboardMetrics, setDashboardMetrics] = useState<DashboardMetrics | null>(null);
  const [pendingConfirmations, setPendingConfirmations] = useState<AgentConfirmation[]>([]);
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [businessDataMode, setBusinessDataMode] = useState<'connected' | 'unavailable'>('unavailable');
  const [error, setError] = useState('');

  const refreshConfirmations = async () => {
    try {
      const data = await fetchAgentConfirmationPage({ status: 'PENDING', page: 1, pageSize: 4 });
      setPendingConfirmations(data.items);
    } catch {
      setPendingConfirmations([]);
    }
  };

  useEffect(() => {
    Promise.allSettled([
      fetchAgentConfirmationPage({ status: 'PENDING', page: 1, pageSize: 4 }),
      fetchLeadRecommendations(20),
      fetchHealth(),
      fetchModelStatus(),
      fetchKnowledgeStatus(),
      fetchSecurityStatus(),
      fetchEventStatus(),
      fetchDashboardMetrics()
    ]).then((results) => {
      const [
        confirmationsResult,
        leadsResult,
        healthResult,
        modelResult,
        knowledgeResult,
        securityResult,
        eventResult,
        dashboardMetricsResult
      ] =
        results;

      if (confirmationsResult.status === 'fulfilled') {
        setPendingConfirmations(confirmationsResult.value.items);
      }
      if (leadsResult.status === 'fulfilled') {
        setLeads(leadsResult.value);
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
      if (dashboardMetricsResult.status === 'fulfilled') {
        setDashboardMetrics(dashboardMetricsResult.value);
      }

      const businessDataFailed = leadsResult.status === 'rejected' || dashboardMetricsResult.status === 'rejected';
      setBusinessDataMode(businessDataFailed ? 'unavailable' : 'connected');
      if (businessDataFailed) {
        setError('后端业务指标暂不可用，今日工作台未加载完整商机、趋势和风险统计。');
      }
    });
  }, []);

  const localMetrics = useMemo(() => {
    const highLeads = leads.filter((item) => item.priority === 'HIGH');
    const amount = highLeads.reduce((sum, item) => sum + Number(item.estimatedAmount || 0), 0);

    return {
      highLeads,
      amount
    };
  }, [leads]);

  const displayMetrics = useMemo(() => {
    const summary = dashboardMetrics?.summary;
    return {
      highLeadCount: summary?.highLeadCount ?? localMetrics.highLeads.length,
      highLeadAmount: Number(summary?.highLeadAmount ?? localMetrics.amount),
      riskCustomerCount: summary?.riskCustomerCount ?? 0,
      dueTaskCount: summary?.dueTaskCount ?? 0,
      renewalCustomerCount: summary?.renewalCustomerCount ?? 0,
      pendingConfirmationCount: summary?.pendingConfirmationCount ?? pendingConfirmations.length
    };
  }, [dashboardMetrics, localMetrics, pendingConfirmations.length]);

  const topLeads = useMemo(
    () => [...leads].sort((a, b) => Number(b.score || 0) - Number(a.score || 0)).slice(0, 4),
    [leads]
  );

  const localLeadTrend = useMemo(() => {
    const buckets = new Map<string, { date: string; amount: number; high: number; total: number }>();
    leads.forEach((lead) => {
      const date = String(lead.expectedCloseDate ?? '') || 'unknown';
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

  const leadTrend = useMemo(
    () => (dashboardMetrics?.leadTrend?.length ? dashboardMetrics.leadTrend : localLeadTrend),
    [dashboardMetrics, localLeadTrend]
  );

  const riskHeatmap = useMemo(() => {
    return dashboardMetrics?.riskHeatmap ?? { industries: [], riskLevels: ['LOW', 'MEDIUM', 'HIGH'], max: 1, cells: [] };
  }, [dashboardMetrics]);

  const workItems = useMemo(
    () => [
      {
        title: '先处理高优商机',
        value: displayMetrics.highLeadCount,
        desc: '主管关注成交窗口，销售优先跟进',
        icon: <ThunderboltOutlined />,
        to: '/leads',
        color: 'red'
      },
      {
        title: '查看风险客户',
        value: displayMetrics.riskCustomerCount,
        desc: '价格异议、效果担忧、质检风险优先处理',
        icon: <ExclamationCircleOutlined />,
        to: '/customers',
        color: 'orange'
      },
      {
        title: '确认 CRM 写入',
        value: displayMetrics.pendingConfirmationCount,
        desc: 'AI 已生成但还未确认落库的动作',
        icon: <SafetyCertificateOutlined />,
        to: '/agent',
        color: 'blue'
      },
      {
        title: '续费客户池',
        value: displayMetrics.renewalCustomerCount,
        desc: '适合用数据复盘和套餐政策推进',
        icon: <UserSwitchOutlined />,
        to: '/customers',
        color: 'green'
      }
    ],
    [displayMetrics]
  );

  const confirmFromDashboard = async (confirmationId: number) => {
    setConfirmingId(confirmationId);
    try {
      await confirmAgentAction(confirmationId);
      antdMessage.success('已确认，CRM 写操作已执行');
      await refreshConfirmations();
    } catch (err) {
      antdMessage.error(describeApiError(err));
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
    } catch (err) {
      antdMessage.error(describeApiError(err));
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
    let chart: EChartsType | null = null;
    let disposed = false;

    async function renderTrendChart() {
      const [charts, components, echartsCore, renderers] = await Promise.all([
        import('echarts/charts'),
        import('echarts/components'),
        import('echarts/core'),
        import('echarts/renderers')
      ]);
      echartsCore.use([
        charts.BarChart,
        charts.LineChart,
        components.GridComponent,
        components.TooltipComponent,
        components.LegendComponent,
        renderers.CanvasRenderer
      ]);
      if (!trendChartRef.current || disposed) {
        return;
      }
      chart = echartsCore.init(trendChartRef.current);
      chart.setOption({
        animationDuration: 700,
        grid: { left: 34, right: 18, top: 34, bottom: 28 },
        tooltip: { trigger: 'axis' },
        legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: '#65758b' } },
        xAxis: {
          type: 'category',
          data: leadTrend.map((item) => trendDateLabel(item.date)),
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
              color: new echartsCore.graphic.LinearGradient(0, 0, 0, 1, [
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
    }

    renderTrendChart();
    const resize = () => chart?.resize();
    window.addEventListener('resize', resize);
    return () => {
      disposed = true;
      window.removeEventListener('resize', resize);
      chart?.dispose();
    };
  }, [leadTrend]);

  return (
    <Space direction="vertical" size={18} style={{ width: '100%' }}>
      {error && <ApiErrorNotice error={error} title="今日工作台数据未完整加载" />}

      <div className="business-hero">
        <div className="business-hero-main">
          <Text className="eyebrow">Sales Command Center</Text>
          <Title level={3}>今天先处理谁，下一步做什么</Title>
          <Paragraph className="overview-copy">
            首页只服务销售和销售主管：把高优商机、风险客户、待办跟进和 AI 写入确认放在同一个业务入口里。
            技术审计、评测和系统状态已经收进系统管理区。
          </Paragraph>
          <div className="hero-kpi-strip">
            <div>
              <span>高优商机</span>
              <strong>{displayMetrics.highLeadCount}</strong>
            </div>
            <div>
              <span>高优金额</span>
              <strong>{currency(displayMetrics.highLeadAmount)}</strong>
            </div>
            <div>
              <span>待确认</span>
              <strong>{displayMetrics.pendingConfirmationCount}</strong>
            </div>
          </div>
          <Space wrap>
            <Link to="/leads">
              <Button type="primary" icon={<ThunderboltOutlined />}>查看今日优先级</Button>
            </Link>
            <Link to="/agent">
              <Button icon={<MessageOutlined />}>让 AI 协助处理</Button>
            </Link>
            <Link to="/customers">
              <Button icon={<TeamOutlined />}>进入客户 360</Button>
            </Link>
          </Space>
        </div>
        <div className="business-hero-panel">
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Text strong>主管关注</Text>
            <Tag color={businessDataMode === 'connected' ? 'green' : 'orange'}>
              {businessDataMode === 'connected' ? '业务数据已连接' : '业务数据未连接'}
            </Tag>
          </Space>
          <div className="manager-focus">
            <div>
              <span>高优商机金额</span>
              <strong>{currency(displayMetrics.highLeadAmount)}</strong>
            </div>
            <div>
              <span>风险客户</span>
              <strong>{displayMetrics.riskCustomerCount} 个</strong>
            </div>
            <div>
              <span>48 小时待办</span>
              <strong>{displayMetrics.dueTaskCount} 个</strong>
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
                          <Button size="small" type="primary" icon={<MessageOutlined />}>让 AI 分析</Button>
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
                <Button size="small" icon={<MessageOutlined />}>进入 AI 助手</Button>
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
                        <div className="metric-label">{item.actionType} · 处理流水 #{item.runId}</div>
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
            {riskHeatmap.industries.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无风险统计" />
            ) : (
              <div className="risk-heatmap">
                <div className="risk-heatmap-head" />
                {riskHeatmap.riskLevels.map((risk) => (
                  <div className="risk-heatmap-head" key={risk}>{risk}</div>
                ))}
                {riskHeatmap.industries.map((industry) => (
                  <div className="risk-heatmap-row" key={industry}>
                    <div className="risk-heatmap-label">{industry}</div>
                    {riskHeatmap.riskLevels.map((risk) => {
                      const heatmapCount = riskHeatmap.cells.find(
                        (cell) => cell.industry === industry && cell.riskLevel === risk
                      )?.count ?? 0;
                      const intensity = heatmapCount / riskHeatmap.max;
                      return (
                        <div
                          className={`risk-heatmap-cell risk-${risk.toLowerCase()}`}
                          style={{ opacity: 0.28 + intensity * 0.72 }}
                          key={`${industry}-${risk}`}
                        >
                          {heatmapCount}
                        </div>
                      );
                    })}
                  </div>
                ))}
              </div>
            )}
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
            <Text strong>AI 助手</Text>
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
            title="作业保障"
          >
            <Descriptions column={1} size="small">
              <Descriptions.Item label="服务状态">{statusTag(health?.status ?? 'LOCAL')}</Descriptions.Item>
              <Descriptions.Item label="AI 助手">
                {modelStatus?.configured ? <Tag color="green">可用</Tag> : <Tag color="orange">规则模式</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="知识覆盖">
                <Space>
                  <Tag color={vectorPercent >= 80 ? 'green' : 'orange'}>{vectorPercent}%</Tag>
                  <Text type="secondary">可检索</Text>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="数据边界">
                <Space>
                  <Tag color={securityStatus?.rbacEnabled ? 'green' : 'orange'}>
                    {securityStatus?.rbacEnabled ? '按身份过滤' : '本地范围'}
                  </Tag>
                  <Text type="secondary">{securityStatus?.rbacUserCount ?? 0} 个工作身份</Text>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="后台通知">
                <Space>
                  <Tag color={(eventStatus?.outboxDeadLetters ?? 0) > 0 ? 'red' : 'green'}>
                    {(eventStatus?.outboxDeadLetters ?? 0) > 0 ? '需处理' : '正常'}
                  </Tag>
                  <Text type="secondary">待分发 {eventStatus?.outboxPending ?? 0}</Text>
                </Space>
              </Descriptions.Item>
            </Descriptions>
            <div className="admin-note">
              <ClockCircleOutlined />
              <span>更细的模型、向量、事件和权限状态统一放在系统管理区排查。</span>
            </div>
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
