import {
  ClockCircleOutlined,
  CodeOutlined,
  DeploymentUnitOutlined,
  ReloadOutlined,
  ThunderboltOutlined
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Progress,
  Segmented,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Timeline,
  Typography,
  message
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  AgentRun,
  AgentToolCall,
  fetchAgentRuns,
  fetchAgentRunToolCalls,
  sendAgentMessage
} from '../../api/client';

const { Paragraph, Text, Title } = Typography;

const statusColor: Record<string, string> = {
  SUCCESS: 'green',
  NEED_CONFIRMATION: 'orange',
  FAILED: 'red',
  REJECTED: 'default',
  CONFIRMED: 'blue'
};

function previewJson(value?: string) {
  if (!value) {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

function latencyPercent(value?: number) {
  if (!value) {
    return 0;
  }
  return Math.max(0, Math.min(100, Math.round(100 - value / 300)));
}

export default function AgentRuns() {
  const [runs, setRuns] = useState<AgentRun[]>([]);
  const [selectedRun, setSelectedRun] = useState<AgentRun | null>(null);
  const [toolCalls, setToolCalls] = useState<AgentToolCall[]>([]);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const filteredRuns = useMemo(
    () => runs.filter((run) => statusFilter === 'ALL' || run.status === statusFilter),
    [runs, statusFilter]
  );

  const metrics = useMemo(() => {
    const success = runs.filter((run) => run.status === 'SUCCESS').length;
    const needConfirm = runs.filter((run) => run.status === 'NEED_CONFIRMATION').length;
    const avgLatency = runs.length
      ? runs.reduce((sum, run) => sum + Number(run.latencyMs || 0), 0) / runs.length
      : 0;
    return { total: runs.length, success, needConfirm, avgLatency };
  }, [runs]);

  const loadRuns = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await fetchAgentRuns();
      setRuns(data);
    } catch {
      setError('读取 Agent 运行记录失败，请确认后端服务已经启动。');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRuns();
  }, []);

  const openRun = async (run: AgentRun) => {
    setSelectedRun(run);
    setToolCalls([]);
    setLoading(true);
    setError('');
    try {
      const data = await fetchAgentRunToolCalls(run.id);
      setToolCalls(data);
    } catch {
      setError('读取工具调用轨迹失败。');
    } finally {
      setLoading(false);
    }
  };

  const createSampleRun = async () => {
    setLoading(true);
    setError('');
    try {
      await sendAgentMessage('今天我应该优先跟进哪些客户？请给出推荐理由和下一步动作。');
      await loadRuns();
      message.success('已生成一条运行记录');
    } catch {
      setError('生成运行记录失败，请确认后端服务已经启动。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <Space className="toolbar-line" wrap>
        <Button icon={<ThunderboltOutlined />} loading={loading} onClick={createSampleRun}>
          生成运行记录
        </Button>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={loadRuns}>
          刷新
        </Button>
        <Segmented
          value={statusFilter}
          onChange={(value) => setStatusFilter(String(value))}
          options={[
            { label: '全部', value: 'ALL' },
            { label: '成功', value: 'SUCCESS' },
            { label: '待确认', value: 'NEED_CONFIRMATION' },
            { label: '失败', value: 'FAILED' }
          ]}
        />
      </Space>

      <div className="audit-metrics">
        <Card className="metric-card">
          <Statistic title="Run 总数" value={metrics.total} prefix={<DeploymentUnitOutlined />} />
          <Text className="metric-label">Agent 对话运行记录</Text>
        </Card>
        <Card className="metric-card">
          <Statistic title="成功运行" value={metrics.success} />
          <Text className="metric-label">状态 SUCCESS</Text>
        </Card>
        <Card className="metric-card">
          <Statistic title="待确认" value={metrics.needConfirm} />
          <Text className="metric-label">写操作 confirmation</Text>
        </Card>
        <Card className="metric-card">
          <Statistic title="平均耗时" value={Math.round(metrics.avgLatency)} suffix="ms" prefix={<ClockCircleOutlined />} />
          <Text className="metric-label">Run latency</Text>
        </Card>
      </div>

      <Card className="command-card" title="Agent Run 审计列表">
        <Table
          rowKey="id"
          loading={loading}
          dataSource={filteredRuns}
          pagination={{ pageSize: 8 }}
          onRow={(record) => ({ onClick: () => openRun(record) })}
          rowClassName="clickable-table-row"
          columns={[
            { title: 'Run ID', dataIndex: 'id', width: 90 },
            { title: 'Session', dataIndex: 'sessionId', width: 90 },
            { title: '意图', dataIndex: 'intent', width: 210 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 130,
              render: (value) => <Tag color={statusColor[value] ?? 'blue'}>{value}</Tag>
            },
            { title: '模型', dataIndex: 'modelName', width: 170 },
            {
              title: '耗时',
              dataIndex: 'latencyMs',
              width: 160,
              render: (value) => (
                <Space>
                  <Progress percent={latencyPercent(value)} size="small" style={{ width: 80 }} />
                  <Text>{value ? `${value} ms` : '-'}</Text>
                </Space>
              )
            },
            {
              title: '用户输入',
              dataIndex: 'userInput',
              render: (value) => <Text ellipsis style={{ maxWidth: 360 }}>{value}</Text>
            },
            { title: '完成时间', dataIndex: 'completedAt', width: 190, render: formatTime }
          ]}
        />
      </Card>

      <Drawer
        width={860}
        open={Boolean(selectedRun)}
        onClose={() => setSelectedRun(null)}
        title={selectedRun ? `运行详情 #${selectedRun.id}` : '运行详情'}
      >
        {selectedRun && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="意图">{selectedRun.intent}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColor[selectedRun.status] ?? 'blue'}>{selectedRun.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Session">{selectedRun.sessionId}</Descriptions.Item>
              <Descriptions.Item label="模型">{selectedRun.modelName}</Descriptions.Item>
              <Descriptions.Item label="耗时">{selectedRun.latencyMs ? `${selectedRun.latencyMs} ms` : '-'}</Descriptions.Item>
              <Descriptions.Item label="完成时间">{formatTime(selectedRun.completedAt)}</Descriptions.Item>
            </Descriptions>

            <Card size="small" title="Trace 展开">
              <Timeline
                items={[
                  {
                    dot: <DeploymentUnitOutlined />,
                    children: (
                      <Space direction="vertical" size={2}>
                        <Text strong>创建 Agent Run</Text>
                        <Text type="secondary">Run #{selectedRun.id} / Session #{selectedRun.sessionId}</Text>
                      </Space>
                    )
                  },
                  ...toolCalls.map((call) => ({
                    dot: <CodeOutlined />,
                    children: (
                      <Space direction="vertical" size={2}>
                        <Space>
                          <Text strong>{call.toolName}</Text>
                          <Tag color={call.toolType === 'WRITE' ? 'orange' : 'green'}>{call.toolType}</Tag>
                          <Tag color={statusColor[call.status] ?? 'blue'}>{call.status}</Tag>
                        </Space>
                        <Text type="secondary">latency={call.latencyMs ?? 0} ms</Text>
                      </Space>
                    )
                  })),
                  {
                    dot: <ClockCircleOutlined />,
                    children: (
                      <Space direction="vertical" size={2}>
                        <Text strong>完成输出</Text>
                        <Text type="secondary">{formatTime(selectedRun.completedAt)}</Text>
                      </Space>
                    )
                  }
                ]}
              />
            </Card>

            <Tabs
              items={[
                {
                  key: 'io',
                  label: '输入 / 输出',
                  children: (
                    <Space direction="vertical" size={12} style={{ width: '100%' }}>
                      <Card size="small" title="用户输入">
                        <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{selectedRun.userInput}</Paragraph>
                      </Card>
                      <Card size="small" title="Agent 输出">
                        <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{selectedRun.agentOutput || '-'}</Paragraph>
                      </Card>
                    </Space>
                  )
                },
                {
                  key: 'tools',
                  label: `Tool Calls (${toolCalls.length})`,
                  children: toolCalls.length === 0 ? (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无工具调用" />
                  ) : (
                    <Table
                      rowKey="id"
                      size="small"
                      pagination={false}
                      dataSource={toolCalls}
                      expandable={{
                        expandedRowRender: (record) => (
                          <Tabs
                            size="small"
                            items={[
                              {
                                key: 'input',
                                label: 'Input JSON',
                                children: <pre className="json-preview">{previewJson(record.inputJson)}</pre>
                              },
                              {
                                key: 'output',
                                label: 'Output JSON',
                                children: <pre className="json-preview">{previewJson(record.outputJson)}</pre>
                              }
                            ]}
                          />
                        )
                      }}
                      columns={[
                        { title: '工具', dataIndex: 'toolName' },
                        {
                          title: '类型',
                          dataIndex: 'toolType',
                          width: 90,
                          render: (value) => <Tag color={value === 'WRITE' ? 'orange' : 'green'}>{value}</Tag>
                        },
                        {
                          title: '状态',
                          dataIndex: 'status',
                          width: 140,
                          render: (value) => <Tag color={statusColor[value] ?? 'blue'}>{value}</Tag>
                        },
                        {
                          title: '确认',
                          dataIndex: 'requiresConfirmation',
                          width: 100,
                          render: (value) => (value ? <Tag color="orange">需要</Tag> : <Tag>无需</Tag>)
                        },
                        { title: '耗时', dataIndex: 'latencyMs', width: 100, render: (value) => (value ? `${value} ms` : '-') }
                      ]}
                    />
                  )
                },
                {
                  key: 'debug',
                  label: 'Debug',
                  children: (
                    <Card size="small">
                      <Title level={5}>排查入口</Title>
                      <Paragraph>
                        这条 Run 可以通过 Run ID、Session ID、Tool Call 明细和后端 X-Trace-Id 日志进行排查。当前页面展示的是数据库审计表中的运行事实。
                      </Paragraph>
                    </Card>
                  )
                }
              ]}
            />
          </Space>
        )}
      </Drawer>
    </Space>
  );
}
