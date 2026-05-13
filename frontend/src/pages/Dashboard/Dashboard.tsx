import { Alert, Card, Progress, Space, Statistic, Table, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { EventStatus, fetchEventStatus, fetchHealth, fetchModelStatus, HealthView, ModelStatus } from '../../api/client';

const { Text } = Typography;

const moduleRows = [
  { key: 'crm', module: 'CRM Core', status: '已完成', owner: '客户、商机、任务、产品包与联系记录' },
  { key: 'scoring', module: 'Lead Scoring', status: '已完成', owner: '规则打分、推荐理由、下一步动作' },
  { key: 'rag', module: 'RAG', status: '已完成', owner: '混合检索、引用、拒答、知识导入' },
  { key: 'agent', module: 'Agent Orchestrator', status: '已完成', owner: '工具调用、确认流、审计轨迹' },
  { key: 'callcenter', module: 'Call Center', status: '已完成', owner: '摘要、质检、客户记忆、联系记录确认' },
  { key: 'eval', module: 'Evaluation', status: '已完成', owner: 'JSONL 评测集与报告生成' }
];

export default function Dashboard() {
  const [health, setHealth] = useState<HealthView | null>(null);
  const [modelStatus, setModelStatus] = useState<ModelStatus | null>(null);
  const [eventStatus, setEventStatus] = useState<EventStatus | null>(null);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    fetchHealth()
      .then(setHealth)
      .catch(() => setError('后端未连接，当前显示前端静态工作台。'));
    fetchModelStatus().then(setModelStatus).catch(() => undefined);
    fetchEventStatus().then(setEventStatus).catch(() => undefined);
  }, []);

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
      detail: modelStatus?.configured ? modelStatus.model : 'Mock mode, 可切 OpenAI-compatible'
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
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}
      <div className="page-grid">
        <Card className="span-3">
          <Statistic title="系统状态" value={health?.status ?? 'LOCAL'} />
          <Text className="metric-label">后端 health check</Text>
        </Card>
        <Card className="span-3">
          <Statistic title="开发阶段" value={health?.phase ?? 'demo-ready'} />
          <Text className="metric-label">面试演示闭环</Text>
        </Card>
        <Card className="span-3">
          <Statistic title="模型模式" value={modelStatus?.mode ?? health?.modelProvider ?? 'mock'} />
          <Text className="metric-label">{modelStatus?.model ?? 'OpenAI-compatible adapter'}</Text>
        </Card>
        <Card className="span-3">
          <Statistic title="事件总线" value={eventStatus?.mode ?? 'log-only'} />
          <Text className="metric-label">Kafka topic 预留与可观测事件</Text>
        </Card>
        <Card className="span-3">
          <Statistic title="演示场景" value={6} suffix="个" />
          <Text className="metric-label">推荐、分析、确认、知识库、质检、评测</Text>
        </Card>
        <Card className="span-12" title="运行探针">
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
                render: (value: string) => <Tag color={value === 'LOCAL' ? 'orange' : 'blue'}>{value}</Tag>
              },
              { title: '说明', dataIndex: 'detail' }
            ]}
          />
        </Card>

        <Card className="span-8" title="交付路线">
          <Table
            size="small"
            pagination={false}
            dataSource={moduleRows}
            columns={[
              { title: '模块', dataIndex: 'module' },
              {
                title: '状态',
                dataIndex: 'status',
                width: 110,
                render: (value: string) => <Tag color="green">{value}</Tag>
              },
              { title: '面试展示点', dataIndex: 'owner' }
            ]}
          />
        </Card>

        <Card className="span-4" title="完整度">
          <Space direction="vertical" style={{ width: '100%' }} size={14}>
            <div>
              <Text>工程骨架</Text>
              <Progress percent={100} size="small" />
            </div>
            <div>
              <Text>CRM 业务流</Text>
              <Progress percent={100} size="small" />
            </div>
            <div>
              <Text>RAG 检索</Text>
              <Progress percent={100} size="small" />
            </div>
            <div>
              <Text>Agent 确认流</Text>
              <Progress percent={100} size="small" />
            </div>
            <div>
              <Text>评测与文档</Text>
              <Progress percent={100} size="small" />
            </div>
          </Space>
        </Card>
      </div>
    </Space>
  );
}
