import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// QA2: 200 requests/sec x 10 events/request = 2,000 events/sec, p95 <= 100 ms,
// while a billing run is in progress (the "billing" scenario starts the batch job after 10 s).
//   k6 run -e BASE_URL=http://localhost:8080 -e BILLING_PERIOD=2026-10 performance/ingest.js
// Local Basic Auth: start the backend with METERFLOW_PASSWORD_HASH_STRENGTH=4 (BCrypt runs per
// request), or use a JWT against the prod profile via -e AUTH="Bearer <token>".
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH = __ENV.AUTH || 'Basic YWRtaW46bWV0ZXJmbG93LWFkbWlu';

export const options = {
  scenarios: {
    ingest: { executor: 'constant-arrival-rate', exec: 'ingest', rate: 200, timeUnit: '1s', duration: __ENV.DURATION || '120s', preAllocatedVUs: 100, maxVUs: 500 },
    billing: { executor: 'shared-iterations', exec: 'billing', vus: 1, iterations: 1, startTime: '10s', maxDuration: '10m' },
  },
  thresholds: {
    'http_req_duration{scenario:ingest}': ['p(95)<=100'],
    'http_req_failed{scenario:ingest}': ['rate<0.01'],
  },
};
const submitted = new Counter('meterflow_events_submitted');

export function ingest() {
  const id = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const customer = `${__ENV.CUSTOMER_PREFIX || 'load-customer'}-${__VU % 50}`;
  const events = Array.from({ length: 10 }, (_, i) => ({ customerId: customer, metric: 'api_calls', quantity: 1, timestamp: new Date().toISOString(),
    idempotencyKey: `${id}-${i}`, properties: { region: 'load', endpoint: '/v1/load' } }));
  const response = http.post(`${BASE}/api/usage/events:batch`, JSON.stringify({ events }), { headers: { 'Content-Type': 'application/json', Authorization: AUTH } });
  check(response, { 'batch accepted': r => r.status === 200 });
  submitted.add(events.length);
}

export function billing() {
  if (!__ENV.BILLING_PERIOD) return;
  const r = http.post(`${BASE}/api/invoices/batch/run?period=${__ENV.BILLING_PERIOD}`, null, { headers: { Authorization: AUTH }, timeout: '600s' });
  check(r, { 'billing job ran': x => x.status === 200 });
}
