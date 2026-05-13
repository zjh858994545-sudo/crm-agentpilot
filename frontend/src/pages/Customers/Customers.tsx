import { Alert, Card, Table, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { Customer, fetchCustomers } from '../../api/client';

const fallbackData: Customer[] = [
  { id: 1001, name: '美家房产', industry: '房产', city: '北京', valueLevel: 'A', riskLevel: 'MEDIUM' },
  { id: 1002, name: '快招人力', industry: '招聘', city: '天津', valueLevel: 'A', riskLevel: 'LOW' },
  { id: 1003, name: '老街火锅', industry: '餐饮', city: '石家庄', valueLevel: 'B', riskLevel: 'HIGH' }
];

export default function Customers() {
  const [data, setData] = useState<Customer[]>(fallbackData);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchCustomers()
      .then(setData)
      .catch(() => setError('后端未连接，当前显示本地示例数据。'));
  }, []);

  return (
    <Card title="客户 360 列表">
      {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Table
        rowKey="id"
        dataSource={data}
        pagination={{ pageSize: 8 }}
        columns={[
          { title: '客户', dataIndex: 'name' },
          { title: '行业', dataIndex: 'industry' },
          { title: '城市', dataIndex: 'city' },
          { title: '价值等级', dataIndex: 'valueLevel', render: (value) => <Tag color="blue">{value}</Tag> },
          { title: '风险', dataIndex: 'riskLevel', render: (value) => <Tag>{value}</Tag> },
          { title: '标签', dataIndex: 'tags', render: (value) => value || '-' }
        ]}
      />
    </Card>
  );
}
