import {
  BookOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  ImportOutlined,
  LinkOutlined,
  ReloadOutlined,
  SearchOutlined
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Divider,
  Empty,
  Input,
  List,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  Typography,
  message
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  askKnowledge,
  fetchKnowledgeChunks,
  fetchKnowledgeDocs,
  fetchKnowledgeStatus,
  importKnowledgeDoc,
  KnowledgeAnswer,
  KnowledgeChunk,
  KnowledgeDoc,
  KnowledgeItem,
  KnowledgeStatus,
  rebuildKnowledgeVectors,
  searchKnowledge
} from '../../api/client';

const { Paragraph, Text, Title } = Typography;
const { TextArea } = Input;

const defaultImportDraft = `客户提出“套餐太贵、续费没有效果”时，销售需要先复述客户顾虑，再用上月曝光量、咨询量、同行案例说明投入产出。
禁止承诺一定成交、保证排名或保证收益。可以承诺提供数据复盘、优化门店页、调整投放关键词和约定下次复盘时间。`;

const docTypeOptions = [
  { value: 'OBJECTION_HANDLING', label: '异议处理' },
  { value: 'QUALITY_CHECK', label: '质检规则' },
  { value: 'PRODUCT_POLICY', label: '产品政策' },
  { value: 'SALES_SOP', label: '销售 SOP' }
];

function scorePercent(score: number) {
  return `${Math.round(score * 100)}%`;
}

function currentWorkspaceRole() {
  try {
    const stored = JSON.parse(window.localStorage.getItem('agentpilot.currentUser') || '{}') as {
      primaryRole?: string;
      roles?: string[];
    };
    if (stored.primaryRole) {
      return stored.primaryRole;
    }
    if (stored.roles?.includes('system_admin')) {
      return 'admin';
    }
    if (stored.roles?.includes('sales_manager')) {
      return 'manager';
    }
    return stored.roles?.[0];
  } catch {
    return undefined;
  }
}

