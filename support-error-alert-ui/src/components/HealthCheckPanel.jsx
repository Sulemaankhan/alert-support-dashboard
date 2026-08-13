import { useEffect, useId, useMemo, useState } from 'react';
import { SECTION } from '../constants/branding.js';
import { NAV_SECTION } from '../constants/nav.js';
import './AlertSourcePanel.css';
import './HealthCheckPanel.css';

const TAB_GROUPS = [
  {
    label: 'Signals',
    items: [
      { id: 'overview', label: 'Overview' },
      { id: 'metrics', label: 'Metrics' },
      { id: 'transactions', label: 'Transactions' },
      { id: 'latency', label: 'Latency' },
      { id: 'errors', label: 'Errors' },
      { id: 'alerts', label: 'Alerts' },
    ],
  },
  {
    label: 'Runtime',
    items: [
      { id: 'health', label: 'Health' },
      { id: 'probes', label: 'Probes' },
      { id: 'heap', label: 'Heap' },
      { id: 'load', label: 'Load' },
    ],
  },
  {
    label: 'Diagnostics',
    items: [{ id: 'stack', label: 'Stack' }],
  },
];

function formatBytes(bytes) {
  if (bytes == null || bytes < 0) return '—';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i += 1;
  }
  return `${value.toFixed(value >= 10 || i === 0 ? 0 : 1)} ${units[i]}`;
}

function formatUptime(ms) {
  if (ms == null || ms < 0) return '—';
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) return `${h}h ${m}m ${s}s`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

function formatCpu(value) {
  if (value == null || value < 0) return '—';
  return `${Number(value).toFixed(1)}%`;
}

function formatNum(value, digits = 1) {
  if (value == null || Number.isNaN(value)) return '—';
  return Number(value).toFixed(digits);
}

function formatTime(iso) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleTimeString();
  } catch {
    return iso;
  }
}

function statusClass(status) {
  const s = String(status || '').toUpperCase();
  if (s === 'UP') return 'health-status--up';
  if (s === 'DOWN') return 'health-status--down';
  if (s === 'DEGRADED' || s === 'OUT_OF_SERVICE') return 'health-status--degraded';
  return 'health-status--unknown';
}

function shellTone(status) {
  const s = String(status || '').toUpperCase();
  if (s === 'UP') return 'up';
  if (s === 'DOWN') return 'down';
  if (s === 'DEGRADED' || s === 'OUT_OF_SERVICE') return 'degraded';
  return 'unknown';
}

function severityClass(severity) {
  const s = String(severity || '').toLowerCase();
  if (s === 'critical') return 'health-alert--critical';
  if (s === 'warning') return 'health-alert--warning';
  return 'health-alert--info';
}

function apdexClass(score) {
  if (score == null) return '';
  if (score >= 0.94) return 'health-apdex--excellent';
  if (score >= 0.85) return 'health-apdex--good';
  if (score >= 0.7) return 'health-apdex--fair';
  return 'health-apdex--poor';
}

function ApdexRing({ score = 0, rating = '—' }) {
  const pct = Math.max(0, Math.min(1, Number(score) || 0));
  const angle = pct * 360;
  return (
    <div className={`health-apdex-ring ${apdexClass(score)}`} style={{ '--apdex-angle': `${angle}deg` }}>
      <div className="health-apdex-ring__inner">
        <span className="health-apdex-ring__score mono">{formatNum(score, 2)}</span>
        <span className="health-apdex-ring__label">{rating}</span>
      </div>
    </div>
  );
}

/** @param {{ samples: any[], getValue: (s: any) => number, color: string, floorMax?: number, height?: number }} props */
function Sparkline({ samples, getValue, color, floorMax = 1, height = 44 }) {
  const gradId = useId();
  if (!samples?.length) {
    return <div className="health-sparkline health-sparkline--empty" style={{ height }} aria-hidden="true" />;
  }
  const values = samples.map(getValue).map((v) => (v == null || v < 0 ? 0 : v));
  const max = Math.max(floorMax, ...values);
  const w = 240;
  const h = height;
  const coords = values.map((v, i) => {
    const x = values.length === 1 ? 0 : (i / (values.length - 1)) * w;
    const y = h - (v / max) * (h - 6) - 3;
    return { x, y };
  });
  const line = coords.map((p) => `${p.x},${p.y}`).join(' ');
  const area = `0,${h} ${line} ${w},${h}`;

  return (
    <svg
      className="health-sparkline"
      viewBox={`0 0 ${w} ${h}`}
      preserveAspectRatio="none"
      style={{ height }}
      aria-hidden="true"
    >
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.35" />
          <stop offset="100%" stopColor={color} stopOpacity="0.02" />
        </linearGradient>
      </defs>
      <polygon fill={`url(#${gradId})`} points={area} />
      <polyline fill="none" stroke={color} strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round" points={line} />
      {coords.length ? (
        <circle cx={coords[coords.length - 1].x} cy={coords[coords.length - 1].y} r="3" fill={color} />
      ) : null}
    </svg>
  );
}

