import {
  BranchesOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  LoadingOutlined,
  MessageOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
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
  Empty,
  Input,
  List,
  Space,
  Tag,
  Timeline,
  Tooltip,
  Typography,
  message as antdMessage
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  AgentChatResponse,
  AgentConfirmation,
  AgentExecutionStep,
  AgentExecutionTrace,
  confirmAgentAction,
  fetchAgentConfirmationPage,
  fetchAgentExecutionTrace,
  rejectAgentAction,
  sendAgentMessage,
  ToolCallView,
  describeApiError
} from '../../api/client';
import ApiErrorNotice from '../../components/ApiErrorNotice';

const { Paragraph, Text, Title } = Typography;
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

const statusMeta: Record<string, { label: string; color: string; className: string }> = {
  COMPLETED: { label: '已完成', color: 'green', className: 'completed' },
  RUNNING: { label: '执行中', color: 'blue', className: 'running' },
  WAITING: { label: '待确认', color: 'orange', className: 'waiting' },
  FAILED: { label: '失败', color: 'red', className: 'failed' },
  REJECTED: { label: '已拒绝', color: 'default', className: 'rejected' }
};

const stageText: Record<string, string> = {
  WAITING_CONFIRMATION: '等待人工确认',
  WRITING_CRM: '正在写入 CRM',
  COMPLETED: '已完成',
  FAILED: '执行失败'
};

const payloadLabels: Record<string, string> = {
  customerId: '客户 ID',
  leadId: '商机 ID',
  salesRepId: '销售 ID',
  title: '标题',
  content: '内容',
  dueTime: '跟进时间',
  stage: '目标阶段',
  channel: '渠道',
  summary: '摘要',
  nextAction: '下一步',
  idempotencyKey: '幂等键'
};

function toolColor(tool: ToolCallView) {
  return tool.toolType === 'WRITE' ? 'orange' : 'green';
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
  return Object.entries(payload)
    .filter(([, item]) => item !== undefined && item !== null && item !== '')
    .slice(0, 8)
    .map(([key, item]) => ({ label: payloadLabels[key] ?? key, value: String(item) }));
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
  return 'AI 处理结果';
}

function resultTone(response: AgentChatResponse | null) {
  if (!response) {
    return 'default';
  }
  if (response.type === 'confirmation_required') {
    return 'orange';
  }
  const title = inferResultTitle(response);
  if (title.includes('商机')) {
    return 'red';
  }
  if (title.includes('知识')) {
    return 'blue';
  }
  return 'green';
}

function nextActionText(response: AgentChatResponse | null) {
  if (!response) {
    return '发送一个销售作业问题后，这里会沉淀结构化结果。';
  }
  if (response.type === 'confirmation_required') {
    return '核对确认单字段，确认后才会写入 CRM；拒绝则不会修改业务数据。';
  }
  const title = inferResultTitle(response);
  if (title.includes('商机')) {
    return '进入客户 360 补齐上下文，再让 AI 助手生成具体跟进任务。';
  }
  if (title.includes('知识')) {
    return '把命中的 SOP 或政策引用带入客户沟通话术。';
  }
  if (title.includes('客户')) {
    return '基于客户风险和异议生成下一次跟进动作。';
  }
  return '继续补充客户或商机上下文，让 AI 助手生成更具体动作。';
}