export default function KnowledgeBase() {
  const [docs, setDocs] = useState<KnowledgeDoc[]>([]);
  const [knowledgeStatus, setKnowledgeStatus] = useState<KnowledgeStatus | null>(null);
  const [selectedDocId, setSelectedDocId] = useState<number>();
  const [chunks, setChunks] = useState<KnowledgeChunk[]>([]);
  const [query, setQuery] = useState('客户嫌套餐贵，销售应该怎么处理？');
  const [question, setQuestion] = useState('遇到客户说续费太贵没有效果，应该如何回复？');
  const [searchResult, setSearchResult] = useState<KnowledgeItem[]>([]);
  const [selectedEvidence, setSelectedEvidence] = useState<KnowledgeItem | null>(null);
  const [answer, setAnswer] = useState<KnowledgeAnswer | null>(null);
  const [importTitle, setImportTitle] = useState('价格异议补充话术');
  const [importType, setImportType] = useState('OBJECTION_HANDLING');
  const [importContent, setImportContent] = useState(defaultImportDraft);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const selectedDoc = useMemo(() => docs.find((item) => item.id === selectedDocId), [docs, selectedDocId]);
  const canManageKnowledge = currentWorkspaceRole() === 'admin';
  const vectorPercent = knowledgeStatus?.chunkCount
    ? Math.round((knowledgeStatus.vectorizedChunkCount / knowledgeStatus.chunkCount) * 100)
    : 0;
  const docTypeCount = useMemo(
    () => docs.reduce<Record<string, number>>((acc, doc) => {
      acc[doc.docType] = (acc[doc.docType] ?? 0) + 1;
      return acc;
    }, {}),
    [docs]
  );

  const loadDocs = async () => {
    const nextDocs = await fetchKnowledgeDocs();
    setDocs(nextDocs);
    setSelectedDocId((current) => current ?? nextDocs[0]?.id);
  };

  const loadStatus = async () => {
    setKnowledgeStatus(await fetchKnowledgeStatus());
  };

  useEffect(() => {
    Promise.all([loadDocs(), loadStatus()]).catch(() => setError('知识库接口暂不可用，请先启动后端服务。'));
  }, []);

  useEffect(() => {
    if (!selectedDocId) {
      setChunks([]);
      return;
    }
    fetchKnowledgeChunks(selectedDocId)
      .then(setChunks)
      .catch(() => setError('读取知识分块失败，请检查后端服务。'));
  }, [selectedDocId]);

  const runSearch = async () => {
    if (!query.trim()) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      const result = await searchKnowledge(query.trim(), 5);
      setSearchResult(result.items);
      setSelectedEvidence(result.items[0] ?? null);
      message.success(`检索完成，改写查询：${result.rewrittenQuery}`);
    } catch {
      setError('检索失败，请确认后端和数据库已经启动。');
    } finally {
      setLoading(false);
    }
  };

  const runAsk = async () => {
    if (!question.trim()) {
      return;
    }
    setLoading(true);
    setError('');
    try {
      const result = await askKnowledge(question.trim(), 5);
      setAnswer(result);
    } catch {
      setError('问答失败，请确认后端和数据库已经启动。');
    } finally {
      setLoading(false);
    }
  };

  const submitImport = async () => {
    if (!canManageKnowledge) {
      setError('当前身份只能查询知识库；导入和重建索引需要切换到系统管理员。');
      return;
    }
    if (!importTitle.trim() || !importType.trim() || !importContent.trim()) {
      setError('标题、类型和正文都不能为空。');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const created = await importKnowledgeDoc({
        title: importTitle.trim(),
        docType: importType,
        content: importContent.trim()
      });
      await loadDocs();
      await loadStatus();
      setSelectedDocId(created.id);
      message.success('文档已导入并自动分块');
    } catch {
      setError('导入失败，请确认后端和数据库已经启动。');
    } finally {
      setLoading(false);
    }
  };

  const rebuildVectors = async () => {
    if (!canManageKnowledge) {
      setError('重建向量需要系统管理员权限。');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const result = await rebuildKnowledgeVectors();
      await loadStatus();
      message.success(`向量补齐完成，更新 ${result.updatedChunks} 个分块`);
    } catch {
      setError('重建向量失败，请确认当前身份拥有知识库写权限，且后端服务已启动。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card className="metric-card">
            <Statistic title="知识文档" value={knowledgeStatus?.docCount ?? docs.length} prefix={<BookOutlined />} />
            <Text className="metric-label">销售 SOP / 质检规则 / 产品政策</Text>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card className="metric-card">
            <Statistic title="知识分块" value={knowledgeStatus?.chunkCount ?? chunks.length} prefix={<FileSearchOutlined />} />
            <Text className="metric-label">当前文档：{selectedDoc?.title ?? '请选择文档'}</Text>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card className="metric-card">
            <Statistic title="知识索引进度" value={vectorPercent} suffix="%" prefix={<DatabaseOutlined />} />
            <Text className="metric-label">知识检索准备度</Text>
          </Card>
        </Col>
      </Row>

      <Card className="command-card" title="知识库管理闭环">
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} xl={10}>
            <Space direction="vertical" size={4}>
              <Text strong>检索索引状态</Text>
              <Text type="secondary">
                    文档导入后会自动切分并建立检索索引；如果迁移后出现索引缺失，管理员可以在这里补齐。
              </Text>
            </Space>
          </Col>
          <Col xs={24} xl={8}>
            <Progress
              percent={vectorPercent}
              status={knowledgeStatus?.pgvectorAvailable ? 'active' : 'normal'}
              strokeColor={knowledgeStatus?.pgvectorAvailable ? '#2563eb' : '#d97706'}
            />
            <Text className="metric-label">
              {knowledgeStatus?.vectorizedChunkCount ?? 0} / {knowledgeStatus?.chunkCount ?? 0} 个知识片段已建索引
            </Text>
          </Col>
          <Col xs={24} xl={6}>
            <Button
              block
              icon={<ReloadOutlined />}
              loading={loading}
              disabled={!canManageKnowledge}
              onClick={rebuildVectors}
            >
              补齐缺失索引
            </Button>
            {!canManageKnowledge && <Text className="metric-label">切换系统管理员后可执行恢复操作</Text>}
          </Col>
        </Row>
      </Card>

      <div className="knowledge-layout">
        <Card className="command-card" title="文档列表">
          <List
            dataSource={docs}
            locale={{ emptyText: '暂无文档，启动后端后会加载种子知识库' }}
            renderItem={(doc) => (
              <List.Item
                className={doc.id === selectedDocId ? 'selectable-row active' : 'selectable-row'}
                onClick={() => setSelectedDocId(doc.id)}
              >
                <Space align="start">
                  <BookOutlined style={{ marginTop: 4, color: '#2563eb' }} />
                  <div>
                    <Text strong>{doc.title}</Text>
                    <div style={{ marginTop: 6 }}>
                      <Tag color="blue">{doc.docType}</Tag>
                      <Tag>{doc.status}</Tag>
                      <Tag>{doc.source || 'seed'}</Tag>
                    </div>
                  </div>
                </Space>
              </List.Item>
            )}
          />
        </Card>

        <Card className="command-card" title={selectedDoc ? `分块预览 · ${selectedDoc.title}` : '分块预览'}>
          <List
            dataSource={chunks}
            locale={{ emptyText: '请选择左侧文档' }}
            renderItem={(chunk) => (
              <List.Item>
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Space>
                    <Tag>#{chunk.chunkIndex}</Tag>
                    <Text strong>{chunk.title}</Text>
                  </Space>
                  <Paragraph ellipsis={{ rows: 2, expandable: true, symbol: '展开' }} style={{ marginBottom: 0 }}>
                    {chunk.content}
                  </Paragraph>
                  {chunk.keywords && <Text type="secondary">关键词：{chunk.keywords}</Text>}
                </Space>
              </List.Item>
            )}
          />
        </Card>
      </div>

      <div className="knowledge-search-layout">
        <Card className="command-card" title="混合检索">
          <Space.Compact style={{ width: '100%' }}>
            <Input value={query} onChange={(event) => setQuery(event.target.value)} onPressEnter={runSearch} />
            <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={runSearch}>
              检索
            </Button>
          </Space.Compact>
          <Divider />
          <List
            dataSource={searchResult}
            locale={{ emptyText: '输入问题后查看检索结果' }}
            renderItem={(item) => (
              <List.Item
                className={selectedEvidence?.chunkId === item.chunkId ? 'selectable-row active' : 'selectable-row'}
                onClick={() => setSelectedEvidence(item)}
              >
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Space wrap>
                    <Tag color="green">{item.retriever}</Tag>
                    <Tag>{scorePercent(item.score)}</Tag>
                    <Text strong>{item.docTitle}</Text>
                  </Space>
                  <Paragraph ellipsis={{ rows: 2, expandable: true, symbol: '展开' }} style={{ marginBottom: 0 }}>
                    {item.content}
                  </Paragraph>
                </Space>
              </List.Item>
            )}
          />
        </Card>

        <Card className="command-card" title="引用预览">
          {selectedEvidence ? (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Space wrap>
                <Tag color="blue">{selectedEvidence.docType}</Tag>
                <Tag>{selectedEvidence.retriever}</Tag>
                <Tag>{scorePercent(selectedEvidence.score)}</Tag>
              </Space>
              <Title level={5}>{selectedEvidence.chunkTitle}</Title>
              <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{selectedEvidence.content}</Paragraph>
              <Text type="secondary">
                <LinkOutlined /> 来源：{selectedEvidence.docTitle}
              </Text>
            </Space>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="点击检索结果后预览引用证据" />
          )}
        </Card>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card className="command-card" title="知识问答">
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <TextArea rows={3} value={question} onChange={(event) => setQuestion(event.target.value)} />
              <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={runAsk}>
                生成答案
              </Button>
              {answer && (
                <div className="inline-panel">
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Title level={5}>回答</Title>
                    <Tag color={answer.refused ? 'red' : 'green'}>
                      {answer.refused ? '已拒答' : '带引用回答'}
                    </Tag>
                  </Space>
                  <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{answer.answer}</Paragraph>
                  <Divider />
                  <List
                    size="small"
                    dataSource={answer.citations}
                    locale={{ emptyText: '暂无引用' }}
                    renderItem={(citation) => (
                      <List.Item>
                        <Space direction="vertical" size={2}>
                          <Text strong>{citation.docTitle}</Text>
                          <Text type="secondary">{citation.quote}</Text>
                        </Space>
                      </List.Item>
                    )}
                  />
                </div>
              )}
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={12}>
          <Card className="command-card" title="导入知识文档">
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              <Space.Compact style={{ width: '100%' }}>
                <Input disabled={!canManageKnowledge} value={importTitle} onChange={(event) => setImportTitle(event.target.value)} />
                <Select
                  style={{ width: 220 }}
                  disabled={!canManageKnowledge}
                  value={importType}
                  onChange={setImportType}
                  options={docTypeOptions}
                />
              </Space.Compact>
              <TextArea
                rows={5}
                disabled={!canManageKnowledge}
                value={importContent}
                onChange={(event) => setImportContent(event.target.value)}
              />
              <Button icon={<ImportOutlined />} loading={loading} disabled={!canManageKnowledge} onClick={submitImport}>
                导入并分块
              </Button>
              {!canManageKnowledge && <Text type="secondary">当前身份只做知识问答；导入文档需要系统管理员。</Text>}
            </Space>
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
