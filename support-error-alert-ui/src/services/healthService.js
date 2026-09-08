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
 * @typedef {Object} ApmGcCollectorStats
 * @property {string} name
 * @property {number} collectionCount
 * @property {number} collectionTimeMs
 * @property {number} collectionCountDelta
 * @property {number} collectionTimeMsDelta
 */

/**
 * @typedef {Object} ApmGcStats
 * @property {number} collectionCount
 * @property {number} collectionTimeMs
 * @property {number} collectionCountDelta
 * @property {number} collectionTimeMsDelta
 * @property {ApmGcCollectorStats[]} [collectors]
 */

/**
 * @typedef {Object} ApmLoadStats
 * @property {number} processCpuLoad
 * @property {number} systemCpuLoad
 * @property {number} systemLoadAverage
 * @property {number} availableProcessors
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
 * @typedef {Object} HealthEnvironment
 * @property {string} id
 * @property {string} label
 * @property {string} url
 * @property {string} service
 * @property {'remote' | 'local' | string} source
 */

/**
 * @typedef {Object} HealthApplication
 * @property {string} id
 * @property {string} name
 * @property {HealthEnvironment[]} environments
 */

/**
 * @typedef {Object} HealthTargetsCatalog
 * @property {string} defaultApplication
 * @property {string} defaultEnvironment
 * @property {HealthApplication[]} applications
 */

/**
 * @typedef {Object} ApmAlertsView
 * @property {string} timestamp
 * @property {string} serviceName
 * @property {string} status
 * @property {number} activeCount
 * @property {ApmAlertEvent[]} [active]
 * @property {ApmAlertEvent[]} alerts
 * @property {ApmAlertEvent[]} latest
 * @property {string} [applicationId]
 * @property {string} [applicationName]
 * @property {string} [environment]
 * @property {string} [environmentLabel]
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
 * @property {number} [nonHeapUsedPercent]
 * @property {number} requestsPerMinute
 * @property {number} errorRatePercent
 * @property {number} [avgLatencyMs]
 * @property {number} [apdex]
 * @property {number} [gcCollectionCount]
 * @property {number} [gcCollectionTimeMs]
 * @property {number} [gcCollectionCountDelta]
 * @property {number} [gcCollectionTimeMsDelta]
 * @property {number} [dbUsagePercent]
 * @property {number} [externalErrorRatePercent]
 */

/**
 * @typedef {Object} ApmDatabasePool
 * @property {string} name
 * @property {string} vendor
 * @property {number} active
 * @property {number} idle
 * @property {number} pending
 * @property {number} min
 * @property {number} max
 * @property {number} timeouts
 * @property {number} usageAvgMs
 * @property {number} acquireAvgMs
 * @property {number} usagePercent
 */

/**
 * @typedef {Object} ApmDatabaseQuery
 * @property {string} repository
 * @property {string} method
 * @property {number} count
 * @property {number} errorCount
 * @property {number} errorRatePercent
 * @property {number} avgMs
 * @property {number} maxMs
 */

/**
 * @typedef {Object} ApmDatabaseStats
 * @property {string} status
 * @property {string} product
 * @property {string} validationQuery
 * @property {ApmDatabasePool[]} [pools]
 * @property {ApmDatabaseQuery[]} [queries]
 * @property {number} active
 * @property {number} idle
 * @property {number} pending
 * @property {number} max
 * @property {number} timeouts
 */

/**
 * @typedef {Object} ApmExternalService
 * @property {string} name
 * @property {string} kind
 * @property {string} target
 * @property {string} uri
 * @property {string} method
 * @property {number} count
 * @property {number} errorCount
 * @property {number} errorRatePercent
 * @property {number} avgMs
 * @property {number} maxMs
 * @property {string} healthStatus
 */

/**
 * @typedef {Object} ApmServiceMapNode
 * @property {string} id
 * @property {string} name
 * @property {string} kind
 * @property {string} status
 * @property {number} avgMs
 * @property {number} calls
 * @property {number} errorRatePercent
 * @property {string} detail
 */

/**
 * @typedef {Object} ApmServiceMapEdge
 * @property {string} from
 * @property {string} to
 * @property {number} calls
 * @property {number} avgMs
 * @property {number} errorRatePercent
 * @property {string} status
 */

/**
 * @typedef {Object} ApmServiceMap
 * @property {ApmServiceMapNode[]} nodes
 * @property {ApmServiceMapEdge[]} edges
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
 * @property {ApmMemoryStats} [nonHeap]
 * @property {ApmGcStats} [gc]
 * @property {ApmThreadStats} threads
 * @property {ApmRequestStats} requests
 * @property {ApmLatencyStats} [latency]
 * @property {ApmApdexStats} [apdex]
 * @property {ApmTransaction[]} [transactions]
 * @property {ApmMetricSample[]} recentSamples
 * @property {ApmDatabaseStats} [database]
 * @property {ApmExternalService[]} [externalServices]
 * @property {ApmServiceMap} [serviceMap]
 * @property {string} [applicationId]
 * @property {string} [applicationName]
 * @property {string} [environment]
 * @property {string} [environmentLabel]
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
 * @property {ApmDatabaseStats} [database]
 * @property {ApmExternalService[]} [externalServices]
 * @property {ApmServiceMap} [serviceMap]
 * @property {string} [targetUrl]
 * @property {'remote' | 'local' | string} [source]
 * @property {string} [applicationId]
 * @property {string} [applicationName]
 * @property {string} [environment]
 * @property {string} [environmentLabel]
 */

