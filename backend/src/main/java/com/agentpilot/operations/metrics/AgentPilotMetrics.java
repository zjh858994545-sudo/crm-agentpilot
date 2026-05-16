package com.agentpilot.operations.metrics;

import com.agentpilot.events.service.OutboxEventService;
import com.agentpilot.operations.service.RetentionMaintenanceService;
import com.agentpilot.rag.service.KnowledgeChunkService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AgentPilotMetrics {
    private final MeterRegistry meterRegistry;
    private final OutboxEventService outboxEventService;
    private final KnowledgeChunkService knowledgeChunkService;
    private final RetentionMaintenanceService retentionMaintenanceService;

    public AgentPilotMetrics(
            MeterRegistry meterRegistry,
            OutboxEventService outboxEventService,
            KnowledgeChunkService knowledgeChunkService,
            RetentionMaintenanceService retentionMaintenanceService
    ) {
        this.meterRegistry = meterRegistry;
        this.outboxEventService = outboxEventService;
        this.knowledgeChunkService = knowledgeChunkService;
        this.retentionMaintenanceService = retentionMaintenanceService;
    }

    @PostConstruct
    void register() {
        Gauge.builder("agentpilot_outbox_pending_events", outboxEventService, OutboxEventService::pendingCount)
                .description("Outbox events waiting to be dispatched")
                .register(meterRegistry);
        Gauge.builder("agentpilot_outbox_dispatching_events", outboxEventService, OutboxEventService::dispatchingCount)
                .description("Outbox events currently claimed by a dispatcher")
                .register(meterRegistry);
        Gauge.builder("agentpilot_outbox_dead_letter_events", outboxEventService, OutboxEventService::deadLetterCount)
                .description("Outbox events that exceeded max retry count")
                .register(meterRegistry);
        Gauge.builder("agentpilot_knowledge_chunks_total", knowledgeChunkService, KnowledgeChunkService::count)
                .description("Total knowledge chunks")
                .register(meterRegistry);
        Gauge.builder("agentpilot_knowledge_vectorized_chunks", knowledgeChunkService, KnowledgeChunkService::vectorizedChunkCount)
                .description("Knowledge chunks with pgvector embeddings")
                .register(meterRegistry);
        Gauge.builder("agentpilot_retention_eligible_rows", retentionMaintenanceService, service -> service.status().totalEligibleRows())
                .description("Operational rows eligible for retention cleanup")
                .register(meterRegistry);
    }
}
