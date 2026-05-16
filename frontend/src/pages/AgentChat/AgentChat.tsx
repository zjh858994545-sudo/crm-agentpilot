import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseOutlined,
  FileSearchOutlined,
  MessageOutlined,
  SendOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TeamOutlined,
  ToolOutlined,
  ThunderboltOutlined,
  UserOutlined
} from '@ant-design/icons';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  List,
  Space,
  Tag,
  Timeline,
  Typography,
  message as antdMessage
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  AgentConfirmation,
  AgentChatResponse,
  confirmAgentAction,
  fetchAgentConfirmations,
  rejectAgentAction,
  sendAgentMessage,
  ToolCallView
} from '../../api/client';

const { Paragraph, Text } = Typography;
const { TextArea } = Input;

interface ChatMessage {
  role: 'user' | 'agent';
  content: string;
}

const scenarioPrompts = [
  {
    title: '今日优先跟进',
    category: '商机',
    prompt: '今天我应该优先跟进哪些客户？请给出推荐理由和下一步动作。'
  },
  {
    title: '客户 360 分析',
    category: '客户',
    prompt: '帮我分析美家房产，重点看续费风险、历史异议和下一步跟进策略。'
  },
  {
    title: '创建跟进任务',
    category: '确认',
    prompt: '帮我创建明天上午10点跟进美家房产续费的任务。'
  },
  {
    title: '知识库问答',
    category: '知识',
    prompt: '客户嫌套餐贵并担心续费效果时，销售应该怎么回复？'
  }
];

const accountHints = [
  '美家房产：A 类客户，套餐临近到期，近期关注续费 ROI。',
  '快招人力：招聘行业高意向客户，适合升级套餐。',
  '老街火锅：价格异议明显，适合用曝光数据和同行案例推进。'
];

function toolColor(tool: ToolCallView) {
  if (tool.toolType === 'WRITE') {
    return 'orange';
  }
  return 'green';
}

function inferResultTitle(response: AgentChatResponse | null) {
  if (!response) {
    return '尚未生成结果';
  }
  const toolNames = response.toolCalls.map((tool) => tool.toolName.toLowerCase());
  if (response.type === 'confirmation_required') {
    return '已生成待确认写操作';
  }
  if (toolNames.some((name) => name.includes('rank') || name.includes('lead'))) {
    return '商机优先级建议';
  }
  if (toolNames.some((name) => name.includes('knowledge') || name.includes('search'))) {
    return '知识库检索结果';
  }
  if (toolNames.some((name) => name.includes('customer') || name.includes('contact'))) {
    return '客户分析结果';
  }
  return 'Agent 处理结果';
}

function parsePayload(value?: string | Record<string, unknown>) {
  if (!value) {
    return null;
  }
  if (typeof value !== 'string') {
    return value;
  }
  try {
    return JSON.parse(value) as Record<string, unknown>;
  } catch {
    return { raw: value };
  }
}

function payloadEntries(value?: string | Record<string, unknown>) {
  const payload = parsePayload(value);
  if (!payload) {
    return [];
  }
  const labels: Record<string, string> = {
    customerId: '客户 ID',
    leadId: '商机 ID',
    salesRepId: '销售 ID',
    title: '标题',
    content: '内容',
    dueTime: '跟进时间',
    stage: '目标阶段',
    channel: '渠道',
    summary: '摘要',
    nextAction: '下一步'
  };
  return Object.entries(payload)
    .filter(([, item]) => item !== undefined && item !== null && item !== '')
    .slice(0, 8)
    .map(([key, item]) => ({ label: labels[key] ?? key, value: String(item) }));
}

function PayloadSummary({ payload }: { payload?: string | Record<string, unknown> }) {
  const entries = payloadEntries(payload);
  if (entries.length === 0) {
    return null;
  }
  return (
    <div className="payload-summary">
      {entries.map((item) => (
        <div className="payload-field" key={item.label}>
          <span>{item.label}</span>
          <strong>{item.value}</strong>
        </div>
      ))}
    </div>
  );
}

function resultTone(response: AgentChatResponse | null) {
  if (!response) {
    return 'default';
  }
  if (response.type === 'confirmation_required') {
    return 'orange';
  }
  if (inferResultTitle(response).includes('商机')) {
    return 'red';
  }
  if (inferResultTitle(response).includes('知识')) {
    return 'blue';
  }
  return 'green';
}

