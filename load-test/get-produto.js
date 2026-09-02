import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUTO_ID = __ENV.PRODUTO_ID || '11111111-1111-1111-1111-111111111111';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    get_produto: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 500,
      maxVUs: 1000,
      gracefulStop: '10s',
      stages: [
        { duration: '10s', target: 2000 },
        { duration: '15s', target: 10000 },
        { duration: '30s', target: 10000 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(99)<50'],
    dropped_iterations: ['count==0'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/produtos/${PRODUTO_ID}`);
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
