import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CustomerServiceOutlined,
  MessageOutlined,
  RightOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import { Alert, Button, Card, Col, Empty, List, Row, Space, Spin, Tag, Typography, message as antMessage } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import {
  confirmAgentAction,
  describeApiError,
  fetchAgentConfirmations,
  fetchDashboardMetrics,
  fetchLeadRecommendations,
  rejectAgentAction,
  type AgentConfirmation,
  type DashboardMetrics,
  type LeadRecommendation
} from '../../api/client';

const { Paragraph, Text, Title } = Typography;

function agentPrompt(lead: LeadRecommendation) {
  return `请帮我分析${lead.customerName}这个客户，重点解释为什么现在要跟进，并给出电话沟通话术和下一步任务建议。`;
}

export default function MobileSales() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [leads, setLeads] = useState<LeadRecommendation[]>([]);
  const [confirmations, setConfirmations] = useState<AgentConfirmation[]>([]);
  const [error, setError] = useState<string | null>(null);

  const topLead = leads[0];
  const pendingConfirmations = useMemo(() => confirmations.slice(0, 4), [confirmations]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [metricsData, leadData, confirmationData] = await Promise.all([
        fetchDashboardMetrics(),
        fetchLeadRecommendations(5),
        fetchAgentConfirmations('PENDING')
      ]);
      setMetrics(metricsData);
      setLeads(leadData);
      setConfirmations(confirmationData);
    } catch (err) {
      setError(describeApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleConfirmation = async (confirmationId: number, action: 'confirm' | 'reject') => {
    setActionLoading(confirmationId);
    try {
      if (action === 'confirm') {
        await confirmAgentAction(confirmationId);
        antMessage.success('已确认写入 CRM');
      } else {
        await rejectAgentAction(confirmationId);
        antMessage.info('已拒绝本次写入');
      }
      await load();
    } catch (err) {
      antMessage.error(describeApiError(err));
    } finally {
      setActionLoading(null);
    }
  };

  if (loading) {
    return (
      <div className="mobile-sales-loading">
        <Spin size="large" />
        <Text type="secondary">正在整理移动作业清单...</Text>
      </div>
    );
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error ? <Alert type="error" showIcon message="移动作业加载失败" description={error} /> : null}

      <section className="mobile-work-hero">
        <Space direction="vertical" size={10}>
          <Text className="eyebrow">Mobile Sales Flow</Text>
          <Title level={3}>今天先处理最影响成交的事</Title>
          <Paragraph>
            手机端只保留销售在路上最常用的动作：看待确认、找高优商机、让 AI 准备话术，然后回到 CRM 留痕。
          </Paragraph>
        </Space>
        <div className="mobile-kpi-grid">
          <div>
            <span>待确认</span>
            <strong>{metrics?.summary.pendingConfirmationCount ?? confirmations.length}</strong>
          </div>
          <div>
            <span>高优商机</span>
            <strong>{metrics?.summary.highLeadCount ?? leads.length}</strong>
          </div>
          <div>
            <span>风险客户</span>
            <strong>{metrics?.summary.riskCustomerCount ?? 0}</strong>
          </div>
        </div>
      </section>

      <Card className="mobile-primary-card" title="下一位建议跟进">
        {topLead ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
              <div>
                <Title level={4} style={{ margin: 0 }}>{topLead.customerName}</Title>
                <Text type="secondary">{topLead.industry} · 预计 {topLead.estimatedAmount.toLocaleString()} 元</Text>
              </div>
              <Tag color={topLead.priority === 'HIGH' ? 'red' : topLead.priority === 'MEDIUM' ? 'orange' : 'green'}>
                {topLead.priority}
              </Tag>
            </Space>
            <div className="mobile-score-strip">
              <ThunderboltOutlined />
              <div>
                <strong>优先级评分 {Math.round(topLead.score)}</strong>
                <span>{topLead.suggestedAction}</span>
              </div>
            </div>
            <List
              size="small"
              dataSource={topLead.reasons.slice(0, 3)}
              renderItem={(item) => (
                <List.Item>
                  <Text>{item}</Text>
                </List.Item>
              )}
            />
            <Row gutter={[8, 8]}>
              <Col span={12}>
                <Button block onClick={() => navigate(`/customers?customerId=${topLead.customerId}`)}>
                  客户详情
                </Button>
              </Col>
              <Col span={12}>
                <Button
                  block
                  type="primary"
                  icon={<MessageOutlined />}
                  onClick={() => navigate(`/agent?customerId=${topLead.customerId}&prompt=${encodeURIComponent(agentPrompt(topLead))}&source=mobile`)}
                >
                  让 AI 准备
                </Button>
              </Col>
            </Row>
          </Space>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无推荐商机" />
        )}
      </Card>

      <Card title="待你背书的 CRM 写入" extra={<Link to="/agent">进入 AI 助手</Link>}>
        {pendingConfirmations.length ? (
          <List
            itemLayout="vertical"
            dataSource={pendingConfirmations}
            renderItem={(item) => (
              <List.Item className="mobile-confirmation-item">
                <Space direction="vertical" size={10} style={{ width: '100%' }}>
                  <Space size={8} wrap>
                    <Tag color="orange" icon={<ClockCircleOutlined />}>待确认</Tag>
                    <Text strong>{item.actionType}</Text>
                  </Space>
                  <Paragraph style={{ marginBottom: 0 }}>{item.actionSummary}</Paragraph>
                  <Row gutter={8}>
                    <Col span={12}>
                      <Button
                        block
                        loading={actionLoading === item.id}
                        onClick={() => handleConfirmation(item.id, 'reject')}
                      >
                        拒绝
                      </Button>
                    </Col>
                    <Col span={12}>
                      <Button
                        block
                        type="primary"
                        icon={<CheckCircleOutlined />}
                        loading={actionLoading === item.id}
                        onClick={() => handleConfirmation(item.id, 'confirm')}
                      >
                        确认写入
                      </Button>
                    </Col>
                  </Row>
                </Space>
              </List.Item>
            )}
          />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有待确认写入" />
        )}
      </Card>

      <Card title="路上常用入口">
        <div className="mobile-shortcut-grid">
          <Link to="/leads">
            <ThunderboltOutlined />
            <strong>商机优先级</strong>
            <RightOutlined />
          </Link>
          <Link to="/callcenter">
            <CustomerServiceOutlined />
            <strong>通话后留痕</strong>
            <RightOutlined />
          </Link>
          <Link to="/agent">
            <SafetyCertificateOutlined />
            <strong>AI 建议确认</strong>
            <RightOutlined />
          </Link>
        </div>
      </Card>
    </Space>
  );
}
