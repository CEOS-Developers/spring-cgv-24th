import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const scenario = __ENV.SCENARIO;
if (scenario !== 'movies' && scenario !== 'seats') {
    throw new Error('SCENARIO must be movies or seats');
}

const rate = positiveInteger('RATE');
const preAllocatedVUs = positiveInteger('PRE_ALLOCATED_VUS', '100');
const maxVUs = positiveInteger('MAX_VUS', '200');
if (maxVUs < preAllocatedVUs) {
    throw new Error('MAX_VUS must be at least PRE_ALLOCATED_VUS');
}

const duration = __ENV.DURATION ?? '40s';
const screeningId = scenario === 'seats' ? positiveInteger('SCREENING_ID', '10001') : null;
const path = scenario === 'movies' ? '/api/v1/movies' : `/api/v1/screenings/${screeningId}/seats`;
const url = `http://app:8080${path}`;
const requestOptions = { timeout: '5s', redirects: 0 };

// These metrics exclude the setup request from the results of the measured scenario.
const requests = new Counter('read_requests');
const successes = new Counter('read_successes');
const latency = new Trend('read_duration', true);

export const options = {
    discardResponseBodies: true,
    summaryTrendStats: ['p(95)', 'p(99)'],
    scenarios: {
        [scenario]: {
            executor: 'constant-arrival-rate',
            rate,
            timeUnit: '1s',
            duration,
            preAllocatedVUs,
            maxVUs,
            gracefulStop: '5s',
        },
    },
};

export function setup() {
    const response = http.get(url, { ...requestOptions, responseType: 'text' });
    if (response.status !== 200) {
        throw new Error(`Fixture check for ${path} returned HTTP ${response.status}`);
    }

    const items = response.json('data');
    if (!Array.isArray(items) || items.length !== 200) {
        const count = Array.isArray(items) ? items.length : 'no data array';
        throw new Error(`Fixture check for ${path} expected 200 items, received ${count}`);
    }

    if (scenario === 'seats') {
        const reserved = items.filter((seat) => seat.status === 'RESERVED').length;
        const available = items.filter((seat) => seat.status === 'AVAILABLE').length;
        if (reserved !== 40 || available !== 160) {
            throw new Error(`Expected 40 RESERVED and 160 AVAILABLE seats, received ${reserved} and ${available}`);
        }
    }
}

export default function () {
    const response = http.get(url, requestOptions);
    requests.add(1);
    successes.add(response.status === 200 ? 1 : 0);
    latency.add(response.timings.duration);
}

export function handleSummary({ metrics, state }) {
    const requestCount = metrics.read_requests?.values.count ?? 0;
    const successCount = metrics.read_successes?.values.count ?? 0;
    const durations = metrics.read_duration?.values;
    const summary = {
        scenario,
        targetRate: rate,
        testRunDurationMs: state.testRunDurationMs,
        requests: requestCount,
        successes: successCount,
        httpFailRate: requestCount > 0 ? (requestCount - successCount) / requestCount : null,
        latencyP95Ms: durations?.['p(95)'] ?? null,
        latencyP99Ms: durations?.['p(99)'] ?? null,
        droppedIterations: metrics.dropped_iterations?.values.count ?? 0,
        vusPeak: metrics.vus?.values.max ?? null,
        vusMax: metrics.vus_max?.values.max ?? null,
    };
    return { stdout: JSON.stringify(summary) + '\n' };
}

function positiveInteger(name, fallback) {
    const input = __ENV[name] ?? fallback;
    const value = Number(input);
    if (!/^[0-9]+$/.test(input ?? '') || !Number.isSafeInteger(value) || value < 1) {
        throw new Error(`${name} must be a positive integer`);
    }
    return value;
}
