package com.agentpilot.rag.service;

import com.agentpilot.rag.entity.KnowledgeChunk;
import com.agentpilot.rag.mapper.KnowledgeChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeChunkService extends ServiceImpl<KnowledgeChunkMapper, KnowledgeChunk> {

    public List<KnowledgeChunk> listByDocId(Long docId) {
        return list(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, docId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
    }
}