function MetricCard({ title, value, sub, children, tone, accent = 'blue', delay = 0 }) {
  return (
    <article
      className={
        tone
          ? `health-metric health-metric--${accent} health-metric--${tone}`
          : `health-metric health-metric--${accent}`
      }
      style={{ '--stagger': `${delay}ms` }}
    >
      <header className="health-metric__head">
        <h3 className="health-metric__title">{title}</h3>
        <span className="health-metric__value mono">{value}</span>
      </header>
      {children}
      {sub ? <p className="health-metric__sub">{sub}</p> : null}
    </article>
  );
}

function ProbeBadge({ label, status }) {
  return (
    <div className={`health-probe health-probe--${shellTone(status)}`}>
      <span className="health-probe__pulse" aria-hidden="true" />
      <span className="health-probe__label">{label}</span>
      <span className={`health-status ${statusClass(status)}`}>{status || 'UNKNOWN'}</span>
    </div>
  );
}

/**
 * @param {Object} props
 * @param {import('../services/healthService.js').ApmSnapshot | null} props.snapshot
 * @param {import('../services/healthService.js').ApmAlertEvent[]} [props.alerts]
 * @param {import('../services/healthService.js').ApmAlertEvent | null} [props.alertToast]
 * @param {number} [props.activeAlertCount]
 * @param {import('../services/healthService.js').ApmThreadStack[]} [props.fullStacks]
 * @param {boolean} props.loading
 * @param {boolean} [props.stackLoading]
 * @param {boolean} props.live
 * @param {boolean} [props.metricsLive]
 * @param {boolean} [props.alertsLive]
 * @param {number} [props.metricsTick]
 * @param {boolean} [props.paused]
 * @param {'idle' | 'sse' | 'poll'} props.mode
 * @param {function(): void | Promise<void>} props.onRefresh
 * @param {function(): void | Promise<void>} [props.onRefreshStack]
 * @param {function(): void} [props.onTogglePause]
 * @param {string | null} [props.error]
 * @param {function(): void} [props.onDismissError]
 * @param {function(): void} [props.onDismissToast]
 */
