import {
  ApiOutlined,
  AuditOutlined,
  ClearOutlined,
  ClusterOutlined,
  DatabaseOutlined,
  EditOutlined,
  LockOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
  message as antdMessage
} from 'antd';
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
  Tenant,
  TenantUpsertPayload,
  createTenant,
  describeApiError,
  fetchDeadLetters,
  fetchEventStatus,
  fetchKnowledgeStatus,
  fetchLaunchReadiness,
  fetchModelStatus,
  fetchOpenAiTools,
  fetchRetentionStatus,
  fetchSecurityStatus,
  fetchSecurityUsers,
  fetchTenants,
  rebuildKnowledgeVectors,
  retryDeadLetter,
  runRetentionCleanup,
  updateTenant,
  updateTenantStatus
} from '../../api/client';
import ApiErrorNotice from '../../components/ApiErrorNotice';

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
  color: 'red' | 'orange' | 'blue';
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
  const [tenantForm] = Form.useForm<TenantUpsertPayload>();
  const [securityStatus, setSecurityStatus] = useState<SecurityStatus | null>(null);
  const [eventStatus, setEventStatus] = useState<EventStatus | null>(null);
  const [knowledgeStatus, setKnowledgeStatus] = useState<KnowledgeStatus | null>(null);
  const [modelStatus, setModelStatus] = useState<ModelStatus | null>(null);
  const [tools, setTools] = useState<OpenAiToolDefinition[]>([]);
  const [securityUsers, setSecurityUsers] = useState<SecurityUser[]>([]);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [tenantModalOpen, setTenantModalOpen] = useState(false);
  const [editingTenant, setEditingTenant] = useState<Tenant | null>(null);
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
      fetchTenants(),
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
    if (results[6].status === 'fulfilled') setTenants(results[6].value);
    if (results[7].status === 'fulfilled') setDeadLetters(results[7].value);
    if (results[8].status === 'fulfilled') setRetentionStatus(results[8].value);
    if (results[9].status === 'fulfilled') setReadinessStatus(results[9].value);
    if (results.some((result) => result.status === 'rejected')) {
      setError('部分运行状态读取失败，请确认后端已启动，并且当前令牌具备系统管理权限。');
    }
    setLoading(false);
  };

  useEffect(() => {
    load();
  }, []);

  const openTenantModal = (tenant?: Tenant) => {
    setEditingTenant(tenant ?? null);
    tenantForm.setFieldsValue({
      id: tenant?.id ?? '',
      name: tenant?.name ?? '',
      planCode: tenant?.planCode ?? 'standard'
    });
    setTenantModalOpen(true);
  };

  const saveTenant = async () => {
    const values = await tenantForm.validateFields();
    setLoading(true);
    try {
      if (editingTenant) {
        await updateTenant(editingTenant.id, {
          name: values.name,
          planCode: values.planCode
        });
        antdMessage.success('租户资料已更新');
      } else {
        await createTenant(values);
        antdMessage.success('租户已开通');
      }
      setTenantModalOpen(false);
      setEditingTenant(null);
      await load();
    } catch (err) {
      const detail = describeApiError(err);
      antdMessage.error(`租户保存失败：${detail}`);
    } finally {
      setLoading(false);
    }
  };

  const changeTenantStatus = async (tenant: Tenant, status: 'ACTIVE' | 'DISABLED') => {
    setLoading(true);
    try {
      await updateTenantStatus(tenant.id, status);
      antdMessage.success(status === 'ACTIVE' ? '租户已启用' : '租户已停用');
      await load();
    } catch (err) {
      const detail = describeApiError(err);
      antdMessage.error(`租户状态更新失败：${detail}`);
    } finally {
      setLoading(false);
    }
  };

  const retryOutboxEvent = async (id: number) => {
    setLoading(true);
    try {
      const result = await retryDeadLetter(id);
      if (result.accepted) {
        antdMessage.success('事件已重新加入待分发队列');
      } else {
        antdMessage.warning('事件当前状态不可重试');
      }
      await load();
    } catch (err) {
      antdMessage.error(describeApiError(err));
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
    } catch (err) {
      antdMessage.error(describeApiError(err));
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
    } catch (err) {
      const detail = describeApiError(err);
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
            detail: `当前 ${readinessStatus.failCount} 个阻塞项、${readinessStatus.warnCount} 个警告项，请优先处理红色检查项。`,
            color: 'red' as const
          }
        ]
      : []),
    ...(securityStatus?.strictWithoutToken
      ? [
          {
            title: '严格模式缺少访问令牌',
            detail: '系统会拒绝所有受保护 API，请配置 AGENTPILOT_API_TOKEN 或 RBAC 用户后再启动。',
            color: 'red' as const
          }
        ]
      : []),
    ...(securityStatus && !securityStatus.rateLimit?.enabled
      ? [
          {
            title: '接口限流未开启',
            detail: '模型调用和 AI 助手缺少成本保护，生产环境建议开启。',
            color: 'orange' as const
          }
        ]
      : []),
    ...(deadLetterCount > 0
      ? [
          {
            title: '存在死信事件',
            detail: 'Outbox 有事件超过最大重试次数，需要管理员检查下游或人工重试。',
            color: 'red' as const
          }
        ]
      : []),
    ...(knowledgeStatus && knowledgeStatus.chunkCount > 0 && vectorCoverage < 80
      ? [
          {
            title: '知识索引覆盖不足',
            detail: `当前仅 ${vectorCoverage}% 知识片段完成向量索引，建议重建知识索引。`,
            color: 'orange' as const
          }
        ]
      : []),
    ...(modelStatus && !modelStatus.configured
      ? [
          {
            title: '模型处于规则模式',
            detail: 'AI 助手会使用确定性规则流；接入模型服务后可启用真实工具选择。',
            color: 'blue' as const
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
        : '使用本地身份上下文，适合单机演示'
    },
    {
      title: '调用保护',
      value: securityStatus?.rateLimit?.enabled ? '限流生效' : '未开启',
      color: securityStatus?.rateLimit?.enabled ? 'green' : 'orange',
      detail: securityStatus?.rateLimit?.enabled
        ? `${securityStatus.rateLimit.backend ?? 'auto'} / AI ${securityStatus.rateLimit.agentCapacity}/min / Model ${securityStatus.rateLimit.modelCapacity}/min`
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
      detail: modelStatus?.configured ? `${modelStatus.vendor ?? modelStatus.provider} / ${modelStatus.model}` : '可回退规则路由'
    }
  ];

  const capabilityRows: CapabilityRow[] = [
    {
      key: 'rate-limit',
      name: '接口限流',
      status: securityStatus?.rateLimit?.enabled ? 'ACTIVE' : 'DISABLED',
      owner: `Spring Security Filter / ${securityStatus?.rateLimit?.backend ?? 'auto'}`,
      why: '防止 Agent Chat 和模型接口被刷爆，控制模型费用和数据库压力。',
      operatingLine: '优先使用 Redis 做分布式限流；Redis 不可用时回退本机 token bucket；超限返回 429。'
    },
    {
      key: 'tenant-scope',
      name: '租户数据隔离',
      status: securityStatus?.rbacEnabled ? 'ACTIVE' : 'LOCAL_PROFILE',
      owner: `CurrentUser / ${securityStatus?.activeTenantCount ?? 0} active tenants`,
      why: '商业化部署必须保证不同企业、不同销售团队之间不会串看客户、商机、知识库和审计数据。',
      operatingLine: '后端从认证主体读取 tenantId 和 salesRepId，CRM、Agent、RAG、呼叫中心接口统一做数据范围校验。'
    },
    {
      key: 'rbac',
      name: 'RBAC Token',
      status: securityStatus?.rbacEnabled ? 'ACTIVE' : 'LOCAL_PROFILE',
      owner: 'agentpilot_user / role / permission',
      why: '让不同销售拿到不同 salesRepId、tenantId 和权限集，避免越权访问客户数据。',
      operatingLine: '真实 token 只存 SHA-256；认证后加载用户、租户、销售归属、角色和权限。'
    },
    {
      key: 'sso-jwt',
      name: '企业 SSO / JWT',
      status: securityStatus?.jwt?.enabled ? (securityStatus.jwt.issuerConfigured ? 'ACTIVE' : 'CONFIG_REQUIRED') : 'READY_TO_ENABLE',
      owner: securityStatus?.jwt?.enabled ? `aud=${securityStatus.jwt.audience}` : 'OIDC / JWT claims',
      why: '商业化部署需要接入企业身份源，由 IdP 管理 MFA、离职禁用、密码策略和登录审计。',
      operatingLine: `user=${securityStatus?.jwt?.userIdClaim ?? 'user_id'} / tenant=${securityStatus?.jwt?.tenantClaim ?? 'tenant_id'} / allowList=${securityStatus?.jwt?.tenantAllowListEnabled ? securityStatus.jwt.allowedTenantCount : 'off'}`
    },
    {
      key: 'outbox-lock',
      name: 'Outbox CAS 分发锁',
      status: eventStatus ? 'READY' : 'UNKNOWN',
      owner: 'agent_outbox_event',
      why: '避免 afterCommit 和定时任务同时发送同一事件，降低重复分发风险。',
      operatingLine: '分发前先 CAS 抢占为 DISPATCHING，只有抢到锁的 worker 能发送。'
    },
    {
      key: 'dead-letter',
      name: 'Outbox DLQ',
      status: deadLetterCount > 0 ? 'HAS_DEAD' : 'READY',
      owner: '事件后台任务',
      why: 'Kafka 或下游故障时，失败事件不应悄悄丢失，超过重试次数后进入死信。',
      operatingLine: '当前是 at-least-once；失败满 5 次进入 DEAD_LETTER，可人工重试。'
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
      operatingLine: 'RAG 先检索销售 SOP 和政策，再基于引用回答，减少模型凭空编造。'
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <ApiErrorNotice error={error} title="系统管理状态未完整加载" onRetry={load} />}

      <Card
        className="command-card"
        title="系统运行中心"
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
            <Paragraph>
              系统管理页面向管理员、运维和产品负责人，集中展示访问控制、接口限流、事件分发、知识索引和模型接入状态。
              销售首页不展示这些技术细节；后台这里负责证明系统安全边界和运行韧性。
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
            <Statistic title="死信事件" value={deadLetterCount} prefix={<ThunderboltOutlined />} />
            <Text className="metric-label">需要人工重试</Text>
          </Card>
        </Col>
      </Row>

      <Card className="command-card" title="上线检查清单" extra={<Tag color={readinessColor}>{readinessStatus?.overallStatus ?? 'LOADING'}</Tag>}>
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
              render: (value) => <Tag color={value === 'PASS' ? 'green' : value === 'FAIL' ? 'red' : 'orange'}>{value}</Tag>
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
          pagination={securityUsers.length > 8 ? { pageSize: 8 } : false}
          dataSource={securityUsers}
          columns={[
            {
              title: '用户',
              render: (_, record: SecurityUser) => (
                <Space direction="vertical" size={0}>
                  <Text strong>{record.displayName}</Text>
                  <Text type="secondary">{record.username}</Text>
                </Space>
              )
            },
            { title: '租户', dataIndex: 'tenantId', render: (value) => <Tag color="cyan">{value || '-'}</Tag> },
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
            { title: '最近认证', dataIndex: 'lastAuthenticatedAt', render: (value) => <Text>{formatTime(value)}</Text> },
            { title: '来源 IP', dataIndex: 'lastAuthenticatedIp', render: (value) => <Text type="secondary">{value || '-'}</Text> },
            { title: '权限数', dataIndex: 'permissions', render: (permissions: string[]) => permissions.length }
          ]}
        />
      </Card>

      <Card
        className="command-card"
        title="租户运营"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openTenantModal()}>
            开通租户
          </Button>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          pagination={tenants.length > 8 ? { pageSize: 8 } : false}
          dataSource={tenants}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无租户数据" /> }}
          columns={[
            {
              title: '租户',
              render: (_, record: Tenant) => (
                <Space direction="vertical" size={0}>
                  <Text strong>{record.name}</Text>
                  <Text type="secondary">{record.id}</Text>
                </Space>
              )
            },
            { title: '套餐', dataIndex: 'planCode', width: 120, render: (value) => <Tag color="blue">{value || 'standard'}</Tag> },
            { title: '状态', dataIndex: 'status', width: 120, render: (value) => statusTag(value) },
            { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value) => <Text>{formatTime(value)}</Text> },
            {
              title: '操作',
              width: 220,
              render: (_, record: Tenant) => {
                const nextStatus = record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
                return (
                  <Space>
                    <Button size="small" type="link" icon={<EditOutlined />} onClick={() => openTenantModal(record)}>
                      编辑
                    </Button>
                    <Popconfirm
                      title={nextStatus === 'DISABLED' ? '确认停用该租户？' : '确认启用该租户？'}
                      description={
                        nextStatus === 'DISABLED'
                          ? '停用后该企业用户将无法通过 RBAC / JWT 访问业务数据。'
                          : '启用后该企业用户可按权限访问业务数据。'
                      }
                      okText="确认"
                      cancelText="取消"
                      onConfirm={() => changeTenantStatus(record, nextStatus)}
                    >
                      <Button size="small" type="link" danger={nextStatus === 'DISABLED'} icon={<PoweroffOutlined />}>
                        {nextStatus === 'DISABLED' ? '停用' : '启用'}
                      </Button>
                    </Popconfirm>
                  </Space>
                );
              }
            }
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
            { title: '聚合对象', render: (_, record: OutboxEvent) => `${record.aggregateType} #${record.aggregateId}` },
            { title: 'Topic', dataIndex: 'topic' },
            { title: '重试次数', dataIndex: 'retryCount', width: 100 },
            { title: '错误', dataIndex: 'errorMessage', ellipsis: true, render: (value) => <Text type="secondary">{value || '-'}</Text> },
            {
              title: '操作',
              width: 120,
              render: (_, record: OutboxEvent) => (
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
            <Text type="secondary">dry-run 只统计，不会删除数据</Text>
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
                { title: '运行说明', dataIndex: 'operatingLine', render: (value) => <Text type="secondary">{value}</Text> }
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
                <Descriptions.Item label="企业 SSO">
                  {securityStatus?.jwt?.enabled ? <Tag color="green">已启用</Tag> : <Tag>可启用</Tag>}
                </Descriptions.Item>
                <Descriptions.Item label="Agent Chat">{securityStatus?.rateLimit?.agentCapacity ?? '-'} / min</Descriptions.Item>
                <Descriptions.Item label="Model Chat">{securityStatus?.rateLimit?.modelCapacity ?? '-'} / min</Descriptions.Item>
                <Descriptions.Item label="普通 API">{securityStatus?.rateLimit?.defaultCapacity ?? '-'} / min</Descriptions.Item>
              </Descriptions>
            </Card>

            <Card className="command-card" title="事件可靠性">
              <Descriptions size="small" bordered column={1}>
                <Descriptions.Item label="模式">{statusTag(eventStatus?.mode ?? 'log-only')}</Descriptions.Item>
                <Descriptions.Item label="待分发">{eventStatus?.outboxPending ?? 0}</Descriptions.Item>
                <Descriptions.Item label="分发中">{eventStatus?.outboxDispatching ?? 0}</Descriptions.Item>
                <Descriptions.Item label="死信">{deadLetterCount}</Descriptions.Item>
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
                  <Text type="secondary">Agent Chat 和 Model Chat 会调用模型，成本和延迟都更高。限流可以防止误操作或恶意请求打爆模型费用和数据库连接。</Text>
                </Space>
              )
            },
            {
              dot: <ClusterOutlined />,
              children: (
                <Space direction="vertical" size={2}>
                  <Text strong>Outbox 为什么要 CAS？</Text>
                  <Text type="secondary">afterCommit 和定时补偿任务可能同时看到同一条 PENDING 事件。CAS 先抢占成 DISPATCHING，只让抢到锁的 worker 发送。</Text>
                </Space>
              )
            },
            {
              dot: <LockOutlined />,
              children: (
                <Space direction="vertical" size={2}>
                  <Text strong>RBAC token 和企业 JWT 有什么区别？</Text>
                  <Text type="secondary">RBAC token 适合系统内部令牌；企业 JWT 适合接入统一身份源，由 IdP 负责账号生命周期、MFA 和登录审计。</Text>
                </Space>
              )
            },
            {
              dot: <DatabaseOutlined />,
              children: (
                <Space direction="vertical" size={2}>
                  <Text strong>这些能力怎么和业务连起来？</Text>
                  <Text type="secondary">RBAC 限制数据范围，限流保护模型和数据库，Outbox 保证 CRM 写入后的事件可重试可恢复。</Text>
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
              { title: '工具名', render: (_, record: OpenAiToolDefinition) => <Text code>{record.function.name}</Text> },
              { title: '用途', render: (_, record: OpenAiToolDefinition) => record.function.description },
              {
                title: '写入风险',
                width: 120,
                render: (_, record: OpenAiToolDefinition) =>
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

      <Modal
        title={editingTenant ? '编辑租户' : '开通租户'}
        open={tenantModalOpen}
        okText={editingTenant ? '保存变更' : '开通'}
        cancelText="取消"
        confirmLoading={loading}
        onOk={saveTenant}
        onCancel={() => {
          setTenantModalOpen(false);
          setEditingTenant(null);
        }}
      >
        <Form form={tenantForm} layout="vertical" initialValues={{ planCode: 'standard' }}>
          <Form.Item
            name="id"
            label="租户 ID"
            tooltip="用于数据隔离、JWT tenant claim 和审计追踪。创建后不建议修改。"
            rules={[
              { required: !editingTenant, message: '请输入租户 ID' },
              { pattern: /^[a-zA-Z0-9][a-zA-Z0-9_-]{1,63}$/, message: '2-64 位，只能包含字母、数字、下划线和短横线' }
            ]}
          >
            <Input disabled={Boolean(editingTenant)} placeholder="例如 tenant_demo" />
          </Form.Item>
          <Form.Item name="name" label="企业名称" rules={[{ required: true, message: '请输入企业名称' }]}>
            <Input placeholder="例如 智享生活演示企业" />
          </Form.Item>
          <Form.Item name="planCode" label="套餐版本" rules={[{ required: true, message: '请选择套餐版本' }]}>
            <Select
              options={[
                { label: 'Standard', value: 'standard' },
                { label: 'Professional', value: 'professional' },
                { label: 'Enterprise', value: 'enterprise' }
              ]}
            />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="租户是商业化部署的数据边界"
            description="RBAC 用户、JWT 登录、CRM 数据和审计记录都会挂在租户维度下。停用租户后，该企业用户无法继续访问业务接口。"
          />
        </Form>
      </Modal>
    </Space>
  );
}
