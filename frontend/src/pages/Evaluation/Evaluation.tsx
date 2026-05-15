import {
  CheckCircleOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  LineChartOutlined,
  WarningOutlined
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography
} from 'antd';
import { useMemo, useState } from 'react';
import { EvaluationMetric, EvaluationReport, runEvaluation } from '../../api/client';

const { Paragraph, Text, Title } = Typography;

const metricDescriptions: Record<string, string> = {
  'RAG Recall@5': '知识检索前 5 条是否命中预期关键词',
  'Citation Hit Rate': '回答是否带有可追溯引用',
  'Refusal Accuracy': '跨领域问题是否拒答',
  'Tool Calling Success Rate': 'Agent 是否调用了预期工具',
  'Write Confirmation Coverage': '写操作是否全部进入人工确认流',
  'Average Latency': '评测用例平均耗时',
  'P95 Latency': '评测用例 P95 耗时'
};

const caseGroups = [
  { key: 'rag', name: 'RAG 检索', metric: 'RAG Recall@5', scope: '销售 SOP / 套餐政策 / 质检规则' },
  { key: 'citation', name: '引用生成', metric: 'Citation Hit Rate', scope: '答案引用来源可追溯' },
  { key: 'refusal', name: '拒答策略', metric: 'Refusal Accuracy', scope: '跨领域问题不胡编' },
  { key: 'tool', name: '工具调用', metric: 'Tool Calling Success Rate', scope: 'Agent 意图与工具选择' },
  { key: 'write', name: '写操作确认', metric: 'Write Confirmation Coverage', scope: '创建任务 / 写联系记录 / 改商机阶段' }
];

function formatValue(metric?: EvaluationMetric) {
  if (!metric) {
    return '-';
  }
  if (metric.unit === 'ratio') {
    return `${Math.round(metric.value * 100)}%`;
  }
  return `${Math.round(metric.value)} ${metric.unit}`;
}

function progressValue(metric?: EvaluationMetric) {
  if (!metric) {
    return 0;
  }
  if (metric.unit === 'ratio') {
    return Math.round(metric.value * 100);
  }
  if (metric.unit === 'ms') {
    return Math.max(0, Math.min(100, Math.round(100 - metric.value / 300)));
  }
  return Math.min(100, Math.round(metric.value));
}

function metricLevel(metric?: EvaluationMetric) {
  if (!metric) {
    return '未运行';
  }
  if (metric.unit === 'ratio') {
    if (metric.value >= 0.9) return '通过';
    if (metric.value >= 0.75) return '关注';
    return '失败';
  }
  if (metric.unit === 'ms') {
    if (metric.value <= 5000) return '通过';
    if (metric.value <= 15000) return '关注';
    return '失败';
  }
  return '通过';
}

function levelColor(level: string) {
  if (level === '通过') return 'green';
  if (level === '关注') return 'orange';
  if (level === '失败') return 'red';
  return 'default';
}

