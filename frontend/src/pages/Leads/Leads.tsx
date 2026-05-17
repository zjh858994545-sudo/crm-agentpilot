import {
  DollarOutlined,
  FilterOutlined,
  FireOutlined,
  MessageOutlined,
  ProfileOutlined,
  RiseOutlined,
  TeamOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Progress,
  Row,
  Segmented,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  describeApiError,
  fetchLeadDetail,
  fetchLeadRecommendations,
  Lead,
  LeadRecommendation
} from '../../api/client';
import ApiErrorNotice from '../../components/ApiErrorNotice';

const { Paragraph, Text, Title } = Typography;

const priorityColor: Record<string, string> = {
  HIGH: 'red',
  MEDIUM: 'orange',
  LOW: 'default'
};

const stageColor: Record<string, string> = {
  NEGOTIATING: 'blue',
  QUALIFIED: 'green',
  OBJECTION: 'orange',
  FOLLOWING: 'cyan',
  WON: 'green',
  LOST: 'red'
};

function currency(value?: number) {
  if (value == null) {
    return '-';
  }
  return `¥${Number(value).toLocaleString('zh-CN')}`;
}

function daysUntil(value?: string) {
  if (!value) {
    return '-';
  }
  const days = Math.ceil((new Date(value).getTime() - Date.now()) / 86400000);
  return Number.isFinite(days) ? `${days} 天` : '-';
}

function agentUrlForLead(lead: LeadRecommendation) {
  const reasons = lead.reasons.join('、');
  const prompt = `请基于商机 ${lead.leadId} 分析${lead.customerName}，推荐理由是：${reasons}。请给出跟进策略，并判断是否需要创建跟进任务。`;
  return `/agent?prompt=${encodeURIComponent(prompt)}&leadId=${lead.leadId}&customerId=${lead.customerId}&source=lead`;
}

