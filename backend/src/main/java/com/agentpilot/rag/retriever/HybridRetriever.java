package com.agentpilot.rag.retriever;

import com.agentpilot.rag.embedding.MockEmbeddingService;
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
    private final MockEmbeddingService embeddingService;

    public HybridRetriever(
            KnowledgeDocService docService,
            KnowledgeChunkService chunkService,
            MockEmbeddingService embeddingService
    ) {
        this.docService = docService;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
    }

    public List<KnowledgeItem> search(String query, String rewrittenQuery, int topK) {
        int limit = Math.max(1, Math.min(topK, 20));
        Map<Long, KnowledgeDoc> docs = docService.list()
                .stream()
                .collect(Collectors.toMap(KnowledgeDoc::getId, Function.identity()));
        double[] queryVector = embeddingService.embed(rewrittenQuery);

        return chunkService.list()
                .stream()
                .map(chunk -> toItem(chunk, docs.get(chunk.getDocId()), rewrittenQuery, queryVector))
                .filter(item -> item.score() > 0.02)
                .sorted(Comparator.comparingDouble(KnowledgeItem::score).reversed())
                .limit(limit)
                .toList();
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
        double score = Math.min(1.0, round(keywordScore * 0.78 + vectorScore * 0.18 + docBoost));
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

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
