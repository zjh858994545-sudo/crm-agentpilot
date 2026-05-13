import axios from 'axios';

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
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

export interface Customer {
  id: number;
  name: string;
  industry: string;
  city: string;
  valueLevel: string;
  riskLevel: string;
  packageExpireAt?: string;
  tags?: string;
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

export async function fetchHealth() {
  const response = await apiClient.get<ApiResponse<HealthView>>('/health');
  return response.data.data;
}

export async function fetchCustomers() {
  const response = await apiClient.get<ApiResponse<Customer[]>>('/customers');
  return response.data.data;
}

export async function fetchLeadRecommendations(topK = 10) {
  const response = await apiClient.get<ApiResponse<LeadRecommendation[]>>('/leads/recommend', {
    params: { topK }
  });
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
    source: 'manual-demo',
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

export async function sendAgentMessage(message: string, sessionId?: number) {
  const response = await apiClient.post<ApiResponse<AgentChatResponse>>('/agent/chat', {
    userId: 1,
    salesRepId: 1,
    sessionId,
    message
  });
  return response.data.data;
}

export async function confirmAgentAction(confirmationId: number) {
  const response = await apiClient.post<ApiResponse<Record<string, unknown>>>(
    `/agent/confirmations/${confirmationId}/confirm`,
    { userId: 1 }
  );
  return response.data.data;
}

export async function rejectAgentAction(confirmationId: number) {
  const response = await apiClient.post<ApiResponse<Record<string, unknown>>>(
    `/agent/confirmations/${confirmationId}/reject`,
    { userId: 1 }
  );
  return response.data.data;
}

export async function fetchAgentRuns() {
  const response = await apiClient.get<ApiResponse<AgentRun[]>>('/agent/runs');
  return response.data.data;
}

export async function fetchAgentRunToolCalls(runId: number) {
  const response = await apiClient.get<ApiResponse<AgentToolCall[]>>(`/agent/runs/${runId}/tool-calls`);
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