export default function Leads() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [recommendations, setRecommendations] = useState<LeadRecommendation[]>([]);
  const [priorityFilter, setPriorityFilter] = useState<string>('ALL');
  const [industryFilter, setIndustryFilter] = useState<string>('ALL');
  const [selected, setSelected] = useState<LeadRecommendation | null>(null);
  const [selectedLead, setSelectedLead] = useState<Lead | null>(null);
  const [loading, setLoading] = useState(false);
  const [dataMode, setDataMode] = useState<'connected' | 'unavailable'>('unavailable');
  const [error, setError] = useState('');
  const urlLeadId = Number(searchParams.get('leadId'));
  const urlCustomerId = Number(searchParams.get('customerId'));
  const urlSalesRepId = Number(searchParams.get('salesRepId'));
  const resolvedUrlLeadId = Number.isFinite(urlLeadId) && urlLeadId > 0 ? urlLeadId : undefined;
  const resolvedUrlCustomerId = Number.isFinite(urlCustomerId) && urlCustomerId > 0 ? urlCustomerId : undefined;
  const resolvedUrlSalesRepId = Number.isFinite(urlSalesRepId) && urlSalesRepId > 0 ? urlSalesRepId : undefined;

  useEffect(() => {
    setLoading(true);
    fetchLeadRecommendations(20, resolvedUrlSalesRepId)
      .then((nextRecommendations) => {
        setRecommendations(nextRecommendations);
        setDataMode('connected');
      })
      .catch((err) => {
        setRecommendations([]);
        setSelectedLead(null);
        setDataMode('unavailable');
        setError(describeApiError(err));
      })
      .finally(() => setLoading(false));
  }, [resolvedUrlSalesRepId]);

  const industries = useMemo(
    () => Array.from(new Set(recommendations.map((item) => item.industry))).filter(Boolean),
    [recommendations]
  );

  const filtered = useMemo(
    () =>
      recommendations.filter((item) => {
        const priorityMatched = priorityFilter === 'ALL' || item.priority === priorityFilter;
        const industryMatched = industryFilter === 'ALL' || item.industry === industryFilter;
        return priorityMatched && industryMatched;
      }),
    [recommendations, priorityFilter, industryFilter]
  );

  const metrics = useMemo(() => {
    const totalAmount = filtered.reduce((sum, item) => sum + Number(item.estimatedAmount || 0), 0);
    const high = filtered.filter((item) => item.priority === 'HIGH').length;
    const avgScore = filtered.length
      ? filtered.reduce((sum, item) => sum + Number(item.score || 0), 0) / filtered.length
      : 0;
    return { total: filtered.length, totalAmount, high, avgScore };
  }, [filtered]);

  const openLead = async (lead: LeadRecommendation, syncUrl = true) => {
    if (syncUrl) {
      const next = new URLSearchParams(searchParams);
      next.set('leadId', String(lead.leadId));
      next.set('customerId', String(lead.customerId));
      setSearchParams(next, { replace: true });
    }
    setSelected(lead);
    setSelectedLead(null);
    try {
      setSelectedLead(await fetchLeadDetail(lead.leadId));
    } catch (err) {
      setSelectedLead(null);
      setError(describeApiError(err));
    }
  };

  useEffect(() => {
    if (selected?.leadId === resolvedUrlLeadId || (!resolvedUrlLeadId && selected?.customerId === resolvedUrlCustomerId)) {
      return;
    }
    const target = recommendations.find((item) =>
      resolvedUrlLeadId ? item.leadId === resolvedUrlLeadId : item.customerId === resolvedUrlCustomerId
    );
    if (target) {
      openLead(target, false);
    }
  }, [recommendations, resolvedUrlLeadId, resolvedUrlCustomerId, selected?.leadId, selected?.customerId]);

  const closeLead = () => {
    const next = new URLSearchParams(searchParams);
    next.delete('leadId');
    next.delete('customerId');
    setSearchParams(next, { replace: true });
    setSelected(null);
    setSelectedLead(null);
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <ApiErrorNotice error={error} title="商机优先级暂时无法加载" />}

      <div className="workflow-hero compact">
        <div>
          <Text className="eyebrow">Opportunity Priority</Text>
          <Title level={4}>先排优先级，再推动下一步动作</Title>
          <Paragraph className="overview-copy">
            商机页面面向销售主管和一线销售：用评分解释确定今天先跟谁，再进入客户 360 补上下文，最后交给 AI 助手生成跟进任务或话术。
          </Paragraph>
          <Tag color={dataMode === 'connected' ? 'green' : 'orange'}>
            {dataMode === 'connected' ? '商机服务已连接' : '商机服务未连接'}
          </Tag>
          {resolvedUrlSalesRepId ? <Tag color="blue">销售 ID {resolvedUrlSalesRepId}</Tag> : null}
        </div>
        <div className="workflow-stepper">
          <Link to="/customers" className="mini-flow-node"><TeamOutlined />客户</Link>
          <span className="mini-flow-node active"><ThunderboltOutlined />商机</span>
          <Link to="/agent" className="mini-flow-node"><MessageOutlined />AI 助手</Link>
          <span className="mini-flow-node muted"><ProfileOutlined />确认</span>
        </div>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="推荐商机" value={metrics.total} prefix={<ThunderboltOutlined />} />
            <Text className="metric-label">当前筛选范围</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="高优先级" value={metrics.high} prefix={<FireOutlined />} />
            <Text className="metric-label">需要今日优先跟进</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="预计金额" value={metrics.totalAmount} prefix={<DollarOutlined />} formatter={() => currency(metrics.totalAmount)} />
            <Text className="metric-label">候选商机金额合计</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="平均分" value={metrics.avgScore.toFixed(1)} prefix={<RiseOutlined />} />
            <Text className="metric-label">可解释评分均值</Text>
          </Card>
        </Col>
      </Row>

      <Card
        className="command-card"
        title="优先级看板"
        extra={
          <Space wrap>
            <Segmented
              value={priorityFilter}
              onChange={(value) => setPriorityFilter(String(value))}
              options={[
                { label: '全部', value: 'ALL' },
                { label: '高', value: 'HIGH' },
                { label: '中', value: 'MEDIUM' },
                { label: '低', value: 'LOW' }
              ]}
            />
            <Select
              value={industryFilter}
              onChange={setIndustryFilter}
              style={{ width: 150 }}
              suffixIcon={<FilterOutlined />}
              options={[
                { label: '全部行业', value: 'ALL' },
                ...industries.map((item) => ({ label: item, value: item }))
              ]}
            />
          </Space>
        }
      >
        {filtered.length === 0 && !loading ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={error ? '商机数据暂不可用' : '暂无符合条件的商机'} />
        ) : (
          <div className="lead-board">
            {['HIGH', 'MEDIUM', 'LOW'].map((priority) => {
              const items = filtered.filter((item) => item.priority === priority);
              return (
                <div className="lead-column" key={priority}>
                  <div className="lead-column-head">
                    <Space>
                      <Tag color={priorityColor[priority]}>{priority}</Tag>
                      <Text strong>{items.length} 个</Text>
                    </Space>
                  </div>
                  <Space direction="vertical" size={10} style={{ width: '100%' }}>
                    {items.map((item) => (
                      <button className="lead-card-button" key={item.leadId} onClick={() => openLead(item)}>
                        <Space direction="vertical" size={8} style={{ width: '100%' }}>
                          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                            <Text strong>{item.customerName}</Text>
                            <Tag>{item.industry}</Tag>
                          </Space>
                          <Progress percent={Math.round(item.score)} size="small" strokeColor={priority === 'HIGH' ? '#ef4444' : '#2563eb'} />
                          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                            <Text type="secondary">{currency(item.estimatedAmount)}</Text>
                            <Text type="secondary">剩余 {daysUntil(item.expectedCloseDate)}</Text>
                          </Space>
                        </Space>
                      </button>
                    ))}
                  </Space>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      <Card className="command-card" title="评分解释明细">
        <Table
          rowKey="leadId"
          loading={loading}
          dataSource={filtered}
          locale={{
            emptyText: error ? '商机数据暂不可用' : '暂无符合条件的商机'
          }}
          pagination={{ pageSize: 8 }}
          onRow={(record) => ({ onClick: () => openLead(record) })}
          rowClassName="clickable-table-row"
          columns={[
            { title: '商机 ID', dataIndex: 'leadId', width: 110 },
            { title: '客户', dataIndex: 'customerName' },
            { title: '行业', dataIndex: 'industry', width: 100 },
            {
              title: '评分',
              dataIndex: 'score',
              width: 150,
              render: (value) => <Progress percent={Math.round(value)} size="small" />
            },
            {
              title: '优先级',
              dataIndex: 'priority',
              width: 110,
              render: (value) => <Tag color={priorityColor[value] ?? 'default'}>{value}</Tag>
            },
            {
              title: '推荐理由',
              dataIndex: 'reasons',
              render: (value: string[]) => (
                <Space size={4} wrap>
                  {value.map((item) => (
                    <Tag key={item}>{item}</Tag>
                  ))}
                </Space>
              )
            },
            { title: '下一步动作', dataIndex: 'suggestedAction' },
            {
              title: '进入流程',
              width: 220,
              render: (_, record) => (
                <Space size={6} wrap onClick={(event) => event.stopPropagation()}>
                  <Link to={`/customers?customerId=${record.customerId}`}>
                    <Button size="small" icon={<TeamOutlined />}>看客户</Button>
                  </Link>
                  <Link to={agentUrlForLead(record)}>
                            <Button size="small" type="primary" icon={<MessageOutlined />}>AI 处理</Button>
                  </Link>
                </Space>
              )
            }
          ]}
        />
      </Card>

      <Drawer
        title={selected ? `商机解释 · ${selected.customerName}` : '商机解释'}
        open={Boolean(selected)}
        width={720}
        onClose={closeLead}
      >
        {selected && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Row gutter={[12, 12]}>
              <Col span={8}>
                <Card size="small" className="risk-card">
                  <Statistic title="评分" value={selected.score} />
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small" className="risk-card">
                  <Statistic title="预计金额" value={selected.estimatedAmount} formatter={() => currency(selected.estimatedAmount)} />
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small" className="risk-card">
                  <Statistic title="预计成交" value={selected.expectedCloseDate} />
                </Card>
              </Col>
            </Row>

            <div className="drawer-action-bar">
              <div>
                <Text strong>推荐处理路径</Text>
              <div className="metric-label">先确认客户背景，再让 AI 助手生成跟进策略或创建确认任务。</div>
              </div>
              <Space wrap>
                <Link to={`/customers?customerId=${selected.customerId}`}>
                  <Button icon={<TeamOutlined />}>查看客户 360</Button>
                </Link>
                <Link to={agentUrlForLead(selected)}>
                <Button type="primary" icon={<MessageOutlined />}>让 AI 处理</Button>
                </Link>
              </Space>
            </div>

            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="商机 ID">{selected.leadId}</Descriptions.Item>
              <Descriptions.Item label="客户 ID">{selected.customerId}</Descriptions.Item>
              <Descriptions.Item label="行业">{selected.industry}</Descriptions.Item>
              <Descriptions.Item label="优先级">
                <Tag color={priorityColor[selected.priority] ?? 'default'}>{selected.priority}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="当前阶段">
                <Tag color={stageColor[selectedLead?.stage ?? ''] ?? 'default'}>{selectedLead?.stage ?? '-'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="意向等级">{selectedLead?.intentLevel ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="来源">{selectedLead?.source ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">{selectedLead?.status ?? '-'}</Descriptions.Item>
            </Descriptions>

            <Card size="small" title="评分解释">
              <Space direction="vertical" style={{ width: '100%' }}>
                {selected.reasons.map((reason) => (
                  <div className="reason-item" key={reason}>
                    <ProfileOutlined />
                    <Text>{reason}</Text>
                  </div>
                ))}
              </Space>
            </Card>

            <Card size="small" title="建议动作">
              <Paragraph style={{ marginBottom: 0 }}>{selected.suggestedAction}</Paragraph>
            </Card>

            {selectedLead?.scoreReason && (
              <Card size="small" title="原始评分备注">
                <Paragraph style={{ marginBottom: 0 }}>{selectedLead.scoreReason}</Paragraph>
              </Card>
            )}
          </Space>
        )}
      </Drawer>
    </Space>
  );
}
