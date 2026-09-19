import { ALL_APPLICATIONS, isAllApplications } from '../constants/healthTargets.js';

function num(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function avg(items, get) {
  if (!items.length) return 0;
  return items.reduce((sum, item) => sum + num(get(item)), 0) / items.length;
}

function sum(items, get) {
  return items.reduce((total, item) => total + num(get(item)), 0);
}

function maxOf(items, get) {
  return items.reduce((highest, item) => Math.max(highest, num(get(item))), 0);
}

function worstStatus(statuses) {
  const rank = { DOWN: 4, OUT_OF_SERVICE: 3, DEGRADED: 2, UNKNOWN: 1, UP: 0 };
  let worst = 'UP';
  let worstRank = -1;
  for (const raw of statuses) {
    const status = String(raw || 'UNKNOWN').toUpperCase();
    const nextRank = rank[status] ?? 1;
    if (nextRank > worstRank) {
      worst = status;
      worstRank = nextRank;
    }
  }
  return worst;
}

function apdexRating(score) {
  if (score >= 0.94) return 'Excellent';
  if (score >= 0.85) return 'Good';
  if (score >= 0.7) return 'Fair';
  return 'Poor';
}

function mergeSamples(lists) {
  const maxLen = Math.max(0, ...lists.map((list) => list.length));
  const out = [];
  for (let i = 0; i < maxLen; i += 1) {
    const points = lists
      .map((list) => list[list.length - maxLen + i])
      .filter(Boolean);
    if (!points.length) continue;
    out.push({
      timestamp: points[points.length - 1].timestamp,
      processCpuLoad: avg(points, (p) => p.processCpuLoad),
      heapUsedPercent: avg(points, (p) => p.heapUsedPercent),
      nonHeapUsedPercent: avg(points, (p) => p.nonHeapUsedPercent),
      requestsPerMinute: sum(points, (p) => p.requestsPerMinute),
      errorRatePercent: avg(points, (p) => p.errorRatePercent),
      avgLatencyMs: avg(points, (p) => p.avgLatencyMs),
      apdex: avg(points, (p) => p.apdex),
      gcCollectionCount: sum(points, (p) => p.gcCollectionCount),
      gcCollectionTimeMs: sum(points, (p) => p.gcCollectionTimeMs),
      gcCollectionCountDelta: sum(points, (p) => p.gcCollectionCountDelta),
      gcCollectionTimeMsDelta: sum(points, (p) => p.gcCollectionTimeMsDelta),
      dbUsagePercent: avg(points, (p) => p.dbUsagePercent),
      externalErrorRatePercent: avg(points, (p) => p.externalErrorRatePercent),
    });
  }
  return out;
}

/**
 * One target per configured application for the selected environment.
 * @param {import('../services/healthService.js').HealthTargetsCatalog | null} catalog
 * @param {string} preferredEnv
 */
export function fleetTargets(catalog, preferredEnv) {
  const apps = catalog?.applications ?? [];
  const targets = [];
  for (const app of apps) {
    const envs = app.environments ?? [];
    const env =
      envs.find((item) => item.id === preferredEnv)
      || envs.find((item) => item.id === catalog?.defaultEnvironment)
      || envs.find((item) => item.id === 'local')
      || envs[0];
    if (!env) continue;
    targets.push({
      application: app.id,
      applicationName: app.name,
      env: env.id,
      envLabel: env.label,
    });
  }
  return targets;
}

/**
 * Combine per-app APM payloads into one snapshot the existing boards can render.
 * @param {Array<{ application?: string, applicationName?: string, env?: string, envLabel?: string, snap?: any, alerts?: any }>} entries
 * @param {string} preferredEnv
 */
export function mergeFleetApm(entries, preferredEnv) {
  const snaps = entries.map((entry) => entry.snap).filter(Boolean);
  const envLabel = entries.find((entry) => entry.env === preferredEnv)?.envLabel
    || entries[0]?.envLabel
    || preferredEnv
    || '';

  const empty = {
    status: 'UNKNOWN',
    timestamp: new Date().toISOString(),
    serviceName: 'All applications',
    applicationId: ALL_APPLICATIONS,
    applicationName: 'All applications',
    environment: preferredEnv,
    environmentLabel: envLabel,
    uptimeMs: 0,
    source: 'fleet',
    targetUrl: '',
    load: { processCpuLoad: 0, systemCpuLoad: 0, systemLoadAverage: 0, availableProcessors: 0 },
    heap: { usedBytes: 0, committedBytes: 0, maxBytes: 0, usedPercent: 0 },
    nonHeap: { usedBytes: 0, committedBytes: 0, maxBytes: 0, usedPercent: 0 },
    gc: { collectionCount: 0, collectionTimeMs: 0, collectionCountDelta: 0, collectionTimeMsDelta: 0, collectors: [] },
    threads: { live: 0, peak: 0, daemon: 0, runnable: 0, blocked: 0, waiting: 0 },
    health: { status: 'UNKNOWN', components: {} },
    probes: { liveness: 'UNKNOWN', readiness: 'UNKNOWN' },
    requests: {
      totalRequests: 0,
      errorRequests: 0,
      requestsDelta: 0,
      errorsDelta: 0,
      requestsPerMinute: 0,
      errorRatePercent: 0,
      avgResponseTimeMs: 0,
    },
    latency: { avgMs: 0, maxMs: 0, apdexThresholdMs: 0 },
    apdex: { score: 0, rating: '—', thresholdMs: 0 },
    transactions: [],
    alerts: [],
    topStacks: [],
    recentSamples: [],
    database: { status: 'UNKNOWN', product: '', pools: [], queries: [], active: 0, idle: 0, pending: 0, max: 0, timeouts: 0 },
    externalServices: [],
    serviceMap: { nodes: [], edges: [] },
  };

  if (!snaps.length) {
    return { snapshot: empty, alerts: [], activeAlerts: [], activeCount: 0 };
  }

  const status = worstStatus(snaps.map((snap) => snap.status));
  const timestamps = snaps.map((snap) => snap.timestamp).filter(Boolean).sort();
  const heapUsed = sum(snaps, (snap) => snap.heap?.usedBytes);
  const heapMax = sum(snaps, (snap) => snap.heap?.maxBytes);
  const nonHeapUsed = sum(snaps, (snap) => snap.nonHeap?.usedBytes);
  const nonHeapMax = sum(snaps, (snap) => snap.nonHeap?.maxBytes);
  const totalReq = sum(snaps, (snap) => snap.requests?.totalRequests);
  const errorReq = sum(snaps, (snap) => snap.requests?.errorRequests);
  const weights = snaps.map((snap) => ({
    w: num(snap.requests?.requestsPerMinute) || num(snap.requests?.totalRequests) || 1,
    avg: num(snap.latency?.avgMs ?? snap.requests?.avgResponseTimeMs),
    apdex: num(snap.apdex?.score),
  }));
  const weightSum = weights.reduce((total, item) => total + item.w, 0) || 1;
  const avgMs = weights.reduce((total, item) => total + item.avg * item.w, 0) / weightSum;
  const apdexScore = weights.reduce((total, item) => total + item.apdex * item.w, 0) / weightSum;

  const components = {};
  for (const entry of entries) {
    const comps = entry.snap?.health?.components ?? {};
    Object.entries(comps).forEach(([key, value]) => {
      components[`${entry.applicationName || entry.application}:${key}`] = value;
    });
  }

  const transactions = entries.flatMap((entry) => (
    (entry.snap?.transactions ?? []).map((tx) => ({
      ...tx,
      uri: `[${entry.applicationName || entry.application}] ${tx.uri}`,
    }))
  ));

  const alerts = entries.flatMap((entry) => {
    const list = entry.alerts?.alerts ?? entry.snap?.alerts ?? [];
    return list.map((alert) => ({
      ...alert,
      message: `[${entry.applicationName || entry.application}] ${alert.message || ''}`,
    }));
  }).sort((a, b) => String(b.timestamp || '').localeCompare(String(a.timestamp || ''))).slice(0, 40);

  const activeAlerts = entries.flatMap((entry) => (entry.alerts?.active ?? []).map((alert) => ({
    ...alert,
    message: `[${entry.applicationName || entry.application}] ${alert.message || ''}`,
  })));
  const activeCount = entries.reduce((total, entry) => total + (Number(entry.alerts?.activeCount) || 0), 0);

  const snapshot = {
    ...empty,
    status,
    timestamp: timestamps.at(-1) || empty.timestamp,
    environmentLabel: envLabel,
    uptimeMs: maxOf(snaps, (snap) => snap.uptimeMs),
    load: {
      processCpuLoad: avg(snaps, (snap) => snap.load?.processCpuLoad),
      systemCpuLoad: avg(snaps, (snap) => snap.load?.systemCpuLoad),
      systemLoadAverage: avg(snaps, (snap) => snap.load?.systemLoadAverage),
      availableProcessors: sum(snaps, (snap) => snap.load?.availableProcessors),
    },
    heap: {
      usedBytes: heapUsed,
      committedBytes: sum(snaps, (snap) => snap.heap?.committedBytes),
      maxBytes: heapMax,
      usedPercent: heapMax > 0 ? (heapUsed * 100) / heapMax : avg(snaps, (snap) => snap.heap?.usedPercent),
    },
    nonHeap: {
      usedBytes: nonHeapUsed,
      committedBytes: sum(snaps, (snap) => snap.nonHeap?.committedBytes),
      maxBytes: nonHeapMax,
      usedPercent: nonHeapMax > 0 ? (nonHeapUsed * 100) / nonHeapMax : avg(snaps, (snap) => snap.nonHeap?.usedPercent),
    },
    gc: {
      collectionCount: sum(snaps, (snap) => snap.gc?.collectionCount),
      collectionTimeMs: sum(snaps, (snap) => snap.gc?.collectionTimeMs),
      collectionCountDelta: sum(snaps, (snap) => snap.gc?.collectionCountDelta),
      collectionTimeMsDelta: sum(snaps, (snap) => snap.gc?.collectionTimeMsDelta),
      collectors: entries.flatMap((entry) => (
        (entry.snap?.gc?.collectors ?? []).map((collector) => ({
          ...collector,
          name: `${entry.applicationName || entry.application} · ${collector.name}`,
        }))
      )),
    },
    threads: {
      live: sum(snaps, (snap) => snap.threads?.live),
      peak: sum(snaps, (snap) => snap.threads?.peak),
      daemon: sum(snaps, (snap) => snap.threads?.daemon),
      runnable: sum(snaps, (snap) => snap.threads?.runnable),
      blocked: sum(snaps, (snap) => snap.threads?.blocked),
      waiting: sum(snaps, (snap) => snap.threads?.waiting),
    },
    health: { status, components },
    probes: {
      liveness: worstStatus(snaps.map((snap) => snap.probes?.liveness)),
      readiness: worstStatus(snaps.map((snap) => snap.probes?.readiness)),
    },
    requests: {
      totalRequests: totalReq,
      errorRequests: errorReq,
      requestsDelta: sum(snaps, (snap) => snap.requests?.requestsDelta),
      errorsDelta: sum(snaps, (snap) => snap.requests?.errorsDelta),
      requestsPerMinute: sum(snaps, (snap) => snap.requests?.requestsPerMinute),
      errorRatePercent: totalReq > 0 ? (errorReq * 100) / totalReq : avg(snaps, (snap) => snap.requests?.errorRatePercent),
      avgResponseTimeMs: avgMs,
    },
    latency: {
      avgMs,
      maxMs: maxOf(snaps, (snap) => snap.latency?.maxMs),
      apdexThresholdMs: avg(snaps, (snap) => snap.latency?.apdexThresholdMs),
    },
    apdex: {
      score: apdexScore,
      rating: apdexRating(apdexScore),
      thresholdMs: avg(snaps, (snap) => snap.apdex?.thresholdMs),
    },
    transactions,
    alerts,
    topStacks: entries.flatMap((entry) => (
      (entry.snap?.topStacks ?? []).map((thread) => ({
        ...thread,
        name: `${entry.applicationName || entry.application} · ${thread.name}`,
      }))
    )).slice(0, 40),
    recentSamples: mergeSamples(snaps.map((snap) => snap.recentSamples ?? [])),
    database: {
      status: worstStatus(snaps.map((snap) => snap.database?.status)),
      product: [...new Set(snaps.map((snap) => snap.database?.product).filter(Boolean))].join(', '),
      pools: entries.flatMap((entry) => (
        (entry.snap?.database?.pools ?? []).map((pool) => ({
          ...pool,
          name: `${entry.applicationName || entry.application} · ${pool.name}`,
        }))
      )),
      queries: entries.flatMap((entry) => (
        (entry.snap?.database?.queries ?? []).map((query) => ({
          ...query,
          sql: `[${entry.applicationName || entry.application}] ${query.sql || ''}`,
        }))
      )),
      active: sum(snaps, (snap) => snap.database?.active),
      idle: sum(snaps, (snap) => snap.database?.idle),
      pending: sum(snaps, (snap) => snap.database?.pending),
      max: sum(snaps, (snap) => snap.database?.max),
      timeouts: sum(snaps, (snap) => snap.database?.timeouts),
    },
    externalServices: entries.flatMap((entry) => (
      (entry.snap?.externalServices ?? []).map((service) => ({
        ...service,
        name: `${entry.applicationName || entry.application} · ${service.name}`,
      }))
    )),
    serviceMap: {
      nodes: entries.flatMap((entry) => (
        (entry.snap?.serviceMap?.nodes ?? []).map((node) => ({
          ...node,
          id: `${entry.application}:${node.id}`,
          name: `${entry.applicationName || entry.application} · ${node.name}`,
        }))
      )),
      edges: entries.flatMap((entry) => (
        (entry.snap?.serviceMap?.edges ?? []).map((edge) => ({
          ...edge,
          from: `${entry.application}:${edge.from}`,
          to: `${entry.application}:${edge.to}`,
        }))
      )),
    },
  };

  return { snapshot, alerts, activeAlerts, activeCount };
}

/**
 * Fleet view for one service, or the merged all-services snapshot.
 * @param {Array<{ application?: string, env?: string, snap?: any, alerts?: any }>} entries
 * @param {string} serviceId
 * @param {string} preferredEnv
 */
export function selectFleetView(entries, serviceId, preferredEnv) {
  if (!isAllApplications(serviceId)) {
    const entry = (entries ?? []).find((item) => item.application === serviceId);
    if (entry) {
      if (entry.snap) {
        const alerts = entry.alerts?.alerts ?? entry.snap.alerts ?? [];
        const activeAlerts = entry.alerts?.active ?? [];
        const activeCount = Number(entry.alerts?.activeCount);
        return {
          snapshot: entry.snap,
          alerts,
          activeAlerts,
          activeCount: Number.isFinite(activeCount) ? activeCount : 0,
          application: entry.application,
          environment: entry.env || preferredEnv,
        };
      }
      return {
        ...mergeFleetApm([], preferredEnv),
        application: entry.application,
        environment: entry.env || preferredEnv,
      };
    }
  }
  const merged = mergeFleetApm(entries ?? [], preferredEnv);
  return {
    ...merged,
    application: ALL_APPLICATIONS,
    environment: preferredEnv,
  };
}
