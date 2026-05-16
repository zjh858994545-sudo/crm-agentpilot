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
import jakarta.validation.Valid;
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
        return ApiResponse.ok(ragService.importDocument(request));
    }

    @GetMapping("/docs")
    public ApiResponse<List<KnowledgeDoc>> docs() {
        return ApiResponse.ok(docService.list());
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "vectorStoreMode", chunkService.vectorStoreMode(),
                "pgvectorAvailable", chunkService.pgvectorAvailable(),
                "docCount", docService.count(),
                "chunkCount", chunkService.count(),
                "vectorizedChunkCount", chunkService.vectorizedChunkCount()
        ));
    }

    @PostMapping("/vectors/rebuild")
    @PreAuthorize("hasAuthority('knowledge:write')")
    public ApiResponse<Map<String, Object>> rebuildMissingVectors() {
        int updatedChunks = chunkService.rebuildMissingEmbeddingVectors();
        return ApiResponse.ok(Map.of(
                "vectorStoreMode", chunkService.vectorStoreMode(),
                "updatedChunks", updatedChunks,
                "vectorizedChunkCount", chunkService.vectorizedChunkCount()
        ));
    }

    @GetMapping("/docs/{id}")
    public ApiResponse<List<KnowledgeChunk>> chunks(@PathVariable Long id) {
        return ApiResponse.ok(chunkService.listByDocId(id));
    }

    @PostMapping("/search")
    public ApiResponse<KnowledgeSearchResponse> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        return ApiResponse.ok(ragService.search(request.query(), request.topK() == null ? 5 : request.topK()));
    }

    @PostMapping("/ask")
    public ApiResponse<KnowledgeAnswer> ask(@Valid @RequestBody KnowledgeAskRequest request) {
        return ApiResponse.ok(ragService.ask(request.question(), request.topK() == null ? 5 : request.topK()));
    }
}