export default function Evaluation() {
  const [report, setReport] = useState<EvaluationReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const metricMap = useMemo(
    () =>
      (report?.metrics ?? []).reduce<Record<string, EvaluationMetric>>((acc, metric) => {
        acc[metric.name] = metric;
        return acc;
      }, {}),
    [report]
  );

  const ratioMetrics = useMemo(
    () => (report?.metrics ?? []).filter((metric) => metric.unit === 'ratio'),
    [report]
  );

  const latencyMetrics = useMemo(
    () => (report?.metrics ?? []).filter((metric) => metric.unit === 'ms'),
    [report]
  );

  const failures = useMemo(
    () =>
      (report?.metrics ?? [])
        .map((metric) => ({ metric, level: metricLevel(metric) }))
        .filter((item) => item.level !== '通过'),
    [report]
  );

  const run = async () => {
    setLoading(true);
    setError('');
    try {
      const nextReport = await runEvaluation();
      setReport(nextReport);
    } catch {
      setError('评测运行失败，请确认后端服务已启动，并且 eval JSONL 文件存在。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <Card
        className="command-card"
        title="评测控制台"
        extra={
          <Button type="primary" icon={<ExperimentOutlined />} loading={loading} onClick={run}>
            运行评测
          </Button>
        }
      >
        <Paragraph style={{ marginBottom: 0 }}>
          评测由后端读取 JSONL 用例，真实调用 RAG、Agent 和写操作确认流后生成 Markdown 报告。这里展示的是本次运行的聚合指标、用例分组和需要关注的失败项。
        </Paragraph>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card className="metric-card">
            <Statistic title="指标数量" value={report?.metrics.length ?? 0} prefix={<LineChartOutlined />} />
            <Text className="metric-label">RAG / Tool Calling / Confirmation / Latency</Text>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card className="metric-card">
            <Statistic title="通过率指标" value={ratioMetrics.length} />
            <Text className="metric-label">以 ratio 形式输出</Text>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card className="metric-card">
            <Statistic title="延迟指标" value={latencyMetrics.length} />
            <Text className="metric-label">Average / P95</Text>
          </Card>
        </Col>
      </Row>

      {report && (
        <Card className="command-card" title="本次报告">
          <Space direction="vertical" size={8}>
            <Text>
              <FileTextOutlined /> {report.reportName}
            </Text>
            <Text type="secondary">生成时间：{report.generatedAt}</Text>
            <Text type="secondary">报告路径：{report.reportPath}</Text>
          </Space>
        </Card>
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card className="command-card" title="指标趋势">
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
                { title: '状态', width: 100, render: (_, metric) => <Tag color={levelColor(metricLevel(metric))}>{metricLevel(metric)}</Tag> },
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
        </Col>
        <Col xs={24} xl={10}>
          <Card className="command-card" title="失败样例 / 关注项">
            {report ? (
              failures.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本次聚合指标均达标，暂无失败项" />
              ) : (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  {failures.map(({ metric, level }) => (
                    <div className="failure-item" key={metric.name}>
                      <Space>
                        <WarningOutlined />
                        <Text strong>{metric.name}</Text>
                        <Tag color={levelColor(level)}>{level}</Tag>
                      </Space>
                      <Text type="secondary">
                        当前值 {formatValue(metric)}，建议查看后端生成的 Markdown 报告和对应 JSONL 用例。
                      </Text>
                    </div>
                  ))}
                </Space>
              )
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="运行评测后展示失败项" />
            )}
          </Card>
        </Col>
      </Row>

      <Card className="command-card" title="用例分组明细">
        <Table
          rowKey="key"
          pagination={false}
          dataSource={caseGroups}
          columns={[
            { title: '分组', dataIndex: 'name', width: 180 },
            { title: '覆盖范围', dataIndex: 'scope' },
            {
              title: '关联指标',
              dataIndex: 'metric',
              width: 230,
              render: (value) => <Text>{value}</Text>
            },
            {
              title: '本次结果',
              width: 180,
              render: (_, row) => {
                const metric = metricMap[row.metric];
                const level = metricLevel(metric);
                return (
                  <Space>
                    <Tag color={levelColor(level)}>{level}</Tag>
                    <Text>{formatValue(metric)}</Text>
                  </Space>
                );
              }
            }
          ]}
        />
      </Card>

      <Card className="command-card" title="评测链路">
        <Timeline
          items={[
            { dot: <FileTextOutlined />, children: '读取 eval/*.jsonl 用例，区分 RAG、工具调用、写操作确认等场景。' },
            { dot: <ExperimentOutlined />, children: '调用真实服务链路，记录命中率、引用、拒答和延迟。' },
            { dot: <CheckCircleOutlined />, children: '生成 Markdown 报告，并将聚合指标返回前端。' }
          ]}
        />
      </Card>
    </Space>
  );
}
