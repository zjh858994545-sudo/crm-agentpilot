import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CopyOutlined,
  CustomerServiceOutlined,
  DeleteOutlined,
  EditOutlined,
  MessageOutlined,
  PhoneOutlined,
  RightOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import { Alert, Button, Card, Col, Empty, Input, List, Modal, Row, Space, Spin, Tag, Typography, message as antMessage } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import {
  confirmAgentAction,
  describeApiError,
  fetchAgentConfirmations,
  fetchCustomerDetail,
  fetchDashboardMetrics,
  fetchLeadRecommendations,
  rejectAgentAction,
  type AgentConfirmation,
  type Customer,
  type DashboardMetrics,
  type LeadRecommendation
} from '../../api/client';

const { Paragraph, Text, Title } = Typography;
const DRAFT_STORAGE_KEY = 'agentpilot.mobileOfflineDrafts';

type OfflineDraft = {
  id: string;
  customerId?: number;
  customerName?: string;
  content: string;
  createdAt: string;
};

function agentPrompt(lead: LeadRecommendation) {
  return `请帮我分析 ${lead.customerName} 这个客户，重点解释为什么现在要跟进，并给出电话沟通话术和下一步任务建议。`;
}

function priorityTag(priority?: string) {
  const color = priority === 'HIGH' ? 'red' : priority === 'MEDIUM' ? 'orange' : 'green';
  const label = priority === 'HIGH' ? '高优先级' : priority === 'MEDIUM' ? '中优先级' : '可跟进';
  return <Tag color={color}>{label}</Tag>;
}

