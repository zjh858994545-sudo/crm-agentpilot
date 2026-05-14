package com.agentpilot.rag.service;

import com.agentpilot.rag.embedding.EmbeddingService;
import com.agentpilot.rag.entity.KnowledgeChunk;
import com.agentpilot.rag.entity.KnowledgeDoc;
import com.agentpilot.rag.entity.RetrievalLog;
import com.agentpilot.rag.retriever.HybridRetriever;
import com.agentpilot.rag.retriever.QueryRewriteService;
import com.agentpilot.rag.splitter.KnowledgeSplitter;
import com.agentpilot.rag.vo.AnswerCitation;
import com.agentpilot.rag.vo.ChunkDraft;
import com.agentpilot.rag.vo.KnowledgeAnswer;
import com.agentpilot.rag.vo.KnowledgeImportRequest;
import com.agentpilot.rag.vo.KnowledgeItem;
import com.agentpilot.rag.vo.KnowledgeSearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class RagService {
    private static final double REFUSAL_THRESHOLD = 0.22;

    private final KnowledgeDocService docService;
    private final KnowledgeChunkService chunkService;
    private final RetrievalLogService retrievalLogService;
    private final KnowledgeSplitter splitter;
    private final EmbeddingService embeddingService;
    private final QueryRewriteService queryRewriteService;
    private final HybridRetriever retriever;
    private final ObjectMapper objectMapper;

    public RagService(
            KnowledgeDocService docService,
            KnowledgeChunkService chunkService,
            RetrievalLogService retrievalLogService,
            KnowledgeSplitter splitter,
            EmbeddingService embeddingService,
            QueryRewriteService queryRewriteService,
            HybridRetriever retriever,
            ObjectMapper objectMapper
    ) {
        this.docService = docService;
        this.chunkService = chunkService;
        this.retrievalLogService = retrievalLogService;
        this.splitter = splitter;
        this.embeddingService = embeddingService;
        this.queryRewriteService = queryRewriteService;
        this.retriever = retriever;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeDoc importDocument(KnowledgeImportRequest request) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setTitle(request.title());
        doc.setDocType(request.docType());
        doc.setSource(request.source() == null ? "manual" : request.source());
        doc.setStatus("ACTIVE");
        doc.setContentHash(Integer.toHexString(request.content().hashCode()));
        doc.setCreatedBy(1L);
        docService.save(doc);

        List<ChunkDraft> chunks = splitter.split(request.title(), request.content());
        for (ChunkDraft draft : chunks) {
            double[] vector = embeddingService.embed(draft.content());
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocId(doc.getId());
            chunk.setChunkIndex(draft.index());
            chunk.setTitle(draft.title());
            chunk.setContent(draft.content());
            chunk.setTokenCount(draft.tokenCount());
            chunk.setKeywords(draft.keywords());
            chunk.setEmbedding(embeddingService.serialize(vector));
            chunkService.save(chunk);
            chunkService.updateEmbeddingVector(chunk.getId(), embeddingService.serializeForPgVector(vector));
        }
        return doc;
    }

    public KnowledgeSearchResponse search(String query, int topK) {
        Instant startedAt = Instant.now();
        String rewrittenQuery = queryRewriteService.rewrite(query);
        List<KnowledgeItem> items = retriever.search(query, rewrittenQuery, topK);
        saveLog(query, rewrittenQuery, topK, items, startedAt);
        return new KnowledgeSearchResponse(query, rewrittenQuery, items);
    }

    public KnowledgeAnswer ask(String question, int topK) {
        KnowledgeSearchResponse search = search(question, topK);
        if (search.items().isEmpty() || search.items().get(0).score() < REFUSAL_THRESHOLD) {
            return new KnowledgeAnswer(
                    question,
                    search.rewrittenQuery(),
                    "没有检索到足够可靠的销售知识库资料，暂时不能给出确定回答。",
                    true,
                    List.of()
            );
        }

        List<KnowledgeItem> evidence = search.items().stream().limit(3).toList();
        String answer = buildAnswer(question, evidence);
        List<AnswerCitation> citations = evidence.stream()
                .map(item -> new AnswerCitation(
                        item.docId(),
                        item.chunkId(),
                        item.docTitle(),
                        item.content().length() > 80 ? item.content().substring(0, 80) : item.content()
                ))
                .toList();
        return new KnowledgeAnswer(question, search.rewrittenQuery(), answer, false, citations);
    }

    private String buildAnswer(String question, List<KnowledgeItem> evidence) {
        StringBuilder builder = new StringBuilder();
        builder.append("基于销售知识库，建议按以下方式处理：");
        for (int i = 0; i < evidence.size(); i++) {
            builder.append("\n").append(i + 1).append(". ");
            builder.append(summarize(evidence.get(i).content()));
        }
        builder.append("\n如果涉及 CRM 写入动作，需要先生成确认项，用户确认后再执行。");
        return builder.toString();
    }

    private String summarize(String content) {
        if (content.length() <= 90) {
            return content;
        }
        return content.substring(0, 90) + "...";
    }

    private void saveLog(
            String query,
            String rewrittenQuery,
            int topK,
            List<KnowledgeItem> items,
            Instant startedAt
    ) {
        RetrievalLog log = new RetrievalLog();
        log.setQuery(query);
        log.setRewrittenQuery(rewrittenQuery);
        log.setRetrieverType("hybrid");
        log.setTopK(topK);
        log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
        try {
            log.setResultJson(objectMapper.writeValueAsString(items));
        } catch (JsonProcessingException ex) {
            log.setResultJson("[]");
        }
        retrievalLogService.save(log);
    }
}
