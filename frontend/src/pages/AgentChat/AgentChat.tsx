import { CheckCircleOutlined, ClockCircleOutlined, SearchOutlined, ToolOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Divider, Input, List, Space, Tag, Timeline, Typography, message as antdMessage } from 'antd';
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

const leadHints = [
  '美家房产：A 类客户，套餐 18 天后到期，近期关注续费 ROI',
  '快招人力：招聘行业高意向客户，最近 9 天未联系',
  '老街火锅：价格异议明显，适合用曝光数据和同行案例推进'
];

export default function AgentChat() {
  const [sessionId, setSessionId] = useState<number | undefined>();
  const [input, setInput] = useState('今天我应该优先跟进哪些客户？请给出推荐理由和下一步动作。');
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: 'agent',
      content: '我可以推荐今日优先跟进客户、分析具体客户，也可以在你确认后创建 CRM 跟进任务。'
    }
  ]);
  const [toolCalls, setToolCalls] = useState<ToolCallView[]>([]);
  const [pending, setPending] = useState<AgentChatResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    if (!input.trim()) {
      return;
    }
    const userText = input.trim();
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
      setError('后端未连接，无法发送 Agent 请求。请确认 Spring Boot 已启动。');
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
      <Space direction="vertical" size={16}>
        <Card title="演示客户上下文">
          <List
            size="small"
            dataSource={leadHints}
            renderItem={(item) => (
              <List.Item>
                <Text>{item}</Text>
              </List.Item>
            )}
          />
        </Card>
        <Card title="Agent 工具边界">
          <Timeline
            items={[
              { dot: <SearchOutlined />, children: '读工具直接执行，并记录工具调用轨迹' },
              { dot: <ClockCircleOutlined />, children: '写工具先生成 confirmation，等待人工确认' },
              { dot: <CheckCircleOutlined />, children: '用户确认后才写入 CRM，并保持幂等' }
            ]}
          />
        </Card>
      </Space>

      <Card className="chat-panel" title="Agent 工作台">
        {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 12 }} />}
        <div className="chat-window">
          {messages.map((item, index) => (
            <div className={`message ${item.role}`} key={`${item.role}-${index}`}>
              <Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{item.content}</Paragraph>
            </div>
          ))}
        </div>
        {pending && (
          <Alert
            type="info"
            showIcon
            style={{ marginTop: 12 }}
            message={pending.actionSummary}
            description="这是写 CRM 数据的动作，必须确认后才会真正执行。"
            action={
              <Space>
                <Button size="small" loading={loading} onClick={reject}>
                  拒绝
                </Button>
                <Button type="primary" size="small" loading={loading} onClick={confirm}>
                  确认执行
                </Button>
              </Space>
            }
          />
        )}
        <Divider orientation="left">工具调用轨迹</Divider>
        <ul className="tool-list">
          {toolCalls.map((tool) => (
            <li key={tool.id}>
              <Space>
                <ToolOutlined />
                <Text>{tool.toolName}</Text>
              </Space>
              <Space>
                <Tag color={tool.toolType === 'WRITE' ? 'orange' : 'green'}>{tool.toolType}</Tag>
                <Tag>{tool.status}</Tag>
              </Space>
            </li>
          ))}
        </ul>
        <Divider />
        <Space.Compact style={{ width: '100%' }}>
          <TextArea
            rows={2}
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="输入销售作业问题，例如：帮我分析美家房产明天怎么跟进"
          />
          <Button type="primary" style={{ height: 54 }} loading={loading} onClick={submit}>
            发送
          </Button>
        </Space.Compact>
      </Card>
    </div>
  );
}
