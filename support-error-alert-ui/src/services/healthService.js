import { API_BASE } from '../config/api.js';
import { request } from './httpClient.js';

/**
 * @typedef {Object} ApmMemoryStats
 * @property {number} usedBytes
 * @property {number} committedBytes
 * @property {number} maxBytes
 * @property {number} usedPercent
 */

/**
 * @typedef {Object} ApmLoadStats
 * @property {number} processCpuLoad
 * @property {number} systemCpuLoad
 * @property {number} systemLoadAverage
 * @property {number} availableProcessors
 */

/**
 * @typedef {Object} ApmGcStats
 * @property {number} collectionCount
 * @property {number} collectionTimeMs
 */

/**
 * @typedef {Object} ApmThreadStats
 * @property {number} live
 * @property {number} peak
 * @property {number} daemon
 * @property {number} runnable
 * @property {number} blocked
 * @property {number} waiting
 */

/**
 * @typedef {Object} ApmHealthStats
 * @property {string} status
 * @property {Record<string, string>} components
 */

/**
 * @typedef {Object} ApmProbeStats
 * @property {string} liveness
 * @property {string} readiness
 */

/**
 * @typedef {Object} ApmRequestStats
 * @property {number} totalRequests
 * @property {number} errorRequests
 * @property {number} requestsDelta
 * @property {number} errorsDelta
 * @property {number} requestsPerMinute
 * @property {number} errorRatePercent
 * @property {number} avgResponseTimeMs
 */

/**
 * @typedef {Object} ApmLatencyStats
 * @property {number} avgMs
 * @property {number} maxMs
 * @property {number} apdexThresholdMs
 */

/**
 * @typedef {Object} ApmApdexStats
 * @property {number} score
 * @property {string} rating
 * @property {number} thresholdMs
 */

/**
 * @typedef {Object} ApmTransaction
 * @property {string} uri
 * @property {string} method
 * @property {number} count
 * @property {number} errorCount
 * @property {number} errorRatePercent
 * @property {number} avgMs
 * @property {number} maxMs
 * @property {number} apdex
 */

/**
 * @typedef {Object} ApmAlertEvent
 * @property {string} timestamp
 * @property {string} severity
 * @property {string} code
 * @property {string} message
 */

/**
 * @typedef {Object} ApmAlertsView
 * @property {string} timestamp
 * @property {string} serviceName
 * @property {string} status
 * @property {number} activeCount
 * @property {ApmAlertEvent[]} alerts
 * @property {ApmAlertEvent[]} latest
 */

/**
 * @typedef {Object} ApmThreadStack
 * @property {string} name
 * @property {string} state
 * @property {number} cpuTimeMs
 * @property {string[]} frames
 */

/**
 * @typedef {Object} ApmMetricSample
 * @property {string} timestamp
 * @property {number} processCpuLoad
 * @property {number} heapUsedPercent
 * @property {number} requestsPerMinute
 * @property {number} errorRatePercent
 * @property {number} [avgLatencyMs]
 * @property {number} [apdex]
 */

/**
 * @typedef {Object} ApmMetricsView
 * @property {string} timestamp
 * @property {string} status
 * @property {string} serviceName
 * @property {string} [targetUrl]
 * @property {string} [source]
 * @property {number} uptimeMs
 * @property {ApmLoadStats} load
 * @property {ApmMemoryStats} heap
 * @property {ApmThreadStats} threads
 * @property {ApmRequestStats} requests
 * @property {ApmLatencyStats} [latency]
 * @property {ApmApdexStats} [apdex]
 * @property {ApmTransaction[]} [transactions]
 * @property {ApmMetricSample[]} recentSamples
 */

/**
 * @typedef {Object} ApmSnapshot
 * @property {'UP' | 'DEGRADED' | 'DOWN' | string} status
 * @property {string} timestamp
 * @property {string} serviceName
 * @property {number} uptimeMs
 * @property {ApmLoadStats} load
 * @property {ApmMemoryStats} heap
 * @property {ApmMemoryStats} nonHeap
 * @property {ApmGcStats} gc
 * @property {ApmThreadStats} threads
 * @property {ApmHealthStats} health
 * @property {ApmProbeStats} probes
 * @property {ApmRequestStats} requests
 * @property {ApmLatencyStats} [latency]
 * @property {ApmApdexStats} [apdex]
 * @property {ApmTransaction[]} [transactions]
 * @property {ApmAlertEvent[]} [alerts]
 * @property {ApmThreadStack[]} topStacks
 * @property {ApmMetricSample[]} recentSamples
 * @property {string} [targetUrl]
 * @property {'remote' | 'local' | string} [source]
 */

function openEventSource(path, handlers) {
  const url = `${API_BASE}${path}`;
  const source = new EventSource(url, { withCredentials: true });

  Object.entries(handlers).forEach(([eventName, handler]) => {
    if (eventName === 'error') return;
    source.addEventListener(eventName, (event) => {
      try {
        handler(JSON.parse(event.data));
      } catch {
        /* ignore malformed */
      }
    });
  });

  if (handlers.message) {
    source.onmessage = (event) => {
      try {
        handlers.message(JSON.parse(event.data));
      } catch {
        /* ignore */
      }
    };
  }

  if (handlers.error) {
    source.onerror = handlers.error;
  }

  return () => source.close();
}

/** @returns {Promise<ApmSnapshot>} */
export async function fetchApmSnapshot() {
  const res = await request('/api/health/apm');
  return res.json();
}

/** @returns {Promise<ApmMetricsView>} */
export async function fetchApmMetrics() {
  const res = await request('/api/health/apm/metrics');
  return res.json();
}

/** @returns {Promise<ApmAlertsView>} */
export async function fetchApmAlerts() {
  const res = await request('/api/health/apm/alerts');
  return res.json();
}

/** @returns {Promise<{ threadCount: number, threads: ApmThreadStack[] }>} */
export async function fetchApmStack() {
  const res = await request('/api/health/apm/stack');
  return res.json();
}

/**
 * Full APM snapshot stream (+ alert push events).
 * @param {{ onApm?: (s: ApmSnapshot) => void, onAlert?: (a: ApmAlertEvent) => void, onError?: (e: Event) => void }} handlers
 */
export function subscribeApmStream(handlers) {
  return openEventSource('/api/health/apm/stream', {
    apm: handlers.onApm,
    alert: handlers.onAlert,
    message: handlers.onApm,
    error: handlers.onError,
  });
}

/**
 * Lightweight metrics stream (~1s).
 * @param {{ onMetrics?: (m: ApmMetricsView) => void, onError?: (e: Event) => void }} handlers
 */
export function subscribeMetricsStream(handlers) {
  return openEventSource('/api/health/apm/metrics/stream', {
    metrics: handlers.onMetrics,
    message: handlers.onMetrics,
    error: handlers.onError,
  });
}

/**
 * Alerts feed stream (full list + single alert pushes).
 * @param {{ onAlerts?: (a: ApmAlertsView) => void, onAlert?: (a: ApmAlertEvent) => void, onError?: (e: Event) => void }} handlers
 */
export function subscribeAlertsStream(handlers) {
  return openEventSource('/api/health/apm/alerts/stream', {
    alerts: handlers.onAlerts,
    alert: handlers.onAlert,
    message: handlers.onAlerts,
    error: handlers.onError,
  });
}
