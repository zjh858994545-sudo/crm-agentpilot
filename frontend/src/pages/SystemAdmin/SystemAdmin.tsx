import {
  ApiOutlined,
  AuditOutlined,
  ClearOutlined,
  ClusterOutlined,
  DatabaseOutlined,
  LockOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import { Alert, Button, Card, Col, Descriptions, Empty, Popconfirm, Row, Space, Statistic, Table, Tag, Timeline, Typography, message as antdMessage } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  EventStatus,
  KnowledgeStatus,
  LaunchReadinessStatus,
  ModelStatus,
  OpenAiToolDefinition,
  OutboxEvent,
  RetentionStatus,
  SecurityStatus,
  SecurityUser,
  fetchDeadLetters,
  fetchEventStatus,
  fetchKnowledgeStatus,
  fetchLaunchReadiness,
  fetchModelStatus,
  fetchOpenAiTools,
  fetchRetentionStatus,
  fetchSecurityStatus,
  fetchSecurityUsers,
  rebuildKnowledgeVectors,
  runRetentionCleanup,
  retryDeadLetter
} from '../../api/client';

const { Paragraph, Text, Title } = Typography;

type CapabilityRow = {
  key: string;
  name: string;
  status: string;
  owner: string;
  why: string;
  operatingLine: string;
};

