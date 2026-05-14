# Resume Bullets

## 中文简历版本

**CRM-AgentPilot｜面向本地生活销售作业的 CRM AI Agent 平台**

技术栈：Java 17、Spring Boot 3、MyBatis-Plus、PostgreSQL/pgvector、Redis、Kafka、React 18、TypeScript、Docker Compose、JUnit 5。

- 设计并实现面向销售 CRM 作业流的 Agent 工作台，覆盖客户 360 分析、可解释商机推荐、销售知识库 RAG、跟进任务确认、通话摘要质检、运行审计与 JSONL 评测。
- 构建 LLM Tool Calling 优先、规则路由兜底的 Agent Orchestrator：通过 schema 化 Tool Registry 暴露 OpenAI-compatible function schema，支持读写工具隔离和可审计工具调用。
- 接入阿里云百炼 Qwen 聊天模型与 `text-embedding-v4` 真实 embedding；聊天与 embedding 均复用 OpenAI-compatible 协议适配层，便于切换不同模型供应商。
- 实现 RAG 工程链路：文档导入、分块、查询改写、pgvector 1024 维向量存储、HNSW 索引、关键词+向量混合检索、引用生成与低置信拒答。
- 设计 `agent_run`、`agent_tool_call`、`agent_confirmation`、`retrieval_log`、`agent_outbox_event` 等审计表，记录输入、输出、状态、耗时、确认人和事件分发状态。
- 对写 CRM 动作建立人工确认流：创建任务、写联系记录、更新商机阶段等写工具先生成 confirmation，用户确认后才落库，并记录确认人 userId。
- 引入 Spring Security API Token 与方法级权限控制；本地演示默认 permissive，strict 模式下通过 `X-AgentPilot-Token` 保护业务 API。
- 实现 outbox 事件机制：Agent run、tool call、CRM task 事件先落 `agent_outbox_event`，事务提交后异步分发到 log-only 或 Kafka topic。
- 搭建 Docker Compose 与一键演示脚本，支持 PostgreSQL/pgvector、Redis、Kafka、后端、前端联动；Docker Hub 不可用时自动回退本地后端/前端启动。
- 建立 JSONL 自动评测脚本，量化 RAG Recall@5、引用命中率、拒答准确率、工具调用成功率、写操作确认覆盖率和延迟指标，并生成 Markdown 报告。

## English Version

**CRM-AgentPilot | Full-stack CRM AI Agent platform for sales operations**

Tech stack: Java 17, Spring Boot 3, MyBatis-Plus, PostgreSQL/pgvector, Redis, Kafka, React 18, TypeScript, Docker Compose, JUnit 5.

- Built a CRM AI Agent workbench for local-services sales workflows, covering customer 360 analysis, explainable lead prioritization, RAG knowledge retrieval, write-action confirmation, call quality checks, run audit, and JSONL evaluation.
- Implemented an Agent Orchestrator that prioritizes LLM tool selection and falls back to deterministic routing, with a schema-driven Tool Registry exposing OpenAI-compatible function schemas.
- Integrated Alibaba Bailian Qwen chat models and `text-embedding-v4` embeddings through an OpenAI-compatible adapter, allowing provider switching without changing business code.
- Implemented a RAG pipeline with document import, chunking, query rewrite, pgvector 1024-d vector storage, HNSW indexing, keyword+vector hybrid retrieval, citation generation, and low-confidence refusal.
- Designed audit tables including `agent_run`, `agent_tool_call`, `agent_confirmation`, `retrieval_log`, and `agent_outbox_event` to record inputs, outputs, status, latency, confirmer, and event dispatch state.
- Added a human-confirmation guardrail for CRM writes: creating tasks, writing contact logs, and updating lead stages must create a confirmation record before any CRM mutation.
- Added Spring Security API-token authentication with method-level permissions; local demos run in permissive mode, while strict mode protects APIs through `X-AgentPilot-Token`.
- Implemented an outbox event mechanism that stores Agent run, tool-call, and CRM task events in `agent_outbox_event` before asynchronous log-only or Kafka dispatch.
- Added Docker Compose and one-command demo scripts for PostgreSQL/pgvector, Redis, Kafka, backend, and frontend; scripts fall back to local services when Docker Hub is unavailable.
- Added JSONL evaluation scripts measuring RAG Recall@5, citation hit rate, refusal accuracy, tool-calling success rate, write-confirmation coverage, and latency.
