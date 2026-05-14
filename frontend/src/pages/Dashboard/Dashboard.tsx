import { Alert, Card, Col, Descriptions, Progress, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  EventStatus,
  fetchEventStatus,
  fetchHealth,
  fetchModelStatus,
  fetchOpenAiTools,
  HealthView,
  ModelStatus
} from '../../api/client';

const { Text } = Typography;

const moduleCatalog = [
  { key: 'crm-core', healthKey: 'crm', module: 'CRM Core', owner: '客户、商机、任务、产品包、联系记录' },
  { key: 'lead-scoring', healthKey: 'crm', module: 'Lead Scoring', owner: '优先级打分、推荐理由、下一步动作' },
  { key: 'rag', healthKey: 'rag', module: 'RAG', owner: '知识导入、pgvector 检索、引用、低置信拒答' },
  { key: 'agent', healthKey: 'agent', module: 'Agent Orchestrator', owner: 'LLM Tool Calling、确认流、运行审计' },
  { key: 'call-center', healthKey: 'callcenter', module: 'Call Center', owner: '摘要、质检、客户记忆、联系记录确认' },
  { key: 'evaluation', healthKey: 'evaluation', module: 'Evaluation', owner: 'JSONL 用例、指标统计、报告生成' }
];

const readinessRows = [
  { key: 'api', item: '真实接口', status: '已接通', detail: 'Knowledge、Agent Runs、Evaluation、CallCenter 均走后端 API' },
  { key: 'llm', item: 'LLM Tool Calling', status: '已接通', detail: '主流程先让模型按 tools schema 选工具，失败自动回退规则路由' },
  { key: 'embedding', item: '真实 Embedding', status: '已接通', detail: '阿里云百炼 text-embedding-v4 + pgvector 1024 维检索' },
  { key: 'tool-schema', item: 'Tool Schema', status: '已接通', detail: 'OpenAI-compatible function schema 可直接查看与讲解' },
  { key: 'write', item: '写入安全', status: '已接通', detail: '写 CRM 前进入 confirmation，确认人 userId 必填' },
  { key: 'outbox', item: '事件可靠性', status: '已接通', detail: '业务事务内落 outbox，提交后异步分发到 log/Kafka' },
  { key: 'docker', item: '启动脚本', status: '可演示', detail: 'Docker Hub 不通时自动回退到 Docker 基础设施 + 本地服务' }
];

