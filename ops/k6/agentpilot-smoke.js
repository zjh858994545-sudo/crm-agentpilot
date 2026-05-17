import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

export const options = {
  scenarios: {
    dashboard: {
      executor: 'constant-vus',
      vus: Number(__ENV.K6_VUS || 5),
      duration: __ENV.K6_DURATION || '1m',
      exec: 'dashboard'
    },
    agent: {
      executor: 'constant-vus',
      vus: Number(__ENV.K6_AGENT_VUS || 2),
      duration: __ENV.K6_AGENT_DURATION || '1m',
      exec: 'agent'
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2500'],
    agent_latency: ['p(95)<8000'],
    dashboard_latency: ['p(95)<1500']
  }
};

const failures = new Rate('agentpilot_check_failed');
const agentLatency = new Trend('agent_latency');
const dashboardLatency = new Trend('dashboard_latency');

const baseUrl = (__ENV.BASE_URL || 'http://localhost:18080').replace(/\/$/, '');
const token = __ENV.AGENTPILOT_API_TOKEN || '';

function headers() {
  const result = {
    'Content-Type': 'application/json',
    'X-Trace-Id': `k6-${__VU}-${__ITER}`
  };
  if (token) {
    result['X-AgentPilot-Token'] = token;
  }
  return result;
}

export function dashboard() {
  const response = http.get(`${baseUrl}/api/dashboard/metrics`, { headers: headers() });
  dashboardLatency.add(response.timings.duration);
  const ok = check(response, {
    'dashboard status 200': (res) => res.status === 200,
    'dashboard api success': (res) => String(res.body).includes('"success":true')
  });
  failures.add(!ok);
  sleep(1);
}

export function agent() {
  const payload = JSON.stringify({
    userId: Number(__ENV.AGENTPILOT_USER_ID || 1),
    salesRepId: Number(__ENV.AGENTPILOT_SALES_REP_ID || 1),
    message: '帮我分析美家房产，给出今天跟进建议'
  });
  const response = http.post(`${baseUrl}/api/agent/chat`, payload, { headers: headers() });
  agentLatency.add(response.timings.duration);
  const ok = check(response, {
    'agent status 200': (res) => res.status === 200,
    'agent api success': (res) => String(res.body).includes('"success":true')
  });
  failures.add(!ok);
  sleep(2);
}
