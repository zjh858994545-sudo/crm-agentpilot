package com.agentpilot.rag.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.rag.entity.KnowledgeChunk;
import com.agentpilot.rag.entity.KnowledgeDoc;
import com.agentpilot.rag.service.KnowledgeChunkService;
import com.agentpilot.rag.service.KnowledgeDocService;
import com.agentpilot.rag.service.RagService;
import com.agentpilot.rag.vo.KnowledgeAnswer;
import com.agentpilot.rag.vo.KnowledgeAskRequest;
import com.agentpilot.rag.vo.KnowledgeImportRequest;
import com.agentpilot.rag.vo.KnowledgeSearchRequest;
import com.agentpilot.rag.vo.KnowledgeSearchResponse;
import com.agentpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@PreAuthorize("hasAuthority('knowledge:read')")
public class KnowledgeController {
    private final KnowledgeDocService docService;
    private final KnowledgeChunkService chunkService;
    private final RagService ragService;

    public KnowledgeController(
            KnowledgeDocService docService,
            KnowledgeChunkService chunkService,
            RagService ragService
    ) {
        this.docService = docService;
        this.chunkService = chunkService;
        this.ragService = ragService;
    }

    @PostMapping("/docs")
    @PreAuthorize("hasAuthority('knowledge:write')")
    public ApiResponse<KnowledgeDoc> importDocument(@Valid @RequestBody KnowledgeImportRequest request) {
        return ApiResponse.ok(ragService.importDocument(CurrentUser.tenantId(), CurrentUser.userId(), request));
    }

    @GetMapping("/docs")
    public ApiResponse<List<KnowledgeDoc>> docs() {
        return ApiResponse.ok(docService.listByTenant(CurrentUser.tenantId()));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        String tenantId = CurrentUser.tenantId();
        return ApiResponse.ok(Map.of(
                "vectorStoreMode", chunkService.vectorStoreMode(),
                "pgvectorAvailable", chunkService.pgvectorAvailable(),
                "docCount", docService.countByTenant(tenantId),
                "chunkCount", chunkService.countByTenant(tenantId),
                "vectorizedChunkCount", chunkService.vectorizedChunkCount(tenantId)
        ));
    }

    @PostMapping("/vectors/rebuild")
    @PreAuthorize("hasAuthority('knowledge:write')")
    public ApiResponse<Map<String, Object>> rebuildMissingVectors() {
        String tenantId = CurrentUser.tenantId();
        int updatedChunks = chunkService.rebuildMissingEmbeddingVectors(tenantId);
        return ApiResponse.ok(Map.of(
                "vectorStoreMode", chunkService.vectorStoreMode(),
                "updatedChunks", updatedChunks,
                "vectorizedChunkCount", chunkService.vectorizedChunkCount(tenantId)
        ));
    }

    @GetMapping("/docs/{id}")
    public ApiResponse<List<KnowledgeChunk>> chunks(@PathVariable Long id) {
        if (!docService.visibleToTenant(id, CurrentUser.tenantId())) {
            throw new AccessDeniedException("knowledge document is outside current tenant");
        }
        return ApiResponse.ok(chunkService.listByDocId(id));
    }

    @PostMapping("/search")
    public ApiResponse<KnowledgeSearchResponse> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        return ApiResponse.ok(ragService.search(CurrentUser.tenantId(), request.query(), request.topK() == null ? 5 : request.topK()));
    }

    @PostMapping("/ask")
    public ApiResponse<KnowledgeAnswer> ask(@Valid @RequestBody KnowledgeAskRequest request) {
        return ApiResponse.ok(ragService.ask(CurrentUser.tenantId(), request.question(), request.topK() == null ? 5 : request.topK()));
    }
}
