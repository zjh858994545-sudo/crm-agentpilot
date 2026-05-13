import { ExperimentOutlined, FileTextOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Progress, Space, Table, Tag, Typography } from 'antd';
import { useState } from 'react';
import { EvaluationMetric, EvaluationReport, runEvaluation } from '../../api/client';

const { Paragraph, Text } = Typography;

const metricDescriptions: Record<string, string> = {
  'RAG Recall@5': '知识检索前 5 条是否命中预期关键词',
  'Citation Hit Rate': '回答是否带有可追溯引用',
  'Refusal Accuracy': '跨领域问题是否拒答',
  'Tool Calling Success Rate': 'Agent 是否调用了预期工具',
  'Write Confirmation Coverage': '写操作是否全部进入人工确认流',
  'Average Latency': '评测用例平均耗时',
  'P95 Latency': '评测用例 P95 耗时'
};

function formatValue(metric: EvaluationMetric) {
  if (metric.unit === 'ratio') {
    return `${Math.round(metric.value * 100)}%`;
  }
  return `${metric.value} ${metric.unit}`;
}

function progressValue(metric: EvaluationMetric) {
  if (metric.unit === 'ratio') {
    return Math.round(metric.value * 100);
  }
  return Math.min(100, Math.round(metric.value));
}

export default function Evaluation() {
  const [report, setReport] = useState<EvaluationReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const run = async () => {
    setLoading(true);
    setError('');
    try {
      const nextReport = await runEvaluation();
      setReport(nextReport);
    } catch {
      setError('评测运行失败，请确认后端服务已启动，且 eval JSONL 文件存在。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}
      <Card
        title="评测原则"
        extra={
          <Button type="primary" icon={<ExperimentOutlined />} loading={loading} onClick={run}>
            运行评测
          </Button>
        }
      >
        <Paragraph style={{ marginBottom: 0 }}>
          这些指标来自后端真实执行：读取 JSONL 评测集，调用 RAG、Agent 和确认流，再生成 Markdown 报告。
          面试时可以强调这不是写死的漂亮数字，而是可重复运行的工程评测。
        </Paragraph>
      </Card>

      {report && (
        <Card title="本次报告">
          <Space direction="vertical" size={8}>
            <Text>
              <FileTextOutlined /> {report.reportName}
            </Text>
            <Text type="secondary">生成时间：{report.generatedAt}</Text>
            <Text type="secondary">报告路径：{report.reportPath}</Text>
          </Space>
        </Card>
      )}

      <Card title="指标看板">
        <Table
          rowKey="name"
          loading={loading}
          dataSource={report?.metrics ?? []}
          locale={{ emptyText: '点击“运行评测”生成当前指标' }}
          pagination={false}
          columns={[
            { title: '指标', dataIndex: 'name', width: 230 },
            {
              title: '当前值',
              render: (_, metric: EvaluationMetric) => (
                <Space style={{ width: '100%' }}>
                  <Progress percent={progressValue(metric)} size="small" style={{ width: 180 }} />
                  <Text>{formatValue(metric)}</Text>
                </Space>
              )
            },
            { title: '单位', dataIndex: 'unit', width: 100, render: (value) => <Tag>{value}</Tag> },
            {
              title: '说明',
              render: (_, metric: EvaluationMetric) => (
                <Text type="secondary">{metricDescriptions[metric.name] ?? '评测脚本输出指标'}</Text>
              )
            }
          ]}
        />
      </Card>
    </Space>
  );
}