/**
 * @param {string} [application]
 * @param {string} [env]
 */
export function targetQuery(application, env) {
  const params = new URLSearchParams();
  if (application) params.set('application', application);
  if (env) params.set('env', env);
  const query = params.toString();
  return query ? `?${query}` : '';
}

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

/** @returns {Promise<HealthTargetsCatalog>} */
export async function fetchHealthTargets() {
  const res = await request('/api/health/targets');
  return res.json();
}

/**
 * @param {string} [application]
 * @param {string} [env]
 * @returns {Promise<ApmSnapshot>}
 */
export async function fetchApmSnapshot(application, env) {
  const res = await request(`/api/health/apm${targetQuery(application, env)}`);
  return res.json();
}

/**
 * @param {string} [application]
 * @param {string} [env]
 * @returns {Promise<ApmMetricsView>}
 */
export async function fetchApmMetrics(application, env) {
  const res = await request(`/api/health/apm/metrics${targetQuery(application, env)}`);
  return res.json();
}

/**
 * @param {string} [application]
 * @param {string} [env]
 * @returns {Promise<ApmAlertsView>}
 */
export async function fetchApmAlerts(application, env) {
  const res = await request(`/api/health/apm/alerts${targetQuery(application, env)}`);
  return res.json();
}

/**
 * @param {string} [application]
 * @param {string} [env]
 * @returns {Promise<{ threadCount: number, threads: ApmThreadStack[] }>}
 */
export async function fetchApmStack(application, env) {
  const res = await request(`/api/health/apm/stack${targetQuery(application, env)}`);
  return res.json();
}

/**
 * @typedef {Object} HeapMemoryPoolUsage
 * @property {string} id
 * @property {string} area
 * @property {number} usedBytes
 * @property {number} committedBytes
 * @property {number} maxBytes
 * @property {number} usedPercent
 */

/**
 * @typedef {Object} HeapClassMemoryUsage
 * @property {number} rank
 * @property {string} className
 * @property {number} instanceCount
 * @property {number} shallowBytes
 * @property {number} percentOfTotal
 */

/**
 * @typedef {Object} HeapAnalysisView
 * @property {string} timestamp
 * @property {string} serviceName
 * @property {string} applicationId
 * @property {string} applicationName
 * @property {string} environment
 * @property {string} environmentLabel
 * @property {string} source
 * @property {string} targetUrl
 * @property {boolean} histogramAvailable
 * @property {string} histogramNote
 * @property {HeapMemoryPoolUsage[]} pools
 * @property {HeapClassMemoryUsage[]} classes
 * @property {number} totalShallowBytes
 * @property {number} classCount
 */

/**
 * @param {string} [application]
 * @param {string} [env]
 * @returns {Promise<HeapAnalysisView>}
 */
export async function fetchHeapAnalysis(application, env) {
  const res = await request(`/api/health/apm/heap-analysis${targetQuery(application, env)}`);
  return res.json();
}

/**
 * Full APM snapshot stream (+ alert push events).
 * @param {{ onApm?: (s: ApmSnapshot) => void, onAlert?: (a: ApmAlertEvent) => void, onError?: (e: Event) => void, application?: string, env?: string }} handlers
 */
export function subscribeApmStream(handlers) {
  return openEventSource(`/api/health/apm/stream${targetQuery(handlers.application, handlers.env)}`, {
    apm: handlers.onApm,
    alert: handlers.onAlert,
    message: handlers.onApm,
    error: handlers.onError,
  });
}

/**
 * Lightweight metrics stream (~1s).
 * @param {{ onMetrics?: (m: ApmMetricsView) => void, onError?: (e: Event) => void, application?: string, env?: string }} handlers
 */
export function subscribeMetricsStream(handlers) {
  return openEventSource(`/api/health/apm/metrics/stream${targetQuery(handlers.application, handlers.env)}`, {
    metrics: handlers.onMetrics,
    message: handlers.onMetrics,
    error: handlers.onError,
  });
}

/**
 * Alerts feed stream (full list + single alert pushes).
 * @param {{ onAlerts?: (a: ApmAlertsView) => void, onAlert?: (a: ApmAlertEvent) => void, onError?: (e: Event) => void, application?: string, env?: string }} handlers
 */
export function subscribeAlertsStream(handlers) {
  return openEventSource(`/api/health/apm/alerts/stream${targetQuery(handlers.application, handlers.env)}`, {
    alerts: handlers.onAlerts,
    alert: handlers.onAlert,
    message: handlers.onAlerts,
    error: handlers.onError,
  });
}