function formatJson(value?: string) {
  if (!value) {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function stepIcon(step: AgentExecutionStep) {
  if (step.status === 'RUNNING') {
    return <LoadingOutlined />;
  }
  if (step.category === 'REQUEST') {
    return <MessageOutlined />;
  }
  if (step.category === 'ROUTING') {
    return <BranchesOutlined />;
  }
  if (step.category === 'CONFIRMATION') {
    return <SafetyCertificateOutlined />;
  }
  if (step.category === 'OUTPUT') {
    return <CheckCircleOutlined />;
  }
  return step.toolType === 'WRITE' ? <DatabaseOutlined /> : <ToolOutlined />;
}

function fallbackSteps(isRunning: boolean): AgentExecutionStep[] {
  return [
    {
      key: 'receive',
      title: '接收销售问题',
      description: '读取用户输入和业务上下文',
      status: isRunning ? 'COMPLETED' : 'WAITING',
      category: 'REQUEST'
    },
    {
      key: 'route',
      title: '选择执行路径',
      description: '智能选择处理方式',
      status: isRunning ? 'RUNNING' : 'WAITING',
      category: 'ROUTING'
    },
    {
      key: 'tool',
      title: '执行工具',
      description: '查询客户、知识库或生成确认单',
      status: 'WAITING',
      category: 'TOOL'
    },
    {
      key: 'output',
      title: '输出结果',
      description: '返回建议或等待人工确认',
      status: 'WAITING',
      category: 'OUTPUT'
    }
  ];
}

function routingModeLabel(value?: string) {
  if (!value) {
    return '等待处理';
  }
  if (value.includes('LLM') || value.includes('Tool')) {
    return '智能选择工具';
  }
  if (value.includes('规则')) {
    return '稳定规则处理';
  }
  return value;
}

function ExecutionProcess({
  trace,
  loading
}: {
  trace: AgentExecutionTrace | null;
  loading: boolean;
}) {
  const steps = trace?.steps?.length ? trace.steps : fallbackSteps(loading);
  const visibleSteps = steps.slice(0, 6);

  return (
    <div className={`agent-execution-board ${loading ? 'is-running' : ''}`}>
      <div className="execution-board-head">
        <div>
          <Text strong>AI 执行过程</Text>
          <div className="metric-label">
            {trace
              ? `${routingModeLabel(trace.routingMode)} · ${stageText[trace.currentStage] ?? trace.currentStage}`
              : '等待发送问题'}
          </div>
        </div>
        <Tag color={trace?.requiresConfirmation ? 'orange' : loading ? 'blue' : 'green'}>
          {trace?.requiresConfirmation ? '需要确认' : loading ? '执行中' : '安全可追溯'}
        </Tag>
      </div>
      <div className="execution-path-grid">
        {visibleSteps.map((step, index) => {
          const meta = statusMeta[step.status] ?? statusMeta.WAITING;
          return (
            <div className={`execution-stage-card ${meta.className}`} key={step.key}>
              <div className="execution-stage-top">
                <span className="execution-stage-index">{index + 1}</span>
                <span className="execution-stage-icon">{stepIcon(step)}</span>
              </div>
              <Text strong>{step.title}</Text>
              <span>{step.description}</span>
              <Space size={6} wrap>
                <Tag color={meta.color}>{meta.label}</Tag>
                {step.toolType && <Tag color={step.toolType === 'WRITE' ? 'orange' : 'green'}>{step.toolType}</Tag>}
                {step.latencyMs !== undefined && step.latencyMs !== null && <Tag>{step.latencyMs} ms</Tag>}
              </Space>
            </div>
          );
        })}
      </div>
      {trace && (
        <div className="execution-safety-note">
          <SafetyCertificateOutlined />
          <span>{trace.safetyBoundary}</span>
        </div>
      )}
    </div>
  );
}

function TraceEvidence({ trace }: { trace: AgentExecutionTrace | null }) {
  if (!trace) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="发送问题后显示真实执行轨迹、工具输入输出和确认单状态"
      />
    );
  }

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <div className="trace-summary-strip">
        <div>
          <span>流水号</span>
          <strong>#{trace.run.id}</strong>
        </div>
        <div>
          <span>处理方式</span>
          <strong>{routingModeLabel(trace.routingMode)}</strong>
        </div>
        <div>
          <span>动作</span>
          <strong>{trace.toolCalls.length}</strong>
        </div>
        <div>
          <span>确认单</span>
          <strong>{trace.confirmations.length}</strong>
        </div>
      </div>
      <Timeline
        className="trace-timeline"
        items={trace.steps.map((step) => {
          const meta = statusMeta[step.status] ?? statusMeta.WAITING;
          return {
            dot: stepIcon(step),
            color: meta.color,
            children: (
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                <Space size={6} wrap>
                  <Text strong>{step.title}</Text>
                  <Tag color={meta.color}>{meta.label}</Tag>
                  {step.toolName && <Tag>{step.toolName}</Tag>}
                </Space>
                <Text type="secondary">{step.description}</Text>
                {(step.inputJson || step.outputJson) && (
                  <details className="trace-json-details">
                    <summary>查看执行明细</summary>
                    {step.inputJson && (
                      <>
                        <Text strong>输入</Text>
                        <pre className="json-preview compact">{formatJson(step.inputJson)}</pre>
                      </>
                    )}
                    {step.outputJson && (
                      <>
                        <Text strong>输出</Text>
                        <pre className="json-preview compact">{formatJson(step.outputJson)}</pre>
                      </>
                    )}
                  </details>
                )}
              </Space>
            )
          };
        })}
      />
    </Space>
  );
}

