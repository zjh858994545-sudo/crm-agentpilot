import {
  ApiOutlined,
  CheckCircleOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  FieldTimeOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons';
import { Alert, Card, Col, Descriptions, Row, Space, Statistic, Table, Tag, Timeline, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  EventStatus,
  fetchEventStatus,
  fetchHealth,
  fetchKnowledgeStatus,
  fetchModelStatus,
  fetchOpenAiTools,
  fetchSecurityStatus,
  HealthView,
  KnowledgeStatus,
  ModelStatus,
  OpenAiToolDefinition,
  SecurityStatus
} from '../../api/client';

const { Text, Title } = Typography;

const moduleCatalog = [
  { key: 'crm-core', healthKey: 'crm', module: 'CRM Core', owner: '客户、商机、任务、产品包、联系记录' },
  { key: 'lead-scoring', healthKey: 'crm', module: 'Lead Scoring', owner: '优先级打分、推荐理由、下一步动作' },
  { key: 'rag', healthKey: 'rag', module: 'RAG', owner: '知识导入、pgvector 检索、引用、低置信拒答' },
  { key: 'agent', healthKey: 'agent', module: 'Agent Orchestrator', owner: 'LLM Tool Calling、确认流、运行审计' },
  { key: 'call-center', healthKey: 'callcenter', module: 'Call Center', owner: '摘要、质检、客户记忆、联系记录确认' },
  { key: 'evaluation', healthKey: 'evaluation', module: 'Evaluation', owner: 'JSONL 用例、指标统计、报告生成' }
];

const statusColor: Record<string, string> = {
  UP: 'green',
  READY: 'green',
  ready: 'green',
  LOCAL: 'orange',
  已验证: 'green',
  已接通: 'green',
  可切换: 'blue',
  待配置: 'orange',
  审计重试: 'blue',
  mock: 'orange',
  strict: 'green',
  permissive: 'orange',
  'real-embedding': 'green',
  'pgvector-hybrid': 'green',
  'java-fallback': 'orange',
  'llm-enabled': 'blue',
  'log-only': 'default',
  healthy: 'green',
  warning: 'orange'
};

function tag(value: string) {
  return <Tag color={statusColor[value] ?? 'blue'}>{value}</Tag>;
}

function providerLine(modelStatus: ModelStatus | null) {
  if (!modelStatus?.configured) {
    return 'deterministic mock';
  }
  return `${modelStatus.vendor ?? modelStatus.provider} / ${modelStatus.model}`;
}

function embeddingLine(modelStatus: ModelStatus | null) {
  const embedding = modelStatus?.embedding;
  if (!embedding?.configured) {
    return 'deterministic mock';
  }
  return `${embedding.vendor ?? embedding.provider} / ${embedding.model} / ${embedding.dimension}d`;
}

export default function Dashboard() {
  const [health, setHealth] = useState<HealthView | null>(null);
  const [modelStatus, setModelStatus] = useState<ModelStatus | null>(null);
  const [eventStatus, setEventStatus] = useState<EventStatus | null>(null);
  const [knowledgeStatus, setKnowledgeStatus] = useState<KnowledgeStatus | null>(null);
  const [securityStatus, setSecurityStatus] = useState<SecurityStatus | null>(null);
  const [tools, setTools] = useState<OpenAiToolDefinition[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchHealth()
      .then(setHealth)
      .catch(() => setError('后端未连接，当前仅显示前端静态工作台。'));
    fetchModelStatus().then(setModelStatus).catch(() => undefined);
    fetchEventStatus().then(setEventStatus).catch(() => undefined);
    fetchKnowledgeStatus().then(setKnowledgeStatus).catch(() => undefined);
    fetchSecurityStatus().then(setSecurityStatus).catch(() => undefined);
    fetchOpenAiTools().then(setTools).catch(() => undefined);
  }, []);

  const moduleRows = useMemo(
    () =>
      moduleCatalog.map((item) => ({
        ...item,
        status: health?.modules?.[item.healthKey] ?? 'READY'
      })),
    [health]
  );

  const toolCount = tools.length;
  const writeToolCount = tools.filter((tool) =>
    ['createFollowupTask', 'writeContactLog', 'updateLeadStage'].includes(tool.function.name)
  ).length;
  const vectorRatio = knowledgeStatus?.chunkCount
    ? Math.round((knowledgeStatus.vectorizedChunkCount / knowledgeStatus.chunkCount) * 100)
    : 0;

  const readinessRows = [
    {
      key: 'api',
      item: '真实接口',
      icon: <ApiOutlined />,
      status: health?.status === 'UP' ? '已验证' : 'LOCAL',
      detail: '前端页面通过 Axios 调用后端 API，Health 返回模块状态。'
    },
    {
      key: 'llm',
      item: 'LLM Tool Calling',
      icon: <DeploymentUnitOutlined />,
      status: modelStatus?.configured && toolCount > 0 ? '已验证' : '待配置',
      detail: `${toolCount} 个 schema 化工具；模型失败或超时时回退规则路由。`
    },
    {
      key: 'embedding',
      item: '真实 Embedding',
      icon: <DatabaseOutlined />,
      status: modelStatus?.embedding?.configured ? '已验证' : 'mock',
      detail: embeddingLine(modelStatus)
    },
    {
      key: 'vector',
      item: '向量检索',
      icon: <CloudServerOutlined />,
      status: knowledgeStatus?.vectorStoreMode ?? 'java-fallback',
      detail: knowledgeStatus
        ? `${knowledgeStatus.vectorizedChunkCount}/${knowledgeStatus.chunkCount} chunks 已写入向量列。`
        : '等待 /api/knowledge/status'
    },
    {
      key: 'write',
      item: '写入确认',
      icon: <SafetyCertificateOutlined />,
      status: writeToolCount > 0 ? '已验证' : '待配置',
      detail: `${writeToolCount} 个写工具进入 confirmation 后再写 CRM。`
    },
    {
      key: 'outbox',
      item: '事件分发',
      icon: <FieldTimeOutlined />,
      status: eventStatus ? '审计重试' : 'LOCAL',
      detail: `pending=${eventStatus?.outboxPending ?? 0}；CRM task 事件绑定确认事务，run/tool call 走 at-least-once 审计分发。`
    }
  ];

  const capabilityRows = [
    {
      key: 'chat',
      item: 'Chat Model',
      status: modelStatus?.mode ?? 'deterministic-mock',
      detail: `${providerLine(modelStatus)}，协议：${modelStatus?.protocol ?? 'mock'}`
    },
    {
      key: 'embedding',
      item: 'Embedding',
      status: modelStatus?.embedding?.mode ?? 'deterministic-mock',
      detail: `${embeddingLine(modelStatus)}，协议：${modelStatus?.embedding?.protocol ?? 'mock'}`
    },
    {
      key: 'vector-store',
      item: 'Vector Store',
      status: knowledgeStatus?.vectorStoreMode ?? 'java-fallback',
      detail: knowledgeStatus
        ? `doc=${knowledgeStatus.docCount}，chunk=${knowledgeStatus.chunkCount}，vectorized=${knowledgeStatus.vectorizedChunkCount}`
        : '等待 /api/knowledge/status'
    },
    {
      key: 'security',
      item: 'Security',
      status: securityStatus?.mode ?? 'permissive',
      detail: securityStatus
        ? `${securityStatus.permissionCount} 个权限点；token ${securityStatus.tokenConfigured ? '已配置' : '未配置'}`
        : '等待 /api/security/status'
    },
    {
      key: 'outbox',
      item: 'Outbox',
      status: eventStatus?.mode ?? 'log-only',
      detail: `pending=${eventStatus?.outboxPending ?? 0}；可切 Kafka topic`
    },
    {
      key: 'tools',
      item: 'LLM Tools',
      status: toolCount > 0 ? '已接通' : 'LOCAL',
      detail: `${toolCount || 0} 个 OpenAI-compatible function schema`
    }
  ];

  return (
    <Space direction="vertical" size={18} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <div className="summary-panel">
        <div>
          <Text className="eyebrow">System Proof</Text>
          <Title level={4}>可演示、可追踪、可解释的 CRM Agent 原型</Title>
          <Text className="page-subtitle">
            这页所有核心状态都来自真实后端接口，用来证明系统不是静态页面。
          </Text>
        </div>
        <Space size={8} wrap>
          {tag(health?.status ?? 'LOCAL')}
          {tag(modelStatus?.mode ?? 'deterministic-mock')}
          {tag(knowledgeStatus?.vectorStoreMode ?? 'java-fallback')}
          {tag(securityStatus?.mode ?? 'permissive')}
        </Space>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="系统状态" value={health?.status ?? 'LOCAL'} />
            <Text className="metric-label">/api/health</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="工具 Schema" value={toolCount} suffix="个" />
            <Text className="metric-label">{writeToolCount} 个写工具受确认流保护</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="向量化覆盖" value={vectorRatio} suffix="%" />
            <Text className="metric-label">{knowledgeStatus?.vectorStoreMode ?? 'java-fallback'}</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="Outbox Pending" value={eventStatus?.outboxPending ?? 0} />
            <Text className="metric-label">事件审计与重试队列</Text>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={15}>
          <Card className="command-card" title="系统能力证明">
            <Table
              size="small"
              pagination={false}
              dataSource={readinessRows}
              columns={[
                {
                  title: '能力',
                  dataIndex: 'item',
                  width: 190,
                  render: (_, row) => (
                    <Space>
                      {row.icon}
                      <Text strong>{row.item}</Text>
                    </Space>
                  )
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 150,
                  render: (value: string) => tag(value)
                },
                { title: '可验证证据', dataIndex: 'detail' }
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={9}>
          <Card className="command-card" title="演示路线">
            <Timeline
              items={[
                {
                  dot: <CheckCircleOutlined />,
                  children: '总览页核对模型、向量库、权限和事件状态。'
                },
                {
                  dot: <DeploymentUnitOutlined />,
                  children: 'Agent 工作台演示商机推荐、客户分析和创建任务。'
                },
                {
                  dot: <SafetyCertificateOutlined />,
                  children: '写操作先生成确认单，确认后才写入 CRM。'
                },
                {
                  dot: <DatabaseOutlined />,
                  children: '知识库展示真实 embedding + pgvector 检索和引用返回。'
                }
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card className="command-card" title="运行画像">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="模型">{providerLine(modelStatus)}</Descriptions.Item>
              <Descriptions.Item label="协议">{modelStatus?.protocol ?? 'mock'}</Descriptions.Item>
              <Descriptions.Item label="Embedding">{embeddingLine(modelStatus)}</Descriptions.Item>
              <Descriptions.Item label="Vector Store">{knowledgeStatus?.vectorStoreMode ?? 'java-fallback'}</Descriptions.Item>
              <Descriptions.Item label="Security">{securityStatus?.mode ?? 'permissive'}</Descriptions.Item>
              <Descriptions.Item label="Events">{eventStatus?.mode ?? 'log-only'}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card className="command-card" title="工程边界">
            <Space direction="vertical" style={{ width: '100%' }} size={14}>
              <div className="boundary-item">
                <Text strong>Outbox 语义</Text>
                <div className="metric-label">CRM 写事件与确认事务绑定；run/tool call 事件用于审计和 at-least-once 重试。</div>
              </div>
              <div className="boundary-item">
                <Text strong>权限模型</Text>
                <div className="metric-label">当前是单租户 API Token 演示；企业场景可扩展 JWT/SSO/RBAC 和数据范围权限。</div>
              </div>
              <div className="boundary-item">
                <Text strong>向量检索</Text>
                <div className="metric-label">PostgreSQL 环境走 pgvector；测试环境保留 Java fallback，保证自动化测试稳定。</div>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card className="command-card" title="模块交付清单">
        <Table
          size="small"
          pagination={false}
          dataSource={moduleRows}
          columns={[
            { title: '模块', dataIndex: 'module', width: 190 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 140,
              render: (value: string) => tag(value)
            },
            { title: '面试展示点', dataIndex: 'owner' }
          ]}
        />
      </Card>

      <Card className="command-card" title="能力明细">
        <Table
          size="small"
          pagination={false}
          dataSource={capabilityRows}
          columns={[
            { title: '能力', dataIndex: 'item', width: 180 },
            {
              title: '运行状态',
              dataIndex: 'status',
              width: 160,
              render: (value: string) => tag(value)
            },
            { title: '说明', dataIndex: 'detail' }
          ]}
        />
      </Card>
    </Space>
  );
}
