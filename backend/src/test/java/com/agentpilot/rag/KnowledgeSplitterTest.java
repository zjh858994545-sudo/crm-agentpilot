package com.agentpilot.rag;

import com.agentpilot.rag.splitter.KnowledgeSplitter;
import com.agentpilot.rag.vo.ChunkDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSplitterTest {

    private final KnowledgeSplitter splitter = new KnowledgeSplitter();

    @Test
    void splitCreatesOrderedChunksWithKeywords() {
        String content = """
                商户觉得套餐价格贵时，先确认预算和经营目标。
                再结合 ROI、曝光数据和同行案例说明套餐价值。
                如果客户仍犹豫，应约定下一次复盘时间。
                """;

        List<ChunkDraft> chunks = splitter.split("价格异议 SOP", content);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).keywords()).contains("价格异议", "预算", "ROI", "曝光");
    }
}

