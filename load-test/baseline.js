// Stage 1 baseline load test for the URL shortener.
//
// Measures latency for create (POST /api/urls) and redirect (GET /{code})
// at a fixed request rate, so later stages can be compared like for like.
// Restart the app before every run so each run starts with an empty store.
//
// Run with Docker (script fed on stdin, so no volume mount is needed):
//   docker run --rm -i grafana/k6 run - < load-test/baseline.js
// PowerShell has no "<" redirection, so pipe the file instead:
//   Get-Content load-test/baseline.js | docker run --rm -i grafana/k6 run -
// Override settings with k6's -e flag, for example:
//   docker run --rm -i grafana/k6 run -e REDIRECT_RATE=1000 - < load-test/baseline.js

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

// host.docker.internal lets the k6 container reach the app running on your machine.
const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8090';
const CREATE_RATE = Number(__ENV.CREATE_RATE || 20);       // creates per second
const REDIRECT_RATE = Number(__ENV.REDIRECT_RATE || 200);  // redirects per second
const DURATION = __ENV.DURATION || '2m';                   // length of the measured phase
const POOL_SIZE = Number(__ENV.POOL_SIZE || 1000);         // links created up front for redirects

const CREATE_PARAMS = {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'create' },
};
// Without a fixed name tag, every /{code} URL would become its own metric series.
const REDIRECT_PARAMS = { tags: { name: 'redirect' } };

// One latency metric per endpoint, so the summary reports them separately.
const createLatency = new Trend('create_latency', true);
const redirectLatency = new Trend('redirect_latency', true);

export const options = {
    // Never follow the 302: we are measuring our server, not the destination site.
    maxRedirects: 0,
    setupTimeout: '2m',
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max', 'count'],
    scenarios: {
        // Warm-up: lets the JVM compile the hot code paths before measuring.
        // Its requests are not added to create_latency or redirect_latency.
        warmup: {
            executor: 'constant-arrival-rate',
            exec: 'warmup',
            rate: 50,
            timeUnit: '1s',
            duration: '20s',
            preAllocatedVUs: 10,
            maxVUs: 50,
        },
        // Measured phase: creates and redirects run at the same time, at fixed rates.
        create: {
            executor: 'constant-arrival-rate',
            exec: 'createLink',
            rate: CREATE_RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 10,
            maxVUs: 100,
            startTime: '25s',
        },
        redirect: {
            executor: 'constant-arrival-rate',
            exec: 'followLink',
            rate: REDIRECT_RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 20,
            maxVUs: 200,
            startTime: '25s',
        },
    },
    thresholds: {
        // Correctness guards, not performance targets:
        // every request must behave exactly as the contract says.
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
    },
};

// Runs once before the test: creates the pool of links that the redirect scenario follows.
export function setup() {
    const runId = Date.now();
    const codes = [];

    for (let i = 0; i < POOL_SIZE; i++) {
        const res = http.post(
            `${BASE_URL}/api/urls`,
            JSON.stringify({ url: `https://load.example.com/pool/${runId}/${i}` }),
            CREATE_PARAMS,
        );
        if (res.status !== 201) {
            throw new Error(`setup: expected 201, got ${res.status}: ${res.body}`);
        }
        codes.push(res.json('code'));
    }

    return { runId, codes };
}

export function warmup(data) {
    const url = `https://load.example.com/warmup/${data.runId}/${exec.scenario.iterationInTest}`;
    const res = http.post(`${BASE_URL}/api/urls`, JSON.stringify({ url }), CREATE_PARAMS);
    const created = check(res, { 'warmup: create returns 201': (r) => r.status === 201 });
    if (created) {
        http.get(`${BASE_URL}/${res.json('code')}`, REDIRECT_PARAMS);
    }
}

export function createLink(data) {
    // Every URL is unique, so this measures the new-link path (201), not the duplicate path (200).
    const url = `https://load.example.com/create/${data.runId}/${exec.scenario.iterationInTest}`;
    const res = http.post(`${BASE_URL}/api/urls`, JSON.stringify({ url }), CREATE_PARAMS);
    createLatency.add(res.timings.duration);
    check(res, { 'create: status is 201': (r) => r.status === 201 });
}

export function followLink(data) {
    const code = data.codes[Math.floor(Math.random() * data.codes.length)];
    const res = http.get(`${BASE_URL}/${code}`, REDIRECT_PARAMS);
    redirectLatency.add(res.timings.duration);
    check(res, {
        'redirect: status is 302': (r) => r.status === 302,
        'redirect: Location header present': (r) => Boolean(r.headers['Location']),
    });
}