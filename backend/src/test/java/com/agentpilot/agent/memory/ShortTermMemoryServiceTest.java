package com.agentpilot.agent.memory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ShortTermMemoryServiceTest {

    @Autowired
    private ShortTermMemoryService memoryService;

    @Test
    void memoryKeepsSlidingWindowAndSummary() {
        long sessionId = 99001L;
        for (int i = 0; i < 15; i++) {
            memoryService.append(sessionId, "user", "message-" + i);
        }

        assertThat(memoryService.recent(sessionId)).hasSize(12);
        assertThat(memoryService.summarize(sessionId)).contains("message-14");
    }
}