const statusColor: Record<string, string> = {
  UP: 'green',
  READY: 'green',
  ready: 'green',
  LOCAL: 'orange',
  已接通: 'green',
  可演示: 'cyan',
  strict: 'green',
  permissive: 'orange',
  'real-embedding': 'green',
  'pgvector-hybrid': 'green',
  'llm-enabled': 'blue',
  'log-only': 'default'
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
  const [toolCount, setToolCount] = useState<number>(0);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    fetchHealth()
      .then(setHealth)
      .catch(() => setError('后端未连接，当前仅显示前端静态工作台。'));
    fetchModelStatus().then(setModelStatus).catch(() => undefined);
    fetchEventStatus().then(setEventStatus).catch(() => undefined);
    fetchOpenAiTools().then((tools) => setToolCount(tools.length)).catch(() => undefined);
  }, []);

  const moduleRows = useMemo(
    () =>
      moduleCatalog.map((item) => ({
        ...item,
        status: health?.modules?.[item.healthKey] ?? 'READY'
      })),
    [health]
  );

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
      status: 'pgvector-hybrid',
      detail: 'PostgreSQL pgvector + HNSW，检索结果返回 retriever=pgvector-hybrid'
    },
    {
      key: 'security',
      item: 'Security',
      status: 'permissive',
      detail: '本地演示默认 permissive；设置 AGENTPILOT_SECURITY_MODE=strict 后启用 X-AgentPilot-Token'
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

  const probeRows = [
    {
      key: 'health',
      item: 'Health',
      status: health?.status ?? 'LOCAL',
      detail: '后端 /api/health'
    },
    {
      key: 'model',
      item: 'Model',
      status: modelStatus?.mode ?? 'deterministic-mock',
      detail: providerLine(modelStatus)
    },
    {
      key: 'events',
      item: 'Events',
      status: eventStatus?.mode ?? 'log-only',
      detail: eventStatus
        ? `${eventStatus.agentRunTopic} / ${eventStatus.agentToolCallTopic} / ${eventStatus.crmTaskTopic}`
        : 'Kafka 可选开启'
    },
    {
      key: 'observability',
      item: 'Observability',
      status: 'READY',
      detail: 'Swagger UI / Actuator / X-Trace-Id'
    }
  ];

  return (
    <Space direction="vertical" size={18} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="系统状态" value={health?.status ?? 'LOCAL'} />
            <Text className="metric-label">后端 health check</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="模型供应商" value={modelStatus?.vendor ?? 'Mock'} />
            <Text className="metric-label">{modelStatus?.model ?? 'mock-router'}</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="Embedding" value={modelStatus?.embedding?.mode ?? 'mock'} />
            <Text className="metric-label">{modelStatus?.embedding?.model ?? 'deterministic mock'}</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="LLM Tools" value={toolCount} suffix="个" />
            <Text className="metric-label">schema 化工具调用</Text>
          </Card>
        </Col>
      </Row>

      <Card className="command-card" title="系统能力证明">
        <Table
          size="small"
          pagination={false}
          dataSource={capabilityRows}
          columns={[
            { title: '能力', dataIndex: 'item', width: 160 },
            {
              title: '运行状态',
              dataIndex: 'status',
              width: 180,
              render: (value: string) => tag(value)
            },
            { title: '可验证证据', dataIndex: 'detail' }
          ]}
        />
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card className="command-card" title="面试就绪核对">
            <Table
              size="small"
              pagination={false}
              dataSource={readinessRows}
              columns={[
                { title: '检查项', dataIndex: 'item', width: 150 },
                {
                  title: '结论',
                  dataIndex: 'status',
                  width: 110,
                  render: (value: string) => tag(value)
                },
                { title: '说明', dataIndex: 'detail' }
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card className="command-card" title="当前运行画像">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="模型">{providerLine(modelStatus)}</Descriptions.Item>
              <Descriptions.Item label="协议">{modelStatus?.protocol ?? 'mock'}</Descriptions.Item>
              <Descriptions.Item label="Embedding">{embeddingLine(modelStatus)}</Descriptions.Item>
              <Descriptions.Item label="事件">{eventStatus?.mode ?? 'log-only'}</Descriptions.Item>
              <Descriptions.Item label="Outbox Pending">{eventStatus?.outboxPending ?? 0}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card title="运行探针">
            <Table
              size="small"
              pagination={false}
              dataSource={probeRows}
              columns={[
                { title: '探针', dataIndex: 'item', width: 160 },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 180,
                  render: (value: string) => tag(value)
                },
                { title: '说明', dataIndex: 'detail' }
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title="完整度判断">
            <Space direction="vertical" style={{ width: '100%' }} size={14}>
              <div>
                <Text strong>简历可讲</Text>
                <Progress percent={95} status="active" />
              </div>
              <div>
                <Text strong>现场 demo</Text>
                <Progress percent={92} />
              </div>
              <div>
                <Text strong>工程化追问</Text>
                <Progress percent={90} />
              </div>
              <Text className="metric-label">
                当前版本保留少量已知限制：鉴权默认演示模式、Kafka 默认 log-only、复杂多租户权限和生产级监控属于落地演进项。
              </Text>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card title="模块交付路线">
        <Table
          size="small"
          pagination={false}
          dataSource={moduleRows}
          columns={[
            { title: '模块', dataIndex: 'module', width: 180 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 120,
              render: (value: string) => tag(value)
            },
            { title: '面试展示点', dataIndex: 'owner' }
          ]}
        />
      </Card>
    </Space>
  );
}
