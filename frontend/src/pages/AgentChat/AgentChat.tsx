import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseOutlined,
  SendOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  ToolOutlined
} from '@ant-design/icons';
import {
  Alert,
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
import { useState } from 'react';
import {
  AgentChatResponse,
  confirmAgentAction,
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
    prompt: '今天我应该优先跟进哪些客户？请给出推荐理由和下一步动作。'
  },
  {
    title: '客户 360 分析',
    prompt: '帮我分析美家房产，重点看续费风险、历史异议和下一步跟进策略。'
  },
  {
    title: '创建跟进任务',
    prompt: '帮我创建明天上午10点跟进美家房产续费的任务。'
  },
  {
    title: '知识库问答',
    prompt: '客户嫌套餐贵并担心续费效果时，销售应该怎么回复？'
  }
];

const demoHints = [
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

export default function AgentChat() {
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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

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
      const response = await sendAgentMessage(userText, sessionId);
      setSessionId(response.sessionId);
      setToolCalls(response.toolCalls);
      setPending(response.type === 'confirmation_required' ? response : null);
      setMessages((items) => [...items, { role: 'agent', content: response.answer }]);
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
    } catch {
      setError('拒绝确认失败，请检查后端服务。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="agent-layout">
      <Space direction="vertical" size={16} className="agent-sidebar">
        <Card title="演示场景" className="command-card">
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            {scenarioPrompts.map((item) => (
              <Button
                key={item.title}
                block
                className="scenario-button"
                onClick={() => setInput(item.prompt)}
              >
                {item.title}
              </Button>
            ))}
          </Space>
        </Card>

        <Card title="客户上下文" className="command-card">
          <List
            size="small"
            dataSource={demoHints}
            renderItem={(item) => (
              <List.Item>
                <Text>{item}</Text>
              </List.Item>
            )}
          />
        </Card>

        <Card title="Agent 安全边界" className="command-card">
          <Timeline
            items={[
              { dot: <SearchOutlined />, children: '读工具直接执行，并记录工具调用轨迹。' },
              { dot: <ClockCircleOutlined />, children: '写工具先生成 confirmation，等待人工确认。' },
              { dot: <CheckCircleOutlined />, children: '确认后才写入 CRM，并记录审计事件。' }
            ]}
          />
        </Card>
      </Space>

      <Card className="chat-panel command-card" title="Agent 对话">
        {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 12 }} />}
        <div className="chat-window">
          {messages.map((item, index) => (
            <div className={`message ${item.role}`} key={`${item.role}-${index}`}>
              <Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{item.content}</Paragraph>
            </div>
          ))}
        </div>

        {pending && (
          <div className="confirmation-panel">
            <div className="confirmation-head">
              <Space>
                <SafetyCertificateOutlined />
                <Text strong>写操作确认</Text>
              </Space>
              <Tag color="orange">需要人工确认</Tag>
            </div>
            <Text>{pending.actionSummary}</Text>
            {pending.payload && (
              <pre className="json-preview">{JSON.stringify(pending.payload, null, 2)}</pre>
            )}
            <Space>
              <Button icon={<CloseOutlined />} loading={loading} onClick={reject}>
                拒绝
              </Button>
              <Button type="primary" icon={<CheckCircleOutlined />} loading={loading} onClick={confirm}>
                确认执行
              </Button>
            </Space>
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

      <Card className="command-card agent-inspector" title="工具调用轨迹">
        {toolCalls.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="发送问题后显示本轮工具调用" />
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
          <Descriptions.Item label="模式">LLM Tool Calling + Rule Fallback</Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  );
}
