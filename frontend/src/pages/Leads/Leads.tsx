import { Alert, Card, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { fetchLeadRecommendations, LeadRecommendation } from '../../api/client';

const { Text } = Typography;

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
    reasons: ['套餐即将到期，A 类客户，最近 10 天未联系'],
    suggestedAction: '优先电话跟进，准备 ROI 案例'
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
    reasons: ['高意向线索，预计成交金额较高'],
    suggestedAction: '输出套餐对比和招聘效果案例'
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
    reasons: ['存在价格异议，需要补充曝光和转化数据'],
    suggestedAction: '处理价格异议，约定复盘时间'
  }
];

export default function Leads() {
  const [data, setData] = useState<LeadRecommendation[]>(fallbackData);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchLeadRecommendations(10)
      .then(setData)
      .catch(() => setError('后端未连接，当前显示本地示例数据。'));
  }, []);

  return (
    <Card title="商机优先级推荐">
      {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Table
        rowKey="leadId"
        dataSource={data}
        pagination={false}
        columns={[
          { title: '商机 ID', dataIndex: 'leadId', width: 110 },
          { title: '客户', dataIndex: 'customerName' },
          { title: '行业', dataIndex: 'industry', width: 100 },
          { title: '分数', dataIndex: 'score', width: 90 },
          {
            title: '优先级',
            dataIndex: 'priority',
            width: 110,
            render: (value) => <Tag color={value === 'HIGH' ? 'red' : 'orange'}>{value}</Tag>
          },
          { title: '推荐理由', dataIndex: 'reasons', render: (value: string[]) => <Text>{value.join('；')}</Text> },
          { title: '下一步动作', dataIndex: 'suggestedAction' }
        ]}
      />
    </Card>
  );
}
