package com.agentpilot.callcenter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agentpilot.callcenter.webhook.signature-enabled=true",
        "agentpilot.callcenter.webhook.secret=test-webhook-secret",
        "agentpilot.callcenter.webhook.max-skew-seconds=300"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CallCenterWebhookSecurityTest {
    private static final String SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signedCallEndedEventIsAcceptedAndReplayIsRejected() throws Exception {
        String payload = payload("CALL-SIGNED-001");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = signature(timestamp, nonce, payload);

        mockMvc.perform(post("/api/callcenter/call-ended-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-AgentPilot-Webhook-Timestamp", timestamp)
                        .header("X-AgentPilot-Webhook-Nonce", nonce)
                        .header("X-AgentPilot-Webhook-Signature", "sha256=" + signature)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.callId", is("CALL-SIGNED-001")));

        mockMvc.perform(post("/api/callcenter/call-ended-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-AgentPilot-Webhook-Timestamp", timestamp)
                        .header("X-AgentPilot-Webhook-Nonce", nonce)
                        .header("X-AgentPilot-Webhook-Signature", "sha256=" + signature)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    void forgedSignatureIsRejected() throws Exception {
        String payload = payload("CALL-FORGED-001");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(post("/api/callcenter/call-ended-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-AgentPilot-Webhook-Timestamp", timestamp)
                        .header("X-AgentPilot-Webhook-Nonce", UUID.randomUUID().toString())
                        .header("X-AgentPilot-Webhook-Signature", "sha256=" + "0".repeat(64))
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    @Test
    void authenticatedInternalCallEndedEventDoesNotRequireWebhookSignature() throws Exception {
        mockMvc.perform(post("/api/callcenter/call-ended-events/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("CALL-INTERNAL-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.callId", is("CALL-INTERNAL-001")));
    }

    @Test
    void staleTimestampIsRejected() throws Exception {
        String payload = payload("CALL-STALE-001");
        String timestamp = String.valueOf(Instant.now().minusSeconds(1_000).getEpochSecond());
        String nonce = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/callcenter/call-ended-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-AgentPilot-Webhook-Timestamp", timestamp)
                        .header("X-AgentPilot-Webhook-Nonce", nonce)
                        .header("X-AgentPilot-Webhook-Signature", "sha256=" + signature(timestamp, nonce, payload))
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    private String payload(String callId) {
        return """
                {"callId":"%s","customerId":1001,"salesRepId":1,"leadId":3001,
                "recordingUrl":"https://voice.example.com/recordings/%s",
                "transcript":"Customer asked for renewal proof. Sales promised to send exposure data tomorrow."}
                """.formatted(callId, callId).trim();
    }

    private String signature(String timestamp, String nonce, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal((timestamp + "." + nonce + "." + body).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