export function HealthCheckPanel({
  snapshot,
  alerts: alertsProp,
  alertToast = null,
  activeAlertCount = 0,
  fullStacks = [],
  loading,
  stackLoading = false,
  live,
  metricsLive = false,
  alertsLive = false,
  metricsTick = 0,
  paused = false,
  mode,
  onRefresh,
  onRefreshStack,
  onTogglePause,
  error = null,
  onDismissError,
  onDismissToast,
}) {
  const [tab, setTab] = useState('overview');
  const [txSort, setTxSort] = useState('count');
  const samples = snapshot?.recentSamples ?? [];
  const status = snapshot?.status ?? (loading ? '…' : 'UNKNOWN');
  const stacks = fullStacks.length ? fullStacks : snapshot?.topStacks ?? [];
  const transactions = snapshot?.transactions ?? [];
  const alerts = alertsProp ?? snapshot?.alerts ?? [];
  const tone = shellTone(status);

  const sortedTransactions = useMemo(() => {
    const list = transactions.filter((tx) => {
      const uri = String(tx?.uri ?? '');
      const lower = uri.toLowerCase();
      if (!uri || uri === 'ROOT' || uri === '/**') return false;
      if (lower.startsWith('/actuator') || lower.includes('{requiredmetricname}')) return false;
      if (lower.startsWith('/api/health')) return false;
      return true;
    });
    list.sort((a, b) => {
      if (txSort === 'errors') return (b.errorRatePercent ?? 0) - (a.errorRatePercent ?? 0);
      if (txSort === 'latency') return (b.avgMs ?? 0) - (a.avgMs ?? 0);
      if (txSort === 'apdex') return (a.apdex ?? 1) - (b.apdex ?? 1);
      return (b.count ?? 0) - (a.count ?? 0);
    });
    return list;
  }, [transactions, txSort]);

  const maxTxCount = Math.max(1, ...sortedTransactions.map((t) => t.count || 0));

  useEffect(() => {
    if (tab === 'stack' && onRefreshStack) {
      onRefreshStack();
    }
  }, [tab, onRefreshStack]);

  return (
    <section
      className={`alert-source-panel alert-source-panel--health health-shell health-shell--${tone}`}
      id={NAV_SECTION.HEALTH_CHECK}
      aria-labelledby="health-check-heading"
    >
      <div className="health-hero">
        <div className="health-hero__copy">
          <p className="health-hero__eyebrow">APM · realtime telemetry</p>
          <h2 id="health-check-heading" className="health-hero__title">
            {SECTION.HEALTH.title}
          </h2>
          <p className="health-hero__service">
            <span className="health-hero__service-name">{snapshot?.serviceName ?? '—'}</span>
            {snapshot?.targetUrl && snapshot.targetUrl !== 'local' ? (
              <span className="health-hero__target mono">{snapshot.targetUrl}</span>
            ) : (
              <span className="health-hero__target">Local JVM</span>
            )}
          </p>
          <p className="health-hero__hint">{SECTION.HEALTH.loadHint}</p>
        </div>

        <div className="health-hero__gauge">
          <ApdexRing score={snapshot?.apdex?.score} rating={snapshot?.apdex?.rating ?? '—'} />
        </div>

        <div className="health-hero__side">
          <div className={`health-status-orb health-status-orb--${tone}`}>
            <span className={`health-status ${statusClass(status)}`}>{status}</span>
            <span className="health-status-orb__meta">
              uptime {formatUptime(snapshot?.uptimeMs)}
              <br />
              updated {formatTime(snapshot?.timestamp)}
            </span>
          </div>
          <div className="health-check-panel__actions">
            <span
              className={
                paused
                  ? 'health-live health-live--paused'
                  : live || metricsLive
                    ? 'health-live health-live--on'
                    : 'health-live health-live--off'
              }
              title={mode === 'sse' ? 'Server-sent events' : mode === 'poll' ? 'Polling fallback' : 'Idle'}
            >
              <span className="health-live__dot" aria-hidden="true" />
              {paused ? 'Paused' : live || metricsLive ? (mode === 'poll' ? 'Live (poll)' : 'Live') : 'Connecting…'}
            </span>
            <span className={metricsLive && !paused ? 'health-live health-live--on' : 'health-live'} title="Metrics stream">
              <span className="health-live__dot" aria-hidden="true" />
              Metrics
            </span>
            <span className={alertsLive && !paused ? 'health-live health-live--on' : 'health-live'} title="Alerts stream">
              <span className="health-live__dot" aria-hidden="true" />
              Alerts{activeAlertCount ? ` · ${activeAlertCount}` : ''}
            </span>
            {onTogglePause ? (
              <button type="button" className="btn health-btn" onClick={onTogglePause}>
                {paused ? 'Resume' : 'Pause'}
              </button>
            ) : null}
            <button type="button" className="btn health-btn" onClick={onRefresh} disabled={loading}>
              Refresh
            </button>
          </div>
        </div>
      </div>

      {alertToast ? (
        <div className={`health-toast ${severityClass(alertToast.severity)}`} role="status">
          <div>
            <strong className="health-toast__label">Realtime alert</strong>
            <span className="health-toast__code mono">{alertToast.code}</span>
            <p className="health-toast__msg">{alertToast.message}</p>
          </div>
          <div className="health-toast__actions">
            <button type="button" className="btn health-btn" onClick={() => setTab('alerts')}>
              View
            </button>
            {onDismissToast ? (
              <button type="button" className="btn health-btn" onClick={onDismissToast}>
                Dismiss
              </button>
            ) : null}
          </div>
        </div>
      ) : null}

      {error ? (
        <div className="health-check-panel__error" role="alert">
          <span>{error}</span>
          {onDismissError ? (
            <button type="button" className="btn" onClick={onDismissError}>
              Dismiss
            </button>
          ) : null}
        </div>
      ) : null}

      <div className="health-signal-strip" aria-label="Key signals">
        <div className="health-signal">
          <span className="health-signal__label">Throughput</span>
          <span className="health-signal__value mono">
            {formatNum(snapshot?.requests?.requestsPerMinute, 1)}
            <small>rpm</small>
          </span>
        </div>
        <div className="health-signal">
          <span className="health-signal__label">Errors</span>
          <span className="health-signal__value mono">
            {formatNum(snapshot?.requests?.errorRatePercent, 1)}
            <small>%</small>
          </span>
        </div>
        <div className="health-signal">
          <span className="health-signal__label">Latency</span>
          <span className="health-signal__value mono">
            {formatNum(snapshot?.latency?.avgMs ?? snapshot?.requests?.avgResponseTimeMs, 0)}
            <small>ms</small>
          </span>
        </div>
        <div className="health-signal">
          <span className="health-signal__label">Heap</span>
          <span className="health-signal__value mono">
            {formatNum(snapshot?.heap?.usedPercent, 0)}
            <small>%</small>
          </span>
        </div>
        <div className="health-signal">
          <span className="health-signal__label">CPU</span>
          <span className="health-signal__value mono">{formatCpu(snapshot?.load?.processCpuLoad)}</span>
        </div>
      </div>

      <nav className="health-apm-nav" aria-label="APM views">
        {TAB_GROUPS.map((group) => (
          <div key={group.label} className="health-apm-nav__group">
            <span className="health-apm-nav__label">{group.label}</span>
            <div className="health-apm-tabs" role="tablist">
              {group.items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  role="tab"
                  aria-selected={tab === item.id}
                  className={tab === item.id ? 'health-apm-tab health-apm-tab--active' : 'health-apm-tab'}
                  onClick={() => setTab(item.id)}
                >
                  {item.label}
                  {item.id === 'alerts' && (activeAlertCount || alerts.length) ? (
                    <span className="health-apm-tab__count">{activeAlertCount || alerts.length}</span>
                  ) : null}
                  {item.id === 'metrics' && metricsLive ? (
                    <span className="health-apm-tab__live">live</span>
                  ) : null}
                </button>
              ))}
            </div>
          </div>
        ))}
      </nav>

      <div key={tab} className="health-tab-stage">
        {tab === 'metrics' ? (
          <div key={metricsTick} className="health-metrics-live">
            <div className="health-metrics-live__head">
              <div>
                <h3 className="health-stack__title">Realtime metrics</h3>
                <p className="health-metric__sub">
                  Streaming from `/api/health/apm/metrics/stream` · tick #{metricsTick} ·{' '}
                  {formatTime(snapshot?.timestamp)}
                </p>
              </div>
              <span className={metricsLive && !paused ? 'health-live health-live--on' : 'health-live health-live--off'}>
                <span className="health-live__dot" aria-hidden="true" />
                {metricsLive && !paused ? 'Streaming 1s' : 'Waiting…'}
              </span>
            </div>
            <div className="health-apm-grid health-apm-grid--hero">
              <MetricCard
                title="Throughput"
                value={`${formatNum(snapshot?.requests?.requestsPerMinute, 1)} rpm`}
                sub={`Δ ${snapshot?.requests?.requestsDelta ?? 0} · total ${snapshot?.requests?.totalRequests ?? 0}`}
                accent="blue"
              >
                <Sparkline samples={samples} getValue={(s) => s.requestsPerMinute} color="#2563eb" floorMax={10} height={64} />
              </MetricCard>
              <MetricCard
                title="Error rate"
                value={`${formatNum(snapshot?.requests?.errorRatePercent, 1)}%`}
                sub={`5xx ${snapshot?.requests?.errorRequests ?? 0}`}
                accent="rose"
                tone={snapshot?.requests?.errorRatePercent >= 5 ? 'danger' : undefined}
              >
                <Sparkline samples={samples} getValue={(s) => s.errorRatePercent} color="#c62828" floorMax={5} height={64} />
              </MetricCard>
              <MetricCard
                title="Latency"
                value={`${formatNum(snapshot?.latency?.avgMs ?? snapshot?.requests?.avgResponseTimeMs, 1)} ms`}
                sub={`Max ${formatNum(snapshot?.latency?.maxMs, 1)} ms`}
                accent="amber"
              >
                <Sparkline samples={samples} getValue={(s) => s.avgLatencyMs ?? 0} color="#b45309" floorMax={50} height={64} />
              </MetricCard>
              <MetricCard
                title="Apdex"
                value={formatNum(snapshot?.apdex?.score, 2)}
                sub={snapshot?.apdex?.rating ?? '—'}
                accent="teal"
              >
                <Sparkline samples={samples} getValue={(s) => (s.apdex ?? 0) * 100} color="#0f766e" floorMax={100} height={64} />
              </MetricCard>
            </div>
            <div className="health-apm-grid">
              <MetricCard
                title="Heap"
                value={`${formatNum(snapshot?.heap?.usedPercent, 1)}%`}
                sub={`${formatBytes(snapshot?.heap?.usedBytes)} used`}
                accent="teal"
              >
                <Sparkline samples={samples} getValue={(s) => s.heapUsedPercent} color="#0f766e" height={48} />
              </MetricCard>
              <MetricCard
                title="CPU"
                value={formatCpu(snapshot?.load?.processCpuLoad)}
                sub={`System ${formatCpu(snapshot?.load?.systemCpuLoad)}`}
                accent="blue"
              >
                <Sparkline samples={samples} getValue={(s) => s.processCpuLoad} color="#2563eb" floorMax={100} height={48} />
              </MetricCard>
              <MetricCard
                title="Threads"
                value={snapshot?.threads?.live ?? '—'}
                sub={`Runnable ${snapshot?.threads?.runnable ?? '—'} · Blocked ${snapshot?.threads?.blocked ?? '—'}`}
                accent="slate"
              />
              <MetricCard
                title="Active alerts"
                value={String(activeAlertCount)}
                sub={alertsLive ? 'Alert stream connected' : 'Alert stream idle'}
                accent="rose"
                tone={activeAlertCount > 0 ? 'warn' : undefined}
              />
            </div>
          </div>
        ) : null}

        {tab === 'overview' ? (
          <>
            <div className="health-apm-grid health-apm-grid--hero">
              <MetricCard
                title="Throughput"
                value={`${formatNum(snapshot?.requests?.requestsPerMinute, 1)} rpm`}
                sub={`Total ${snapshot?.requests?.totalRequests ?? 0} · Δ ${snapshot?.requests?.requestsDelta ?? 0}`}
                accent="blue"
                delay={0}
              >
                <Sparkline samples={samples} getValue={(s) => s.requestsPerMinute} color="#2563eb" floorMax={10} height={56} />
              </MetricCard>
              <MetricCard
                title="Error rate"
                value={`${formatNum(snapshot?.requests?.errorRatePercent, 1)}%`}
                sub={`5xx ${snapshot?.requests?.errorRequests ?? 0} · Δ ${snapshot?.requests?.errorsDelta ?? 0}`}
                accent="rose"
                tone={snapshot?.requests?.errorRatePercent >= 5 ? 'danger' : undefined}
                delay={60}
              >
                <Sparkline samples={samples} getValue={(s) => s.errorRatePercent} color="#c62828" floorMax={5} height={56} />
              </MetricCard>
              <MetricCard
                title="Latency"
                value={`${formatNum(snapshot?.latency?.avgMs ?? snapshot?.requests?.avgResponseTimeMs, 1)} ms`}
                sub={`Max ${formatNum(snapshot?.latency?.maxMs, 1)} ms · T=${formatNum(snapshot?.latency?.apdexThresholdMs ?? snapshot?.apdex?.thresholdMs, 0)} ms`}
                accent="amber"
                delay={120}
              >
                <Sparkline samples={samples} getValue={(s) => s.avgLatencyMs ?? 0} color="#b45309" floorMax={50} height={56} />
              </MetricCard>
              <MetricCard
                title="Apdex trend"
                value={formatNum(snapshot?.apdex?.score, 2)}
                sub={snapshot?.apdex?.rating ?? '—'}
                accent="teal"
                tone={snapshot?.apdex?.score < 0.7 ? 'warn' : undefined}
                delay={180}
              >
                <Sparkline samples={samples} getValue={(s) => (s.apdex ?? 0) * 100} color="#0f766e" floorMax={100} height={56} />
              </MetricCard>
            </div>
            <div className="health-apm-grid">
              <MetricCard
                title="Heap"
                value={snapshot ? `${formatNum(snapshot.heap.usedPercent)}%` : '—'}
                sub={`${formatBytes(snapshot?.heap?.usedBytes)} / ${formatBytes(snapshot?.heap?.maxBytes)}`}
                accent="teal"
                delay={40}
              >
                <div className="health-bar" aria-hidden="true">
                  <div
                    className="health-bar__fill"
                    style={{ width: `${Math.min(100, snapshot?.heap?.usedPercent ?? 0)}%` }}
                  />
                </div>
                <Sparkline samples={samples} getValue={(s) => s.heapUsedPercent} color="#0f766e" />
              </MetricCard>
              <MetricCard
                title="Process load"
                value={formatCpu(snapshot?.load?.processCpuLoad)}
                sub={`System ${formatCpu(snapshot?.load?.systemCpuLoad)} · ${snapshot?.load?.availableProcessors ?? '—'} CPUs`}
                accent="blue"
                delay={100}
              >
                <Sparkline samples={samples} getValue={(s) => s.processCpuLoad} color="#2563eb" floorMax={100} />
              </MetricCard>
              <MetricCard
                title="Health / probes"
                value={snapshot?.health?.status ?? '—'}
                sub={`Liveness ${snapshot?.probes?.liveness ?? '—'} · Readiness ${snapshot?.probes?.readiness ?? '—'}`}
                accent="slate"
                delay={160}
              />
              <MetricCard
                title="Top transaction"
                value={sortedTransactions[0]?.uri ?? '—'}
                sub={
                  sortedTransactions[0]
                    ? `${sortedTransactions[0].count} calls · ${formatNum(sortedTransactions[0].avgMs, 1)} ms · err ${formatNum(sortedTransactions[0].errorRatePercent, 1)}%`
                    : 'No URI breakdown yet — generate traffic on the target.'
                }
                accent="slate"
                delay={220}
              />
            </div>
          </>
        ) : null}

        {tab === 'transactions' ? (
          <div className="health-tx">
            <div className="health-tx__toolbar">
              <div>
                <h3 className="health-stack__title">Web transactions</h3>
                <p className="health-metric__sub">Ranked by live Actuator `http.server.requests` URI tags.</p>
              </div>
              <label className="health-tx__sort">
                Sort by{' '}
                <select value={txSort} onChange={(e) => setTxSort(e.target.value)}>
                  <option value="count">Throughput</option>
                  <option value="latency">Avg latency</option>
                  <option value="errors">Error rate</option>
                  <option value="apdex">Apdex (worst)</option>
                </select>
              </label>
            </div>
            {!sortedTransactions.length ? (
              <div className="health-empty">
                <p>No transactions yet.</p>
                <span>Hit endpoints on the monitored service to populate URI breakdown.</span>
              </div>
            ) : (
              <ul className="health-tx__cards">
                {sortedTransactions.map((tx, index) => (
                  <li key={`${tx.method}-${tx.uri}`} className="health-tx__card" style={{ '--stagger': `${index * 40}ms` }}>
                    <div className="health-tx__card-top">
                      <span className="health-tx__method">{tx.method || '*'}</span>
                      <span className="health-tx__uri mono">{tx.uri}</span>
                      <span className={`health-tx__apdex mono ${apdexClass(tx.apdex)}`}>{formatNum(tx.apdex, 2)}</span>
                    </div>
                    <div className="health-tx__bar" aria-hidden="true">
                      <div className="health-tx__bar-fill" style={{ width: `${(tx.count / maxTxCount) * 100}%` }} />
                    </div>
                    <div className="health-tx__stats">
                      <span>
                        <strong className="mono">{tx.count}</strong> calls
                      </span>
                      <span>
                        <strong className="mono">{formatNum(tx.avgMs, 1)}</strong> ms avg
                      </span>
                      <span>
                        <strong className="mono">{formatNum(tx.maxMs, 1)}</strong> ms max
                      </span>
                      <span className={tx.errorCount > 0 ? 'health-tx__err' : ''}>
                        <strong className="mono">{tx.errorCount}</strong> err · {formatNum(tx.errorRatePercent, 1)}%
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ) : null}

        {tab === 'latency' ? (
          <div className="health-apm-grid">
            <MetricCard
              title="Average response time"
              value={`${formatNum(snapshot?.latency?.avgMs, 1)} ms`}
              sub={`Apdex threshold T=${formatNum(snapshot?.latency?.apdexThresholdMs, 0)} ms`}
              accent="amber"
            >
              <Sparkline samples={samples} getValue={(s) => s.avgLatencyMs ?? 0} color="#b45309" floorMax={50} height={64} />
            </MetricCard>
            <MetricCard
              title="Max response time"
              value={`${formatNum(snapshot?.latency?.maxMs, 1)} ms`}
              sub="Micrometer sliding-window MAX"
              accent="rose"
            />
            <MetricCard
              title="Apdex score"
              value={formatNum(snapshot?.apdex?.score, 2)}
              sub={`${snapshot?.apdex?.rating ?? '—'} · satisfied ≤ T, tolerating ≤ 4T`}
              accent="teal"
            >
              <Sparkline samples={samples} getValue={(s) => (s.apdex ?? 0) * 100} color="#0f766e" floorMax={100} height={64} />
            </MetricCard>
          </div>
        ) : null}

        {tab === 'errors' ? (
          <div className="health-apm-grid">
            <MetricCard
              title="Error rate"
              value={`${formatNum(snapshot?.requests?.errorRatePercent, 1)}%`}
              sub={`Server errors ${snapshot?.requests?.errorRequests ?? 0} of ${snapshot?.requests?.totalRequests ?? 0}`}
              accent="rose"
              tone={snapshot?.requests?.errorRatePercent >= 5 ? 'danger' : undefined}
            >
              <Sparkline samples={samples} getValue={(s) => s.errorRatePercent} color="#c62828" floorMax={5} height={64} />
            </MetricCard>
            <MetricCard
              title="Erroring transactions"
              value={String(sortedTransactions.filter((t) => t.errorCount > 0).length)}
              sub="URIs with SERVER_ERROR outcome"
              accent="amber"
            />
            <div className="health-metric health-metric--wide health-metric--slate">
              <h3 className="health-metric__title">Errors by transaction</h3>
              {!sortedTransactions.filter((t) => t.errorCount > 0).length ? (
                <p className="health-metric__sub">No server errors in the current metrics window.</p>
              ) : (
                <ul className="health-error-list">
                  {sortedTransactions
                    .filter((t) => t.errorCount > 0)
                    .slice(0, 8)
                    .map((t) => (
                      <li key={t.uri}>
                        <span className="mono">{t.uri}</span>
                        <span>
                          {t.errorCount} errors · {formatNum(t.errorRatePercent, 1)}%
                        </span>
                      </li>
                    ))}
                </ul>
              )}
            </div>
          </div>
        ) : null}

        {tab === 'alerts' ? (
          <div className="health-alerts">
            <div className="health-metrics-live__head">
              <div>
                <h3 className="health-stack__title">Realtime alerts</h3>
                <p className="health-metric__sub">
                  Streaming from `/api/health/apm/alerts/stream` · active conditions {activeAlertCount}
                </p>
              </div>
              <span className={alertsLive && !paused ? 'health-live health-live--on' : 'health-live health-live--off'}>
                <span className="health-live__dot" aria-hidden="true" />
                {alertsLive && !paused ? 'Live feed' : 'Waiting…'}
              </span>
            </div>
            {!alerts.length ? (
              <div className="health-empty">
                <p>All quiet</p>
                <span>Alerts appear instantly on status, heap, error-rate, or Apdex crossings.</span>
              </div>
            ) : (
              <ul className="health-alerts__list">
                {alerts.map((alert, index) => (
                  <li
                    key={`${alert.code}-${alert.timestamp}-${index}`}
                    className={`health-alert ${severityClass(alert.severity)} ${index === 0 ? 'health-alert--fresh' : ''}`}
                    style={{ '--stagger': `${index * 45}ms` }}
                  >
                    <div className="health-alert__top">
                      <span className="health-alert__severity">{alert.severity}</span>
                      <span className="health-alert__code mono">{alert.code}</span>
                      {index === 0 ? <span className="health-alert__fresh-tag">latest</span> : null}
                      <span className="health-alert__time">{formatTime(alert.timestamp)}</span>
                    </div>
                    <p className="health-alert__msg">{alert.message}</p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ) : null}

        {(tab === 'health' || tab === 'probes') && (
          <div className="health-apm-grid">
            {tab === 'health' && (
              <MetricCard
                title="Health"
                value={snapshot?.health?.status ?? '—'}
                sub={
                  snapshot?.health?.components
                    ? Object.entries(snapshot.health.components)
                        .map(([k, v]) => `${k}: ${v}`)
                        .join(' · ') || 'Actuator health components'
                    : 'Actuator health'
                }
                accent="teal"
              >
                <span className={`health-status ${statusClass(snapshot?.health?.status)}`}>
                  {snapshot?.health?.status ?? 'UNKNOWN'}
                </span>
              </MetricCard>
            )}
            {tab === 'probes' && (
              <article className="health-metric health-metric--wide health-metric--slate">
                <header className="health-metric__head">
                  <h3 className="health-metric__title">Probes</h3>
                </header>
                <div className="health-probe-row">
                  <ProbeBadge label="Liveness" status={snapshot?.probes?.liveness} />
                  <ProbeBadge label="Readiness" status={snapshot?.probes?.readiness} />
                </div>
              </article>
            )}
          </div>
        )}

        {tab === 'heap' ? (
          <div className="health-apm-grid">
            <MetricCard
              title="Heap"
              value={snapshot ? `${formatNum(snapshot.heap.usedPercent)}%` : '—'}
              sub={`${formatBytes(snapshot?.heap?.usedBytes)} / ${formatBytes(snapshot?.heap?.maxBytes)} · GC ${snapshot?.gc?.collectionCount ?? '—'} (${snapshot?.gc?.collectionTimeMs ?? '—'} ms)`}
              accent="teal"
            >
              <div className="health-bar" aria-hidden="true">
                <div
                  className="health-bar__fill"
                  style={{ width: `${Math.min(100, snapshot?.heap?.usedPercent ?? 0)}%` }}
                />
              </div>
              <Sparkline samples={samples} getValue={(s) => s.heapUsedPercent} color="#0f766e" height={56} />
            </MetricCard>
            <MetricCard
              title="Non-heap"
              value={formatBytes(snapshot?.nonHeap?.usedBytes)}
              sub={`Committed ${formatBytes(snapshot?.nonHeap?.committedBytes)}`}
              accent="slate"
            />
          </div>
        ) : null}

        {tab === 'load' ? (
          <div className="health-apm-grid">
            <MetricCard
              title="Process load"
              value={formatCpu(snapshot?.load?.processCpuLoad)}
              sub={`System ${formatCpu(snapshot?.load?.systemCpuLoad)} · load avg ${formatNum(snapshot?.load?.systemLoadAverage, 2)} · ${snapshot?.load?.availableProcessors ?? '—'} CPUs`}
              accent="blue"
            >
              <Sparkline samples={samples} getValue={(s) => s.processCpuLoad} color="#2563eb" floorMax={100} height={56} />
            </MetricCard>
            <MetricCard
              title="Threads"
              value={snapshot?.threads?.live ?? '—'}
              sub={`Runnable ${snapshot?.threads?.runnable ?? '—'} · Blocked ${snapshot?.threads?.blocked ?? '—'} · Waiting ${snapshot?.threads?.waiting ?? '—'} · Peak ${snapshot?.threads?.peak ?? '—'}`}
              accent="slate"
            />
          </div>
        ) : null}

        {tab === 'stack' ? (
          <div className="health-stack">
            <div className="health-stack__head">
              <div>
                <h3 className="health-stack__title">Stack · top threads</h3>
                <p className="health-metric__sub">CPU-ordered thread dump from the monitored process.</p>
              </div>
              {onRefreshStack ? (
                <button type="button" className="btn health-btn" onClick={onRefreshStack} disabled={stackLoading}>
                  {stackLoading ? 'Loading…' : 'Dump stacks'}
                </button>
              ) : null}
            </div>
            {!stacks.length ? (
              <div className="health-empty">
                <p>No thread stacks yet.</p>
              </div>
            ) : (
              <ul className="health-stack__list">
                {stacks.map((thread, index) => (
                  <li
                    key={`${thread.name}-${thread.cpuTimeMs}`}
                    className="health-stack__item"
                    style={{ '--stagger': `${index * 35}ms` }}
                  >
                    <div className="health-stack__meta">
                      <span className="health-stack__name mono">{thread.name}</span>
                      <span className="health-stack__state">{thread.state}</span>
                      <span className="health-stack__cpu">{thread.cpuTimeMs} ms CPU</span>
                    </div>
                    <pre className="health-stack__frames mono">
                      {(thread.frames || []).slice(0, 40).join('\n') || '(no frames)'}
                    </pre>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ) : null}
      </div>
    </section>
  );
}
