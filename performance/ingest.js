import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 200 requests/sec x 10 events/request = 2,000 submitted events/sec.
export const options = {
  scenarios: { ingest: { executor: 'constant-arrival-rate', rate: 200, timeUnit: '1s', duration: __ENV.DURATION || '60s', preAllocatedVUs: 100, maxVUs: 500 } },
  thresholds: { http_req_duration: ['p(95)<=100'], http_req_failed: ['rate<0.01'] }
};
const submitted = new Counter('meterflow_events_submitted');
export default function () {
  const id = `k6-${__VU}-${__ITER}-${Date.now()}`;
  const events = Array.from({length: 10}, (_, i) => ({ customerId: __ENV.CUSTOMER_ID || 'load-customer', metric: 'api_calls', quantity: 1, timestamp: new Date().toISOString(), idempotencyKey: `${id}-${i}`, properties: { region: 'load', endpoint: '/v1/load' } }));
  const response = http.post(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/usage/events:batch`, JSON.stringify({events}), {headers: {'Content-Type':'application/json', 'Authorization': __ENV.AUTH || 'Basic YWRtaW46bWV0ZXJmbG93LWFkbWlu'}});
  check(response, {'batch accepted': r => r.status === 200});
  submitted.add(events.length);
}