export default function AgentChat() {
  const [searchParams] = useSearchParams();
  const [sessionId, setSessionId] = useState<number | undefined>();
  const [input, setInput] = useState(scenarioPrompts[0].prompt);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: 'agent',
      content:
        '我可以帮你分析客户、推荐商机、检索销售知识库，也可以在你确认后创建 CRM 跟进任务。'
    }
  ]);
  const [toolCalls, setToolCalls] = useState<ToolCallView[]>([]);
  const [pending, setPending] = useState<AgentChatResponse | null>(null);
  const [lastResponse, setLastResponse] = useState<AgentChatResponse | null>(null);
  const [executionTrace, setExecutionTrace] = useState<AgentExecutionTrace | null>(null);
  const [pendingConfirmations, setPendingConfirmations] = useState<AgentConfirmation[]>([]);
  const [pendingConfirmationTotal, setPendingConfirmationTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [traceLoading, setTraceLoading] = useState(false);
  const [error, setError] = useState('');

  const contextPrompt = searchParams.get('prompt');
  const contextSource = searchParams.get('source');
  const contextCustomerId = Number(searchParams.get('customerId'));
  const resolvedCustomerId = Number.isFinite(contextCustomerId) && contextCustomerId > 0 ? contextCustomerId : undefined;

  const resultTitle = useMemo(() => inferResultTitle(lastResponse), [lastResponse]);

  const refreshConfirmations = async () => {
    try {
      const data = await fetchAgentConfirmationPage({ status: 'PENDING', page: 1, pageSize: 4 });
      setPendingConfirmations(data.items);
      setPendingConfirmationTotal(data.total);
    } catch {
      setPendingConfirmations([]);
      setPendingConfirmationTotal(0);
    }
  };

  const loadExecutionTrace = async (runId: number) => {
    setTraceLoading(true);
    try {
      const trace = await fetchAgentExecutionTrace(runId);
      setExecutionTrace(trace);
    } catch {
      setExecutionTrace(null);
    } finally {
      setTraceLoading(false);
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

  const submit = async (text?: string) => {
    const userText = (text ?? input).trim();
    if (!userText) {
      return;
    }
    setMessages((items) => [...items, { role: 'user', content: userText }]);
    setInput('');
    setLoading(true);
    setError('');
    setExecutionTrace(null);
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
      await Promise.all([refreshConfirmations(), loadExecutionTrace(response.runId)]);
    } catch (err) {
      setError(describeApiError(err));
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
      const runId = pending.runId;
      await confirmAgentAction(pending.confirmationId);
      antdMessage.success('已确认，CRM 写操作已执行');
      setMessages((items) => [...items, { role: 'agent', content: '已确认并完成 CRM 写入。' }]);
      setPending(null);
      await Promise.all([refreshConfirmations(), loadExecutionTrace(runId)]);
    } catch (err) {
      setError(describeApiError(err));
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
      const runId = pending.runId;
      await rejectAgentAction(pending.confirmationId);
      antdMessage.info('已拒绝，本次写操作未执行');
      setMessages((items) => [...items, { role: 'agent', content: '已取消写入，CRM 数据没有变化。' }]);
      setPending(null);
      await Promise.all([refreshConfirmations(), loadExecutionTrace(runId)]);
    } catch (err) {
      setError(describeApiError(err));
    } finally {
      setLoading(false);
    }
  };

  const confirmFromCenter = async (confirmation: AgentConfirmation) => {
    setLoading(true);
    setError('');
    try {
      await confirmAgentAction(confirmation.id);
      antdMessage.success('已确认，CRM 写操作已执行');
      if (pending?.confirmationId === confirmation.id) {
        setPending(null);
      }
      await Promise.all([refreshConfirmations(), loadExecutionTrace(confirmation.runId)]);
    } catch (err) {
      setError(describeApiError(err));
    } finally {
      setLoading(false);
    }
  };

  const rejectFromCenter = async (confirmation: AgentConfirmation) => {
    setLoading(true);
    setError('');
    try {
      await rejectAgentAction(confirmation.id);
      antdMessage.info('已拒绝，本次写操作未执行');
      if (pending?.confirmationId === confirmation.id) {
        setPending(null);
      }
      await Promise.all([refreshConfirmations(), loadExecutionTrace(confirmation.runId)]);
    } catch (err) {
      setError(describeApiError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div className="workflow-hero">
        <div>
          <Text className="eyebrow">AI Sales Assistant</Text>
          <Title level={4}>把客户判断变成可确认的 CRM 动作</Title>
          <Paragraph className="overview-copy">
            从客户或商机页面带着上下文进入，让 AI 助手分析客户、检索知识、生成任务建议；凡是写 CRM 的动作都会先进入人工确认。
          </Paragraph>
        </div>
        <div className="workflow-stepper">
          <Link to="/customers" className="mini-flow-node">
            <TeamOutlined />
            客户
          </Link>
          <Link to="/leads" className="mini-flow-node">
            <ThunderboltOutlined />
            商机
          </Link>
          <span className="mini-flow-node active">
            <MessageOutlined />
            AI 助手
          </span>
          <span className="mini-flow-node">
            <SafetyCertificateOutlined />
            确认
          </span>
          <Link to="/runs" className="mini-flow-node muted">
            <ToolOutlined />
            审计
          </Link>
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
                <Button key={item.title} block className="scenario-button" onClick={() => setInput(item.prompt)}>
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
                { dot: <ToolOutlined />, children: '读取信息：客户、商机、知识库直接查询事实。' },
                { dot: <ClockCircleOutlined />, children: '写入动作：只生成确认单，不直接落库。' },
                { dot: <CheckCircleOutlined />, children: '确认后：写入 CRM，并进入运行审计。' }
              ]}
            />
          </Card>
        </Space>

        <Card
          className="chat-panel command-card"
          title="AI 助手对话"
          extra={
            <Space>
              <Link to="/customers">
                <Button size="small" icon={<TeamOutlined />}>
                  客户
                </Button>
              </Link>
              <Link to="/leads">
                <Button size="small" icon={<ThunderboltOutlined />}>
                  商机
                </Button>
              </Link>
            </Space>
          }
        >
          {error && <ApiErrorNotice error={error} title="AI 助手暂时无法完成操作" />}
          <div className="agent-session-strip">
            <div>
              <span>Session</span>
              <strong>{sessionId ?? 'new'}</strong>
            </div>
            <div>
              <span>客户上下文</span>
              <strong>{resolvedCustomerId ? `#${resolvedCustomerId}` : '未指定'}</strong>
            </div>
            <div>
              <span>工具调用</span>
              <strong>{executionTrace?.toolCalls.length ?? toolCalls.length}</strong>
            </div>
            <div>
              <span>待确认</span>
              <strong>{pendingConfirmationTotal}</strong>
            </div>
          </div>

          <ExecutionProcess trace={executionTrace} loading={loading || traceLoading} />

          <div className="chat-window">
            {messages.map((item, index) => (
              <div className={`message-row ${item.role}`} key={`${item.role}-${index}`}>
                {item.role === 'agent' && <Avatar size={28} icon={<MessageOutlined />} className="message-avatar agent" />}
                <div className={`message ${item.role}`}>
                  <Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{item.content}</Paragraph>
                </div>
                {item.role === 'user' && <Avatar size={28} icon={<UserOutlined />} className="message-avatar user" />}
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
                  <span>处理流水</span>
                  <strong>#{lastResponse.runId}</strong>
                </div>
                <div className="result-fact">
                  <span>Session</span>
                  <strong>{lastResponse.sessionId}</strong>
                </div>
                <div className="result-fact">
                  <span>处理方式</span>
                  <strong>{routingModeLabel(executionTrace?.routingMode)}</strong>
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

        <Card className="command-card agent-inspector" title="执行证据与确认中心">
          <div className="pending-center">
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <Text strong>待确认写入</Text>
              <Tag color={pendingConfirmationTotal ? 'orange' : 'green'}>{pendingConfirmationTotal}</Tag>
            </Space>
            {pendingConfirmationTotal === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待确认写操作" />
            ) : (
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                {pendingConfirmations.map((item) => (
                  <div className="pending-item" key={item.id}>
                    <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                      <div>
                        <Text strong>{item.actionSummary}</Text>
                        <div className="metric-label">
                          {item.actionType} · 处理流水 #{item.runId}
                        </div>
                      </div>
                      <Tag color="orange">{item.status}</Tag>
                    </Space>
                    <PayloadSummary payload={item.payloadJson} />
                    <Space>
                      <Button size="small" loading={loading} onClick={() => rejectFromCenter(item)}>
                        拒绝
                      </Button>
                      <Button size="small" type="primary" loading={loading} onClick={() => confirmFromCenter(item)}>
                        确认
                      </Button>
                    </Space>
                  </div>
                ))}
              </Space>
            )}
          </div>

          <div className="inspector-divider" />

          <TraceEvidence trace={executionTrace} />

          <Link to="/runs">
            <Tooltip title="进入系统管理视角查看完整处理记录、工具调用和输入输出">
              <Button block icon={<FileSearchOutlined />} style={{ marginTop: 12 }}>
                查看处理明细
              </Button>
            </Tooltip>
          </Link>
        </Card>
      </div>
    </Space>
  );
}
