package com.agentpilot.rag.retriever;

import com.agentpilot.rag.embedding.EmbeddingService;
import com.agentpilot.rag.entity.KnowledgeChunk;
import com.agentpilot.rag.entity.KnowledgeDoc;
import com.agentpilot.rag.service.KnowledgeChunkService;
import com.agentpilot.rag.service.KnowledgeDocService;
import com.agentpilot.rag.vo.KnowledgeItem;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HybridRetriever {
    private final KnowledgeDocService docService;
    private final KnowledgeChunkService chunkService;
    private final EmbeddingService embeddingService;

    public HybridRetriever(
            KnowledgeDocService docService,
            KnowledgeChunkService chunkService,
            EmbeddingService embeddingService
    ) {
        this.docService = docService;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
    }

    public List<KnowledgeItem> search(String query, String rewrittenQuery, int topK) {
        return search("demo", query, rewrittenQuery, topK);
    }

    public List<KnowledgeItem> search(String tenantId, String query, String rewrittenQuery, int topK) {
        int limit = Math.max(1, Math.min(topK, 20));
        double[] queryVector = embeddingService.embed(rewrittenQuery);
        if (chunkService.pgvectorAvailable()) {
            List<KnowledgeItem> vectorItems = chunkService.searchByVector(
                            tenantId,
                            embeddingService.serializeForPgVector(queryVector),
                            Math.max(limit * 4, 20)
                    )
                    .stream()
                    .map(row -> toItem(row, rewrittenQuery))
                    .filter(item -> item.score() > 0.02)
                    .sorted(Comparator.comparingDouble(KnowledgeItem::score).reversed())
                    .limit(limit)
                    .toList();
            if (!vectorItems.isEmpty()) {
                return vectorItems;
            }
        }

        Map<Long, KnowledgeDoc> docs = docService.listByTenant(tenantId)
                .stream()
                .collect(Collectors.toMap(KnowledgeDoc::getId, Function.identity()));

        return chunkService.list()
                .stream()
                .filter(chunk -> docs.containsKey(chunk.getDocId()))
                .map(chunk -> toItem(chunk, docs.get(chunk.getDocId()), rewrittenQuery, queryVector))
                .filter(item -> item.score() > 0.02)
                .sorted(Comparator.comparingDouble(KnowledgeItem::score).reversed())
                .limit(limit)
                .toList();
    }

    private KnowledgeItem toItem(VectorSearchRow row, String rewrittenQuery) {
        String text = row.chunkTitle() + " " + row.content() + " " + row.keywords();
        double keywordScore = keywordScore(rewrittenQuery, text);
        double docBoost = !row.docType().isBlank() && rewrittenQuery.contains(row.docType()) ? 0.08 : 0.0;
        double score = guardedScore(keywordScore, row.vectorScore(), docBoost, 0.30, 0.62, false);
        return new KnowledgeItem(
                row.chunkId(),
                row.docId(),
                row.docTitle(),
                row.docType(),
                row.chunkTitle(),
                row.content(),
                score,
                "pgvector-hybrid"
        );
    }

    private KnowledgeItem toItem(
            KnowledgeChunk chunk,
            KnowledgeDoc doc,
            String rewrittenQuery,
            double[] queryVector
    ) {
        String text = chunk.getTitle() + " " + chunk.getContent() + " " + chunk.getKeywords();
        double keywordScore = keywordScore(rewrittenQuery, text);
        double vectorScore = embeddingService.cosine(queryVector, embeddingService.embed(text));
        double docBoost = doc != null && rewrittenQuery.contains(doc.getDocType()) ? 0.08 : 0.0;
        double score = guardedScore(keywordScore, vectorScore, docBoost, 0.78, 0.18, true);
        return new KnowledgeItem(
                chunk.getId(),
                chunk.getDocId(),
                doc == null ? "" : doc.getTitle(),
                doc == null ? "" : doc.getDocType(),
                chunk.getTitle(),
                chunk.getContent(),
                score,
                "hybrid"
        );
    }

    private double keywordScore(String query, String text) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String normalizedText = text.toLowerCase(Locale.ROOT);
        List<String> terms = Arrays.stream(normalizedQuery.split("[\\s,，。；;]+"))
                .map(String::trim)
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
        if (terms.isEmpty()) {
            return 0.0;
        }
        long hits = terms.stream().filter(normalizedText::contains).count();
        return Math.min(1.0, hits / 6.0);
    }

    private double guardedScore(
            double keywordScore,
            double vectorScore,
            double docBoost,
            double keywordWeight,
            double vectorWeight,
            boolean requireKeywordAnchor
    ) {
        double score = Math.min(1.0, keywordScore * keywordWeight + vectorScore * vectorWeight + docBoost);
        if (!requireKeywordAnchor) {
            return round(score);
        }
        if (keywordScore == 0.0) {
            score = Math.min(score, 0.18);
        } else if (keywordScore < 0.17) {
            score = Math.min(score, 0.21);
        }
        return round(score);
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
