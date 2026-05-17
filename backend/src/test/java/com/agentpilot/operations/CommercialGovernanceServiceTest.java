package com.agentpilot.operations;

import com.agentpilot.export.entity.ExportRequest;
import com.agentpilot.export.service.ExportRequestService;
import com.agentpilot.export.vo.ExportRequestCreateRequest;
import com.agentpilot.tenant.entity.TenantConfig;
import com.agentpilot.tenant.service.TenantConfigService;
import com.agentpilot.tenant.vo.TenantConfigUpsertRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommercialGovernanceServiceTest {
    @Autowired
    private TenantConfigService tenantConfigService;

    @Autowired
    private ExportRequestService exportRequestService;

    @Test
    void tenantConfigCanBeUpsertedListedAndRemoved() {
        TenantConfig saved = tenantConfigService.upsert(
                "demo",
                new TenantConfigUpsertRequest("model.chat.model", "qwen3.6-flash", "string", "tenant model override"),
                1L
        );

        assertThat(saved.getTenantId()).isEqualTo("demo");
        assertThat(saved.getConfigKey()).isEqualTo("model.chat.model");
        assertThat(tenantConfigService.listByTenant("demo"))
                .extracting(TenantConfig::getConfigKey)
                .contains("model.chat.model");

        TenantConfig updated = tenantConfigService.upsert(
                "demo",
                new TenantConfigUpsertRequest("model.chat.model", "qwen3.6-plus", "string", "upgraded model"),
                2L
        );

        assertThat(updated.getConfigValue()).isEqualTo("qwen3.6-plus");
        assertThat(updated.getUpdatedBy()).isEqualTo(2L);
        assertThat(tenantConfigService.removeConfig("demo", "model.chat.model")).isTrue();
        assertThat(tenantConfigService.listByTenant("demo"))
                .extracting(TenantConfig::getConfigKey)
                .doesNotContain("model.chat.model");
    }

    @Test
    void exportRequestRequiresPendingStatusForDecision() {
        ExportRequest request = exportRequestService.create(
                "demo",
                1L,
                new ExportRequestCreateRequest("customers", "weekly customer review")
        );

        ExportRequest approved = exportRequestService.approve(request.getId(), "demo", 100L, "approved");

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getApproverUserId()).isEqualTo(100L);
        assertThat(approved.getFileName()).endsWith(".csv");
        assertThat(approved.getFileSizeBytes()).isPositive();
        assertThat(approved.getExpiresAt()).isNotNull();
        ExportRequestService.ExportDownload download = exportRequestService.download(request.getId(), "demo", 1L, false);
        assertThat(download.fileName()).isEqualTo(approved.getFileName());
        assertThat(new String(download.content())).contains("contact_mobile_masked");
        assertThat(new String(download.content())).doesNotContain("13910001001");
        assertThat(exportRequestService.listByTenant("demo", "APPROVED", 10))
                .extracting(ExportRequest::getId)
                .contains(request.getId());
        assertThatThrownBy(() -> exportRequestService.reject(request.getId(), "demo", 101L, "too late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending");
    }
}
