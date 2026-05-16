import {
  ApiOutlined,
  AuditOutlined,
  ClusterOutlined,
  DatabaseOutlined,
  LockOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import { Alert, Button, Card, Col, Descriptions, Empty, Row, Space, Statistic, Table, Tag, Timeline, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  EventStatus,
  KnowledgeStatus,
  ModelStatus,
  OpenAiToolDefinition,
  SecurityStatus,
  fetchEventStatus,
  fetchKnowledgeStatus,
  fetchModelStatus,
  fetchOpenAiTools,
  fetchSecurityStatus
} from '../../api/client';

const { Paragraph, Text, Title } = Typography;

type CapabilityRow = {
  key: string;
  name: string;
  status: string;
  owner: string;
  why: string;
  interviewLine: string;
};

function statusTag(value?: string | boolean) {
  if (value === true) {
    return <Tag color="green">已开启</Tag>;
  }
  if (value === false) {
    return <Tag color="orange">未开启</Tag>;
  }
  const text = value || '-';
  const color = ['ACTIVE', 'READY', 'pgvector', 'strict', 'kafka'].includes(String(text)) ? 'green' : 'blue';
  return <Tag color={color}>{String(text)}</Tag>;
}

export default function SystemAdmin() {
  const [securityStatus, setSecurityStatus] = useState<SecurityStatus | null>(null);
  const [eventStatus, setEventStatus] = useState<EventStatus | null>(null);
  const [knowledgeStatus, setKnowledgeStatus] = useState<KnowledgeStatus | null>(null);
  const [modelStatus, setModelStatus] = useState<ModelStatus | null>(null);
  const [tools, setTools] = useState<OpenAiToolDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    const results = await Promise.allSettled([
      fetchSecurityStatus(),
      fetchEventStatus(),
      fetchKnowledgeStatus(),
      fetchModelStatus(),
      fetchOpenAiTools()
    ]);
    if (results[0].status === 'fulfilled') setSecurityStatus(results[0].value);
    if (results[1].status === 'fulfilled') setEventStatus(results[1].value);
    if (results[2].status === 'fulfilled') setKnowledgeStatus(results[2].value);
    if (results[3].status === 'fulfilled') setModelStatus(results[3].value);
    if (results[4].status === 'fulfilled') setTools(results[4].value);
    if (results.some((result) => result.status === 'rejected')) {
      setError('部分系统状态读取失败，请确认后端已经启动并且当前 token 具备系统管理权限。');
    }
    setLoading(false);
  };

  useEffect(() => {
    load();
  }, []);

  const writeToolCount = useMemo(
    () => tools.filter((tool) => ['createFollowupTask', 'writeContactLog', 'updateLeadStage'].includes(tool.function.name)).length,
    [tools]
  );

  const capabilityRows: CapabilityRow[] = [
    {
      key: 'rate-limit',
      name: '接口限流',
      status: securityStatus?.rateLimit?.enabled ? 'ACTIVE' : 'DISABLED',
      owner: 'Spring Security Filter',
      why: '防止 Agent Chat 和模型接口被刷爆，控制模型调用费用和数据库压力。',
      interviewLine: '我把普通 API、Agent Chat、Model Chat 分成不同 token bucket，超限返回 429。'
    },
    {
      key: 'rbac',
      name: 'RBAC Token',
      status: securityStatus?.rbacEnabled ? 'ACTIVE' : 'DEMO_FALLBACK',
      owner: 'agentpilot_user / role / permission',
      why: '让不同销售拿到不同 salesRepId 和权限集，避免 A 销售看到 B 销售客户。',
      interviewLine: '真实 token 只存 SHA-256，登录后从数据库加载 userId、salesRepId 和权限。'
    },
    {
      key: 'outbox-lock',
      name: 'Outbox CAS 分发锁',
      status: eventStatus ? 'READY' : 'UNKNOWN',
      owner: 'agent_outbox_event',
      why: '避免 afterCommit 和定时任务同时发送同一事件，降低重复分发风险。',
      interviewLine: '分发前先把事件 CAS 抢占到 DISPATCHING，只有抢到锁的 worker 能发。'
    },
    {
      key: 'dead-letter',
      name: 'Outbox DLQ',
      status: (eventStatus?.outboxDeadLetters ?? 0) > 0 ? 'HAS_DEAD' : 'READY',
      owner: '事件后台任务',
      why: 'Kafka 或下游故障时，失败事件不会悄悄丢掉，超过重试次数进入死信。',
      interviewLine: '当前是 at-least-once，失败满 5 次进入 DEAD_LETTER，可以人工重试。'
    },
    {
      key: 'tool-schema',
      name: 'LLM Tool Schema',
      status: `${tools.length} tools`,
      owner: 'Tool Registry',
      why: '让模型只能从后端允许的工具中选择，写工具统一进入 confirmation。',
      interviewLine: `工具 schema 由后端暴露，当前 ${writeToolCount} 个写工具需要人工确认。`
    },
    {
      key: 'vector-store',
      name: 'pgvector RAG',
      status: knowledgeStatus?.vectorStoreMode ?? 'unknown',
      owner: 'Knowledge Service',
      why: '用真实 embedding 和向量库做知识检索，回答带引用，低置信拒答。',
      interviewLine: 'RAG 不是让模型凭空答，而是先检索销售 SOP 和政策，再基于引用回答。'
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <Card
        className="command-card"
        title="系统能力总览"
        extra={
          <Button icon={<ReloadOutlined />} loading={loading} onClick={load}>
            刷新
          </Button>
        }
      >
        <Paragraph style={{ marginBottom: 0 }}>
          这个页面只服务系统管理员和面试讲解：把限流、RBAC、Outbox、Tool Schema、RAG 这些生产化能力集中到一处，
          方便判断系统是否可控、可追踪、可恢复。
        </Paragraph>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={6}>
          <Card className="metric-card">
            <Statistic title="RBAC 用户" value={securityStatus?.rbacUserCount ?? 0} prefix={<SafetyCertificateOutlined />} />
            <Text className="metric-label">数据库 token 用户</Text>
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card className="metric-card">
            <Statistic title="限流桶" value={securityStatus?.rateLimit?.enabled ? 3 : 0} prefix={<ApiOutlined />} />
            <Text className="metric-label">default / agent / model</Text>
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card className="metric-card">
            <Statistic title="Outbox 待分发" value={eventStatus?.outboxPending ?? 0} prefix={<AuditOutlined />} />
            <Text className="metric-label">PENDING / FAILED</Text>
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card className="metric-card">
            <Statistic title="死信事件" value={eventStatus?.outboxDeadLetters ?? 0} prefix={<ThunderboltOutlined />} />
            <Text className="metric-label">需要人工重试</Text>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card className="command-card" title="产品化能力清单">
            <Table
              rowKey="key"
              loading={loading}
              pagination={false}
              dataSource={capabilityRows}
              columns={[
                { title: '能力', dataIndex: 'name', width: 160 },
                { title: '状态', dataIndex: 'status', width: 140, render: (value) => statusTag(value) },
                { title: '落点', dataIndex: 'owner', width: 220 },
                { title: '为什么要做', dataIndex: 'why' },
                {
                  title: '面试讲法',
                  dataIndex: 'interviewLine',
                  render: (value) => <Text type="secondary">{value}</Text>
                }
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card className="command-card" title="RBAC 与限流">
              <Descriptions size="small" bordered column={1}>
                <Descriptions.Item label="权限模式">
                  <Space>
                    {securityStatus?.strict ? <Tag color="green">strict</Tag> : <Tag color="orange">permissive</Tag>}
                    {securityStatus?.rbacEnabled ? <Tag color="blue">RBAC</Tag> : <Tag>demo fallback</Tag>}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="角色 / 用户">
                  {securityStatus?.rbacRoleCount ?? 0} roles / {securityStatus?.rbacUserCount ?? 0} users
                </Descriptions.Item>
                <Descriptions.Item label="Agent Chat">
                  {securityStatus?.rateLimit?.agentCapacity ?? '-'} / min
                </Descriptions.Item>
                <Descriptions.Item label="Model Chat">
                  {securityStatus?.rateLimit?.modelCapacity ?? '-'} / min
                </Descriptions.Item>
                <Descriptions.Item label="普通 API">
                  {securityStatus?.rateLimit?.defaultCapacity ?? '-'} / min
                </Descriptions.Item>
              </Descriptions>
            </Card>

            <Card className="command-card" title="Outbox 分发状态">
              <Descriptions size="small" bordered column={1}>
                <Descriptions.Item label="模式">{statusTag(eventStatus?.mode ?? 'log-only')}</Descriptions.Item>
                <Descriptions.Item label="待分发">{eventStatus?.outboxPending ?? 0}</Descriptions.Item>
                <Descriptions.Item label="分发中">{eventStatus?.outboxDispatching ?? 0}</Descriptions.Item>
                <Descriptions.Item label="死信">{eventStatus?.outboxDeadLetters ?? 0}</Descriptions.Item>
                <Descriptions.Item label="最大重试">{eventStatus?.maxRetryCount ?? 5}</Descriptions.Item>
              </Descriptions>
            </Card>
          </Space>
        </Col>
      </Row>

      <Card className="command-card" title="面试时怎么讲这三件事">
        <Timeline
          items={[
            {
              dot: <ApiOutlined />,
              children: (
                <Space direction="vertical" size={2}>
                  <Text strong>为什么要限流？</Text>
                  <Text type="secondary">
                    Agent Chat 和 Model Chat 会调用模型，成本和延迟都比普通接口高。限流可以防止恶意刷接口或误操作把模型费用、数据库连接打爆。
                  </Text>
                </Space>
              )
            },
            {
              dot: <ClusterOutlined />,
              children: (
                <Space direction="vertical" size={2}>
                  <Text strong>Outbox 为什么要 CAS？</Text>
                  <Text type="secondary">
                    afterCommit 和定时补偿任务可能同时看到同一条 PENDING 事件。CAS 先抢占成 DISPATCHING，只有抢到锁的 worker 发送，减少重复分发。
                  </Text>
                </Space>
              )
            },
            {
              dot: <LockOutlined />,
              children: (
                <Space direction="vertical" size={2}>
                  <Text strong>RBAC token 和 demo token 有什么区别？</Text>
                  <Text type="secondary">
                    demo token 是本地演示兜底；RBAC token 来自数据库，token 只存 SHA-256，认证后加载真实用户、销售归属和权限集合。
                  </Text>
                </Space>
              )
            },
            {
              dot: <DatabaseOutlined />,
              children: (
                <Space direction="vertical" size={2}>
                  <Text strong>这些能力怎么和业务连起来？</Text>
                  <Text type="secondary">
                    RBAC 限制销售只能看自己的客户，限流保护模型和数据库，Outbox 保证 CRM 写入后的事件可重试可恢复。
                  </Text>
                </Space>
              )
            }
          ]}
        />
      </Card>

      <Card className="command-card" title="Tool Schema 概览">
        {tools.length ? (
          <Table
            rowKey={(record) => record.function.name}
            size="small"
            pagination={false}
            dataSource={tools}
            columns={[
              { title: '工具名', render: (_, record) => <Text code>{record.function.name}</Text> },
              { title: '用途', render: (_, record) => record.function.description },
              {
                title: '写入风险',
                width: 120,
                render: (_, record) =>
                  ['createFollowupTask', 'writeContactLog', 'updateLeadStage'].includes(record.function.name) ? (
                    <Tag color="orange">需确认</Tag>
                  ) : (
                    <Tag color="green">只读</Tag>
                  )
              }
            ]}
          />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="后端启动后展示工具 schema" />
        )}
      </Card>

      <Card className="command-card" title="模型与向量检索">
        <Descriptions size="small" bordered column={2}>
          <Descriptions.Item label="模型">{modelStatus?.model ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="模型模式">{modelStatus?.mode ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="Embedding">{modelStatus?.embedding?.model ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="向量维度">{modelStatus?.embedding?.dimension ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="向量库">{knowledgeStatus?.vectorStoreMode ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="向量化 Chunk">
            {knowledgeStatus?.vectorizedChunkCount ?? 0} / {knowledgeStatus?.chunkCount ?? 0}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
}
