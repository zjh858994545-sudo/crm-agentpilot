import axios from 'axios';

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
  traceId?: string;
}

export class AgentPilotApiError extends Error {
  status?: number;
  code?: string;
  traceId?: string;

  constructor(message: string, options: { status?: number; code?: string; traceId?: string } = {}) {
    super(message);
    this.name = 'AgentPilotApiError';
    this.status = options.status;
    this.code = options.code;
    this.traceId = options.traceId;
  }
}

export function describeApiError(error: unknown) {
  if (error instanceof AgentPilotApiError) {
    return error.traceId ? `${error.message} Trace ID：${error.traceId}` : error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '系统暂时不可用，请稍后重试。';
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface HealthView {
  status: string;
  app: string;
  version: string;
  phase: string;
  modelProvider: string;
  checkedAt: string;
  modules: Record<string, string>;
}

export interface ModelStatus {
  provider: string;
  vendor?: string;
  protocol?: string;
  model: string;
  configured: boolean;
  mode: string;
  embedding?: {
    provider: string;
    vendor?: string;
    protocol?: string;
    model: string;
    configured: boolean;
    dimension: number;
    mode: string;
  };
}

export interface EventStatus {
  mode: string;
  kafkaEnabled: boolean;
  agentRunTopic: string;
  agentToolCallTopic: string;
  crmTaskTopic: string;
  outboxPending?: number;
  outboxDispatching?: number;
  outboxDeadLetters?: number;
  maxRetryCount?: number;
  dispatcherWorkerId?: string;
}

export interface OutboxEvent {
  id: number;
  eventId: string;
  topic: string;
  eventType: string;
  aggregateType: string;
  aggregateId: string;
  traceId?: string;
  payloadJson?: string;
  status: string;
  retryCount: number;
  errorMessage?: string;
  lockedBy?: string;
  lockedAt?: string;
  createdAt?: string;
  publishedAt?: string;
}

export interface KnowledgeStatus {
  vectorStoreMode: string;
  pgvectorAvailable: boolean;
  docCount: number;
  chunkCount: number;
  vectorizedChunkCount: number;
}

export interface KnowledgeVectorRebuildResult {
  vectorStoreMode: string;
  updatedChunks: number;
  vectorizedChunkCount: number;
}

export interface SecurityStatus {
  mode: string;
  strict: boolean;
  permissionCount: number;
  rbacEnabled?: boolean;
  rbacUserCount?: number;
  rbacRoleCount?: number;
  activeTenantCount?: number;
  tokenConfigured: boolean;
  seedUsersEnabled?: boolean;
  strictWithoutToken: boolean;
  jwt?: {
    enabled: boolean;
    issuerConfigured: boolean;
    audience: string;
    userIdClaim: string;
    tenantClaim: string;
    salesRepClaim: string;
    rolesClaim: string;
    permissionsClaim: string;
    tenantAllowListEnabled?: boolean;
    allowedTenantCount?: number;
  };
  rateLimit?: {
    enabled: boolean;
    backend?: string;
    defaultCapacity: number;
    defaultRefillPerMinute: number;
    agentCapacity: number;
    agentRefillPerMinute: number;
    modelCapacity: number;
    modelRefillPerMinute: number;
  };
}

export interface AuthProfile {
  userId: number;
  tenantId: string;
  username: string;
  displayName: string;
  salesRepId: number;
  roles: string[];
  permissions: string[];
  primaryRole: 'sales' | 'manager' | 'admin';
}

export interface SecurityUser {
  userId: number;
  tenantId: string;
  username: string;
  displayName: string;
  salesRepId: number;
  status: string;
  lastAuthenticatedAt?: string;
  lastAuthenticatedIp?: string;
  roles: string[];
  permissions: string[];
}

export interface SecurityUserUpsertPayload {
  tenantId?: string;
  username?: string;
  displayName: string;
  salesRepId?: number;
  roles: string[];
}

export interface SecurityUserProvisioningResponse {
  profile: SecurityUser;
  apiToken: string;
}

export interface Tenant {
  id: string;
  name: string;
  status: 'ACTIVE' | 'DISABLED' | string;
  planCode: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TenantUpsertPayload {
  id?: string;
  name: string;
  planCode?: string;
}

export interface RetentionCategoryStatus {
  key: string;
  name: string;
  retentionDays: number;
  cutoffAt: string;
  eligibleRows: number;
  protectedRows: number;
  protectionRule: string;
}

export interface RetentionStatus {
  enabled: boolean;
  scheduledCleanupEnabled: boolean;
  cleanupCron: string;
  maxDeleteRowsPerRun: number;
  checkedAt: string;
  totalEligibleRows: number;
  categories: RetentionCategoryStatus[];
}

export interface RetentionCategoryResult {
  key: string;
  name: string;
  retentionDays: number;
  cutoffAt: string;
  eligibleRows: number;
  deletedRows: number;
  protectedRows: number;
}

export interface RetentionCleanupResult {
  dryRun: boolean;
  executedAt: string;
  totalEligibleRows: number;
  totalDeletedRows: number;
  categories: RetentionCategoryResult[];
}

export interface LaunchReadinessCheck {
  key: string;
  name: string;
  status: 'PASS' | 'WARN' | 'FAIL';
  severity: string;
  detail: string;
  action: string;
}

export interface LaunchReadinessStatus {
  overallStatus: 'READY' | 'WARN' | 'BLOCKED';
  phase: string;
  checkedAt: string;
  passCount: number;
  warnCount: number;
  failCount: number;
  checks: LaunchReadinessCheck[];
}

export interface DashboardSummary {
  highLeadCount: number;
  highLeadAmount: number;
  riskCustomerCount: number;
  dueTaskCount: number;
  renewalCustomerCount: number;
  pendingConfirmationCount: number;
}

export interface DashboardTrendPoint {
  date: string;
  amount: number;
  high: number;
  total: number;
}

export interface DashboardRiskCell {
  industry: string;
  riskLevel: string;
  count: number;
}

export interface DashboardRiskHeatmap {
  industries: string[];
  riskLevels: string[];
  max: number;
  cells: DashboardRiskCell[];
}

export interface DashboardMetrics {
  salesRepId: number;
  generatedAt: string;
  summary: DashboardSummary;
  leadTrend: DashboardTrendPoint[];
  riskHeatmap: DashboardRiskHeatmap;
}

export interface OpenAiToolDefinition {
  type: string;
  function: {
    name: string;
    description: string;
    parameters: Record<string, unknown>;
  };
}

export interface Customer {
  id: number;
  name: string;
  industry: string;
  city: string;
  address?: string;
  contactName?: string;
  contactMobile?: string;
  lifecycleStage?: string;
  valueLevel: string;
  riskLevel: string;
  ownerSalesRepId?: number;
  lastContactAt?: string;
  nextFollowTime?: string;
  packageExpireAt?: string;
  tags?: string;
  remark?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ContactLog {
  id: number;
  customerId: number;
  salesRepId: number;
  leadId?: number;
  channel: string;
  content: string;
  summary: string;
  customerIntent: string;
  objections?: string;
  nextAction?: string;
  contactAt: string;
  createdAt?: string;
}

export interface Lead {
  id: number;
  customerId: number;
  salesRepId: number;
  source: string;
  stage: string;
  intentLevel: string;
  estimatedAmount: number;
  expectedCloseDate: string;
  score: number;
  scoreReason?: string;
  status: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CrmTask {
  id: number;
  customerId: number;
  leadId?: number;
  salesRepId: number;
  title: string;
  content: string;
  dueTime: string;
  status: string;
  source: string;
  idempotencyKey?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface KnowledgeDoc {
  id: number;
  title: string;
  docType: string;
  source: string;
  version?: string;
  status: string;
  createdAt?: string;
}

export interface KnowledgeChunk {
  id: number;
  docId: number;
  chunkIndex: number;
  title: string;
  content: string;
  keywords?: string;
}

export interface KnowledgeItem {
  chunkId: number;
  docId: number;
  docTitle: string;
  docType: string;
  chunkTitle: string;
  content: string;
  score: number;
  retriever: string;
}

export interface KnowledgeSearchResponse {
  query: string;
  rewrittenQuery: string;
  items: KnowledgeItem[];
}

export interface AnswerCitation {
  docId: number;
  chunkId: number;
  docTitle: string;
  quote: string;
}

export interface KnowledgeAnswer {
  question: string;
  rewrittenQuery: string;
  answer: string;
  refused: boolean;
  citations: AnswerCitation[];
}

export interface LeadRecommendation {
  leadId: number;
  customerId: number;
  customerName: string;
  industry: string;
  estimatedAmount: number;
  expectedCloseDate: string;
  score: number;
  priority: string;
  reasons: string[];
  suggestedAction: string;
}

export interface ToolCallView {
  id: number;
  toolName: string;
  toolType: string;
  status: string;
  requiresConfirmation: boolean;
  confirmationId?: number;
}

export interface AgentChatResponse {
  type: 'final_answer' | 'confirmation_required';
  sessionId: number;
  runId: number;
  answer: string;
  confirmationId?: number;
  actionSummary?: string;
  payload?: Record<string, unknown>;
  toolCalls: ToolCallView[];
}

export interface AgentMessageContext {
  sessionId?: number;
  customerId?: number;
  salesRepId?: number;
}

export interface AgentRun {
  id: number;
  sessionId: number;
  userId: number;
  salesRepId: number;
  customerId?: number;
  userInput: string;
  agentOutput: string;
  intent: string;
  status: string;
  modelName: string;
  latencyMs?: number;
  errorMessage?: string;
  completedAt?: string;
}

export interface AgentToolCall {
  id: number;
  runId: number;
  toolName: string;
  toolType: string;
  inputJson: string;
  outputJson?: string;
  status: string;
  latencyMs?: number;
  errorMessage?: string;
  requiresConfirmation: boolean;
  confirmationId?: number;
  completedAt?: string;
}

export interface AgentExecutionStep {
  key: string;
  title: string;
  description: string;
  status: string;
  category: string;
  toolName?: string;
  toolType?: string;
  latencyMs?: number;
  confirmationId?: number;
  confirmationStatus?: string;
  occurredAt?: string;
  inputJson?: string;
  outputJson?: string;
}

export interface AgentExecutionTrace {
  run: AgentRun;
  toolCalls: AgentToolCall[];
  confirmations: AgentConfirmation[];
  steps: AgentExecutionStep[];
  routingMode: string;
  currentStage: string;
  safetyBoundary: string;
  requiresConfirmation: boolean;
}

export interface AgentConfirmation {
  id: number;
  runId: number;
  toolCallId?: number;
  actionType: string;
  actionSummary: string;
  payloadJson?: string;
  status: string;
  confirmedBy?: number;
  confirmedAt?: string;
  expiredAt?: string;
}

export interface EvaluationMetric {
  name: string;
  value: number;
  unit: string;
}

export interface EvaluationReport {
  reportName: string;
  generatedAt: string;
  metrics: EvaluationMetric[];
  reportPath: string;
}

export interface CallSummaryResponse {
  summary: string;
  customerIntent: string;
  objections: string;
  nextAction: string;
}

export interface QualityViolation {
  rule: string;
  severity: string;
  evidence: string;
  suggestion: string;
}

export interface QualityCheckResponse {
  riskLevel: string;
  violations: QualityViolation[];
  citations: AnswerCitation[];
}

export interface ContactLogConfirmationResponse {
  runId: number;
  confirmationId: number;
  actionSummary: string;
  payload: Record<string, unknown>;
}

export interface CustomerMemory {
  id: number;
  customerId: number;
  memoryType: string;
  content: string;
  importance: number;
  updatedAt?: string;
}

export const apiClient = axios.create({
  baseURL: '/api',
  timeout: 30000
});

interface StoredWorkspaceUser {
  userId?: number;
  tenantId?: string;
  salesRepId?: number;
  displayName?: string;
  primaryRole?: AuthProfile['primaryRole'];
}

function readStoredWorkspaceUser(): StoredWorkspaceUser {
  try {
    return JSON.parse(window.localStorage.getItem('agentpilot.currentUser') || '{}') as StoredWorkspaceUser;
  } catch {
    return {};
  }
}

function currentUserId() {
  return readStoredWorkspaceUser().userId ?? 1;
}

function currentSalesRepId(fallback?: number) {
  return fallback ?? readStoredWorkspaceUser().salesRepId ?? 1;
}

apiClient.interceptors.request.use((config) => {
  const bearerToken =
    import.meta.env.VITE_AGENTPILOT_BEARER_TOKEN ||
    window.localStorage.getItem('agentpilot.bearerToken');
  const token =
    import.meta.env.VITE_AGENTPILOT_API_TOKEN ||
    window.localStorage.getItem('agentpilot.apiToken');
  if (bearerToken) {
    config.headers.set('Authorization', `Bearer ${bearerToken}`);
    return config;
  }
  if (token) {
    config.headers.set('X-AgentPilot-Token', token);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
      const body = error.response?.data;
      const status = error.response?.status;
      const code = body?.code;
      const traceId = body?.traceId || error.response?.headers?.['x-trace-id'];
      const message =
        body?.message ||
        (status === 401
          ? '登录已失效或 Token 不正确，请重新登录。'
          : status === 403
            ? '当前账号没有执行该操作的权限。'
            : status === 429
              ? '请求过于频繁，请稍后再试。'
              : status && status >= 500
                ? '服务端暂时异常，请联系管理员并提供 Trace ID。'
                : '网络请求失败，请检查服务是否已启动。');
      return Promise.reject(new AgentPilotApiError(message, { status, code, traceId }));
    }
    return Promise.reject(error);
  }
);

export async function fetchHealth() {
  const response = await apiClient.get<ApiResponse<HealthView>>('/health');
  return response.data.data;
}

export async function fetchModelStatus() {
  const response = await apiClient.get<ApiResponse<ModelStatus>>('/model/status');
  return response.data.data;
}

export async function fetchEventStatus() {
  const response = await apiClient.get<ApiResponse<EventStatus>>('/events/status');
  return response.data.data;
}

export async function fetchDeadLetters() {
  const response = await apiClient.get<ApiResponse<OutboxEvent[]>>('/events/dead-letters');
  return response.data.data;
}

export async function retryDeadLetter(id: number) {
  const response = await apiClient.post<ApiResponse<{ accepted: boolean; outboxId: number }>>(`/events/dead-letters/${id}/retry`);
  return response.data.data;
}

export async function fetchKnowledgeStatus() {
  const response = await apiClient.get<ApiResponse<KnowledgeStatus>>('/knowledge/status');
  return response.data.data;
}

export async function rebuildKnowledgeVectors() {
  const response = await apiClient.post<ApiResponse<KnowledgeVectorRebuildResult>>('/knowledge/vectors/rebuild');
  return response.data.data;
}

export async function fetchSecurityStatus() {
  const response = await apiClient.get<ApiResponse<SecurityStatus>>('/security/status');
  return response.data.data;
}

export async function fetchSecurityUsers() {
  const response = await apiClient.get<ApiResponse<SecurityUser[]>>('/security/users');
  return response.data.data;
}

export async function createSecurityUser(payload: SecurityUserUpsertPayload) {
  const response = await apiClient.post<ApiResponse<SecurityUserProvisioningResponse>>('/security/users', payload);
  return response.data.data;
}

export async function updateSecurityUser(userId: number, payload: SecurityUserUpsertPayload) {
  const response = await apiClient.put<ApiResponse<SecurityUser>>(`/security/users/${userId}`, payload);
  return response.data.data;
}

export async function updateSecurityUserStatus(userId: number, status: 'ACTIVE' | 'DISABLED') {
  const response = await apiClient.patch<ApiResponse<SecurityUser>>(`/security/users/${userId}/status`, { status });
  return response.data.data;
}

export async function regenerateSecurityUserToken(userId: number) {
  const response = await apiClient.post<ApiResponse<SecurityUserProvisioningResponse>>(`/security/users/${userId}/token`);
  return response.data.data;
}

export async function fetchTenants() {
  const response = await apiClient.get<ApiResponse<Tenant[]>>('/tenants');
  return response.data.data;
}

export async function createTenant(payload: TenantUpsertPayload) {
  const response = await apiClient.post<ApiResponse<Tenant>>('/tenants', payload);
  return response.data.data;
}

export async function updateTenant(id: string, payload: TenantUpsertPayload) {
  const response = await apiClient.put<ApiResponse<Tenant>>(`/tenants/${id}`, payload);
  return response.data.data;
}

export async function updateTenantStatus(id: string, status: 'ACTIVE' | 'DISABLED') {
  const response = await apiClient.patch<ApiResponse<Tenant>>(`/tenants/${id}/status`, { status });
  return response.data.data;
}

export async function fetchRetentionStatus() {
  const response = await apiClient.get<ApiResponse<RetentionStatus>>('/operations/retention');
  return response.data.data;
}

export async function fetchLaunchReadiness() {
  const response = await apiClient.get<ApiResponse<LaunchReadinessStatus>>('/operations/readiness');
  return response.data.data;
}

export async function runRetentionCleanup(dryRun = true) {
  const response = await apiClient.post<ApiResponse<RetentionCleanupResult>>('/operations/retention/run', { dryRun });
  return response.data.data;
}

export async function fetchCurrentUser() {
  const response = await apiClient.get<ApiResponse<AuthProfile>>('/auth/me');
  return response.data.data;
}

export async function fetchDashboardMetrics() {
  const response = await apiClient.get<ApiResponse<DashboardMetrics>>('/dashboard/metrics');
  return response.data.data;
}

export async function fetchOpenAiTools() {
  const response = await apiClient.get<ApiResponse<OpenAiToolDefinition[]>>('/agent/tools/openai');
  return response.data.data;
}

export async function fetchCustomers() {
  const response = await apiClient.get<ApiResponse<Customer[]>>('/customers');
  return response.data.data;
}

export async function fetchCustomerPage(params: {
  page?: number;
  pageSize?: number;
  keyword?: string;
} = {}) {
  const response = await apiClient.get<ApiResponse<PageResponse<Customer>>>('/customers/page', { params });
  return response.data.data;
}

export async function fetchCustomerDetail(customerId: number) {
  const response = await apiClient.get<ApiResponse<Customer>>(`/customers/${customerId}`);
  return response.data.data;
}

export async function fetchCustomerContactLogs(customerId: number) {
  const response = await apiClient.get<ApiResponse<ContactLog[]>>(`/customers/${customerId}/contact-logs`);
  return response.data.data;
}

export async function fetchLeads() {
  const response = await apiClient.get<ApiResponse<Lead[]>>('/leads');
  return response.data.data;
}

export async function fetchLeadDetail(leadId: number) {
  const response = await apiClient.get<ApiResponse<Lead>>(`/leads/${leadId}`);
  return response.data.data;
}

export async function fetchLeadRecommendations(topK = 10) {
  const response = await apiClient.get<ApiResponse<LeadRecommendation[]>>('/leads/recommend', {
    params: { topK }
  });
  return response.data.data;
}

export async function fetchTasks() {
  const response = await apiClient.get<ApiResponse<CrmTask[]>>('/tasks');
  return response.data.data;
}

export async function fetchKnowledgeDocs() {
  const response = await apiClient.get<ApiResponse<KnowledgeDoc[]>>('/knowledge/docs');
  return response.data.data;
}

export async function fetchKnowledgeChunks(docId: number) {
  const response = await apiClient.get<ApiResponse<KnowledgeChunk[]>>(`/knowledge/docs/${docId}`);
  return response.data.data;
}

export async function importKnowledgeDoc(payload: {
  title: string;
  docType: string;
  source?: string;
  content: string;
}) {
  const response = await apiClient.post<ApiResponse<KnowledgeDoc>>('/knowledge/docs', {
    source: 'manual-upload',
    ...payload
  });
  return response.data.data;
}

export async function searchKnowledge(query: string, topK = 5) {
  const response = await apiClient.post<ApiResponse<KnowledgeSearchResponse>>('/knowledge/search', {
    query,
    topK
  });
  return response.data.data;
}

export async function askKnowledge(question: string, topK = 5) {
  const response = await apiClient.post<ApiResponse<KnowledgeAnswer>>('/knowledge/ask', {
    question,
    topK
  });
  return response.data.data;
}

export async function sendAgentMessage(message: string, context: AgentMessageContext = {}) {
  const response = await apiClient.post<ApiResponse<AgentChatResponse>>('/agent/chat', {
    userId: currentUserId(),
    salesRepId: currentSalesRepId(context.salesRepId),
    sessionId: context.sessionId,
    customerId: context.customerId,
    message
  });
  return response.data.data;
}

export async function confirmAgentAction(confirmationId: number) {
  const response = await apiClient.post<ApiResponse<Record<string, unknown>>>(
    `/agent/confirmations/${confirmationId}/confirm`,
    { userId: currentUserId() }
  );
  return response.data.data;
}

export async function rejectAgentAction(confirmationId: number) {
  const response = await apiClient.post<ApiResponse<Record<string, unknown>>>(
    `/agent/confirmations/${confirmationId}/reject`,
    { userId: currentUserId() }
  );
  return response.data.data;
}

export async function fetchAgentConfirmations(status = 'PENDING') {
  const response = await apiClient.get<ApiResponse<AgentConfirmation[]>>('/agent/confirmations', {
    params: { status }
  });
  return response.data.data;
}

export async function fetchAgentConfirmationPage(params: {
  page?: number;
  pageSize?: number;
  status?: string;
  keyword?: string;
} = {}) {
  const response = await apiClient.get<ApiResponse<PageResponse<AgentConfirmation>>>('/agent/confirmations/page', { params });
  return response.data.data;
}

export async function fetchAgentRuns() {
  const response = await apiClient.get<ApiResponse<AgentRun[]>>('/agent/runs');
  return response.data.data;
}

export async function fetchAgentRunPage(params: {
  page?: number;
  pageSize?: number;
  status?: string;
  keyword?: string;
} = {}) {
  const response = await apiClient.get<ApiResponse<PageResponse<AgentRun>>>('/agent/runs/page', { params });
  return response.data.data;
}

export async function exportAgentRunsCsv(params: {
  status?: string;
  keyword?: string;
  limit?: number;
} = {}) {
  const response = await apiClient.get<Blob>('/agent/runs/export', {
    params,
    responseType: 'blob'
  });
  return response.data;
}

export async function fetchAgentRunToolCalls(runId: number) {
  const response = await apiClient.get<ApiResponse<AgentToolCall[]>>(`/agent/runs/${runId}/tool-calls`);
  return response.data.data;
}

export async function fetchAgentExecutionTrace(runId: number) {
  const response = await apiClient.get<ApiResponse<AgentExecutionTrace>>(`/agent/runs/${runId}/execution`);
  return response.data.data;
}

export async function runEvaluation() {
  const response = await apiClient.post<ApiResponse<EvaluationReport>>('/evaluation/run');
  return response.data.data;
}

export async function summarizeCall(payload: { customerId: number; salesRepId: number; leadId?: number; text: string }) {
  const response = await apiClient.post<ApiResponse<CallSummaryResponse>>('/callcenter/summary', payload);
  return response.data.data;
}

export async function checkCallQuality(payload: { customerId: number; salesRepId: number; leadId?: number; text: string }) {
  const response = await apiClient.post<ApiResponse<QualityCheckResponse>>('/callcenter/quality-check', payload);
  return response.data.data;
}

export async function createContactLogConfirmation(payload: {
  customerId: number;
  salesRepId: number;
  leadId?: number;
  text: string;
}) {
  const response = await apiClient.post<ApiResponse<ContactLogConfirmationResponse>>(
    '/callcenter/contact-log-confirmations',
    payload
  );
  return response.data.data;
}

export async function fetchCustomerMemory(customerId: number) {
  const response = await apiClient.get<ApiResponse<CustomerMemory[]>>(`/callcenter/customers/${customerId}/memory`);
  return response.data.data;
}
