import { useCallback, useEffect, useRef, useState } from 'react';
import * as healthService from '../services/healthService.js';

const POLL_INTERVAL_MS = 3000;
const TOAST_TTL_MS = 8000;

function formatError(e) {
  return e instanceof Error ? e.message : String(e);
}

function alertKey(alert) {
  return `${alert?.code ?? ''}|${alert?.timestamp ?? ''}|${alert?.message ?? ''}`;
}

/**
 * Live APM: full snapshot + dedicated realtime metrics/alerts SSE streams.
 * @param {{ enabled?: boolean }} [options]
 */
export function useHealthCheck({ enabled = true } = {}) {
  /** @type {[import('../services/healthService.js').ApmSnapshot | null, import('react').Dispatch<any>]} */
  const [snapshot, setSnapshot] = useState(null);
  /** @type {[import('../services/healthService.js').ApmMetricsView | null, import('react').Dispatch<any>]} */
  const [metrics, setMetrics] = useState(null);
  /** @type {[import('../services/healthService.js').ApmAlertEvent[], import('react').Dispatch<any>]} */
  const [alerts, setAlerts] = useState([]);
  const [activeAlertCount, setActiveAlertCount] = useState(0);
  /** @type {[import('../services/healthService.js').ApmAlertEvent | null, import('react').Dispatch<any>]} */
  const [alertToast, setAlertToast] = useState(null);
  /** @type {[import('../services/healthService.js').ApmThreadStack[], import('react').Dispatch<any>]} */
  const [fullStacks, setFullStacks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [stackLoading, setStackLoading] = useState(false);
  const [error, setError] = useState(/** @type {string | null} */ (null));
  const [live, setLive] = useState(false);
  const [metricsLive, setMetricsLive] = useState(false);
  const [alertsLive, setAlertsLive] = useState(false);
  const [paused, setPaused] = useState(false);
  const [mode, setMode] = useState(/** @type {'idle' | 'sse' | 'poll'} */ ('idle'));
  const [metricsTick, setMetricsTick] = useState(0);
  const mounted = useRef(true);
  const pausedRef = useRef(false);
  const sseFailCount = useRef(0);
  const seenAlertKeys = useRef(new Set());
  const toastTimer = useRef(/** @type {ReturnType<typeof setTimeout> | null} */ (null));

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      if (toastTimer.current) clearTimeout(toastTimer.current);
    };
  }, []);

  useEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  const pushToast = useCallback((/** @type {import('../services/healthService.js').ApmAlertEvent} */ alert) => {
    if (!mounted.current || pausedRef.current) return;
    setAlertToast(alert);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => {
      if (mounted.current) setAlertToast(null);
    }, TOAST_TTL_MS);
  }, []);

  const applySnapshot = useCallback((/** @type {import('../services/healthService.js').ApmSnapshot} */ data) => {
    if (!mounted.current || pausedRef.current) return;
    setSnapshot(data);
    if (Array.isArray(data.alerts)) setAlerts(data.alerts);
    setError(null);
    setLoading(false);
  }, []);

  const applyMetrics = useCallback((/** @type {import('../services/healthService.js').ApmMetricsView} */ data) => {
    if (!mounted.current || pausedRef.current) return;
    setMetrics(data);
    setMetricsTick((n) => n + 1);
    setSnapshot((prev) => {
      if (!prev) {
        return {
          ...data,
          nonHeap: prev?.nonHeap ?? { usedBytes: 0, committedBytes: 0, maxBytes: 0, usedPercent: 0 },
          gc: prev?.gc ?? { collectionCount: 0, collectionTimeMs: 0 },
          health: prev?.health ?? { status: data.status, components: {} },
          probes: prev?.probes ?? { liveness: 'UNKNOWN', readiness: 'UNKNOWN' },
          alerts: prev?.alerts ?? [],
          topStacks: prev?.topStacks ?? [],
        };
      }
      return {
        ...prev,
        status: data.status ?? prev.status,
        timestamp: data.timestamp ?? prev.timestamp,
        serviceName: data.serviceName ?? prev.serviceName,
        targetUrl: data.targetUrl ?? prev.targetUrl,
        source: data.source ?? prev.source,
        uptimeMs: data.uptimeMs ?? prev.uptimeMs,
        load: data.load ?? prev.load,
        heap: data.heap ?? prev.heap,
        threads: data.threads ?? prev.threads,
        requests: data.requests ?? prev.requests,
        latency: data.latency ?? prev.latency,
        apdex: data.apdex ?? prev.apdex,
        transactions: data.transactions ?? prev.transactions,
        recentSamples: data.recentSamples ?? prev.recentSamples,
      };
    });
  }, []);

  const applyAlertsView = useCallback((/** @type {import('../services/healthService.js').ApmAlertsView} */ data) => {
    if (!mounted.current || pausedRef.current) return;
    if (Array.isArray(data.alerts)) setAlerts(data.alerts);
    setActiveAlertCount(Number(data.activeCount) || 0);
    setSnapshot((prev) => (prev ? { ...prev, alerts: data.alerts ?? prev.alerts, status: data.status || prev.status } : prev));
    if (Array.isArray(data.latest)) {
      data.latest.forEach((alert) => {
        const key = alertKey(alert);
        if (!seenAlertKeys.current.has(key)) {
          seenAlertKeys.current.add(key);
          pushToast(alert);
        }
      });
    }
  }, [pushToast]);

  const onAlertPush = useCallback((/** @type {import('../services/healthService.js').ApmAlertEvent} */ alert) => {
    if (!mounted.current || pausedRef.current || !alert) return;
    const key = alertKey(alert);
    if (seenAlertKeys.current.has(key)) return;
    seenAlertKeys.current.add(key);
    setAlerts((prev) => [alert, ...prev].slice(0, 40));
    pushToast(alert);
  }, [pushToast]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [snap, metricsView, alertsView] = await Promise.all([
        healthService.fetchApmSnapshot(),
        healthService.fetchApmMetrics().catch(() => null),
        healthService.fetchApmAlerts().catch(() => null),
      ]);
      if (!mounted.current) return;
      setSnapshot(snap);
      if (metricsView) setMetrics(metricsView);
      if (alertsView) {
        setAlerts(alertsView.alerts ?? []);
        setActiveAlertCount(Number(alertsView.activeCount) || 0);
      } else if (Array.isArray(snap.alerts)) {
        setAlerts(snap.alerts);
      }
      setError(null);
      setLoading(false);
    } catch (e) {
      if (mounted.current) {
        setError(formatError(e));
        setLoading(false);
      }
    }
  }, []);

  const refreshStack = useCallback(async () => {
    setStackLoading(true);
    try {
      const data = await healthService.fetchApmStack();
      if (mounted.current) {
        setFullStacks(Array.isArray(data.threads) ? data.threads : []);
      }
    } catch (e) {
      if (mounted.current) setError(formatError(e));
    } finally {
      if (mounted.current) setStackLoading(false);
    }
  }, []);

  const togglePause = useCallback(() => {
    setPaused((value) => !value);
  }, []);

  const dismissToast = useCallback(() => setAlertToast(null), []);

  useEffect(() => {
    if (!enabled) {
      setLive(false);
      setMetricsLive(false);
      setAlertsLive(false);
      setMode('idle');
      return undefined;
    }

    setLoading(true);
    let closeApm = /** @type {null | (() => void)} */ (null);
    let closeMetrics = /** @type {null | (() => void)} */ (null);
    let closeAlerts = /** @type {null | (() => void)} */ (null);
    let pollTimer = /** @type {ReturnType<typeof setInterval> | null} */ (null);
    let fallbackTimer = /** @type {ReturnType<typeof setTimeout> | null} */ (null);
    let usingPoll = false;

    const startPolling = () => {
      if (usingPoll) return;
      usingPoll = true;
      setMode('poll');
      setLive(true);
      setMetricsLive(true);
      setAlertsLive(true);
      refresh();
      pollTimer = setInterval(() => {
        if (!pausedRef.current) refresh();
      }, POLL_INTERVAL_MS);
    };

    const startStreams = () => {
      setMode('sse');

      closeApm = healthService.subscribeApmStream({
        onApm: (data) => {
          sseFailCount.current = 0;
          setLive(true);
          applySnapshot(data);
        },
        onAlert: onAlertPush,
        onError: () => {
          sseFailCount.current += 1;
          setLive(false);
          if (closeApm) {
            closeApm();
            closeApm = null;
          }
          if (sseFailCount.current >= 2) {
            startPolling();
          } else {
            fallbackTimer = setTimeout(startStreams, 1500);
          }
        },
      });

      closeMetrics = healthService.subscribeMetricsStream({
        onMetrics: (data) => {
          setMetricsLive(true);
          applyMetrics(data);
        },
        onError: () => setMetricsLive(false),
      });

      closeAlerts = healthService.subscribeAlertsStream({
        onAlerts: (data) => {
          setAlertsLive(true);
          applyAlertsView(data);
        },
        onAlert: onAlertPush,
        onError: () => setAlertsLive(false),
      });
    };

    startStreams();

    return () => {
      if (closeApm) closeApm();
      if (closeMetrics) closeMetrics();
      if (closeAlerts) closeAlerts();
      if (pollTimer) clearInterval(pollTimer);
      if (fallbackTimer) clearTimeout(fallbackTimer);
      setLive(false);
      setMetricsLive(false);
      setAlertsLive(false);
      setMode('idle');
    };
  }, [enabled, applySnapshot, applyMetrics, applyAlertsView, onAlertPush, refresh]);

  const dismissError = useCallback(() => setError(null), []);

  return {
    snapshot,
    metrics,
    alerts,
    activeAlertCount,
    alertToast,
    fullStacks,
    loading,
    stackLoading,
    error,
    live,
    metricsLive,
    alertsLive,
    paused,
    mode,
    metricsTick,
    refresh,
    refreshStack,
    togglePause,
    dismissError,
    dismissToast,
  };
}
