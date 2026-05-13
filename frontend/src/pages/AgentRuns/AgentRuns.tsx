import { ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Descriptions, Drawer, Space, Table, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  AgentRun,
  AgentToolCall,
  fetchAgentRuns,
  fetchAgentRunToolCalls,
  sendAgentMessage
} from '../../api/client';

const { Paragraph, Text } = Typography;

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

export default function AgentRuns() {
  const [runs, setRuns] = useState<AgentRun[]>([]);
  const [selectedRun, setSelectedRun] = useState<AgentRun | null>(null);
  const [toolCalls, setToolCalls] = useState<AgentToolCall[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const totalToolCalls = useMemo(() => toolCalls.length, [toolCalls]);

  const loadRuns = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await fetchAgentRuns();
      setRuns(data);
    } catch {
      setError('读取 Agent 运行记录失败，请确认后端服务已启动。');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRuns();
  }, []);

  const openRun = async (run: AgentRun) => {
    setSelectedRun(run);
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

  const createDemoRun = async () => {
    setLoading(true);
    setError('');
    try {
      await sendAgentMessage('今天我应该优先跟进哪些客户？请给出推荐理由和下一步动作。');
      await loadRuns();
      message.success('已生成一条演示运行记录');
    } catch {
      setError('生成演示运行失败，请确认后端服务已启动。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Card
        title="Agent 运行审计"
        extra={
          <Space>
            <Button icon={<ThunderboltOutlined />} loading={loading} onClick={createDemoRun}>
              生成演示运行
            </Button>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={loadRuns}>
              刷新
            </Button>
          </Space>
        }
      >
        {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 12 }} />}
        <Table
          rowKey="id"
          loading={loading}
          dataSource={runs}
          pagination={{ pageSize: 8 }}
          onRow={(record) => ({ onClick: () => openRun(record) })}
          columns={[
            { title: 'Run ID', dataIndex: 'id', width: 90 },
            { title: '会话', dataIndex: 'sessionId', width: 90 },
            { title: '意图', dataIndex: 'intent', width: 180 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 130,
              render: (value) => <Tag color={statusColor[value] ?? 'blue'}>{value}</Tag>
            },
            { title: '模型', dataIndex: 'modelName', width: 150 },
            { title: '耗时', dataIndex: 'latencyMs', width: 100, render: (value) => (value ? `${value} ms` : '-') },
            {
              title: '用户输入',
              dataIndex: 'userInput',
              render: (value) => <Text ellipsis style={{ maxWidth: 360 }}>{value}</Text>
            },
            { title: '完成时间', dataIndex: 'completedAt', width: 190 }
          ]}
        />
      </Card>

      <Drawer
        width={720}
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
              <Descriptions.Item label="工具调用">{totalToolCalls}</Descriptions.Item>
              <Descriptions.Item label="耗时">{selectedRun.latencyMs ? `${selectedRun.latencyMs} ms` : '-'}</Descriptions.Item>
            </Descriptions>
            <Card size="small" title="用户输入">
              <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{selectedRun.userInput}</Paragraph>
            </Card>
            <Card size="small" title="Agent 输出">
              <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{selectedRun.agentOutput || '-'}</Paragraph>
            </Card>
            <Card size="small" title="工具调用明细">
              <Table
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={toolCalls}
                expandable={{
                  expandedRowRender: (record) => (
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <Text strong>Input</Text>
                      <pre className="json-preview">{previewJson(record.inputJson)}</pre>
                      <Text strong>Output</Text>
                      <pre className="json-preview">{previewJson(record.outputJson)}</pre>
                    </Space>
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
                    width: 90,
                    render: (value) => (value ? <Tag color="orange">需要</Tag> : <Tag>无需</Tag>)
                  },
                  { title: '耗时', dataIndex: 'latencyMs', width: 90, render: (value) => (value ? `${value} ms` : '-') }
                ]}
              />
            </Card>
          </Space>
        )}
      </Drawer>
    </>
  );
}
