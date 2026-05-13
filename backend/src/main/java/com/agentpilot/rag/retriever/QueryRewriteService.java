package com.agentpilot.rag.retriever;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class QueryRewriteService {

    public String rewrite(String query) {
        Set<String> terms = new LinkedHashSet<>();
        terms.add(query);
        if (containsAny(query, "价格", "贵", "预算")) {
            terms.add("价格异议 ROI 曝光 同行案例 套餐价值");
        }
        if (containsAny(query, "续费", "到期")) {
            terms.add("续费 到期 30天 效果复盘 优惠 下一步任务");
        }
        if (containsAny(query, "质检", "违规", "承诺", "保证")) {
            terms.add("质检 违规承诺 保证收益 保证排名 人工确认");
        }
        if (query.contains("房产")) {
            terms.add("房产 房源曝光 真实电话 线索质量 同区域案例");
        }
        if (query.contains("招聘")) {
            terms.add("招聘 旺季 岗位曝光 简历线索 不承诺入职");
        }
        if (containsAny(query, "沉默", "召回", "很久没联系")) {
            terms.add("沉默客户 14天未联系 召回 优化建议 跟进任务");
        }
        return String.join(" ", terms);
    }

    private boolean containsAny(String query, String... terms) {
        for (String term : terms) {
            if (query.contains(term)) {
                return true;
            }
        }
        return false;
    }
}

