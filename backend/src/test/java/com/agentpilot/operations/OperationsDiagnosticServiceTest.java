package com.agentpilot.operations;

import com.agentpilot.operations.service.OperationsDiagnosticService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OperationsDiagnosticServiceTest {
    @Autowired
    private OperationsDiagnosticService operationsDiagnosticService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void diagnosticsBundleUsesCurrentOperationalTableNames() throws Exception {
        byte[] bundle = operationsDiagnosticService.buildBundle("demo", 1L);
        JsonNode database = objectMapper.readTree(readZipEntry(bundle, "database.json"));

        JsonNode tableCounts = database.path("selectedTableCounts");
        assertThat(tableCounts).isNotEmpty();
        assertThat(tableCounts.toString()).contains("crm_agent_run", "agentpilot_export_request");
        assertThat(tableCounts.toString()).doesNotContain("agent_run unavailable");
    }

    private String readZipEntry(byte[] payload, String entryName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(payload), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalArgumentException("zip entry not found: " + entryName);
    }
}
