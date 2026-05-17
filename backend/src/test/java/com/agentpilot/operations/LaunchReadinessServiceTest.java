package com.agentpilot.operations;

import com.agentpilot.operations.service.LaunchReadinessService;
import com.agentpilot.operations.vo.LaunchReadinessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LaunchReadinessServiceTest {
    @Autowired
    private LaunchReadinessService launchReadinessService;

    @Test
    void readinessStatusSummarizesLaunchBlockingAndWarningChecks() {
        LaunchReadinessStatus status = launchReadinessService.status();

        assertThat(status.checks()).isNotEmpty();
        assertThat(status.passCount() + status.warnCount() + status.failCount()).isEqualTo(status.checks().size());
        assertThat(status.checks())
                .extracting("key")
                .contains(
                        "security.strict",
                        "security.rbac",
                        "security.tenant-registry",
                        "security.jwt-tenant-allow-list",
                        "rate-limit.enabled",
                        "model.chat",
                        "rag.vector-store",
                        "operations.retention"
                );
    }
}
