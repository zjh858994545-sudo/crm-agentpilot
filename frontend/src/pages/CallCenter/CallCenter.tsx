import { CheckOutlined, FileDoneOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Input,
  InputNumber,
  List,
  Space,
  Tag,
  Typography,
  message
} from 'antd';
import { useEffect, useState } from 'react';
import {
  CallSummaryResponse,
  checkCallQuality,
  confirmAgentAction,
  ContactLogConfirmationResponse,
  createContactLogConfirmation,
  CustomerMemory,
  fetchCustomerMemory,
  QualityCheckResponse,
  summarizeCall
} from '../../api/client';

const { Paragraph, Text } = Typography;
const { TextArea } = Input;

const demoText =
  '客户说套餐有点贵，担心续费后没有效果。销售表示可以帮客户争取优惠，并说明会在明天上午提供上月曝光数据和同行案例，但不会承诺一定成交。';

export default function CallCenter() {
  const [customerId, setCustomerId] = useState(1001);
  const [salesRepId, setSalesRepId] = useState(1);
  const [leadId, setLeadId] = useState(3001);
  const [text, setText] = useState(demoText);
  const [summary, setSummary] = useState<CallSummaryResponse | null>(null);
  const [quality, setQuality] = useState<QualityCheckResponse | null>(null);
  const [confirmation, setConfirmation] = useState<ContactLogConfirmationResponse | null>(null);
  const [memory, setMemory] = useState<CustomerMemory[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const payload = { customerId, salesRepId, leadId, text };

  const loadMemory = async () => {
    try {
      setMemory(await fetchCustomerMemory(customerId));
    } catch {
      setMemory([]);
    }
  };

  useEffect(() => {
    loadMemory();
  }, [customerId]);

  const runSummary = async () => {
    setLoading(true);
    setError('');
    try {
      setSummary(await summarizeCall(payload));
    } catch {
      setError('通话摘要失败，请确认后端服务已启动。');
    } finally {
      setLoading(false);
    }
  };

  const runQuality = async () => {
    setLoading(true);
    setError('');
    try {
      setQuality(await checkCallQuality(payload));
    } catch {
      setError('质检失败，请确认后端服务已启动。');
    } finally {
      setLoading(false);
    }
  };

  const proposeWrite = async () => {
    setLoading(true);
    setError('');
    try {
      const result = await createContactLogConfirmation(payload);
      setConfirmation(result);
      message.info('已生成联系记录写入确认，请确认后再写入 CRM。');
    } catch {
      setError('生成联系记录确认失败，请确认后端服务已启动。');
    } finally {
      setLoading(false);
    }
  };

  const confirmWrite = async () => {
    if (!confirmation) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      await confirmAgentAction(confirmation.confirmationId);
      message.success('联系记录已写入 CRM');
      setConfirmation(null);
      await loadMemory();
    } catch {
      setError('确认写入失败，请检查后端服务。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}
      <Card title="呼叫中心 AI 辅助">
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space wrap>
            <Text>客户 ID</Text>
            <InputNumber min={1} value={customerId} onChange={(value) => setCustomerId(value ?? 1001)} />
            <Text>销售 ID</Text>
            <InputNumber min={1} value={salesRepId} onChange={(value) => setSalesRepId(value ?? 1)} />
            <Text>商机 ID</Text>
            <InputNumber min={1} value={leadId} onChange={(value) => setLeadId(value ?? 3001)} />
          </Space>
          <TextArea rows={5} value={text} onChange={(event) => setText(event.target.value)} />
          <Space wrap>
            <Button type="primary" icon={<FileDoneOutlined />} loading={loading} onClick={runSummary}>
              生成摘要
            </Button>
            <Button icon={<SafetyCertificateOutlined />} loading={loading} onClick={runQuality}>
              质检
            </Button>
            <Button icon={<CheckOutlined />} loading={loading} onClick={proposeWrite}>
              生成联系记录确认
            </Button>
          </Space>
        </Space>
      </Card>

      <div className="page-grid">
        <Card className="span-6" title="结构化摘要">
          {summary ? (
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="摘要">{summary.summary}</Descriptions.Item>
              <Descriptions.Item label="客户意向">{summary.customerIntent}</Descriptions.Item>
              <Descriptions.Item label="异议">{summary.objections}</Descriptions.Item>
              <Descriptions.Item label="下一步">{summary.nextAction}</Descriptions.Item>
            </Descriptions>
          ) : (
            <Text type="secondary">点击“生成摘要”后展示客户意向、异议和下一步动作。</Text>
          )}
        </Card>

        <Card className="span-6" title="质检结果">
          {quality ? (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Space>
                <Text>风险等级</Text>
                <Tag color={quality.riskLevel === 'HIGH' ? 'red' : quality.riskLevel === 'MEDIUM' ? 'orange' : 'green'}>
                  {quality.riskLevel}
                </Tag>
              </Space>
              <List
                size="small"
                dataSource={quality.violations}
                locale={{ emptyText: '未发现明显违规表达' }}
                renderItem={(item) => (
                  <List.Item>
                    <Space direction="vertical" size={2}>
                      <Space>
                        <Tag color={item.severity === 'HIGH' ? 'red' : 'orange'}>{item.severity}</Tag>
                        <Text strong>{item.rule}</Text>
                      </Space>
                      <Text type="secondary">证据：{item.evidence}</Text>
                      <Text>建议：{item.suggestion}</Text>
                    </Space>
                  </List.Item>
                )}
              />
              <Divider />
              <List
                size="small"
                dataSource={quality.citations}
                renderItem={(item) => (
                  <List.Item>
                    <Text type="secondary">{item.docTitle}：{item.quote}</Text>
                  </List.Item>
                )}
              />
            </Space>
          ) : (
            <Text type="secondary">点击“质检”后会检索质检知识库并识别承诺类风险。</Text>
          )}
        </Card>

        <Card className="span-6" title="客户记忆">
          <List
            dataSource={memory}
            locale={{ emptyText: '暂无客户记忆或后端未连接' }}
            renderItem={(item) => (
              <List.Item>
                <Space direction="vertical" size={2}>
                  <Space>
                    <Tag>{item.memoryType}</Tag>
                    <Tag color="blue">重要度 {item.importance}</Tag>
                  </Space>
                  <Text>{item.content}</Text>
                </Space>
              </List.Item>
            )}
          />
        </Card>

        <Card className="span-6" title="写入确认">
          {confirmation ? (
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Alert type="info" showIcon message={confirmation.actionSummary} />
              <Paragraph className="json-preview">
                {JSON.stringify(confirmation.payload, null, 2)}
              </Paragraph>
              <Button type="primary" loading={loading} onClick={confirmWrite}>
                确认写入 CRM
              </Button>
            </Space>
          ) : (
            <Text type="secondary">写联系记录前必须先生成 confirmation，避免 Agent 直接改 CRM 数据。</Text>
          )}
        </Card>
      </div>
    </Space>
  );
}
