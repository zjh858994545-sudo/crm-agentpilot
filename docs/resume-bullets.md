# Resume Bullets

## 中文简历版本

CRM-AgentPilot｜CRM AI Agent 全栈平台

技术栈：Spring Boot、MyBatis-Plus、PostgreSQL/pgvector、Redis、Kafka、React、Docker、JUnit 5

- 设计并实现面向本地生活销售场景的 CRM Agent 工作台，覆盖客户 360 分析、商机优先级推荐、销售知识库检索、跟进策略建议、通话摘要质检和 CRM 任务确认流。
- 搭建基于规则路由的 Agent Orchestrator 与 Tool Registry，支持读写工具隔离、工具调用审计、失败记录与人工确认后写入 CRM，并预留 OpenAI-compatible 模型适配层。
- 设计 `agent_run`、`agent_tool_call`、`agent_confirmation` 等审计表，记录工具输入输出、状态、耗时和 confirmation，实现 Agent 行为可追踪、可排查。
- 实现销售知识库 RAG 工程链路，包含文档导入、分块、查询改写、关键词检索、deterministic mock embedding、引用生成和低置信拒答，用于销售 SOP、产品政策和质检规则问答。
- 实现可解释商机评分，根据套餐到期、客户等级、最近联系、历史异议、意向等级等因素输出优先级、推荐理由和下一步动作。
- 建立 JSONL 评测集与自动评测脚本，量化 RAG Recall@5、引用命中、拒答准确率、工具调用成功率、写操作确认覆盖率和延迟。

## English Version

CRM-AgentPilot | Full-stack CRM AI Agent platform for sales operations

Tech stack: Spring Boot, MyBatis-Plus, PostgreSQL/pgvector, Redis, Kafka, React, Docker, JUnit 5

- Designed and implemented a CRM AI Agent platform for local-services sales workflows, covering customer 360 analysis, lead prioritization, RAG-based sales knowledge retrieval, follow-up strategy generation, call summary quality checks, and CRM task creation.
- Built a rule-routed Agent Orchestrator and Tool Registry to support read/write tool separation, auditable tool execution, failure handling, and human confirmation before CRM writes.
- Designed `agent_run`, `agent_tool_call`, and `agent_confirmation` audit records to capture tool input, output, status, latency, and confirmation state for tracing and debugging.
- Implemented a sales knowledge RAG pipeline with document import, chunking, query rewrite, keyword retrieval, deterministic mock embeddings, citation, and refusal behavior for low-confidence answers.
- Implemented explainable lead scoring based on package expiry, customer value, contact recency, historical objections, intent level, and task status.
- Added JSONL evaluation cases and scripts to measure RAG Recall@5, citation hit rate, refusal accuracy, tool-calling success rate, write-confirmation coverage, and latency.
