package com.agentpilot.operations.service;

import com.agentpilot.common.config.ApplicationInfoProperties;
import com.agentpilot.events.service.OutboxEventService;
import com.agentpilot.model.ChatModelClient;
import com.agentpilot.operations.config.RetentionProperties;
import com.agentpilot.operations.vo.LaunchReadinessCheck;
import com.agentpilot.operations.vo.LaunchReadinessStatus;
import com.agentpilot.operations.vo.RetentionStatus;
import com.agentpilot.rag.embedding.EmbeddingService;
import com.agentpilot.rag.service.KnowledgeChunkService;
import com.agentpilot.rag.service.KnowledgeDocService;
import com.agentpilot.security.RbacPrincipalService;
import com.agentpilot.security.config.AgentPilotSecurityProperties;
import com.agentpilot.security.config.JwtSsoProperties;
import com.agentpilot.security.ratelimit.ApiRateLimitProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class LaunchReadinessService {
    private final ApplicationInfoProperties appProperties;
    private final AgentPilotSecurityProperties securityProperties;
    private final JwtSsoProperties jwtSsoProperties;
    private final ApiRateLimitProperties rateLimitProperties;
    private final RbacPrincipalService rbacPrincipalService;
    private final ChatModelClient chatModelClient;
    private final EmbeddingService embeddingService;
    private final KnowledgeDocService knowledgeDocService;
    private final KnowledgeChunkService knowledgeChunkService;
    private final OutboxEventService outboxEventService;
    private final RetentionProperties retentionProperties;
    private final RetentionMaintenanceService retentionMaintenanceService;

    public LaunchReadinessService(
            ApplicationInfoProperties appProperties,
            AgentPilotSecurityProperties securityProperties,
            JwtSsoProperties jwtSsoProperties,
            ApiRateLimitProperties rateLimitProperties,
            RbacPrincipalService rbacPrincipalService,
            ChatModelClient chatModelClient,
            EmbeddingService embeddingService,
            KnowledgeDocService knowledgeDocService,
            KnowledgeChunkService knowledgeChunkService,
            OutboxEventService outboxEventService,
            RetentionProperties retentionProperties,
            RetentionMaintenanceService retentionMaintenanceService
    ) {
        this.appProperties = appProperties;
        this.securityProperties = securityProperties;
        this.jwtSsoProperties = jwtSsoProperties;
        this.rateLimitProperties = rateLimitProperties;
        this.rbacPrincipalService = rbacPrincipalService;
        this.chatModelClient = chatModelClient;
        this.embeddingService = embeddingService;
        this.knowledgeDocService = knowledgeDocService;
        this.knowledgeChunkService = knowledgeChunkService;
        this.outboxEventService = outboxEventService;
        this.retentionProperties = retentionProperties;
        this.retentionMaintenanceService = retentionMaintenanceService;
    }

    public LaunchReadinessStatus status() {
        boolean productionPhase = isProductionPhase(appProperties.phase());
        List<LaunchReadinessCheck> checks = new ArrayList<>();
        checks.add(check(
                "security.strict",
                "严格认证模式",
                securityProperties.strict(),
                productionPhase ? "FAIL" : "WARN",
                "当前安全模式：" + securityProperties.getMode(),
                "生产环境设置 AGENTPILOT_SECURITY_MODE=strict"
        ));
        checks.add(check(
                "security.seed-users",
                "禁用种子账号",
                !securityProperties.isSeedUsersEnabled(),
                productionPhase ? "FAIL" : "WARN",
                securityProperties.isSeedUsersEnabled() ? "本地种子账号仍可用" : "种子账号已禁用",
                "生产环境设置 AGENTPILOT_SEED_USERS_ENABLED=false，并导入真实 RBAC 用户"
        ));
        checks.add(check(
                "security.rbac",
                "RBAC 用户体系",
                rbacPrincipalService.activeUserCount() > 0,
                "FAIL",
                "活跃 RBAC 用户数：" + rbacPrincipalService.activeUserCount(),
                "至少保留一个系统管理员账号，并为销售/主管分配真实 token"
        ));
        checks.add(check(
                "security.tenant-registry",
                "租户注册表",
                rbacPrincipalService.activeTenantCount() > 0,
                "FAIL",
                "活跃租户数：" + rbacPrincipalService.activeTenantCount(),
                "至少保留一个 ACTIVE 租户；企业客户开通、停用和备份恢复都应以租户注册表为准"
        ));
        checks.add(jwtTenantAllowListCheck(productionPhase));
        boolean rateLimitReady = rateLimitProperties.isEnabled()
                && (!productionPhase || "redis".equalsIgnoreCase(rateLimitProperties.getBackend()));
        checks.add(check(
                "rate-limit.enabled",
                "接口限流",
                rateLimitReady,
                "FAIL",
                "限流后端：" + rateLimitProperties.getBackend(),
                "生产环境开启 AGENTPILOT_RATE_LIMIT_ENABLED=true，并设置 AGENTPILOT_RATE_LIMIT_BACKEND=redis"
        ));
        checks.add(check(
                "model.chat",
                "真实 LLM Tool Calling",
                chatModelClient.configured(),
                productionPhase ? "FAIL" : "WARN",
                chatModelClient.provider() + " / " + chatModelClient.modelName(),
                "配置真实模型 baseUrl、apiKey 和 chat model，避免生产使用 mock"
        ));
        checks.add(check(
                "model.embedding",
                "真实 Embedding",
                embeddingService.configured() && embeddingService.dimension() == 1024,
                productionPhase ? "FAIL" : "WARN",
                embeddingService.provider() + " / " + embeddingService.modelName() + " / " + embeddingService.dimension() + "维",
                "配置 text-embedding-v4 或等价 1024 维 embedding，并确认维度与 pgvector 列一致"
        ));
        checks.add(vectorCheck());
        checks.add(check(
                "outbox.dead-letter",
                "Outbox 死信清零",
                outboxEventService.deadLetterCount() == 0,
                "FAIL",
                "死信事件数：" + outboxEventService.deadLetterCount(),
                "上线前处理或重试 DEAD_LETTER，避免下游事件丢失"
        ));
        checks.add(retentionCheck());

        long failCount = checks.stream().filter(item -> "FAIL".equals(item.status())).count();
        long warnCount = checks.stream().filter(item -> "WARN".equals(item.status())).count();
        long passCount = checks.stream().filter(item -> "PASS".equals(item.status())).count();
        String overall = failCount > 0 ? "BLOCKED" : warnCount > 0 ? "WARN" : "READY";
        return new LaunchReadinessStatus(
                overall,
                appProperties.phase(),
                LocalDateTime.now(),
                passCount,
                warnCount,
                failCount,
                checks
        );
    }

    private LaunchReadinessCheck vectorCheck() {
        long chunkCount = knowledgeChunkService.count();
        long vectorizedChunkCount = knowledgeChunkService.vectorizedChunkCount();
        boolean noKnowledgeYet = knowledgeDocService.count() == 0 || chunkCount == 0;
        boolean ready = knowledgeChunkService.pgvectorAvailable()
                && chunkCount > 0
                && vectorizedChunkCount * 100 >= chunkCount * 80;
        if (noKnowledgeYet) {
            return new LaunchReadinessCheck(
                    "rag.vector-store",
                    "pgvector 知识索引",
                    "WARN",
                    "WARN",
                    "当前没有可评估的知识分块",
                    "导入销售 SOP/政策文档，并重建知识索引"
            );
        }
        return check(
                "rag.vector-store",
                "pgvector 知识索引",
                ready,
                "WARN",
                knowledgeChunkService.vectorStoreMode() + " / " + vectorizedChunkCount + "/" + chunkCount + " chunks",
                "确保 vectorStoreMode=pgvector-hybrid，且向量化覆盖率不低于 80%"
        );
    }

    private LaunchReadinessCheck jwtTenantAllowListCheck(boolean productionPhase) {
        if (!jwtSsoProperties.isEnabled()) {
            return new LaunchReadinessCheck(
                    "security.jwt-tenant-allow-list",
                    "JWT 租户白名单",
                    "PASS",
                    "WARN",
                    "企业 JWT 未启用，当前由 RBAC token 或本地身份接管",
                    "启用企业 SSO 时配置 AGENTPILOT_JWT_ALLOWED_TENANTS"
            );
        }
        int tenantCount = jwtSsoProperties.normalizedAllowedTenants().size();
        return check(
                "security.jwt-tenant-allow-list",
                "JWT 租户白名单",
                tenantCount > 0,
                productionPhase ? "FAIL" : "WARN",
                "允许租户数：" + tenantCount,
                "商业部署必须显式列出已开通租户，避免外部 JWT 携带未知 tenantId 获得访问"
        );
    }

    private LaunchReadinessCheck retentionCheck() {
        RetentionStatus retentionStatus = retentionMaintenanceService.status();
        boolean ready = retentionProperties.isEnabled() || retentionStatus.totalEligibleRows() == 0;
        return check(
                "operations.retention",
                "数据保留策略",
                ready,
                "WARN",
                "可清理历史行数：" + retentionStatus.totalEligibleRows() + "，保留策略开关：" + retentionProperties.isEnabled(),
                "上线前完成备份恢复演练；如果历史数据超过保留周期，先 dry-run 再开启清理"
        );
    }

    private LaunchReadinessCheck check(
            String key,
            String name,
            boolean passed,
            String failureSeverity,
            String detail,
            String action
    ) {
        return new LaunchReadinessCheck(
                key,
                name,
                passed ? "PASS" : "WARN".equals(failureSeverity) ? "WARN" : "FAIL",
                failureSeverity,
                detail,
                action
        );
    }

    private boolean isProductionPhase(String phase) {
        return switch (phase == null ? "" : phase.trim().toLowerCase(Locale.ROOT)) {
            case "prod", "production", "commercial", "launch" -> true;
            default -> false;
        };
    }
}
