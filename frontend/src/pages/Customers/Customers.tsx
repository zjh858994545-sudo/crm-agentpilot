import {
  ClockCircleOutlined,
  EnvironmentOutlined,
  PhoneOutlined,
  ProfileOutlined,
  SearchOutlined,
  UserOutlined,
  WarningOutlined
} from '@ant-design/icons';
import {
  Alert,
  Card,
  Col,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  ContactLog,
  Customer,
  fetchCustomerContactLogs,
  fetchCustomerDetail,
  fetchCustomers
} from '../../api/client';

const { Paragraph, Text } = Typography;

const fallbackData: Customer[] = [
  {
    id: 1001,
    name: '美家房产',
    industry: '房产',
    city: '北京',
    contactName: '王经理',
    contactMobile: '13910001001',
    lifecycleStage: '续费期',
    valueLevel: 'A',
    riskLevel: 'MEDIUM',
    packageExpireAt: '2026-05-31T00:00:00',
    tags: '续费,价格敏感,ROI关注',
    remark: 'A 类客户，适合用曝光和咨询数据推进续费。'
  },
  {
    id: 1002,
    name: '快招人力',
    industry: '招聘',
    city: '天津',
    contactName: '赵总',
    contactMobile: '13910001002',
    lifecycleStage: '高意向',
    valueLevel: 'A',
    riskLevel: 'LOW',
    packageExpireAt: '2026-06-10T00:00:00',
    tags: '招聘旺季,升级套餐'
  },
  {
    id: 1003,
    name: '老街火锅',
    industry: '餐饮',
    city: '石家庄',
    contactName: '刘店长',
    contactMobile: '13910001003',
    lifecycleStage: '异议处理',
    valueLevel: 'B',
    riskLevel: 'HIGH',
    packageExpireAt: '2026-05-24T00:00:00',
    tags: '价格异议,效果担忧'
  }
];

const riskColor: Record<string, string> = {
  LOW: 'green',
  MEDIUM: 'orange',
  HIGH: 'red'
};

const valueColor: Record<string, string> = {
  A: 'blue',
  B: 'purple',
  C: 'default'
};

