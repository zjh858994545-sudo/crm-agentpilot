package com.agentpilot.rag.service;

import com.agentpilot.rag.entity.KnowledgeDoc;
import com.agentpilot.rag.mapper.KnowledgeDocMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class KnowledgeDocService extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc> {
    public List<KnowledgeDoc> listByTenant(String tenantId) {
        return list(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getTenantId, tenantId)
                .orderByDesc(KnowledgeDoc::getId));
    }

    public boolean visibleToTenant(Long docId, String tenantId) {
        KnowledgeDoc doc = getById(docId);
        return doc != null && Objects.equals(doc.getTenantId(), tenantId);
    }

    public long countByTenant(String tenantId) {
        return count(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getTenantId, tenantId));
    }
}
