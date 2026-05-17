import {
  CheckOutlined,
  FileDoneOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  InputNumber,
  List,
  Row,
  Col,
  Space,
  Statistic,
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
  describeApiError,
  fetchCustomerMemory,
  processCallEndedEvent,
  QualityCheckResponse,
  summarizeCall
} from '../../api/client';
import ApiErrorNotice from '../../components/ApiErrorNotice';

const { Paragraph, Text } = Typography;
const { TextArea } = Input;

const defaultTranscript =
  '客户说套餐有点贵，担心续费后没有效果。销售表示可以帮客户争取优惠，并说明会在明天上午提供上月曝光数据和同行案例，但不会承诺一定成交。';

function riskColor(value?: string) {
  if (value === 'HIGH') return 'red';
  if (value === 'MEDIUM') return 'orange';
  return 'green';
}

const confirmationFieldLabels: Record<string, string> = {
  customerId: '客户',
  salesRepId: '销售',
  leadId: '商机',
  channel: '渠道',
  content: '联系内容',
  summary: '摘要',
  customerIntent: '客户意向',
  objections: '异议',
  nextAction: '下一步',
  contactAt: '联系时间',
  idempotencyKey: '防重复标记'
};

export default function CallCenter() {
  const [customerId, setCustomerId] = useState(1001);
  const [salesRepId, setSalesRepId] = useState(1);
  const [leadId, setLeadId] = useState(3001);
  const [text, setText] = useState(defaultTranscript);
  const [summary, setSummary] = useState<CallSummaryResponse | null>(null);
  const [quality, setQuality] = useState<QualityCheckResponse | null>(null);
  const [confirmation, setConfirmation] = useState<ContactLogConfirmationResponse | null>(null);
  const [lastCallId, setLastCallId] = useState('');
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
    } catch (err) {
      setError(describeApiError(err));
    } finally {
      setLoading(false);
    }
  };

  const runQuality = async () => {
    setLoading(true);
    setError('');
    try {
      setQuality(await checkCallQuality(payload));
    } catch (err) {
      setError(describeApiError(err));
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
    } catch (err) {
      setError(describeApiError(err));
    } finally {
      setLoading(false);
    }
  };

  const runPostCallAutomation = async () => {
    setLoading(true);
    setError('');
    try {
      const callId = `CALL-${Date.now()}`;
      const result = await processCallEndedEvent({
        callId,
        customerId,
        salesRepId,
        leadId,
        recordingUrl: `https://voice.example.com/recordings/${callId}`,
        transcript: text
      });
      setLastCallId(result.callId);
      setSummary(result.summary);
      setQuality(result.quality);
      setConfirmation(result.confirmation);
      message.success('已根据通话结束事件生成摘要、质检和 CRM 写入确认');
    } catch (err) {
      setError(describeApiError(err));
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
    } catch (err) {
      setError(describeApiError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <ApiErrorNotice error={error} title="通话处理暂时无法完成" />}

      <Card className="command-card" title="通话结束自动处理">
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message="真实业务里，这里由云通信在电话挂断后自动回调"
            description="回调带入 callId、录音地址和转写文本，系统自动生成摘要、质检结果和 CRM 写入确认。销售只需要在手机或工作台里确认。"
          />
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
            <Button type="primary" icon={<PhoneOutlined />} loading={loading} onClick={runPostCallAutomation}>
              模拟电话挂断自动生成
            </Button>
            <Button icon={<FileDoneOutlined />} loading={loading} onClick={runSummary}>
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

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card className="metric-card">
            <Statistic title="通话事件" value={lastCallId || '-'} prefix={<PhoneOutlined />} />
            <Text className="metric-label">电话挂断后自动触发</Text>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card className="metric-card">
            <Statistic title="质检风险" value={quality?.riskLevel ?? '-'} />
            <Text className="metric-label">风险表达与合规规则</Text>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card className="metric-card">
            <Statistic title="客户记忆" value={memory.length} />
            <Text className="metric-label">可用于后续上下文</Text>
          </Card>
        </Col>
      </Row>

      <div className="page-grid">
        <Card className="span-6 command-card" title="结构化摘要">
          {summary ? (
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="摘要">{summary.summary}</Descriptions.Item>
              <Descriptions.Item label="客户意向">{summary.customerIntent}</Descriptions.Item>
              <Descriptions.Item label="异议">{summary.objections || '-'}</Descriptions.Item>
              <Descriptions.Item label="下一步">{summary.nextAction}</Descriptions.Item>
            </Descriptions>
          ) : (
            <Text type="secondary">点击“生成摘要”后展示客户意向、异议和下一步动作。</Text>
          )}
        </Card>

        <Card className="span-6 command-card" title="质检结果">
          {quality ? (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Space>
                <Text>风险等级</Text>
                <Tag color={riskColor(quality.riskLevel)}>{quality.riskLevel}</Tag>
              </Space>
              <List
                size="small"
                dataSource={quality.violations}
                locale={{ emptyText: '未发现明显违规表达' }}
                renderItem={(item) => (
                  <List.Item>
                    <Space direction="vertical" size={2}>
                      <Space>
                        <Tag color={riskColor(item.severity)}>{item.severity}</Tag>
                        <Text strong>{item.rule}</Text>
                      </Space>
                      <Text type="secondary">证据：{item.evidence}</Text>
                      <Text>建议：{item.suggestion}</Text>
                    </Space>
                  </List.Item>
                )}
              />
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
            <Text type="secondary">点击“质检”后检索质检知识库并识别承诺类风险。</Text>
          )}
        </Card>

        <Card className="span-6 command-card" title="客户记忆">
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

        <Card className="span-6 command-card" title="写入确认">
          {confirmation ? (
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Alert type="info" showIcon message={confirmation.actionSummary} />
              <Descriptions bordered size="small" column={1}>
                {Object.entries(confirmation.payload).map(([key, value]) => (
                  <Descriptions.Item label={confirmationFieldLabels[key] ?? key} key={key}>
                    {String(value ?? '-')}
                  </Descriptions.Item>
                ))}
              </Descriptions>
              <Button type="primary" loading={loading} onClick={confirmWrite}>
                确认写入 CRM
              </Button>
            </Space>
          ) : (
                    <Text type="secondary">写联系记录前必须先生成确认单，避免 AI 助手直接修改 CRM 数据。</Text>
          )}
        </Card>
      </div>
    </Space>
  );
}
