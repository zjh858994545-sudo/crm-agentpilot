package com.agentpilot.rag.splitter;

import com.agentpilot.rag.vo.ChunkDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class KnowledgeSplitter {
    private static final int MAX_CHARS = 220;

    public List<ChunkDraft> split(String title, String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").trim();
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : normalized.split("\\n+")) {
            if (!paragraph.isBlank()) {
                paragraphs.add(paragraph.trim());
            }
        }
        if (paragraphs.isEmpty() && !normalized.isBlank()) {
            paragraphs.add(normalized);
        }

        List<ChunkDraft> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (buffer.length() + paragraph.length() > MAX_CHARS && !buffer.isEmpty()) {
                chunks.add(toDraft(title, chunks.size(), buffer.toString()));
                buffer.setLength(0);
            }
            if (!buffer.isEmpty()) {
                buffer.append('\n');
            }
            buffer.append(paragraph);
        }
        if (!buffer.isEmpty()) {
            chunks.add(toDraft(title, chunks.size(), buffer.toString()));
        }
        return chunks;
    }

    private ChunkDraft toDraft(String docTitle, int index, String content) {
        String title = docTitle + " #" + (index + 1);
        return new ChunkDraft(index, title, content, estimateTokens(content), extractKeywords(content));
    }

    private int estimateTokens(String content) {
        return Math.max(1, content.length() / 2);
    }

    private String extractKeywords(String content) {
        String[] dictionary = {
                "价格异议", "预算", "ROI", "曝光", "续费", "到期", "电话线索", "质检",
                "违规承诺", "人工确认", "房产", "招聘", "套餐", "案例", "沉默客户", "召回"
        };
        Set<String> keywords = new LinkedHashSet<>();
        if (content.contains("价格") || content.contains("贵")) {
            keywords.add("价格异议");
        }
        for (String keyword : dictionary) {
            if (content.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        return String.join(",", keywords);
    }
}
