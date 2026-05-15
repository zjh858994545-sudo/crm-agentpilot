import {
  DollarOutlined,
  FilterOutlined,
  FireOutlined,
  ProfileOutlined,
  RiseOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import {
  Alert,
  Card,
  Col,
  Descriptions,
  Drawer,
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
import {
  fetchLeadRecommendations,
  fetchLeads,
  Lead,
  LeadRecommendation
} from '../../api/client';

const { Paragraph, Text, Title } = Typography;

const fallbackData: LeadRecommendation[] = [
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
    reasons: ['招聘旺季', '高意向线索', '预计成交金额较高'],
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

export default function Leads() {
  const [recommendations, setRecommendations] = useState<LeadRecommendation[]>(fallbackData);
  const [leads, setLeads] = useState<Lead[]>([]);
  const [priorityFilter, setPriorityFilter] = useState<string>('ALL');
  const [industryFilter, setIndustryFilter] = useState<string>('ALL');
  const [selected, setSelected] = useState<LeadRecommendation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    Promise.all([fetchLeadRecommendations(20), fetchLeads()])
      .then(([nextRecommendations, nextLeads]) => {
        setRecommendations(nextRecommendations);
        setLeads(nextLeads);
      })
      .catch(() => setError('后端未连接，当前显示离线样例商机。'))
      .finally(() => setLoading(false));
  }, []);

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

  const selectedLead = useMemo(
    () => leads.find((item) => item.id === selected?.leadId),
    [leads, selected]
  );

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

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
                    <button className="lead-card-button" key={item.leadId} onClick={() => setSelected(item)}>
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
      </Card>

      <Card className="command-card" title="评分解释明细">
        <Table
          rowKey="leadId"
          loading={loading}
          dataSource={filtered}
          pagination={{ pageSize: 8 }}
          onRow={(record) => ({ onClick: () => setSelected(record) })}
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
            { title: '下一步动作', dataIndex: 'suggestedAction' }
          ]}
        />
      </Card>

      <Drawer
        title={selected ? `商机解释 · ${selected.customerName}` : '商机解释'}
        open={Boolean(selected)}
        width={720}
        onClose={() => setSelected(null)}
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
