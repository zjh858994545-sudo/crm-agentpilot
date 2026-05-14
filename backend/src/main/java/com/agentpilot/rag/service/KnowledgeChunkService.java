package com.agentpilot.rag.service;

import com.agentpilot.rag.entity.KnowledgeChunk;
import com.agentpilot.rag.embedding.EmbeddingService;
import com.agentpilot.rag.mapper.KnowledgeChunkMapper;
import com.agentpilot.rag.retriever.VectorSearchRow;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

@Service
public class KnowledgeChunkService extends ServiceImpl<KnowledgeChunkMapper, KnowledgeChunk> {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeChunkService.class);
    private static final int PGVECTOR_DIMENSION = 1024;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private boolean pgvectorAvailable;

    public KnowledgeChunkService(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    void detectVectorStore() {
        try {
            boolean postgres;
            try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
                DatabaseMetaData metadata = connection.getMetaData();
                postgres = metadata.getDatabaseProductName().toLowerCase().contains("postgresql");
            }
            if (!postgres) {
                pgvectorAvailable = false;
                return;
            }
            Boolean extensionAvailable = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                    Boolean.class
            );
            pgvectorAvailable = Boolean.TRUE.equals(extensionAvailable);
            if (pgvectorAvailable && embeddingService.dimension() != PGVECTOR_DIMENSION) {
                pgvectorAvailable = false;
                log.info("RAG vector store mode=java-fallback reason=embedding dimension {} does not match pgvector dimension {}",
                        embeddingService.dimension(), PGVECTOR_DIMENSION);
                return;
            }
            log.info("RAG vector store mode={}", pgvectorAvailable ? "pgvector" : "java-fallback");
            if (pgvectorAvailable) {
                backfillMissingEmbeddingVectors();
            }
        } catch (Exception ex) {
            pgvectorAvailable = false;
            log.info("RAG vector store mode=java-fallback reason={}", ex.getMessage());
        }
    }

    public List<KnowledgeChunk> listByDocId(Long docId) {
        return list(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, docId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
    }

    public boolean pgvectorAvailable() {
        return pgvectorAvailable;
    }

    public String vectorStoreMode() {
        return pgvectorAvailable ? "pgvector-hybrid" : "java-fallback";
    }

    public long vectorizedChunkCount() {
        if (!pgvectorAvailable) {
            return 0L;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crm_knowledge_chunk WHERE embedding_vector IS NOT NULL",
                Long.class
        );
        return count == null ? 0L : count;
    }

    public void updateEmbeddingVector(Long chunkId, String vectorLiteral) {
        if (!pgvectorAvailable || chunkId == null || vectorLiteral == null) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE crm_knowledge_chunk SET embedding_vector = CAST(? AS vector) WHERE id = ?",
                vectorLiteral,
                chunkId
        );
    }

    public List<VectorSearchRow> searchByVector(String vectorLiteral, int limit) {
        if (!pgvectorAvailable) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        SELECT c.id AS chunk_id,
                               c.doc_id,
                               COALESCE(d.title, '') AS doc_title,
                               COALESCE(d.doc_type, '') AS doc_type,
                               COALESCE(c.title, '') AS chunk_title,
                               c.content,
                               COALESCE(c.keywords, '') AS keywords,
                               1 - (c.embedding_vector <=> CAST(? AS vector)) AS vector_score
                        FROM crm_knowledge_chunk c
                        LEFT JOIN crm_knowledge_doc d ON d.id = c.doc_id
                        WHERE c.embedding_vector IS NOT NULL
                        ORDER BY c.embedding_vector <=> CAST(? AS vector)
                        LIMIT ?
                        """,
                ps -> {
                    ps.setString(1, vectorLiteral);
                    ps.setString(2, vectorLiteral);
                    ps.setInt(3, Math.max(1, limit));
                },
                (rs, rowNum) -> new VectorSearchRow(
                        rs.getLong("chunk_id"),
                        rs.getLong("doc_id"),
                        rs.getString("doc_title"),
                        rs.getString("doc_type"),
                        rs.getString("chunk_title"),
                        rs.getString("content"),
                        rs.getString("keywords"),
                        rs.getDouble("vector_score")
                )
        );
    }

    private void backfillMissingEmbeddingVectors() {
        List<Long> missingChunkIds = jdbcTemplate.queryForList(
                "SELECT id FROM crm_knowledge_chunk WHERE embedding_vector IS NULL ORDER BY id",
                Long.class
        );
        int updated = 0;
        for (Long chunkId : missingChunkIds) {
            KnowledgeChunk chunk = getById(chunkId);
            if (chunk == null) {
                continue;
            }
            String source = chunk.getContent() + " " + chunk.getKeywords();
            double[] vector = embeddingService.embed(source);
            if (chunk.getEmbedding() == null || chunk.getEmbedding().isBlank()) {
                chunk.setEmbedding(embeddingService.serialize(vector));
                updateById(chunk);
            }
            updateEmbeddingVector(chunk.getId(), embeddingService.serializeForPgVector(vector));
            updated++;
        }
        log.info("RAG pgvector backfill updated {} missing chunks", updated);
    }
}
