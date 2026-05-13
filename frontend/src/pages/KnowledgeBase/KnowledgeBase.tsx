import { BookOutlined, ImportOutlined, SearchOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Divider, Input, List, Select, Space, Tag, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  askKnowledge,
  fetchKnowledgeChunks,
  fetchKnowledgeDocs,
  importKnowledgeDoc,
  KnowledgeAnswer,
  KnowledgeChunk,
  KnowledgeDoc,
  KnowledgeItem,
  searchKnowledge
} from '../../api/client';

const { Paragraph, Text, Title } = Typography;
const { TextArea } = Input;

const demoDoc = `客户提出“套餐太贵、续费没有效果”时，销售需要先复述客户顾虑，再用上月曝光量、咨询量、同行案例说明投入产出。
禁止承诺一定成交、保证排名或保证收益。可以承诺提供数据复盘、优化门店页、调整投放关键词和约定下次复盘时间。`;

export default function KnowledgeBase() {
  const [docs, setDocs] = useState<KnowledgeDoc[]>([]);
  const [selectedDocId, setSelectedDocId] = useState<number>();
  const [chunks, setChunks] = useState<KnowledgeChunk[]>([]);
  const [query, setQuery] = useState('客户嫌套餐贵，销售应该怎么处理？');
  const [question, setQuestion] = useState('遇到客户说续费太贵没有效果，应该如何回复？');
  const [searchResult, setSearchResult] = useState<KnowledgeItem[]>([]);
  const [answer, setAnswer] = useState<KnowledgeAnswer | null>(null);
  const [importTitle, setImportTitle] = useState('价格异议补充话术');
  const [importType, setImportType] = useState('OBJECTION_HANDLING');
  const [importContent, setImportContent] = useState(demoDoc);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const selectedDoc = useMemo(() => docs.find((item) => item.id === selectedDocId), [docs, selectedDocId]);

  const loadDocs = async () => {
    const nextDocs = await fetchKnowledgeDocs();
    setDocs(nextDocs);
    setSelectedDocId((current) => current ?? nextDocs[0]?.id);
  };

  useEffect(() => {
    loadDocs().catch(() => setError('知识库接口暂不可用，请先启动后端服务。'));
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
      message.success(`检索完成，改写查询：${result.rewrittenQuery}`);
    } catch {
      setError('检索失败，请确认后端和数据库已启动。');
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
      setError('问答失败，请确认后端和数据库已启动。');
    } finally {
      setLoading(false);
    }
  };

  const submitImport = async () => {
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
      setSelectedDocId(created.id);
      message.success('文档已导入并自动分块');
    } catch {
      setError('导入失败，请确认后端和数据库已启动。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {error && <Alert type="warning" showIcon message={error} />}
      <div className="page-grid">
        <Card className="span-5" title="知识文档">
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

        <Card className="span-7" title={selectedDoc ? `分块预览：${selectedDoc.title}` : '分块预览'}>
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

        <Card className="span-6" title="混合检索">
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
              <List.Item>
                <Space direction="vertical" size={4}>
                  <Space>
                    <Tag color="green">{item.retriever}</Tag>
                    <Tag>{item.score.toFixed(3)}</Tag>
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

        <Card className="span-6" title="RAG 问答与引用">
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <TextArea rows={3} value={question} onChange={(event) => setQuestion(event.target.value)} />
            <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={runAsk}>
              生成答案
            </Button>
            {answer && (
              <div className="inline-panel">
                <Title level={5}>回答</Title>
                <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{answer.answer}</Paragraph>
                <Tag color={answer.refused ? 'red' : 'green'}>{answer.refused ? '已拒答' : '带引用回答'}</Tag>
                <Divider />
                <List
                  size="small"
                  dataSource={answer.citations}
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

        <Card className="span-12" title="快速导入演示文档">
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Space.Compact style={{ width: '100%' }}>
              <Input value={importTitle} onChange={(event) => setImportTitle(event.target.value)} />
              <Select
                style={{ width: 220 }}
                value={importType}
                onChange={setImportType}
                options={[
                  { value: 'OBJECTION_HANDLING', label: '异议处理' },
                  { value: 'QUALITY_CHECK', label: '质检规则' },
                  { value: 'PRODUCT_POLICY', label: '产品政策' },
                  { value: 'SALES_SOP', label: '销售 SOP' }
                ]}
              />
            </Space.Compact>
            <TextArea rows={4} value={importContent} onChange={(event) => setImportContent(event.target.value)} />
            <Button icon={<ImportOutlined />} loading={loading} onClick={submitImport}>
              导入并分块
            </Button>
          </Space>
        </Card>
      </div>
    </Space>
  );
}