export default function MobileSales() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [leads, setLeads] = useState<LeadRecommendation[]>([]);
  const [topCustomer, setTopCustomer] = useState<Customer | null>(null);
  const [confirmations, setConfirmations] = useState<AgentConfirmation[]>([]);
  const [drafts, setDrafts] = useState<OfflineDraft[]>([]);
  const [draftModalOpen, setDraftModalOpen] = useState(false);
  const [draftText, setDraftText] = useState('');
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
    setDrafts(readDrafts());
  }, []);

  useEffect(() => {
    if (!topLead?.customerId) {
      setTopCustomer(null);
      return;
    }
    fetchCustomerDetail(topLead.customerId)
      .then(setTopCustomer)
      .catch(() => setTopCustomer(null));
  }, [topLead?.customerId]);

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

  const handleBatchConfirmation = async (action: 'confirm' | 'reject') => {
    if (!pendingConfirmations.length) {
      return;
    }
    setLoading(true);
    try {
      for (const item of pendingConfirmations) {
        if (action === 'confirm') {
          await confirmAgentAction(item.id);
        } else {
          await rejectAgentAction(item.id);
        }
      }
      antMessage.success(action === 'confirm' ? '本页待确认已全部写入' : '本页待确认已全部拒绝');
      await load();
    } catch (err) {
      antMessage.error(describeApiError(err));
    } finally {
      setLoading(false);
    }
  };

  const copyMobile = async () => {
    if (!topCustomer?.contactMobile) {
      antMessage.info('当前客户没有可复制的联系电话');
      return;
    }
    await navigator.clipboard.writeText(topCustomer.contactMobile);
    antMessage.success('联系电话已复制');
  };

  const saveDraft = () => {
    if (!draftText.trim()) {
      antMessage.warning('请先输入拜访记录');
      return;
    }
    const nextDraft: OfflineDraft = {
      id: `${Date.now()}`,
      customerId: topLead?.customerId,
      customerName: topLead?.customerName,
      content: draftText.trim(),
      createdAt: new Date().toISOString()
    };
    const nextDrafts = [nextDraft, ...drafts].slice(0, 20);
    writeDrafts(nextDrafts);
    setDrafts(nextDrafts);
    setDraftText('');
    setDraftModalOpen(false);
    antMessage.success('离线草稿已保存，稍后可同步到 CRM 留痕');
  };

  const removeDraft = (id: string) => {
    const nextDrafts = drafts.filter((draft) => draft.id !== id);
    writeDrafts(nextDrafts);
    setDrafts(nextDrafts);
  };

  const copyDraft = async (draft: OfflineDraft) => {
    await navigator.clipboard.writeText(draft.content);
    antMessage.success('草稿内容已复制，可粘贴到通话后留痕');
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
              {priorityTag(topLead.priority)}
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
              <Col span={12}>
                <Button block icon={<CopyOutlined />} onClick={copyMobile}>
                  复制电话
                </Button>
              </Col>
              <Col span={12}>
                <Button
                  block
                  icon={<PhoneOutlined />}
                  disabled={!topCustomer?.contactMobile || topCustomer.contactMobile.includes('*')}
                  href={topCustomer?.contactMobile && !topCustomer.contactMobile.includes('*') ? `tel:${topCustomer.contactMobile}` : undefined}
                >
                  一键拨打
                </Button>
              </Col>
            </Row>
          </Space>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无推荐商机" />
        )}
      </Card>

      <Card
        title="待你确认的 CRM 写入"
        extra={
          <Space>
            {pendingConfirmations.length ? (
              <>
                <Button size="small" onClick={() => handleBatchConfirmation('reject')} loading={loading}>
                  批量拒绝
                </Button>
                <Button size="small" type="primary" onClick={() => handleBatchConfirmation('confirm')} loading={loading}>
                  批量确认
                </Button>
              </>
            ) : null}
            <Link to="/agent">进入 AI 助手</Link>
          </Space>
        }
      >
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

      <Card
        title="拜访后离线草稿"
        extra={
          <Button size="small" type="primary" icon={<EditOutlined />} onClick={() => setDraftModalOpen(true)}>
            快速记录
          </Button>
        }
      >
        {drafts.length ? (
          <List
            size="small"
            dataSource={drafts.slice(0, 4)}
            renderItem={(draft) => (
              <List.Item
                actions={[
                  <Button key="copy" type="link" size="small" onClick={() => copyDraft(draft)}>
                    复制
                  </Button>,
                  <Button key="sync" type="link" size="small" onClick={() => navigate(`/callcenter?draft=${encodeURIComponent(draft.content)}`)}>
                    去留痕
                  </Button>,
                  <Button key="delete" type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => removeDraft(draft.id)} />
                ]}
              >
                <List.Item.Meta
                  title={draft.customerName || '未关联客户'}
                  description={
                    <Space direction="vertical" size={2}>
                      <Text>{draft.content}</Text>
                      <Text type="secondary">{draft.createdAt.replace('T', ' ').slice(0, 16)}</Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="断网或刚拜访完时，可先记草稿，稍后再同步" />
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
          <Link to={topLead ? `/agent?customerId=${topLead.customerId}&prompt=${encodeURIComponent(agentPrompt(topLead))}&source=mobile-call` : '/agent'}>
            <PhoneOutlined />
            <strong>拨打前准备</strong>
            <RightOutlined />
          </Link>
        </div>
      </Card>

      <Modal
        title="快速记录拜访草稿"
        open={draftModalOpen}
        okText="保存草稿"
        cancelText="取消"
        onOk={saveDraft}
        onCancel={() => setDraftModalOpen(false)}
      >
        <Space direction="vertical" size={10} style={{ width: '100%' }}>
          <Alert type="info" showIcon message="草稿仅保存在当前浏览器本地，不会直接写入 CRM。" />
          <Input.TextArea
            rows={5}
            value={draftText}
            onChange={(event) => setDraftText(event.target.value)}
            placeholder="例如：客户认可续费方案，但希望明天下午确认预算；重点关注曝光效果和优惠截止时间。"
          />
        </Space>
      </Modal>
    </Space>
  );
}

function readDrafts(): OfflineDraft[] {
  try {
    return JSON.parse(window.localStorage.getItem(DRAFT_STORAGE_KEY) || '[]') as OfflineDraft[];
  } catch {
    return [];
  }
}

function writeDrafts(drafts: OfflineDraft[]) {
  window.localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(drafts));
}