function splitTags(value?: string) {
  return (value ?? '')
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function maskMobile(value?: string) {
  if (!value || value.length < 7) {
    return value ?? '-';
  }
  return `${value.slice(0, 3)}****${value.slice(-4)}`;
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

function daysUntil(value?: string) {
  if (!value) {
    return '-';
  }
  const target = new Date(value).getTime();
  const now = Date.now();
  const days = Math.ceil((target - now) / 86400000);
  return Number.isFinite(days) ? `${days} 天` : '-';
}

export default function Customers() {
  const [data, setData] = useState<Customer[]>(fallbackData);
  const [keyword, setKeyword] = useState('');
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null);
  const [contactLogs, setContactLogs] = useState<ContactLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    fetchCustomers()
      .then(setData)
      .catch(() => setError('后端未连接，当前显示本地示例客户。'))
      .finally(() => setLoading(false));
  }, []);

  const filteredData = useMemo(() => {
    const term = keyword.trim().toLowerCase();
    if (!term) {
      return data;
    }
    return data.filter((item) =>
      [item.name, item.industry, item.city, item.tags, item.contactName]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term))
    );
  }, [data, keyword]);

  const metrics = useMemo(() => {
    const highRisk = data.filter((item) => item.riskLevel === 'HIGH').length;
    const aLevel = data.filter((item) => item.valueLevel === 'A').length;
    const renewal = data.filter((item) => splitTags(item.tags).some((tag) => tag.includes('续费'))).length;
    return { total: data.length, highRisk, aLevel, renewal };
  }, [data]);

  const openCustomer = async (customer: Customer) => {
    setSelectedCustomer(customer);
    setContactLogs([]);
    try {
      const [detail, logs] = await Promise.all([
        fetchCustomerDetail(customer.id),
        fetchCustomerContactLogs(customer.id)
      ]);
      setSelectedCustomer(detail ?? customer);
      setContactLogs(logs);
    } catch {
      setContactLogs([]);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="客户总数" value={metrics.total} />
            <Text className="metric-label">CRM 客户池</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="A 类客户" value={metrics.aLevel} />
            <Text className="metric-label">高价值优先跟进</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="高风险客户" value={metrics.highRisk} />
            <Text className="metric-label">需要质检或异议处理</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="续费相关" value={metrics.renewal} />
            <Text className="metric-label">标签中包含续费诉求</Text>
          </Card>
        </Col>
      </Row>

      <Card
        className="command-card"
        title="客户 360 列表"
        extra={
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索客户、行业、城市、标签"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            style={{ width: 280 }}
          />
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={filteredData}
          pagination={{ pageSize: 8 }}
          onRow={(record) => ({ onClick: () => openCustomer(record) })}
          rowClassName="clickable-table-row"
          columns={[
            {
              title: '客户',
              dataIndex: 'name',
              render: (_, record) => (
                <Space direction="vertical" size={2}>
                  <Text strong>{record.name}</Text>
                  <Text type="secondary">
                    <EnvironmentOutlined /> {record.city} · {record.industry}
                  </Text>
                </Space>
              )
            },
            {
              title: '联系人',
              render: (_, record) => (
                <Space direction="vertical" size={2}>
                  <Text>
                    <UserOutlined /> {record.contactName ?? '-'}
                  </Text>
                  <Text type="secondary">
                    <PhoneOutlined /> {maskMobile(record.contactMobile)}
                  </Text>
                </Space>
              )
            },
            {
              title: '价值等级',
              dataIndex: 'valueLevel',
              width: 110,
              render: (value) => <Tag color={valueColor[value] ?? 'default'}>{value}</Tag>
            },
            {
              title: '风险',
              dataIndex: 'riskLevel',
              width: 110,
              render: (value) => <Tag color={riskColor[value] ?? 'default'}>{value}</Tag>
            },
            {
              title: '套餐到期',
              dataIndex: 'packageExpireAt',
              width: 160,
              render: (value) => (
                <Space direction="vertical" size={2}>
                  <Text>{formatDate(value).slice(0, 10)}</Text>
                  <Text type="secondary">剩余 {daysUntil(value)}</Text>
                </Space>
              )
            },
            {
              title: '标签',
              dataIndex: 'tags',
              render: (value) => (
                <Space size={4} wrap>
                  {splitTags(value).map((item) => (
                    <Tag key={item}>{item}</Tag>
                  ))}
                </Space>
              )
            }
          ]}
        />
      </Card>

      <Drawer
        title={selectedCustomer ? `客户详情 · ${selectedCustomer.name}` : '客户详情'}
        open={Boolean(selectedCustomer)}
        width={760}
        onClose={() => setSelectedCustomer(null)}
      >
        {selectedCustomer && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Row gutter={[12, 12]}>
              <Col span={8}>
                <Card size="small" className="risk-card">
                  <Statistic title="价值等级" value={selectedCustomer.valueLevel} />
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small" className="risk-card">
                  <Statistic title="风险等级" value={selectedCustomer.riskLevel} />
                </Card>
              </Col>
              <Col span={8}>
                <Card size="small" className="risk-card">
                  <Statistic title="到期倒计时" value={daysUntil(selectedCustomer.packageExpireAt)} />
                </Card>
              </Col>
            </Row>

            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="客户 ID">{selectedCustomer.id}</Descriptions.Item>
              <Descriptions.Item label="生命周期">{selectedCustomer.lifecycleStage ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="行业">{selectedCustomer.industry}</Descriptions.Item>
              <Descriptions.Item label="城市">{selectedCustomer.city}</Descriptions.Item>
              <Descriptions.Item label="联系人">{selectedCustomer.contactName ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="手机号">{maskMobile(selectedCustomer.contactMobile)}</Descriptions.Item>
              <Descriptions.Item label="最近联系">{formatDate(selectedCustomer.lastContactAt)}</Descriptions.Item>
              <Descriptions.Item label="下次跟进">{formatDate(selectedCustomer.nextFollowTime)}</Descriptions.Item>
              <Descriptions.Item label="地址" span={2}>{selectedCustomer.address ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>{selectedCustomer.remark ?? '-'}</Descriptions.Item>
            </Descriptions>

            <Card size="small" title="客户标签">
              <Space wrap>
                {splitTags(selectedCustomer.tags).map((item) => (
                  <Tag key={item} color={item.includes('风险') || item.includes('异议') ? 'orange' : 'blue'}>
                    {item}
                  </Tag>
                ))}
              </Space>
            </Card>

            <Card size="small" title="跟进时间线">
              {contactLogs.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无联系记录或后端未连接" />
              ) : (
                <Timeline
                  items={contactLogs.map((log) => ({
                    dot: log.objections ? <WarningOutlined /> : <ProfileOutlined />,
                    children: (
                      <Space direction="vertical" size={4}>
                        <Space wrap>
                          <Tag color="blue">{log.channel}</Tag>
                          <Text type="secondary">{formatDate(log.contactAt)}</Text>
                          {log.customerIntent && <Tag>{log.customerIntent}</Tag>}
                        </Space>
                        <Text strong>{log.summary || '未生成摘要'}</Text>
                        <Paragraph style={{ marginBottom: 0 }} ellipsis={{ rows: 2, expandable: true }}>
                          {log.content}
                        </Paragraph>
                        {log.nextAction && <Text type="secondary">下一步：{log.nextAction}</Text>}
                      </Space>
                    )
                  }))}
                />
              )}
            </Card>
          </Space>
        )}
      </Drawer>
    </Space>
  );
}