function nextActionText(response: AgentChatResponse | null) {
  if (!response) {
    return '发送一个销售作业问题后，我会在这里沉淀结构化结果。';
  }
  if (response.type === 'confirmation_required') {
    return '请核对确认单字段，确认后才会写入 CRM。';
  }
  const title = inferResultTitle(response);
  if (title.includes('商机')) {
    return '建议进入客户 360 补齐上下文，再让 Agent 生成跟进任务。';
  }
  if (title.includes('知识')) {
    return '建议把命中的 SOP 或政策引用带入客户沟通话术。';
  }
  if (title.includes('客户')) {
    return '建议基于客户风险和异议生成下一次跟进动作。';
  }
  return '建议继续补充客户或商机上下文，让 Agent 生成更具体动作。';
}

export default function AgentChat() {
  const [searchParams] = useSearchParams();
  const [sessionId, setSessionId] = useState<number | undefined>();
  const [input, setInput] = useState(scenarioPrompts[0].prompt);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: 'agent',
      content: '我可以帮助销售分析客户、推荐商机、检索销售知识库，也可以在你确认后创建 CRM 跟进任务。'
    }
  ]);
  const [toolCalls, setToolCalls] = useState<ToolCallView[]>([]);
  const [pending, setPending] = useState<AgentChatResponse | null>(null);
  const [lastResponse, setLastResponse] = useState<AgentChatResponse | null>(null);
  const [pendingConfirmations, setPendingConfirmations] = useState<AgentConfirmation[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const contextPrompt = searchParams.get('prompt');
  const contextSource = searchParams.get('source');
  const contextCustomerId = Number(searchParams.get('customerId'));
  const resolvedCustomerId = Number.isFinite(contextCustomerId) && contextCustomerId > 0 ? contextCustomerId : undefined;

  const refreshConfirmations = async () => {
    try {
      const items = await fetchAgentConfirmations('PENDING');
      setPendingConfirmations(items);
    } catch {
      setPendingConfirmations([]);
    }
  };

  useEffect(() => {
    if (contextPrompt) {
      setInput(contextPrompt);
    }
  }, [contextPrompt]);

  useEffect(() => {
    refreshConfirmations();
  }, []);

  const resultTitle = useMemo(() => inferResultTitle(lastResponse), [lastResponse]);

  const submit = async (text?: string) => {
    const userText = (text ?? input).trim();
    if (!userText) {
      return;
    }
    setMessages((items) => [...items, { role: 'user', content: userText }]);
    setInput('');
    setLoading(true);
    setError('');
    try {
      const response = await sendAgentMessage(userText, {
        sessionId,
        customerId: resolvedCustomerId
      });
      setSessionId(response.sessionId);
      setToolCalls(response.toolCalls);
      setPending(response.type === 'confirmation_required' ? response : null);
      setLastResponse(response);
      setMessages((items) => [...items, { role: 'agent', content: response.answer }]);
      refreshConfirmations();
    } catch {
      setError('后端未连接，无法发送 Agent 请求。请确认 Spring Boot 服务已经启动。');
    } finally {
      setLoading(false);
    }
  };

  const confirm = async () => {
    if (!pending?.confirmationId) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      await confirmAgentAction(pending.confirmationId);
      antdMessage.success('已确认，CRM 写操作已执行');
      setMessages((items) => [...items, { role: 'agent', content: '已确认并完成 CRM 写入。' }]);
      setPending(null);
      refreshConfirmations();
    } catch {
      setError('确认失败，请检查后端服务。');
    } finally {
      setLoading(false);
    }
  };

  const reject = async () => {
    if (!pending?.confirmationId) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      await rejectAgentAction(pending.confirmationId);
      antdMessage.info('已拒绝，本次写操作未执行');
      setMessages((items) => [...items, { role: 'agent', content: '已取消写入，CRM 数据没有变化。' }]);
      setPending(null);
      refreshConfirmations();
    } catch {
      setError('拒绝确认失败，请检查后端服务。');
    } finally {
      setLoading(false);
    }
  };

  const confirmFromCenter = async (confirmationId: number) => {
    setLoading(true);
    setError('');
    try {
      await confirmAgentAction(confirmationId);
      antdMessage.success('已确认，CRM 写操作已执行');
      if (pending?.confirmationId === confirmationId) {
        setPending(null);
      }
      refreshConfirmations();
    } catch {
      setError('确认失败，请检查后端服务。');
    } finally {
      setLoading(false);
    }
  };

  const rejectFromCenter = async (confirmationId: number) => {
    setLoading(true);
    setError('');
    try {
      await rejectAgentAction(confirmationId);
      antdMessage.info('已拒绝，本次写操作未执行');
      if (pending?.confirmationId === confirmationId) {
        setPending(null);
      }
      refreshConfirmations();
    } catch {
      setError('拒绝确认失败，请检查后端服务。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div className="workflow-hero">
        <div>
          <Text className="eyebrow">Agent Action Center</Text>
          <Typography.Title level={4}>把客户判断变成可确认的 CRM 动作</Typography.Title>
          <Paragraph className="overview-copy">
            先从商机或客户页带着上下文进入，再让 Agent 分析客户、检索知识、生成任务建议；凡是写 CRM 的动作都必须人工确认。
          </Paragraph>
        </div>
        <div className="workflow-stepper">
          <Link to="/customers" className="mini-flow-node"><TeamOutlined />客户</Link>
          <Link to="/leads" className="mini-flow-node"><ThunderboltOutlined />商机</Link>
          <span className="mini-flow-node active"><MessageOutlined />Agent</span>
          <span className="mini-flow-node"><SafetyCertificateOutlined />确认</span>
          <Link to="/runs" className="mini-flow-node muted"><ToolOutlined />审计</Link>
        </div>
      </div>

      {contextPrompt && (
        <Alert
          type="info"
          showIcon
          message="已带入上游业务上下文"
          description={`来源：${contextSource === 'lead' ? '商机优先级' : contextSource === 'customer' ? '客户 360' : '业务页面'}，你可以直接发送，也可以继续补充要求。`}
        />
      )}

      <div className="agent-layout">
        <Space direction="vertical" size={16} className="agent-sidebar">
          <Card title="作业模板" className="command-card">
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              {scenarioPrompts.map((item) => (
                <Button
                  key={item.title}
                  block
                  className="scenario-button"
                  onClick={() => setInput(item.prompt)}
                >
                  <Space>
                    <Tag color="blue">{item.category}</Tag>
                    {item.title}
                  </Space>
                </Button>
              ))}
            </Space>
          </Card>

          <Card title="推荐上下文" className="command-card">
            <List
              size="small"
              dataSource={accountHints}
              renderItem={(item) => (
                <List.Item
                  actions={[
                    <Button
                      size="small"
                      type="link"
                      key="use"
                      onClick={() => setInput(`请基于这个客户背景给出跟进策略：${item}`)}
                    >
                      带入
                    </Button>
                  ]}
                >
                  <Text>{item}</Text>
                </List.Item>
              )}
            />
          </Card>

          <Card title="安全边界" className="command-card">
            <Timeline
              items={[
                { dot: <SearchOutlined />, children: '读工具：客户、商机、知识库直接查询。' },
                { dot: <ClockCircleOutlined />, children: '写工具：先生成确认单，不直接落库。' },
                { dot: <CheckCircleOutlined />, children: '确认后：写入 CRM，并进入运行审计。' }
              ]}
            />
          </Card>
        </Space>

        <Card
          className="chat-panel command-card"
          title="Agent 对话"
          extra={
            <Space>
              <Link to="/customers"><Button size="small" icon={<TeamOutlined />}>客户</Button></Link>
              <Link to="/leads"><Button size="small" icon={<ThunderboltOutlined />}>商机</Button></Link>
            </Space>
          }
        >
          {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 12 }} />}
          <div className="chat-window">
            {messages.map((item, index) => (
              <div className={`message-row ${item.role}`} key={`${item.role}-${index}`}>
                {item.role === 'agent' && (
                  <Avatar size={28} icon={<MessageOutlined />} className="message-avatar agent" />
                )}
                <div className={`message ${item.role}`}>
                  <Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{item.content}</Paragraph>
                </div>
                {item.role === 'user' && (
                  <Avatar size={28} icon={<UserOutlined />} className="message-avatar user" />
                )}
              </div>
            ))}
          </div>

          {pending && (
            <div className="confirmation-panel">
              <div className="confirmation-head">
                <Space>
                  <SafetyCertificateOutlined />
                  <Text strong>待确认写操作</Text>
                </Space>
                <Tag color="orange">需要人工确认</Tag>
              </div>
              <Text>{pending.actionSummary}</Text>
              <PayloadSummary payload={pending.payload} />
              <Space>
                <Button icon={<CloseOutlined />} loading={loading} onClick={reject}>
                  拒绝
                </Button>
                <Button type="primary" icon={<CheckCircleOutlined />} loading={loading} onClick={confirm}>
                  确认写入 CRM
                </Button>
              </Space>
            </div>
          )}

          {lastResponse && (
            <div className="structured-result-card">
              <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                <div>
                  <Text strong>{resultTitle}</Text>
                  <Paragraph className="metric-label" ellipsis={{ rows: 4 }} style={{ marginBottom: 0 }}>
                    {lastResponse.answer}
                  </Paragraph>
                </div>
                <Tag color={resultTone(lastResponse)}>
                  {lastResponse.type === 'confirmation_required' ? '待确认' : '已完成'}
                </Tag>
              </Space>
              <div className="result-fact-grid">
                <div className="result-fact">
                  <span>Run</span>
                  <strong>#{lastResponse.runId}</strong>
                </div>
                <div className="result-fact">
                  <span>Session</span>
                  <strong>{lastResponse.sessionId}</strong>
                </div>
                <div className="result-fact">
                  <span>工具调用</span>
                  <strong>{lastResponse.toolCalls.length}</strong>
                </div>
                <div className="result-fact">
                  <span>写入保护</span>
                  <strong>{lastResponse.type === 'confirmation_required' ? '需要确认' : '无需写入'}</strong>
                </div>
              </div>
              <div className="result-next-action">
                <Text strong>建议下一步</Text>
                <span>{nextActionText(lastResponse)}</span>
              </div>
              <div className="result-tool-strip">
                {lastResponse.toolCalls.length === 0 ? (
                  <Tag>无工具调用</Tag>
                ) : (
                  lastResponse.toolCalls.map((tool) => (
                    <Tag key={tool.id} color={toolColor(tool)}>
                      {tool.toolName} · {tool.status}
                    </Tag>
                  ))
                )}
              </div>
            </div>
          )}

          <div className="composer">
            <TextArea
              rows={3}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onPressEnter={(event) => {
                if (!event.shiftKey) {
                  event.preventDefault();
                  submit();
                }
              }}
              placeholder="输入销售作业问题，例如：帮我分析美家房产明天怎么跟进"
            />
            <Button type="primary" icon={<SendOutlined />} loading={loading} onClick={() => submit()}>
              发送
            </Button>
          </div>
        </Card>

        <Card className="command-card agent-inspector" title="本轮证据与确认中心">
          <div className="pending-center">
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <Text strong>待确认写入</Text>
              <Tag color={pendingConfirmations.length ? 'orange' : 'green'}>{pendingConfirmations.length}</Tag>
            </Space>
            {pendingConfirmations.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待确认写操作" />
            ) : (
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                {pendingConfirmations.slice(0, 4).map((item) => (
                  <div className="pending-item" key={item.id}>
                    <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                      <div>
                        <Text strong>{item.actionSummary}</Text>
                        <div className="metric-label">{item.actionType} · Run #{item.runId}</div>
                      </div>
                      <Tag color="orange">{item.status}</Tag>
                    </Space>
                    <PayloadSummary payload={item.payloadJson} />
                    <Space>
                      <Button size="small" loading={loading} onClick={() => rejectFromCenter(item.id)}>拒绝</Button>
                      <Button size="small" type="primary" loading={loading} onClick={() => confirmFromCenter(item.id)}>
                        确认
                      </Button>
                    </Space>
                  </div>
                ))}
              </Space>
            )}
          </div>

          <div className="inspector-divider" />

          {toolCalls.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="发送问题后显示工具调用、确认单和审计入口" />
          ) : (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              {toolCalls.map((tool) => (
                <div className="tool-call-item" key={tool.id}>
                  <Space>
                    <ToolOutlined />
                    <Text strong>{tool.toolName}</Text>
                  </Space>
                  <Space size={6} wrap>
                    <Tag color={toolColor(tool)}>{tool.toolType}</Tag>
                    <Tag>{tool.status}</Tag>
                    {tool.requiresConfirmation && <Tag color="orange">confirmation</Tag>}
                  </Space>
                </div>
              ))}
            </Space>
          )}
          <Descriptions column={1} size="small" bordered className="inspector-meta">
            <Descriptions.Item label="Session">{sessionId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="确认单">{pending?.confirmationId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="主流程">工具路由 + 规则兜底</Descriptions.Item>
          </Descriptions>
          <Link to="/runs">
            <Button block icon={<FileSearchOutlined />} style={{ marginTop: 12 }}>
              查看运行审计
            </Button>
          </Link>
        </Card>
      </div>
    </Space>
  );
}
