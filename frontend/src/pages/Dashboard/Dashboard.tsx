import {
  ApiOutlined,
  BookOutlined,
  CheckCircleOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  FieldTimeOutlined,
  MessageOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import { Alert, Button, Card, Col, Descriptions, Row, Space, Statistic, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
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

const { Paragraph, Text, Title } = Typography;

const statusColor: Record<string, string> = {
  UP: 'green',
  READY: 'green',
  ready: 'green',
  LOCAL: 'orange',
  mock: 'orange',
  strict: 'green',
  permissive: 'orange',
  'real-embedding': 'green',
  'pgvector-hybrid': 'green',
  'java-fallback': 'orange',
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
  const [knowledgeStatus, setKnowledgeStatus] = useState<KnowledgeStatus | null>(null);
  const [securityStatus, setSecurityStatus] = useState<SecurityStatus | null>(null);
  const [tools, setTools] = useState<OpenAiToolDefinition[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchHealth()
      .then(setHealth)
      .catch(() => setError('后端未连接，部分运行状态暂不可用。'));
    fetchModelStatus().then(setModelStatus).catch(() => undefined);
    fetchEventStatus().then(setEventStatus).catch(() => undefined);
    fetchKnowledgeStatus().then(setKnowledgeStatus).catch(() => undefined);
    fetchSecurityStatus().then(setSecurityStatus).catch(() => undefined);
    fetchOpenAiTools().then(setTools).catch(() => undefined);
  }, []);

  const writeToolCount = tools.filter((tool) =>
    ['createFollowupTask', 'writeContactLog', 'updateLeadStage'].includes(tool.function.name)
  ).length;
  const vectorRatio = knowledgeStatus?.chunkCount
    ? Math.round((knowledgeStatus.vectorizedChunkCount / knowledgeStatus.chunkCount) * 100)
    : 0;

  const serviceReadyCount = useMemo(
    () => Object.values(health?.modules ?? {}).filter((value) => value === 'ready' || value === 'READY').length,
    [health]
  );

  const quickActions = [
    {
      title: '开始 Agent 对话',
      desc: '分析客户、检索知识、生成写操作确认',
      icon: <MessageOutlined />,
      to: '/agent'
    },
    {
      title: '查看高优商机',
      desc: '按评分和风险优先处理跟进客户',
      icon: <ThunderboltOutlined />,
      to: '/leads'
    },
    {
      title: '检索销售知识',
      desc: '查 SOP、套餐政策和质检规则',
      icon: <BookOutlined />,
      to: '/knowledge'
    },
    {
      title: '查看运行审计',
      desc: '追踪每一次 Agent Run 和 Tool Call',
      icon: <FieldTimeOutlined />,
      to: '/runs'
    }
  ];

  const workflowCards = [
    {
      title: '客户理解',
      icon: <TeamOutlined />,
      text: '聚合客户画像、标签、跟进记录和风险状态。'
    },
    {
      title: '商机排序',
      icon: <ThunderboltOutlined />,
      text: '用可解释评分帮助销售决定今天先跟谁。'
    },
    {
      title: '知识辅助',
      icon: <BookOutlined />,
      text: '从销售 SOP、套餐政策和质检规则中检索证据。'
    },
    {
      title: '安全写入',
      icon: <SafetyCertificateOutlined />,
      text: '任务、联系记录、商机阶段变更先确认再落库。'
    },
    {
      title: '通话质检',
      icon: <PhoneOutlined />,
      text: '识别承诺类风险，沉淀摘要和下一步动作。'
    }
  ];

  return (
    <Space direction="vertical" size={18} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <div className="overview-hero">
        <div>
          <Text className="eyebrow">Workspace Overview</Text>
          <Title level={3}>销售作业 AI 工作台</Title>
          <Paragraph className="overview-copy">
            把客户分析、商机推荐、知识库问答、通话质检和 CRM 写操作确认放在一个工作流里。读操作快速执行，写操作先确认再落库。
          </Paragraph>
          <Space wrap>
            <Link to="/agent">
              <Button type="primary" icon={<MessageOutlined />}>进入 Agent 工作台</Button>
            </Link>
            <Link to="/leads">
              <Button icon={<ThunderboltOutlined />}>查看商机推荐</Button>
            </Link>
          </Space>
        </div>
        <div className="overview-status-card">
          <Space direction="vertical" size={10} style={{ width: '100%' }}>
            <Space style={{ justifyContent: 'space-between', width: '100%' }}>
              <Text strong>运行状态</Text>
              {tag(health?.status ?? 'LOCAL')}
            </Space>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="模型">{providerLine(modelStatus)}</Descriptions.Item>
              <Descriptions.Item label="Embedding">{embeddingLine(modelStatus)}</Descriptions.Item>
              <Descriptions.Item label="向量库">{knowledgeStatus?.vectorStoreMode ?? 'java-fallback'}</Descriptions.Item>
              <Descriptions.Item label="权限">{securityStatus?.mode ?? 'permissive'}</Descriptions.Item>
            </Descriptions>
          </Space>
        </div>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="服务模块" value={serviceReadyCount} suffix="个" prefix={<ApiOutlined />} />
            <Text className="metric-label">后端健康检查</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="LLM 工具" value={tools.length} suffix="个" />
            <Text className="metric-label">{writeToolCount} 个写工具受确认流保护</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="知识向量化" value={vectorRatio} suffix="%" prefix={<DatabaseOutlined />} />
            <Text className="metric-label">{knowledgeStatus?.vectorStoreMode ?? 'java-fallback'}</Text>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="metric-card">
            <Statistic title="待分发事件" value={eventStatus?.outboxPending ?? 0} prefix={<CloudServerOutlined />} />
            <Text className="metric-label">Outbox 审计与重试</Text>
          </Card>
        </Col>
      </Row>

      <Card className="command-card" title="快捷入口">
        <div className="quick-action-grid">
          {quickActions.map((item) => (
            <Link to={item.to} className="quick-action-card" key={item.title}>
              <div className="quick-action-icon">{item.icon}</div>
              <div>
                <Text strong>{item.title}</Text>
                <span>{item.desc}</span>
              </div>
            </Link>
          ))}
        </div>
      </Card>

      <Card className="command-card" title="业务工作流">
        <div className="workflow-strip">
          {workflowCards.map((item, index) => (
            <div className="workflow-step" key={item.title}>
              <div className="workflow-index">{index + 1}</div>
              <div className="workflow-icon">{item.icon}</div>
              <Text strong>{item.title}</Text>
              <span>{item.text}</span>
            </div>
          ))}
        </div>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card className="command-card" title="系统能力">
            <div className="status-list">
              <div className="status-row">
                <Space><CheckCircleOutlined /> <Text strong>模型调用</Text></Space>
                <Space>{tag(modelStatus?.mode ?? 'deterministic-mock')}<Text type="secondary">{providerLine(modelStatus)}</Text></Space>
              </div>
              <div className="status-row">
                <Space><DatabaseOutlined /> <Text strong>向量检索</Text></Space>
                <Space>{tag(knowledgeStatus?.vectorStoreMode ?? 'java-fallback')}<Text type="secondary">{knowledgeStatus?.vectorizedChunkCount ?? 0}/{knowledgeStatus?.chunkCount ?? 0} chunks</Text></Space>
              </div>
              <div className="status-row">
                <Space><SafetyCertificateOutlined /> <Text strong>写入确认</Text></Space>
                <Space>{tag(writeToolCount > 0 ? 'READY' : 'LOCAL')}<Text type="secondary">{writeToolCount} write tools</Text></Space>
              </div>
              <div className="status-row">
                <Space><FieldTimeOutlined /> <Text strong>事件分发</Text></Space>
                <Space>{tag(eventStatus?.mode ?? 'log-only')}<Text type="secondary">pending={eventStatus?.outboxPending ?? 0}</Text></Space>
              </div>
            </div>
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card className="command-card" title="工程边界">
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <div className="boundary-item">
                <Text strong>写操作安全</Text>
                <div className="metric-label">Agent 只生成确认单，用户确认后才写入 CRM。</div>
              </div>
              <div className="boundary-item">
                <Text strong>事件语义</Text>
                <div className="metric-label">CRM 写事件与事务绑定；运行审计事件采用 at-least-once 分发。</div>
              </div>
              <div className="boundary-item">
                <Text strong>权限模式</Text>
                <div className="metric-label">当前为 API Token 模式，可扩展到 JWT / SSO / RBAC。</div>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