type RiskItem = {
  title: string;
  detail: string;
  color: string;
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

function formatTime(value?: string) {
  if (!value) {
    return '未使用';
  }
  return value.replace('T', ' ').slice(0, 19);
}

export default function SystemAdmin() {
  const [securityStatus, setSecurityStatus] = useState<SecurityStatus | null>(null);
  const [eventStatus, setEventStatus] = useState<EventStatus | null>(null);
  const [knowledgeStatus, setKnowledgeStatus] = useState<KnowledgeStatus | null>(null);
  const [modelStatus, setModelStatus] = useState<ModelStatus | null>(null);
  const [tools, setTools] = useState<OpenAiToolDefinition[]>([]);
  const [securityUsers, setSecurityUsers] = useState<SecurityUser[]>([]);
  const [deadLetters, setDeadLetters] = useState<OutboxEvent[]>([]);
  const [retentionStatus, setRetentionStatus] = useState<RetentionStatus | null>(null);
  const [readinessStatus, setReadinessStatus] = useState<LaunchReadinessStatus | null>(null);
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
      fetchOpenAiTools(),
      fetchSecurityUsers(),
      fetchDeadLetters(),
      fetchRetentionStatus(),
      fetchLaunchReadiness()
    ]);
    if (results[0].status === 'fulfilled') setSecurityStatus(results[0].value);
    if (results[1].status === 'fulfilled') setEventStatus(results[1].value);
    if (results[2].status === 'fulfilled') setKnowledgeStatus(results[2].value);
    if (results[3].status === 'fulfilled') setModelStatus(results[3].value);
    if (results[4].status === 'fulfilled') setTools(results[4].value);
    if (results[5].status === 'fulfilled') setSecurityUsers(results[5].value);
    if (results[6].status === 'fulfilled') setDeadLetters(results[6].value);
    if (results[7].status === 'fulfilled') setRetentionStatus(results[7].value);
    if (results[8].status === 'fulfilled') setReadinessStatus(results[8].value);
    if (results.some((result) => result.status === 'rejected')) {
      setError('部分系统状态读取失败，请确认后端已经启动并且当前 token 具备系统管理权限。');
    }
    setLoading(false);
  };

  useEffect(() => {
    load();
  }, []);

  const retryOutboxEvent = async (id: number) => {
    setLoading(true);
    try {
      const result = await retryDeadLetter(id);
      if (result.accepted) {
        antdMessage.success('已重新加入待分发队列');
      } else {
        antdMessage.warning('事件当前状态不可重试');
      }
      await load();
    } catch {
      antdMessage.error('重试事件失败，请检查后端服务或权限。');
    } finally {
      setLoading(false);
    }
  };

  const rebuildVectors = async () => {
    setLoading(true);
    try {
      const result = await rebuildKnowledgeVectors();
      antdMessage.success(`知识索引已重建，更新 ${result.updatedChunks} 个片段`);
      await load();
    } catch {
      antdMessage.error('重建知识索引失败，请检查模型、Embedding 和 pgvector 状态。');
    } finally {
      setLoading(false);
    }
  };

  const runRetention = async (dryRun: boolean) => {
    setLoading(true);
    try {
      const result = await runRetentionCleanup(dryRun);
      if (dryRun) {
        antdMessage.info(`保留策略预演完成，当前可清理 ${result.totalEligibleRows} 行数据。`);
      } else {
        antdMessage.success(`历史数据清理完成，已删除 ${result.totalDeletedRows} 行。`);
      }
      await load();
    } catch (error) {
      const detail = error instanceof Error ? error.message : '请检查保留策略开关、权限和后端日志。';
      antdMessage.error(`数据生命周期操作失败：${detail}`);
    } finally {
      setLoading(false);
    }
  };

  const writeToolCount = useMemo(
    () => tools.filter((tool) => ['createFollowupTask', 'writeContactLog', 'updateLeadStage'].includes(tool.function.name)).length,
    [tools]
  );

  const vectorCoverage = knowledgeStatus?.chunkCount
    ? Math.round(((knowledgeStatus.vectorizedChunkCount ?? 0) / knowledgeStatus.chunkCount) * 100)
    : 0;
  const deadLetterCount = eventStatus?.outboxDeadLetters ?? 0;
  const retentionEligibleRows = retentionStatus?.totalEligibleRows ?? 0;
  const readinessColor =
    readinessStatus?.overallStatus === 'READY' ? 'green' : readinessStatus?.overallStatus === 'BLOCKED' ? 'red' : 'orange';

  const riskItems: RiskItem[] = [
    ...(readinessStatus?.overallStatus === 'BLOCKED'
      ? [
          {
            title: '上线检查存在阻塞项',
            detail: `当前 ${readinessStatus.failCount} 个阻塞项、${readinessStatus.warnCount} 个警告项。请先处理红色检查项。`,
            color: 'red'
          }
        ]
      : []),
    ...(securityStatus?.strictWithoutToken
      ? [
          {
            title: '严格模式缺少访问 token',
            detail: '系统会拒绝所有受保护 API，请配置 AGENTPILOT_API_TOKEN 后再启动。',
            color: 'red'
          }
        ]
      : []),
    ...(securityStatus && !securityStatus.rateLimit?.enabled
      ? [
          {
            title: '接口限流未开启',
            detail: '模型调用和 AI 助手接口缺少成本保护，建议生产环境开启。',
            color: 'orange'
          }
        ]
      : []),
    ...(deadLetterCount > 0
      ? [
          {
            title: '存在死信事件',
            detail: 'Outbox 有事件超过最大重试次数，需要管理员检查下游或人工重试。',
            color: 'red'
          }
        ]
      : []),
    ...(retentionStatus && !retentionStatus.enabled && retentionEligibleRows > 0
      ? [
          {
            title: '历史数据达到清理阈值',
            detail: `当前有 ${retentionEligibleRows} 行日志/审计数据超过保留周期。建议先预演，再在备份完成后开启清理。`,
            color: 'orange'
          }
        ]
      : []),
    ...(knowledgeStatus && knowledgeStatus.chunkCount > 0 && vectorCoverage < 80
      ? [
          {
            title: '知识索引覆盖不足',
            detail: `当前仅 ${vectorCoverage}% 知识片段完成向量索引，建议重建知识索引。`,
            color: 'orange'
          }
        ]
      : []),
    ...(modelStatus && !modelStatus.configured
      ? [
          {
            title: '模型处于规则模式',
            detail: 'AI 助手会使用确定性规则流，接入模型服务后可启用真实工具选择。',
            color: 'blue'
          }
        ]
      : [])
  ];

  const opsCards = [
    {
      title: '上线就绪',
      value: readinessStatus?.overallStatus ?? '检查中',
      color: readinessColor,
      detail: `${readinessStatus?.passCount ?? 0} 通过 / ${readinessStatus?.warnCount ?? 0} 警告 / ${readinessStatus?.failCount ?? 0} 阻塞`
    },
    {
      title: '访问控制',
      value: securityStatus?.rbacEnabled ? 'RBAC 已接入' : '本地身份',
      color: securityStatus?.rbacEnabled ? 'green' : 'orange',
      detail: securityStatus?.rbacEnabled
        ? `${securityStatus.rbacUserCount ?? 0} 个用户 / ${securityStatus.rbacRoleCount ?? 0} 个角色`
        : '使用本地身份上下文，适合单机部署'
    },
    {
      title: '调用保护',
      value: securityStatus?.rateLimit?.enabled ? '限流生效' : '未开启',
      color: securityStatus?.rateLimit?.enabled ? 'green' : 'orange',
      detail: securityStatus?.rateLimit?.enabled
        ? `${securityStatus.rateLimit.backend ?? 'auto'} · AI 助手 ${securityStatus.rateLimit.agentCapacity}/min，模型 ${securityStatus.rateLimit.modelCapacity}/min`
        : '生产环境建议开启'
    },
    {
      title: '事件可靠性',
      value: deadLetterCount > 0 ? '需处理' : '正常',
      color: deadLetterCount > 0 ? 'red' : 'green',
      detail: `${eventStatus?.outboxPending ?? 0} 待分发 / ${eventStatus?.outboxDispatching ?? 0} 分发中 / ${deadLetterCount} 死信`
    },
    {
      title: '知识检索',
      value: knowledgeStatus?.pgvectorAvailable ? 'pgvector' : knowledgeStatus?.vectorStoreMode ?? '未知',
      color: knowledgeStatus?.pgvectorAvailable ? 'green' : 'blue',
      detail: `${knowledgeStatus?.vectorizedChunkCount ?? 0}/${knowledgeStatus?.chunkCount ?? 0} 片段已索引，覆盖 ${vectorCoverage}%`
    },
    {
      title: '模型接入',
      value: modelStatus?.configured ? '真实模型' : '规则模式',
      color: modelStatus?.configured ? 'green' : 'orange',
      detail: modelStatus?.configured ? `${modelStatus.vendor ?? modelStatus.provider} · ${modelStatus.model}` : '可回退规则路由'
    },
    {
      title: '数据生命周期',
      value: retentionStatus?.enabled ? '已启用' : '预演模式',
      color: retentionStatus?.enabled ? 'green' : retentionEligibleRows > 0 ? 'orange' : 'blue',
      detail: `${retentionEligibleRows} 行可清理，单次上限 ${retentionStatus?.maxDeleteRowsPerRun ?? '-'}`
    }
  ];

  const capabilityRows: CapabilityRow[] = [
    {
      key: 'rate-limit',
      name: '接口限流',
      status: securityStatus?.rateLimit?.enabled ? 'ACTIVE' : 'DISABLED',
      owner: `Spring Security Filter · ${securityStatus?.rateLimit?.backend ?? 'auto'}`,
      why: '防止 Agent Chat 和模型接口被刷爆，控制模型调用费用和数据库压力。',
      operatingLine: '优先使用 Redis 做分布式限流；Redis 不可用时回退本机 token bucket，超限返回 429。'
    },
    {
      key: 'rbac',
      name: 'RBAC Token',
      status: securityStatus?.rbacEnabled ? 'ACTIVE' : 'LOCAL_PROFILE',
      owner: 'agentpilot_user / role / permission',
      why: '让不同销售拿到不同 salesRepId 和权限集，避免 A 销售看到 B 销售客户。',
      operatingLine: '真实 token 只存 SHA-256，登录后从数据库加载 userId、salesRepId 和权限。'
    },
    {
      key: 'outbox-lock',
      name: 'Outbox CAS 分发锁',
      status: eventStatus ? 'READY' : 'UNKNOWN',
      owner: 'agent_outbox_event',
      why: '避免 afterCommit 和定时任务同时发送同一事件，降低重复分发风险。',
      operatingLine: '分发前先把事件 CAS 抢占到 DISPATCHING，只有抢到锁的 worker 能发。'
    },
    {
      key: 'dead-letter',
      name: 'Outbox DLQ',
      status: (eventStatus?.outboxDeadLetters ?? 0) > 0 ? 'HAS_DEAD' : 'READY',
      owner: '事件后台任务',
      why: 'Kafka 或下游故障时，失败事件不会悄悄丢掉，超过重试次数进入死信。',
      operatingLine: '当前是 at-least-once，失败满 5 次进入 DEAD_LETTER，可以人工重试。'
    },
    {
      key: 'tool-schema',
      name: 'LLM Tool Schema',
      status: `${tools.length} tools`,
      owner: 'Tool Registry',
      why: '让模型只能从后端允许的工具中选择，写工具统一进入 confirmation。',
      operatingLine: `工具 schema 由后端暴露，当前 ${writeToolCount} 个写工具需要人工确认。`
    },
    {
      key: 'vector-store',
      name: 'pgvector RAG',
      status: knowledgeStatus?.vectorStoreMode ?? 'unknown',
      owner: 'Knowledge Service',
      why: '用真实 embedding 和向量库做知识检索，回答带引用，低置信拒答。',
      operatingLine: 'RAG 不是让模型凭空答，而是先检索销售 SOP 和政策，再基于引用回答。'
    },
    {
      key: 'retention',
      name: '数据保留策略',
      status: retentionStatus?.enabled ? 'ACTIVE' : 'DRY_RUN',
      owner: 'Operations Retention',
      why: '审计、检索和事件日志会持续增长，需要可预估、可回滚意识的清理机制。',
      operatingLine: '默认只做 dry-run 预演；真正删除需要开启保留策略，并且不超过单次删除上限。'
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <Card
        className="command-card"
        title="系统运行中枢"
        extra={
          <Space>
            <Button icon={<DatabaseOutlined />} loading={loading} onClick={rebuildVectors}>
              重建知识索引
            </Button>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={load}>
              刷新运行状态
            </Button>
          </Space>
        }
      >
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} lg={14}>
            <Title level={4} style={{ marginTop: 0 }}>
              把“能不能上线”拆成可观察、可解释、可恢复的运行指标
            </Title>
            <Paragraph style={{ marginBottom: 12 }}>
              系统管理页面向管理员、运维和产品负责人，集中展示访问控制、接口限流、事件分发、知识索引和模型接入状态。
              销售首页不展示这些技术细节，后台这里负责证明系统安全边界和运行韧性。
            </Paragraph>
            <Space wrap>
              {statusTag(securityStatus?.mode ?? 'security-loading')}
              {statusTag(eventStatus?.mode ?? 'events-loading')}
              {statusTag(knowledgeStatus?.vectorStoreMode ?? 'knowledge-loading')}
              {modelStatus?.configured ? <Tag color="green">真实模型已接入</Tag> : <Tag color="orange">模型规则模式</Tag>}
            </Space>
          </Col>
          <Col xs={24} lg={10}>
            <div className="admin-risk-panel">
              <Text strong>管理员待办</Text>
              {riskItems.length ? (
                <Space direction="vertical" size={8} style={{ width: '100%', marginTop: 10 }}>
                  {riskItems.map((item) => (
                    <Alert
                      key={item.title}
                      type={item.color === 'red' ? 'error' : item.color === 'orange' ? 'warning' : 'info'}
                      showIcon
                      message={item.title}
                      description={item.detail}
                    />
                  ))}
                </Space>
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前无阻塞项，可进入业务链路" style={{ margin: '12px 0 0' }} />
              )}
            </div>
          </Col>
        </Row>
      </Card>

      <Row gutter={[16, 16]}>
        {opsCards.map((item) => (
          <Col xs={24} md={12} xl={6} xxl={4} key={item.title}>
            <Card className="metric-card">
              <Space direction="vertical" size={4}>
                <Text type="secondary">{item.title}</Text>
                <Tag color={item.color}>{item.value}</Tag>
                <Text strong>{item.detail}</Text>
              </Space>
            </Card>
          </Col>
        ))}
      </Row>

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

      <Card
        className="command-card"
        title="上线检查清单"
        extra={
          <Space>
            <Tag color={readinessColor}>{readinessStatus?.overallStatus ?? 'LOADING'}</Tag>
            <Text type="secondary">phase: {readinessStatus?.phase ?? '-'}</Text>
          </Space>
        }
      >
        <Table
          rowKey="key"
          loading={loading}
          pagination={false}
          dataSource={readinessStatus?.checks ?? []}
          columns={[
            { title: '检查项', dataIndex: 'name', width: 180 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (value) => (
                <Tag color={value === 'PASS' ? 'green' : value === 'FAIL' ? 'red' : 'orange'}>{value}</Tag>
              )
            },
            { title: '当前情况', dataIndex: 'detail' },
            { title: '处理建议', dataIndex: 'action', render: (value) => <Text type="secondary">{value}</Text> }
          ]}
        />
      </Card>

      <Card className="command-card" title="用户与权限范围">
        <Table
          rowKey="userId"
          loading={loading}
          pagination={false}
          dataSource={securityUsers}
          columns={[
            {
              title: '用户',
              render: (_, record) => (
                <Space direction="vertical" size={0}>
                  <Text strong>{record.displayName}</Text>
                  <Text type="secondary">{record.username}</Text>
                </Space>
              )
            },
            {
              title: '租户',
              dataIndex: 'tenantId',
              render: (value) => <Tag color="cyan">{value || '-'}</Tag>
            },
            {
              title: '角色',
              dataIndex: 'roles',
              render: (roles: string[]) => (
                <Space size={4} wrap>
                  {roles.map((role) => (
                    <Tag key={role} color={role === 'system_admin' ? 'geekblue' : role === 'sales_manager' ? 'purple' : 'blue'}>
                      {role}
                    </Tag>
                  ))}
                </Space>
              )
            },
            { title: '销售范围', dataIndex: 'salesRepId', render: (value) => <Tag>salesRep #{value}</Tag> },
            { title: '状态', dataIndex: 'status', render: (value) => statusTag(value) },
            {
              title: '最近认证',
              dataIndex: 'lastAuthenticatedAt',
              render: (value) => <Text type={value ? undefined : 'secondary'}>{formatTime(value)}</Text>
            },
            {
              title: '来源 IP',
              dataIndex: 'lastAuthenticatedIp',
              render: (value) => <Text type="secondary">{value || '-'}</Text>
            },
            { title: '权限数', dataIndex: 'permissions', render: (permissions: string[]) => permissions.length }
          ]}
        />
      </Card>

      <Card className="command-card" title="Outbox 死信处理">
        <Table
          rowKey="id"
          loading={loading}
          dataSource={deadLetters}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有死信事件" /> }}
          pagination={deadLetters.length > 8 ? { pageSize: 8 } : false}
          columns={[
            { title: 'ID', dataIndex: 'id', width: 80 },
            { title: '事件类型', dataIndex: 'eventType', render: (value) => <Text code>{value}</Text> },
            { title: '聚合对象', render: (_, record) => `${record.aggregateType} #${record.aggregateId}` },
            { title: 'Topic', dataIndex: 'topic' },
            { title: '重试次数', dataIndex: 'retryCount', width: 100 },
            {
              title: '错误',
              dataIndex: 'errorMessage',
              ellipsis: true,
              render: (value) => <Text type="secondary">{value || '-'}</Text>
            },
            {
              title: '操作',
              width: 120,
              render: (_, record) => (
                <Button size="small" type="link" onClick={() => retryOutboxEvent(record.id)}>
                  重新分发
                </Button>
              )
            }
          ]}
        />
      </Card>

      <Card
        className="command-card"
        title="数据生命周期治理"
        extra={
          <Space>
            <Button icon={<AuditOutlined />} loading={loading} onClick={() => runRetention(true)}>
              预演清理影响
            </Button>
            <Popconfirm
              title="确认执行历史数据清理？"
              description="请确保已经完成数据库备份。系统只清理超过保留周期且不处于待处理状态的数据。"
              okText="确认清理"
              cancelText="取消"
              onConfirm={() => runRetention(false)}
            >
              <Button danger icon={<ClearOutlined />} loading={loading} disabled={!retentionStatus?.enabled || retentionEligibleRows <= 0}>
                执行清理
              </Button>
            </Popconfirm>
          </Space>
        }
      >
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} md={8}>
            <Statistic title="超过保留周期" value={retentionEligibleRows} suffix="行" />
            <Text type="secondary">dry-run 会先统计，不会删除数据</Text>
          </Col>
          <Col xs={24} md={8}>
            <Statistic title="单次清理上限" value={retentionStatus?.maxDeleteRowsPerRun ?? 0} suffix="行" />
            <Text type="secondary">避免一次事务锁住过多历史数据</Text>
          </Col>
          <Col xs={24} md={8}>
            <Statistic title="自动清理" value={retentionStatus?.scheduledCleanupEnabled ? '已开启' : '未开启'} />
            <Text type="secondary">{retentionStatus?.cleanupCron ?? '未配置'}</Text>
          </Col>
        </Row>
        <Table
          rowKey="key"
          loading={loading}
          pagination={false}
          dataSource={retentionStatus?.categories ?? []}
          columns={[
            { title: '数据类别', dataIndex: 'name', width: 180 },
            { title: '保留周期', dataIndex: 'retentionDays', width: 110, render: (value) => `${value} 天` },
            { title: '截止时间', dataIndex: 'cutoffAt', width: 180, render: (value) => formatTime(value) },
            { title: '可清理', dataIndex: 'eligibleRows', width: 100, render: (value) => <Tag color={value > 0 ? 'orange' : 'green'}>{value}</Tag> },
            { title: '受保护', dataIndex: 'protectedRows', width: 100, render: (value) => <Tag>{value}</Tag> },
            { title: '保护规则', dataIndex: 'protectionRule', render: (value) => <Text type="secondary">{value}</Text> }
          ]}
        />
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card className="command-card" title="生产运行能力">
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
                  title: '运行说明',
                  dataIndex: 'operatingLine',
                  render: (value) => <Text type="secondary">{value}</Text>
                }
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Card className="command-card" title="访问控制与调用保护">
              <Descriptions size="small" bordered column={1}>
                <Descriptions.Item label="权限模式">
                  <Space>
                    {securityStatus?.strict ? <Tag color="green">strict</Tag> : <Tag color="orange">permissive</Tag>}
                    {securityStatus?.rbacEnabled ? <Tag color="blue">RBAC</Tag> : <Tag>local profile</Tag>}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="角色 / 用户">
                  {securityStatus?.rbacRoleCount ?? 0} roles / {securityStatus?.rbacUserCount ?? 0} users
                </Descriptions.Item>
                <Descriptions.Item label="种子账号">
                  {securityStatus?.seedUsersEnabled ? <Tag color="orange">本地可用</Tag> : <Tag color="green">生产禁用</Tag>}
                </Descriptions.Item>
                <Descriptions.Item label="Agent Chat">
                  {securityStatus?.rateLimit?.agentCapacity ?? '-'} / min
                </Descriptions.Item>
                <Descriptions.Item label="限流后端">
                  {securityStatus?.rateLimit?.backend ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label="Model Chat">
                  {securityStatus?.rateLimit?.modelCapacity ?? '-'} / min
                </Descriptions.Item>
                <Descriptions.Item label="普通 API">
                  {securityStatus?.rateLimit?.defaultCapacity ?? '-'} / min
                </Descriptions.Item>
              </Descriptions>
            </Card>

            <Card className="command-card" title="事件可靠性">
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

      <Card className="command-card" title="运行治理说明">
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
                  <Text strong>RBAC token 和本地身份有什么区别？</Text>
                  <Text type="secondary">
                    本地身份用于单机环境快速进入系统；RBAC token 来自数据库，token 只存 SHA-256，认证后加载真实用户、销售归属和权限集合。
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
            },
            {
              dot: <ClearOutlined />,
              children: (
                <Space direction="vertical" size={2}>
                  <Text strong>为什么要做数据保留策略？</Text>
                  <Text type="secondary">
                    Agent 运行、工具调用、知识检索和 Outbox 会持续写日志。保留策略先预估影响，再按周期清理已结束数据，防止数据库无限增长。
                  </Text>
                </Space>
              )
            }
          ]}
        />
      </Card>

      <Card className="command-card" title="工具边界">
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

      <Card className="command-card" title="模型与知识检索">
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
