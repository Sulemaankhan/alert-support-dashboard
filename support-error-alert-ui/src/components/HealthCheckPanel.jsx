import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { SECTION } from '../constants/branding.js';
import { NAV_SECTION } from '../constants/nav.js';
import './AlertSourcePanel.css';
import './HealthCheckPanel.css';

const TAB_GROUPS = [
  {
    label: 'Signals',
    items: [
      { id: 'overview', label: 'Overview' },
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

function formatRelative(iso) {
  if (!iso) return '—';
  try {
    const ms = Date.now() - new Date(iso).getTime();
    if (ms < 0) return 'just now';
    if (ms < 2500) return 'just now';
    if (ms < 60_000) return `${Math.floor(ms / 1000)}s ago`;
    if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m ago`;
    return formatTime(iso);
  } catch {
    return formatTime(iso);
  }
}

function trendFromSamples(samples, getValue) {
  if (!samples || samples.length < 4) return 'flat';
  const recent = samples.slice(-6).map(getValue).map((v) => (v == null || v < 0 ? 0 : Number(v)));
  const mid = Math.floor(recent.length / 2);
  const a = recent.slice(0, mid).reduce((s, n) => s + n, 0) / Math.max(1, mid);
  const b = recent.slice(mid).reduce((s, n) => s + n, 0) / Math.max(1, recent.length - mid);
  if (b > a * 1.08) return 'up';
  if (b < a * 0.92) return 'down';
  return 'flat';
}

const OVERVIEW_SERIES = [
  {
    id: 'rpm',
    label: 'Throughput',
    unit: 'rpm',
    color: '#2563eb',
    get: (s) => Math.max(0, Number(s.requestsPerMinute) || 0),
    format: (v) => formatNum(v, 1),
  },
  {
    id: 'errors',
    label: 'Error rate',
    unit: '%',
    color: '#c62828',
    get: (s) => Math.max(0, Number(s.errorRatePercent) || 0),
    format: (v) => formatNum(v, 1),
  },
  {
    id: 'latency',
    label: 'Latency',
    unit: 'ms',
    color: '#b45309',
    get: (s) => Math.max(0, Number(s.avgLatencyMs) || 0),
    format: (v) => formatNum(v, 0),
  },
  {
    id: 'apdex',
    label: 'Apdex',
    unit: '',
    color: '#0f766e',
    get: (s) => Math.max(0, Math.min(1, Number(s.apdex) || 0)) * 100,
    format: (v) => formatNum(v / 100, 2),
    displayRaw: (s) => formatNum(s.apdex, 2),
  },
  {
    id: 'heap',
    label: 'Heap',
    unit: '%',
    color: '#0891b2',
    get: (s) => Math.max(0, Number(s.heapUsedPercent) || 0),
    format: (v) => formatNum(v, 1),
  },
  {
    id: 'cpu',
    label: 'CPU',
    unit: '%',
    color: '#4f46e5',
    get: (s) => Math.max(0, Number(s.processCpuLoad) || 0),
    format: (v) => formatNum(v, 1),
  },
];

/**
 * Multi-series realtime overview chart (normalized overlay + absolute hover details).
 * @param {{ samples: any[], live?: boolean, tick?: number }} props
 */
function OverviewRealtimeChart({ samples = [], live = false, tick = 0 }) {
  const [enabled, setEnabled] = useState(() =>
    Object.fromEntries(OVERVIEW_SERIES.map((s) => [s.id, true])),
  );
  const [hover, setHover] = useState(/** @type {null | number} */ (null));
  const svgRef = useRef(/** @type {SVGSVGElement | null} */ (null));

  const width = 720;
  const height = 260;
  const pad = { top: 18, right: 16, bottom: 28, left: 36 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;

  const points = useMemo(() => {
    return samples.map((sample, index) => {
      const row = { index, sample, timestamp: sample?.timestamp };
      for (const series of OVERVIEW_SERIES) {
        row[series.id] = series.get(sample);
      }
      return row;
    });
  }, [samples]);

  const ranges = useMemo(() => {
    /** @type {Record<string, { min: number, max: number }>} */
    const out = {};
    for (const series of OVERVIEW_SERIES) {
      const vals = points.map((p) => p[series.id]).filter((v) => Number.isFinite(v));
      const max = Math.max(series.id === 'apdex' ? 100 : 1, ...(vals.length ? vals : [0]));
      out[series.id] = { min: 0, max: max * 1.08 || 1 };
    }
    return out;
  }, [points]);

  const pathFor = (seriesId) => {
    if (points.length < 2) return '';
    const range = ranges[seriesId];
    return points
      .map((p, i) => {
        const x = pad.left + (i / (points.length - 1)) * innerW;
        const norm = (p[seriesId] - range.min) / (range.max - range.min || 1);
        const y = pad.top + innerH - Math.max(0, Math.min(1, norm)) * innerH;
        return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  };

  const hoverIndex =
    hover == null || points.length === 0
      ? points.length
        ? points.length - 1
        : -1
      : Math.max(0, Math.min(points.length - 1, hover));
  const hoverPoint = hoverIndex >= 0 ? points[hoverIndex] : null;
  const hoverX =
    hoverPoint && points.length > 1
      ? pad.left + (hoverIndex / (points.length - 1)) * innerW
      : points.length === 1
        ? pad.left + innerW / 2
        : null;

  const onMove = (event) => {
    const svg = svgRef.current;
    if (!svg || points.length < 2) return;
    const rect = svg.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * width;
    const idx = Math.round(((x - pad.left) / innerW) * (points.length - 1));
    setHover(Math.max(0, Math.min(points.length - 1, idx)));
  };

  const latest = points.length ? points[points.length - 1] : null;
  const activeSeries = OVERVIEW_SERIES.filter((s) => enabled[s.id]);

  return (
    <section className={`health-overview-chart${live ? ' health-overview-chart--live' : ''}`}>
      <div className="health-overview-chart__head">
        <div>
          <h3 className="health-stack__title">Overview timeline</h3>
          <p className="health-metric__sub">
            All live signals on one chart · {points.length} samples
            {latest?.timestamp ? ` · last ${formatRelative(latest.timestamp)}` : ''}
          </p>
        </div>
        <span className={live ? 'health-live health-live--on' : 'health-live health-live--off'}>
          <span className="health-live__dot" aria-hidden="true" />
          {live ? 'Realtime' : 'Waiting…'}
        </span>
      </div>

      <div className="health-overview-chart__legend" role="group" aria-label="Toggle series">
        {OVERVIEW_SERIES.map((series) => {
          const value = latest ? (series.displayRaw ? series.displayRaw(latest.sample) : series.format(latest[series.id])) : '—';
          return (
            <button
              key={series.id}
              type="button"
              className={
                enabled[series.id]
                  ? 'health-overview-legend health-overview-legend--on'
                  : 'health-overview-legend'
              }
              style={{ '--legend-color': series.color }}
              onClick={() => setEnabled((prev) => ({ ...prev, [series.id]: !prev[series.id] }))}
              aria-pressed={enabled[series.id]}
            >
              <span className="health-overview-legend__swatch" aria-hidden="true" />
              <span className="health-overview-legend__label">{series.label}</span>
              <strong className="health-overview-legend__value mono" key={`${series.id}-${tick}`}>
                {value}
                {series.unit ? <small>{series.unit}</small> : null}
              </strong>
            </button>
          );
        })}
      </div>

      <div className="health-overview-chart__canvas">
        {!points.length ? (
          <div className="health-empty health-empty--chart">
            <p>Waiting for realtime samples…</p>
            <span>Keep this tab open — the chart fills as metrics stream in.</span>
          </div>
        ) : (
          <>
            <svg
              ref={svgRef}
              className="health-overview-chart__svg"
              viewBox={`0 0 ${width} ${height}`}
              role="img"
              aria-label="Realtime overview of throughput, errors, latency, Apdex, heap, and CPU"
              onMouseMove={onMove}
              onMouseLeave={() => setHover(null)}
            >
              {[0, 0.25, 0.5, 0.75, 1].map((t) => {
                const y = pad.top + innerH * (1 - t);
                return (
                  <g key={t}>
                    <line
                      x1={pad.left}
                      x2={pad.left + innerW}
                      y1={y}
                      y2={y}
                      className="health-overview-chart__grid"
                    />
                  </g>
                );
              })}

              {activeSeries.map((series) => (
                <path
                  key={series.id}
                  d={pathFor(series.id)}
                  fill="none"
                  stroke={series.color}
                  strokeWidth="2.4"
                  strokeLinejoin="round"
                  strokeLinecap="round"
                  className="health-overview-chart__line"
                />
              ))}

              {hoverX != null ? (
                <line
                  x1={hoverX}
                  x2={hoverX}
                  y1={pad.top}
                  y2={pad.top + innerH}
                  className="health-overview-chart__crosshair"
                />
              ) : null}

              {activeSeries.map((series) => {
                if (!hoverPoint || points.length < 1) return null;
                const range = ranges[series.id];
                const norm = (hoverPoint[series.id] - range.min) / (range.max - range.min || 1);
                const y = pad.top + innerH - Math.max(0, Math.min(1, norm)) * innerH;
                const x = hoverX ?? pad.left;
                return <circle key={`dot-${series.id}`} cx={x} cy={y} r="3.5" fill={series.color} />;
              })}

              {points.length > 1 ? (
                <>
                  <text x={pad.left} y={height - 8} className="health-overview-chart__axis">
                    {formatTime(points[0].timestamp)}
                  </text>
                  <text
                    x={pad.left + innerW}
                    y={height - 8}
                    textAnchor="end"
                    className="health-overview-chart__axis"
                  >
                    {formatTime(points[points.length - 1].timestamp)}
                  </text>
                </>
              ) : null}
            </svg>

            {hoverPoint ? (
              <div className="health-overview-chart__tooltip" aria-live="polite">
                <div className="health-overview-chart__tooltip-time mono">
                  {formatTime(hoverPoint.timestamp)}
                  {hover === null ? ' · now' : ''}
                </div>
                <ul className="health-overview-chart__tooltip-list">
                  {OVERVIEW_SERIES.filter((s) => enabled[s.id]).map((series) => (
                    <li key={series.id} style={{ '--legend-color': series.color }}>
                      <span>{series.label}</span>
                      <strong className="mono">
                        {series.displayRaw
                          ? series.displayRaw(hoverPoint.sample)
                          : series.format(hoverPoint[series.id])}
                        {series.unit ? ` ${series.unit}` : ''}
                      </strong>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </>
        )}
      </div>

      <div className="health-overview-details">
        <div className="health-overview-details__card">
          <span className="health-overview-details__label">Window</span>
          <strong className="mono">{points.length} pts</strong>
        </div>
        <div className="health-overview-details__card">
          <span className="health-overview-details__label">Throughput</span>
          <strong className="mono">{latest ? `${formatNum(latest.rpm, 1)} rpm` : '—'}</strong>
        </div>
        <div className="health-overview-details__card">
          <span className="health-overview-details__label">Errors</span>
          <strong className="mono">{latest ? `${formatNum(latest.errors, 1)}%` : '—'}</strong>
        </div>
        <div className="health-overview-details__card">
          <span className="health-overview-details__label">Latency</span>
          <strong className="mono">{latest ? `${formatNum(latest.latency, 0)} ms` : '—'}</strong>
        </div>
        <div className="health-overview-details__card">
          <span className="health-overview-details__label">Apdex</span>
          <strong className="mono">{latest ? formatNum(latest.sample?.apdex, 2) : '—'}</strong>
        </div>
        <div className="health-overview-details__card">
          <span className="health-overview-details__label">Heap</span>
          <strong className="mono">{latest ? `${formatNum(latest.heap, 1)}%` : '—'}</strong>
        </div>
        <div className="health-overview-details__card">
          <span className="health-overview-details__label">CPU</span>
          <strong className="mono">{latest ? `${formatNum(latest.cpu, 1)}%` : '—'}</strong>
        </div>
        <div className="health-overview-details__card">
          <span className="health-overview-details__label">Series on</span>
          <strong className="mono">
            {activeSeries.length}/{OVERVIEW_SERIES.length}
          </strong>
        </div>
      </div>
    </section>
  );
}

function ApdexRing({ score = 0, rating = '—' }) {
  const pct = Math.max(0, Math.min(1, Number(score) || 0));
  const angle = pct * 360;
  return (
    <div className={`health-apdex-ring ${apdexClass(score)}`} style={{ '--apdex-angle': `${angle}deg` }}>
      <div className="health-apdex-ring__sweep" aria-hidden="true" />
      <div className="health-apdex-ring__inner">
        <span className="health-apdex-ring__score mono">{formatNum(score, 2)}</span>
        <span className="health-apdex-ring__label">{rating}</span>
      </div>
    </div>
  );
}

/** @param {{ samples: any[], getValue: (s: any) => number, color: string, floorMax?: number, height?: number, live?: boolean }} props */
function Sparkline({ samples, getValue, color, floorMax = 1, height = 44, live = false }) {
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
  const last = coords[coords.length - 1];

  return (
    <svg
      className={live ? 'health-sparkline health-sparkline--live' : 'health-sparkline'}
      viewBox={`0 0 ${w} ${h}`}
      preserveAspectRatio="none"
      style={{ height }}
      aria-hidden="true"
    >
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.38" />
          <stop offset="100%" stopColor={color} stopOpacity="0.02" />
        </linearGradient>
      </defs>
      <polygon fill={`url(#${gradId})`} points={area} />
      <polyline fill="none" stroke={color} strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round" points={line} />
      {last ? (
        <>
          <circle className="health-sparkline__pulse" cx={last.x} cy={last.y} r="5" fill={color} opacity="0.25" />
          <circle cx={last.x} cy={last.y} r="3" fill={color} />
        </>
      ) : null}
    </svg>
  );
}

function MetricCard({ title, value, sub, children, tone, accent = 'blue', delay = 0, flashKey = 0 }) {
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
        <span key={flashKey} className="health-metric__value mono health-metric__value--flash">
          {value}
        </span>
      </header>
      {children}
      {sub ? <p className="health-metric__sub">{sub}</p> : null}
    </article>
  );
}

function SignalTile({ label, value, unit, samples, getValue, color, floorMax, warn, title }) {
  const trend = trendFromSamples(samples, getValue);
  return (
    <div className={warn ? 'health-signal health-signal--warn' : 'health-signal'} title={title}>
      <div className="health-signal__top">
        <span className="health-signal__label">{label}</span>
        <span className={`health-signal__trend health-signal__trend--${trend}`} aria-hidden="true">
          {trend === 'up' ? '▲' : trend === 'down' ? '▼' : '●'}
        </span>
      </div>
      <span className="health-signal__value mono">
        {value}
        {unit ? <small>{unit}</small> : null}
      </span>
      <Sparkline samples={samples} getValue={getValue} color={color} floorMax={floorMax} height={28} live />
    </div>
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

const TX_PALETTE = ['#2563eb', '#0f766e', '#b45309', '#c62828', '#4f46e5', '#0891b2', '#7c3aed', '#db2777'];

function shortUri(uri) {
  const value = String(uri || '');
  if (value.length <= 36) return value;
  return `…${value.slice(-34)}`;
}

/**
 * Donut / ring segment path (degrees; 0 = east, -90 = north).
 * @param {number} cx
 * @param {number} cy
 * @param {number} startDeg
 * @param {number} sweepDeg
 * @param {number} rInner
 * @param {number} rOuter
 * @param {number} [angleOffset=0] extra rotation (e.g. -90 so 0° is north)
 */
function donutArcPath(cx, cy, startDeg, sweepDeg, rInner, rOuter, angleOffset = 0) {
  if (sweepDeg <= 0.05) return '';
  const end = startDeg + Math.min(359.9, sweepDeg);
  const large = sweepDeg > 180 ? 1 : 0;
  const polar = (deg, r) => {
    const rad = ((deg + angleOffset) * Math.PI) / 180;
    return [cx + r * Math.cos(rad), cy + r * Math.sin(rad)];
  };
  const [x0, y0] = polar(startDeg, rOuter);
  const [x1, y1] = polar(end, rOuter);
  const [x2, y2] = polar(end, rInner);
  const [x3, y3] = polar(startDeg, rInner);
  return `M ${x0} ${y0} A ${rOuter} ${rOuter} 0 ${large} 1 ${x1} ${y1} L ${x2} ${y2} A ${rInner} ${rInner} 0 ${large} 0 ${x3} ${y3} Z`;
}

function txMetricValue(tx, sort) {
  if (sort === 'errors') return Number(tx.errorRatePercent) || 0;
  if (sort === 'latency') return Number(tx.avgMs) || 0;
  if (sort === 'apdex') return Math.max(0, (1 - (Number(tx.apdex) || 0)) * 100);
  return Number(tx.count) || 0;
}

function txMetricLabel(sort) {
  if (sort === 'errors') return 'Error rate';
  if (sort === 'latency') return 'Avg latency';
  if (sort === 'apdex') return 'Apdex gap';
  return 'Calls';
}

function txMetricUnit(sort) {
  if (sort === 'errors') return '%';
  if (sort === 'latency') return 'ms';
  if (sort === 'apdex') return '';
  return '';
}

function formatTxMetric(tx, sort) {
  if (sort === 'errors') return `${formatNum(tx.errorRatePercent, 1)}%`;
  if (sort === 'latency') return `${formatNum(tx.avgMs, 0)} ms`;
  if (sort === 'apdex') return formatNum(tx.apdex, 2);
  return String(tx.count ?? 0);
}

/**
 * Graphical transactions board: share donut, ranked bars, rich cards.
 * @param {{ transactions: any[], sort: string, onSort: (v: string) => void }} props
 */
function TransactionsBoard({ transactions, sort, onSort }) {
  const [focus, setFocus] = useState(0);

  useEffect(() => {
    setFocus(0);
  }, [sort, transactions]);

  const totalCalls = transactions.reduce((sum, tx) => sum + (Number(tx.count) || 0), 0);
  const totalErrors = transactions.reduce((sum, tx) => sum + (Number(tx.errorCount) || 0), 0);
  const avgLatency =
    totalCalls > 0
      ? transactions.reduce((sum, tx) => sum + (Number(tx.avgMs) || 0) * (Number(tx.count) || 0), 0) / totalCalls
      : 0;
  const worstApdex = transactions.length
    ? Math.min(...transactions.map((tx) => Number(tx.apdex) || 1))
    : 1;
  const maxMetric = Math.max(1, ...transactions.map((tx) => txMetricValue(tx, sort)));
  const maxLatency = Math.max(1, ...transactions.map((tx) => Number(tx.avgMs) || 0));
  const maxErrors = Math.max(1, ...transactions.map((tx) => Number(tx.errorRatePercent) || 0));

  const palette = TX_PALETTE;
  const focused = transactions[Math.min(focus, Math.max(0, transactions.length - 1))] || null;

  const donut = useMemo(() => {
    const calls = transactions.reduce((sum, tx) => sum + (Number(tx.count) || 0), 0);
    if (!transactions.length || calls <= 0) return [];
    let angle = -90;
    return transactions.slice(0, 8).map((tx, index) => {
      const share = (Number(tx.count) || 0) / calls;
      const sweep = share * 360;
      const start = angle;
      angle += sweep;
      return { tx, index, share, start, sweep, color: TX_PALETTE[index % TX_PALETTE.length] };
    });
  }, [transactions]);

  if (!transactions.length) {
    return (
      <div className="health-tx">
        <div className="health-tx__toolbar">
          <div>
            <h3 className="health-stack__title">Web transactions</h3>
            <p className="health-metric__sub">Live URI breakdown from the monitored service.</p>
          </div>
        </div>
        <div className="health-empty">
          <p>No transactions yet.</p>
          <span>Hit app endpoints on the monitored service (not /actuator) to populate charts.</span>
        </div>
      </div>
    );
  }

  return (
    <div className="health-tx">
      <div className="health-tx__toolbar">
        <div>
          <h3 className="health-stack__title">Web transactions</h3>
          <p className="health-metric__sub">
            {transactions.length} endpoints · {totalCalls} calls · graphical ranking
          </p>
        </div>
        <div className="health-tx__sort-pills" role="group" aria-label="Sort transactions">
          {[
            { id: 'count', label: 'Calls' },
            { id: 'latency', label: 'Latency' },
            { id: 'errors', label: 'Errors' },
            { id: 'apdex', label: 'Apdex' },
          ].map((opt) => (
            <button
              key={opt.id}
              type="button"
              className={sort === opt.id ? 'health-tx__pill health-tx__pill--on' : 'health-tx__pill'}
              onClick={() => onSort(opt.id)}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      <div className="health-tx__kpis">
        <div className="health-tx__kpi">
          <span>Total calls</span>
          <strong className="mono">{totalCalls}</strong>
        </div>
        <div className="health-tx__kpi">
          <span>Avg latency</span>
          <strong className="mono">{formatNum(avgLatency, 0)}<small>ms</small></strong>
        </div>
        <div className={`health-tx__kpi${totalErrors > 0 ? ' health-tx__kpi--warn' : ''}`}>
          <span>Errors</span>
          <strong className="mono">{totalErrors}</strong>
        </div>
        <div className={`health-tx__kpi${worstApdex < 0.7 ? ' health-tx__kpi--warn' : ''}`}>
          <span>Worst Apdex</span>
          <strong className={`mono ${apdexClass(worstApdex)}`}>{formatNum(worstApdex, 2)}</strong>
        </div>
      </div>

      <div className="health-tx__visuals">
        <article className="health-tx__panel health-tx__panel--donut">
          <header className="health-tx__panel-head">
            <h4>Traffic share</h4>
            <span>by call volume</span>
          </header>
          <div className="health-tx__donut-wrap">
            <svg className="health-tx__donut" viewBox="0 0 200 200" aria-hidden="true">
              <circle cx="100" cy="100" r="88" fill="none" stroke="#e8edf3" strokeWidth="26" />
              {donut.map((slice) => (
                <path
                  key={slice.tx.uri}
                  d={donutArcPath(100, 100, slice.start, slice.sweep, 62, 88)}
                  fill={slice.color}
                  className={focus === slice.index ? 'health-tx__slice health-tx__slice--on' : 'health-tx__slice'}
                  onMouseEnter={() => setFocus(slice.index)}
                  onClick={() => setFocus(slice.index)}
                />
              ))}
              <circle cx="100" cy="100" r="48" fill="#fff" />
              <text x="100" y="96" textAnchor="middle" className="health-tx__donut-value">
                {focused ? `${Math.round(((Number(focused.count) || 0) / totalCalls) * 100)}%` : '—'}
              </text>
              <text x="100" y="114" textAnchor="middle" className="health-tx__donut-label">
                share
              </text>
            </svg>
            <ul className="health-tx__donut-legend">
              {donut.map((slice) => (
                <li key={slice.tx.uri}>
                  <button
                    type="button"
                    className={focus === slice.index ? 'health-tx__legend-btn health-tx__legend-btn--on' : 'health-tx__legend-btn'}
                    style={{ '--slice': slice.color }}
                    onMouseEnter={() => setFocus(slice.index)}
                    onClick={() => setFocus(slice.index)}
                  >
                    <span className="health-tx__legend-swatch" />
                    <span className="mono health-tx__legend-uri">{shortUri(slice.tx.uri)}</span>
                    <strong className="mono">{Math.round(slice.share * 100)}%</strong>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </article>

        <article className="health-tx__panel health-tx__panel--bars">
          <header className="health-tx__panel-head">
            <h4>Ranked by {txMetricLabel(sort).toLowerCase()}</h4>
            <span>top {Math.min(8, transactions.length)}</span>
          </header>
          <ul className="health-tx__rank">
            {transactions.slice(0, 8).map((tx, index) => {
              const value = txMetricValue(tx, sort);
              const width = Math.max(4, (value / maxMetric) * 100);
              const color = palette[index % palette.length];
              return (
                <li key={`rank-${tx.uri}`}>
                  <button
                    type="button"
                    className={focus === index ? 'health-tx__rank-row health-tx__rank-row--on' : 'health-tx__rank-row'}
                    onMouseEnter={() => setFocus(index)}
                    onClick={() => setFocus(index)}
                  >
                    <span className="health-tx__rank-idx mono">{index + 1}</span>
                    <span className="health-tx__rank-body">
                      <span className="health-tx__rank-top">
                        <span className="mono health-tx__rank-uri">{shortUri(tx.uri)}</span>
                        <strong className="mono">
                          {formatTxMetric(tx, sort)}
                          {txMetricUnit(sort) ? ` ${txMetricUnit(sort)}` : ''}
                        </strong>
                      </span>
                      <span className="health-tx__rank-track">
                        <span
                          className="health-tx__rank-fill"
                          style={{ width: `${width}%`, background: color }}
                        />
                      </span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </article>
      </div>

      {focused ? (
        <article className="health-tx__focus" style={{ '--focus-color': palette[Math.min(focus, palette.length - 1) % palette.length] }}>
          <div className="health-tx__focus-main">
            <span className="health-tx__method">{focused.method || '*'}</span>
            <div>
              <h4 className="mono">{focused.uri}</h4>
              <p>
                #{focus + 1} · {Math.round(((Number(focused.count) || 0) / Math.max(1, totalCalls)) * 100)}% of traffic
              </p>
            </div>
          </div>
          <div className="health-tx__focus-meters">
            <div>
              <span>Calls</span>
              <strong className="mono">{focused.count}</strong>
              <div className="health-tx__meter">
                <i style={{ width: `${((Number(focused.count) || 0) / Math.max(1, totalCalls)) * 100}%` }} />
              </div>
            </div>
            <div>
              <span>Avg latency</span>
              <strong className="mono">{formatNum(focused.avgMs, 1)} ms</strong>
              <div className="health-tx__meter health-tx__meter--amber">
                <i style={{ width: `${((Number(focused.avgMs) || 0) / maxLatency) * 100}%` }} />
              </div>
            </div>
            <div>
              <span>Error rate</span>
              <strong className="mono">{formatNum(focused.errorRatePercent, 1)}%</strong>
              <div className="health-tx__meter health-tx__meter--rose">
                <i style={{ width: `${((Number(focused.errorRatePercent) || 0) / maxErrors) * 100}%` }} />
              </div>
            </div>
            <div>
              <span>Apdex</span>
              <strong className={`mono ${apdexClass(focused.apdex)}`}>{formatNum(focused.apdex, 2)}</strong>
              <div className="health-tx__meter health-tx__meter--teal">
                <i style={{ width: `${Math.max(0, Math.min(100, (Number(focused.apdex) || 0) * 100))}%` }} />
              </div>
            </div>
          </div>
        </article>
      ) : null}

      <ul className="health-tx__cards">
        {transactions.map((tx, index) => {
          const color = palette[index % palette.length];
          const share = totalCalls > 0 ? ((Number(tx.count) || 0) / totalCalls) * 100 : 0;
          return (
            <li
              key={`${tx.method}-${tx.uri}`}
              className={focus === index ? 'health-tx__card health-tx__card--on' : 'health-tx__card'}
              style={{ '--stagger': `${index * 35}ms`, '--card-accent': color }}
              onMouseEnter={() => setFocus(index)}
            >
              <div className="health-tx__card-top">
                <span className="health-tx__rank-badge mono">{index + 1}</span>
                <span className="health-tx__method">{tx.method || '*'}</span>
                <span className="health-tx__uri mono" title={tx.uri}>
                  {tx.uri}
                </span>
                <span className={`health-tx__apdex-chip mono ${apdexClass(tx.apdex)}`}>
                  {formatNum(tx.apdex, 2)}
                </span>
              </div>

              <div className="health-tx__mini-grid">
                <div className="health-tx__mini">
                  <span>Calls</span>
                  <strong className="mono">{tx.count}</strong>
                  <div className="health-tx__mini-bar">
                    <i style={{ width: `${share}%`, background: color }} />
                  </div>
                </div>
                <div className="health-tx__mini">
                  <span>Avg</span>
                  <strong className="mono">{formatNum(tx.avgMs, 0)}<small>ms</small></strong>
                  <div className="health-tx__mini-bar">
                    <i
                      style={{
                        width: `${((Number(tx.avgMs) || 0) / maxLatency) * 100}%`,
                        background: '#b45309',
                      }}
                    />
                  </div>
                </div>
                <div className="health-tx__mini">
                  <span>Max</span>
                  <strong className="mono">{formatNum(tx.maxMs, 0)}<small>ms</small></strong>
                  <div className="health-tx__mini-bar">
                    <i
                      style={{
                        width: `${((Number(tx.maxMs) || 0) / Math.max(maxLatency, Number(tx.maxMs) || 1)) * 100}%`,
                        background: '#c62828',
                      }}
                    />
                  </div>
                </div>
                <div className="health-tx__mini">
                  <span>Errors</span>
                  <strong className={`mono${tx.errorCount > 0 ? ' health-tx__err' : ''}`}>
                    {tx.errorCount}
                    <small> · {formatNum(tx.errorRatePercent, 0)}%</small>
                  </strong>
                  <div className="health-tx__mini-bar">
                    <i
                      style={{
                        width: `${((Number(tx.errorRatePercent) || 0) / maxErrors) * 100}%`,
                        background: '#c62828',
                      }}
                    />
                  </div>
                </div>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

const ERR_PALETTE = ['#c62828', '#b45309', '#db2777', '#7c3aed', '#e11d48', '#ea580c', '#be123c', '#9f1239'];

/**
 * Graphical errors board: rate gauge, timeline, share, ranked error URIs.
 * @param {{
 *   snapshot: import('../services/healthService.js').ApmSnapshot | null,
 *   transactions: any[],
 *   samples: any[],
 *   live?: boolean,
 *   tick?: number,
 * }} props
 */
function ErrorsBoard({ snapshot, transactions, samples = [], live = false, tick = 0 }) {
  const [focus, setFocus] = useState(0);
  const [sort, setSort] = useState(/** @type {'count' | 'rate'} */ ('count'));

  const erroring = useMemo(() => {
    const list = transactions.filter((t) => (t.errorCount ?? 0) > 0);
    list.sort((a, b) =>
      sort === 'rate'
        ? (b.errorRatePercent ?? 0) - (a.errorRatePercent ?? 0)
        : (b.errorCount ?? 0) - (a.errorCount ?? 0),
    );
    return list;
  }, [transactions, sort]);

  useEffect(() => {
    setFocus(0);
  }, [sort, erroring.length]);

  const totalRequests = Number(snapshot?.requests?.totalRequests) || 0;
  const errorRequests = Number(snapshot?.requests?.errorRequests) || 0;
  const okRequests = Math.max(0, totalRequests - errorRequests);
  const errorRate = Number(snapshot?.requests?.errorRatePercent) || 0;
  const errorsDelta = Number(snapshot?.requests?.errorsDelta) || 0;
  const healthyCount = Math.max(0, transactions.length - erroring.length);
  const maxErrCount = Math.max(1, ...erroring.map((t) => Number(t.errorCount) || 0));
  const maxErrRate = Math.max(1, ...erroring.map((t) => Number(t.errorRatePercent) || 0));
  const focused = erroring[Math.min(focus, Math.max(0, erroring.length - 1))] || null;
  const severity =
    errorRate >= 20 ? 'critical' : errorRate >= 5 ? 'warn' : errorRate > 0 ? 'watch' : 'ok';

  const rateAngle = Math.max(0, Math.min(100, errorRate)) * 3.6;
  const successSweep = totalRequests > 0 ? (okRequests / totalRequests) * 360 : 0;
  const errorSweep = totalRequests > 0 ? (errorRequests / totalRequests) * 360 : 0;

  return (
    <div className={`health-err health-err--${severity}`}>
      <div className="health-err__toolbar">
        <div>
          <h3 className="health-stack__title">Error intelligence</h3>
          <p className="health-metric__sub">
            HTTP 4xx/5xx across app URIs · {erroring.length} erroring endpoint
            {erroring.length === 1 ? '' : 's'}
          </p>
        </div>
        <span className={live ? 'health-live health-live--on' : 'health-live health-live--off'}>
          <span className="health-live__dot" aria-hidden="true" />
          {live ? 'Live' : 'Waiting…'}
        </span>
      </div>

      <div className="health-err__kpis">
        <div className={`health-err__kpi health-err__kpi--rate health-err__kpi--${severity}`}>
          <span>Error rate</span>
          <strong className="mono" key={`rate-${tick}`}>
            {formatNum(errorRate, 1)}
            <small>%</small>
          </strong>
          <em>
            {severity === 'critical'
              ? 'Critical'
              : severity === 'warn'
                ? 'Elevated'
                : severity === 'watch'
                  ? 'Watch'
                  : 'Healthy'}
          </em>
        </div>
        <div className="health-err__kpi">
          <span>Error count</span>
          <strong className="mono">{errorRequests}</strong>
          <em>of {totalRequests} requests</em>
        </div>
        <div className="health-err__kpi">
          <span>Recent Δ</span>
          <strong className="mono">{errorsDelta >= 0 ? `+${errorsDelta}` : errorsDelta}</strong>
          <em>since last sample</em>
        </div>
        <div className="health-err__kpi">
          <span>Erroring URIs</span>
          <strong className="mono">{erroring.length}</strong>
          <em>{healthyCount} healthy</em>
        </div>
      </div>

      <div className="health-err__visuals">
        <article className="health-err__panel">
          <header className="health-err__panel-head">
            <h4>Rate gauge</h4>
            <span>target &lt; 5%</span>
          </header>
          <div className="health-err__gauge-wrap">
            <svg className="health-err__gauge" viewBox="0 0 160 160" aria-hidden="true">
              <circle cx="80" cy="80" r="58" fill="none" stroke="#fee2e2" strokeWidth="14" />
              <circle
                cx="80"
                cy="80"
                r="58"
                fill="none"
                stroke={severity === 'ok' ? '#16a34a' : severity === 'watch' ? '#b45309' : '#c62828'}
                strokeWidth="14"
                strokeLinecap="round"
                strokeDasharray={`${(rateAngle / 360) * 364.4} 364.4`}
                transform="rotate(-90 80 80)"
                className="health-err__gauge-arc"
              />
              <text x="80" y="76" textAnchor="middle" className="health-err__gauge-value">
                {formatNum(errorRate, 1)}%
              </text>
              <text x="80" y="96" textAnchor="middle" className="health-err__gauge-label">
                error rate
              </text>
            </svg>
            <div className="health-err__gauge-meta">
              <div>
                <span>Threshold</span>
                <strong>5.0%</strong>
              </div>
              <div>
                <span>Status</span>
                <strong className={`health-err__badge health-err__badge--${severity}`}>
                  {severity === 'critical'
                    ? 'Critical'
                    : severity === 'warn'
                      ? 'Elevated'
                      : severity === 'watch'
                        ? 'Watch'
                        : 'Healthy'}
                </strong>
              </div>
              <div>
                <span>Success</span>
                <strong className="mono">
                  {totalRequests > 0 ? formatNum((okRequests / totalRequests) * 100, 1) : '—'}%
                </strong>
              </div>
            </div>
          </div>
        </article>

        <article className="health-err__panel">
          <header className="health-err__panel-head">
            <h4>Success vs errors</h4>
            <span>request outcome mix</span>
          </header>
          <div className="health-err__mix">
            <svg className="health-err__mix-donut" viewBox="0 0 160 160" aria-hidden="true">
              <circle cx="80" cy="80" r="58" fill="none" stroke="#e8edf3" strokeWidth="26" />
              {totalRequests > 0 ? (
                <>
                  <path d={donutArcPath(80, 80, 0, successSweep, 45, 71, -90)} fill="#16a34a" />
                  <path d={donutArcPath(80, 80, successSweep, errorSweep, 45, 71, -90)} fill="#c62828" />
                </>
              ) : null}
              <circle cx="80" cy="80" r="36" fill="#fff" />
              <text x="80" y="78" textAnchor="middle" className="health-err__mix-value">
                {errorRequests}
              </text>
              <text x="80" y="94" textAnchor="middle" className="health-err__mix-label">
                errors
              </text>
            </svg>
            <ul className="health-err__mix-legend">
              <li>
                <span className="health-err__dot health-err__dot--ok" />
                Success
                <strong className="mono">{okRequests}</strong>
              </li>
              <li>
                <span className="health-err__dot health-err__dot--bad" />
                Errors
                <strong className="mono">{errorRequests}</strong>
              </li>
            </ul>
          </div>
        </article>

        <article className="health-err__panel health-err__panel--wide">
          <header className="health-err__panel-head">
            <h4>Error rate timeline</h4>
            <span>{samples.length} samples</span>
          </header>
          <Sparkline
            samples={samples}
            getValue={(s) => s.errorRatePercent}
            color="#c62828"
            floorMax={5}
            height={88}
            live={live}
          />
          <div className="health-err__timeline-foot">
            <span>0%</span>
            <span>live threshold 5%</span>
            <span>{formatNum(Math.max(5, ...samples.map((s) => Number(s.errorRatePercent) || 0)), 0)}%</span>
          </div>
        </article>
      </div>

      <div className="health-err__rank-head">
        <div>
          <h4 className="health-stack__title">Errors by endpoint</h4>
          <p className="health-metric__sub">Hover a bar to inspect impact</p>
        </div>
        <div className="health-tx__sort-pills" role="group" aria-label="Sort errors">
          <button
            type="button"
            className={sort === 'count' ? 'health-tx__pill health-tx__pill--on' : 'health-tx__pill'}
            onClick={() => setSort('count')}
          >
            By count
          </button>
          <button
            type="button"
            className={sort === 'rate' ? 'health-tx__pill health-tx__pill--on' : 'health-tx__pill'}
            onClick={() => setSort('rate')}
          >
            By rate
          </button>
        </div>
      </div>

      {!erroring.length ? (
        <div className="health-empty health-err__empty">
          <p>{totalRequests > 0 ? 'No erroring endpoints' : 'No request data yet'}</p>
          <span>
            {totalRequests > 0
              ? 'App URIs look clean — no 4xx/5xx responses in the current window.'
              : 'Generate traffic on app APIs (not /actuator) to populate error charts.'}
          </span>
        </div>
      ) : (
        <>
          <div className="health-err__rank-grid">
            <ul className="health-err__rank">
              {erroring.slice(0, 8).map((tx, index) => {
                const value = sort === 'rate' ? Number(tx.errorRatePercent) || 0 : Number(tx.errorCount) || 0;
                const max = sort === 'rate' ? maxErrRate : maxErrCount;
                const width = Math.max(6, (value / max) * 100);
                const color = ERR_PALETTE[index % ERR_PALETTE.length];
                return (
                  <li key={`err-rank-${tx.uri}`}>
                    <button
                      type="button"
                      className={
                        focus === index
                          ? 'health-err__rank-row health-err__rank-row--on'
                          : 'health-err__rank-row'
                      }
                      onMouseEnter={() => setFocus(index)}
                      onClick={() => setFocus(index)}
                    >
                      <span className="health-err__rank-idx mono">{index + 1}</span>
                      <span className="health-err__rank-body">
                        <span className="health-err__rank-top">
                          <span className="mono">{shortUri(tx.uri)}</span>
                          <strong className="mono">
                            {sort === 'rate'
                              ? `${formatNum(tx.errorRatePercent, 1)}%`
                              : `${tx.errorCount} err`}
                          </strong>
                        </span>
                        <span className="health-err__rank-track">
                          <i style={{ width: `${width}%`, background: color }} />
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>

            {focused ? (
              <article
                className="health-err__focus"
                style={{ '--focus-color': ERR_PALETTE[Math.min(focus, ERR_PALETTE.length - 1) % ERR_PALETTE.length] }}
              >
                <div className="health-err__focus-top">
                  <span className="health-tx__method">{focused.method || '*'}</span>
                  <div>
                    <h4 className="mono">{focused.uri}</h4>
                    <p>
                      #{focus + 1} · {focused.errorCount} of {focused.count} calls failed
                    </p>
                  </div>
                </div>
                <div className="health-err__focus-meters">
                  <div>
                    <span>Error count</span>
                    <strong className="mono">{focused.errorCount}</strong>
                    <div className="health-tx__meter health-tx__meter--rose">
                      <i style={{ width: `${((Number(focused.errorCount) || 0) / maxErrCount) * 100}%` }} />
                    </div>
                  </div>
                  <div>
                    <span>Error rate</span>
                    <strong className="mono">{formatNum(focused.errorRatePercent, 1)}%</strong>
                    <div className="health-tx__meter health-tx__meter--rose">
                      <i style={{ width: `${((Number(focused.errorRatePercent) || 0) / maxErrRate) * 100}%` }} />
                    </div>
                  </div>
                  <div>
                    <span>Avg latency</span>
                    <strong className="mono">{formatNum(focused.avgMs, 0)} ms</strong>
                    <div className="health-tx__meter health-tx__meter--amber">
                      <i
                        style={{
                          width: `${Math.min(
                            100,
                            ((Number(focused.avgMs) || 0) /
                              Math.max(1, ...erroring.map((t) => Number(t.avgMs) || 0))) *
                              100,
                          )}%`,
                        }}
                      />
                    </div>
                  </div>
                  <div>
                    <span>Apdex</span>
                    <strong className={`mono ${apdexClass(focused.apdex)}`}>
                      {formatNum(focused.apdex, 2)}
                    </strong>
                    <div className="health-tx__meter health-tx__meter--teal">
                      <i style={{ width: `${Math.max(0, Math.min(100, (Number(focused.apdex) || 0) * 100))}%` }} />
                    </div>
                  </div>
                </div>
              </article>
            ) : null}
          </div>

          <ul className="health-err__cards">
            {erroring.map((tx, index) => {
              const color = ERR_PALETTE[index % ERR_PALETTE.length];
              const failShare = (Number(tx.count) || 0) > 0
                ? ((Number(tx.errorCount) || 0) / Number(tx.count)) * 100
                : 0;
              return (
                <li
                  key={`err-card-${tx.uri}`}
                  className={focus === index ? 'health-err__card health-err__card--on' : 'health-err__card'}
                  style={{ '--stagger': `${index * 35}ms`, '--card-accent': color }}
                  onMouseEnter={() => setFocus(index)}
                >
                  <div className="health-err__card-top">
                    <span className="health-err__rank-badge mono">{index + 1}</span>
                    <span className="health-tx__method">{tx.method || '*'}</span>
                    <span className="mono health-err__card-uri" title={tx.uri}>
                      {tx.uri}
                    </span>
                    <span className="health-err__card-rate mono">{formatNum(tx.errorRatePercent, 1)}%</span>
                  </div>
                  <div className="health-err__failbar" aria-hidden="true">
                    <i style={{ width: `${failShare}%` }} />
                  </div>
                  <div className="health-err__card-stats">
                    <span>
                      <strong className="mono">{tx.errorCount}</strong> errors
                    </span>
                    <span>
                      <strong className="mono">{tx.count}</strong> calls
                    </span>
                    <span>
                      avg <strong className="mono">{formatNum(tx.avgMs, 0)}</strong> ms
                    </span>
                    <span className={apdexClass(tx.apdex)}>
                      Apdex <strong className="mono">{formatNum(tx.apdex, 2)}</strong>
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </div>
  );
}

const LAT_PALETTE = ['#b45309', '#ea580c', '#c2410c', '#d97706', '#f59e0b', '#92400e', '#a16207', '#78350f'];

/**
 * Graphical latency board: Apdex zones, timeline, ranked slow endpoints.
 * @param {{
 *   snapshot: import('../services/healthService.js').ApmSnapshot | null,
 *   transactions: any[],
 *   samples: any[],
 *   live?: boolean,
 *   tick?: number,
 * }} props
 */
function LatencyBoard({ snapshot, transactions, samples = [], live = false, tick = 0 }) {
  const [focus, setFocus] = useState(0);
  const [sort, setSort] = useState(/** @type {'avg' | 'max'} */ ('avg'));

  const threshold =
    Number(snapshot?.latency?.apdexThresholdMs ?? snapshot?.apdex?.thresholdMs) || 500;
  const tolerate = threshold * 4;

  const ranked = useMemo(() => {
    const list = [...transactions];
    list.sort((a, b) =>
      sort === 'max'
        ? (b.maxMs ?? 0) - (a.maxMs ?? 0)
        : (b.avgMs ?? 0) - (a.avgMs ?? 0),
    );
    return list;
  }, [transactions, sort]);

  useEffect(() => {
    setFocus(0);
  }, [sort, ranked.length]);

  const avgMs = Number(snapshot?.latency?.avgMs ?? snapshot?.requests?.avgResponseTimeMs) || 0;
  const maxMs =
    Number(snapshot?.latency?.maxMs) ||
    Math.max(0, ...transactions.map((t) => Number(t.maxMs) || 0));
  const apdex = Number(snapshot?.apdex?.score);
  const apdexScore = Number.isFinite(apdex) ? apdex : 0;
  const apdexRating = snapshot?.apdex?.rating || '—';

  const buckets = useMemo(() => {
    let satisfied = 0;
    let tolerating = 0;
    let frustrated = 0;
    for (const tx of transactions) {
      const avg = Number(tx.avgMs) || 0;
      if (avg <= threshold) satisfied += 1;
      else if (avg <= tolerate) tolerating += 1;
      else frustrated += 1;
    }
    return { satisfied, tolerating, frustrated };
  }, [transactions, threshold, tolerate]);

  const maxAvg = Math.max(1, ...ranked.map((t) => Number(t.avgMs) || 0));
  const maxMax = Math.max(1, ...ranked.map((t) => Number(t.maxMs) || 0));
  const focused = ranked[Math.min(focus, Math.max(0, ranked.length - 1))] || null;

  const latencyTone =
    avgMs > tolerate ? 'critical' : avgMs > threshold ? 'warn' : avgMs > 0 ? 'ok' : 'idle';
  const apdexTone =
    apdexScore >= 0.94 ? 'ok' : apdexScore >= 0.85 ? 'good' : apdexScore >= 0.7 ? 'warn' : 'critical';

  const gaugeMax = Math.max(tolerate * 1.15, maxMs, avgMs * 1.2, 50);
  const avgAngle = Math.max(0, Math.min(1, avgMs / gaugeMax)) * 270;
  const threshAngle = Math.max(0, Math.min(1, threshold / gaugeMax)) * 270;
  const tolAngle = Math.max(0, Math.min(1, tolerate / gaugeMax)) * 270;

  const polar = (deg, r) => {
    const rad = ((deg - 225) * Math.PI) / 180;
    return [90 + r * Math.cos(rad), 90 + r * Math.sin(rad)];
  };

  const arc = (start, sweep, r0, r1, largeHint) => {
    if (sweep <= 0.05) return '';
    const end = start + Math.min(269.9, sweep);
    const large = largeHint ?? (sweep > 180 ? 1 : 0);
    const [x0, y0] = polar(start, r1);
    const [x1, y1] = polar(end, r1);
    const [x2, y2] = polar(end, r0);
    const [x3, y3] = polar(start, r0);
    return `M ${x0} ${y0} A ${r1} ${r1} 0 ${large} 1 ${x1} ${y1} L ${x2} ${y2} A ${r0} ${r0} 0 ${large} 0 ${x3} ${y3} Z`;
  };

  const bucketTotal = Math.max(1, buckets.satisfied + buckets.tolerating + buckets.frustrated);
  const satSweep = (buckets.satisfied / bucketTotal) * 360;
  const tolSweep = (buckets.tolerating / bucketTotal) * 360;
  const fruSweep = (buckets.frustrated / bucketTotal) * 360;

  const needle = polar(avgAngle, 52);

  return (
    <div className={`health-lat health-lat--${latencyTone}`}>
      <div className="health-lat__toolbar">
        <div>
          <h3 className="health-stack__title">Latency intelligence</h3>
          <p className="health-metric__sub">
            Response time vs Apdex T={formatNum(threshold, 0)} ms · tolerate ≤ {formatNum(tolerate, 0)}{' '}
            ms
          </p>
        </div>
        <span className={live ? 'health-live health-live--on' : 'health-live health-live--off'}>
          <span className="health-live__dot" aria-hidden="true" />
          {live ? 'Live' : 'Waiting…'}
        </span>
      </div>

      <div className="health-lat__kpis">
        <div className={`health-lat__kpi health-lat__kpi--avg health-lat__kpi--${latencyTone}`}>
          <span>Avg latency</span>
          <strong className="mono" key={`avg-${tick}`}>
            {formatNum(avgMs, 0)}
            <small>ms</small>
          </strong>
          <em>
            {latencyTone === 'critical'
              ? 'Above 4T'
              : latencyTone === 'warn'
                ? 'Above T'
                : latencyTone === 'ok'
                  ? 'Within T'
                  : 'No data'}
          </em>
        </div>
        <div className="health-lat__kpi">
          <span>Max latency</span>
          <strong className="mono">{formatNum(maxMs, 0)}<small>ms</small></strong>
          <em>peak across URIs</em>
        </div>
        <div className={`health-lat__kpi health-lat__kpi--apdex health-lat__kpi--${apdexTone}`}>
          <span>Apdex</span>
          <strong className={`mono ${apdexClass(apdexScore)}`}>{formatNum(apdexScore, 2)}</strong>
          <em>{apdexRating}</em>
        </div>
        <div className="health-lat__kpi">
          <span>Slow endpoints</span>
          <strong className="mono">{buckets.frustrated + buckets.tolerating}</strong>
          <em>
            {buckets.satisfied} fast · {buckets.tolerating} ok · {buckets.frustrated} slow
          </em>
        </div>
      </div>

      <div className="health-lat__visuals">
        <article className="health-lat__panel">
          <header className="health-lat__panel-head">
            <h4>Response gauge</h4>
            <span>avg vs T / 4T</span>
          </header>
          <div className="health-lat__gauge-wrap">
            <svg className="health-lat__gauge" viewBox="0 0 180 140" aria-hidden="true">
              <path d={arc(0, threshAngle, 48, 68)} fill="#16a34a" opacity="0.35" />
              <path
                d={arc(threshAngle, Math.max(0, tolAngle - threshAngle), 48, 68)}
                fill="#d97706"
                opacity="0.4"
              />
              <path
                d={arc(tolAngle, Math.max(0, 270 - tolAngle), 48, 68)}
                fill="#c62828"
                opacity="0.35"
              />
              <path d={arc(0, 270, 48, 68, 1)} fill="none" stroke="#e8edf3" strokeWidth="1" />
              <line
                x1="90"
                y1="90"
                x2={needle[0]}
                y2={needle[1]}
                stroke="#78350f"
                strokeWidth="2.5"
                strokeLinecap="round"
                className="health-lat__needle"
              />
              <circle cx="90" cy="90" r="5" fill="#78350f" />
              <text x="90" y="118" textAnchor="middle" className="health-lat__gauge-value">
                {formatNum(avgMs, 0)} ms
              </text>
            </svg>
            <div className="health-lat__zones">
              <div>
                <span className="health-lat__zone-dot health-lat__zone-dot--ok" />
                Satisfied ≤ T
                <strong className="mono">{formatNum(threshold, 0)} ms</strong>
              </div>
              <div>
                <span className="health-lat__zone-dot health-lat__zone-dot--warn" />
                Tolerating ≤ 4T
                <strong className="mono">{formatNum(tolerate, 0)} ms</strong>
              </div>
              <div>
                <span className="health-lat__zone-dot health-lat__zone-dot--bad" />
                Frustrated &gt; 4T
                <strong className="mono">{formatNum(maxMs, 0)} ms max</strong>
              </div>
            </div>
          </div>
        </article>

        <article className="health-lat__panel">
          <header className="health-lat__panel-head">
            <h4>Apdex zones</h4>
            <span>endpoints by avg</span>
          </header>
          <div className="health-lat__mix">
            <svg className="health-lat__mix-donut" viewBox="0 0 160 160" aria-hidden="true">
              <circle cx="80" cy="80" r="58" fill="none" stroke="#e8edf3" strokeWidth="26" />
              {transactions.length ? (
                <>
                  <path d={donutArcPath(80, 80, 0, satSweep, 45, 71, -90)} fill="#16a34a" />
                  <path d={donutArcPath(80, 80, satSweep, tolSweep, 45, 71, -90)} fill="#d97706" />
                  <path d={donutArcPath(80, 80, satSweep + tolSweep, fruSweep, 45, 71, -90)} fill="#c62828" />
                </>
              ) : null}
              <circle cx="80" cy="80" r="36" fill="#fff" />
              <text x="80" y="78" textAnchor="middle" className="health-lat__mix-value">
                {formatNum(apdexScore, 2)}
              </text>
              <text x="80" y="94" textAnchor="middle" className="health-lat__mix-label">
                apdex
              </text>
            </svg>
            <ul className="health-lat__mix-legend">
              <li>
                <span className="health-lat__zone-dot health-lat__zone-dot--ok" />
                Fast ≤ T
                <strong className="mono">{buckets.satisfied}</strong>
              </li>
              <li>
                <span className="health-lat__zone-dot health-lat__zone-dot--warn" />
                OK ≤ 4T
                <strong className="mono">{buckets.tolerating}</strong>
              </li>
              <li>
                <span className="health-lat__zone-dot health-lat__zone-dot--bad" />
                Slow &gt; 4T
                <strong className="mono">{buckets.frustrated}</strong>
              </li>
            </ul>
          </div>
        </article>

        <article className="health-lat__panel health-lat__panel--wide">
          <header className="health-lat__panel-head">
            <h4>Latency timeline</h4>
            <span>{samples.length} samples</span>
          </header>
          <Sparkline
            samples={samples}
            getValue={(s) => s.avgLatencyMs ?? 0}
            color="#b45309"
            floorMax={Math.max(50, avgMs || 50)}
            height={88}
            live={live}
          />
          <div className="health-lat__timeline-foot">
            <span>0 ms</span>
            <span>T = {formatNum(threshold, 0)} ms</span>
            <span>
              {formatNum(
                Math.max(50, ...samples.map((s) => Number(s.avgLatencyMs) || 0), avgMs),
                0,
              )}{' '}
              ms
            </span>
          </div>
        </article>
      </div>

      <div className="health-lat__rank-head">
        <div>
          <h4 className="health-stack__title">Slowest endpoints</h4>
          <p className="health-metric__sub">Hover a bar to inspect response times</p>
        </div>
        <div className="health-tx__sort-pills" role="group" aria-label="Sort latency">
          <button
            type="button"
            className={sort === 'avg' ? 'health-tx__pill health-tx__pill--on' : 'health-tx__pill'}
            onClick={() => setSort('avg')}
          >
            By avg
          </button>
          <button
            type="button"
            className={sort === 'max' ? 'health-tx__pill health-tx__pill--on' : 'health-tx__pill'}
            onClick={() => setSort('max')}
          >
            By max
          </button>
        </div>
      </div>

      {!ranked.length ? (
        <div className="health-empty health-lat__empty">
          <p>No latency data yet</p>
          <span>Generate traffic on app APIs (not /actuator) to populate response-time charts.</span>
        </div>
      ) : (
        <>
          <div className="health-lat__rank-grid">
            <ul className="health-lat__rank">
              {ranked.slice(0, 8).map((tx, index) => {
                const value = sort === 'max' ? Number(tx.maxMs) || 0 : Number(tx.avgMs) || 0;
                const max = sort === 'max' ? maxMax : maxAvg;
                const width = Math.max(6, (value / max) * 100);
                const color = LAT_PALETTE[index % LAT_PALETTE.length];
                const zone =
                  (Number(tx.avgMs) || 0) > tolerate
                    ? 'frustrated'
                    : (Number(tx.avgMs) || 0) > threshold
                      ? 'tolerating'
                      : 'satisfied';
                return (
                  <li key={`lat-rank-${tx.uri}`}>
                    <button
                      type="button"
                      className={
                        focus === index
                          ? 'health-lat__rank-row health-lat__rank-row--on'
                          : 'health-lat__rank-row'
                      }
                      onMouseEnter={() => setFocus(index)}
                      onClick={() => setFocus(index)}
                    >
                      <span className="health-lat__rank-idx mono">{index + 1}</span>
                      <span className="health-lat__rank-body">
                        <span className="health-lat__rank-top">
                          <span className="mono">{shortUri(tx.uri)}</span>
                          <strong className="mono">
                            {formatNum(value, 0)}
                            <small>ms</small>
                          </strong>
                        </span>
                        <span className="health-lat__rank-track">
                          <i style={{ width: `${width}%`, background: color }} />
                        </span>
                        <span className={`health-lat__zone-tag health-lat__zone-tag--${zone}`}>
                          {zone}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>

            {focused ? (
              <article
                className="health-lat__focus"
                style={{
                  '--focus-color': LAT_PALETTE[Math.min(focus, LAT_PALETTE.length - 1) % LAT_PALETTE.length],
                }}
              >
                <div className="health-lat__focus-top">
                  <span className="health-tx__method">{focused.method || '*'}</span>
                  <div>
                    <h4 className="mono">{focused.uri}</h4>
                    <p>
                      #{focus + 1} · {focused.count} calls · Apdex {formatNum(focused.apdex, 2)}
                    </p>
                  </div>
                </div>
                <div className="health-lat__focus-meters">
                  <div>
                    <span>Avg</span>
                    <strong className="mono">{formatNum(focused.avgMs, 1)} ms</strong>
                    <div className="health-tx__meter health-tx__meter--amber">
                      <i style={{ width: `${((Number(focused.avgMs) || 0) / maxAvg) * 100}%` }} />
                    </div>
                  </div>
                  <div>
                    <span>Max</span>
                    <strong className="mono">{formatNum(focused.maxMs, 1)} ms</strong>
                    <div className="health-tx__meter health-tx__meter--rose">
                      <i style={{ width: `${((Number(focused.maxMs) || 0) / maxMax) * 100}%` }} />
                    </div>
                  </div>
                  <div>
                    <span>vs threshold</span>
                    <strong className="mono">
                      {threshold > 0
                        ? `${formatNum(((Number(focused.avgMs) || 0) / threshold) * 100, 0)}% of T`
                        : '—'}
                    </strong>
                    <div className="health-tx__meter health-tx__meter--amber">
                      <i
                        style={{
                          width: `${Math.min(
                            100,
                            ((Number(focused.avgMs) || 0) / Math.max(threshold, 1)) * 100,
                          )}%`,
                        }}
                      />
                    </div>
                  </div>
                  <div>
                    <span>Apdex</span>
                    <strong className={`mono ${apdexClass(focused.apdex)}`}>
                      {formatNum(focused.apdex, 2)}
                    </strong>
                    <div className="health-tx__meter health-tx__meter--teal">
                      <i
                        style={{
                          width: `${Math.max(0, Math.min(100, (Number(focused.apdex) || 0) * 100))}%`,
                        }}
                      />
                    </div>
                  </div>
                </div>
              </article>
            ) : null}
          </div>

          <ul className="health-lat__cards">
            {ranked.map((tx, index) => {
              const color = LAT_PALETTE[index % LAT_PALETTE.length];
              const zone =
                (Number(tx.avgMs) || 0) > tolerate
                  ? 'frustrated'
                  : (Number(tx.avgMs) || 0) > threshold
                    ? 'tolerating'
                    : 'satisfied';
              const vsT =
                threshold > 0 ? Math.min(100, ((Number(tx.avgMs) || 0) / threshold) * 100) : 0;
              return (
                <li
                  key={`lat-card-${tx.uri}`}
                  className={
                    focus === index ? 'health-lat__card health-lat__card--on' : 'health-lat__card'
                  }
                  style={{ '--stagger': `${index * 35}ms`, '--card-accent': color }}
                  onMouseEnter={() => setFocus(index)}
                >
                  <div className="health-lat__card-top">
                    <span className="health-lat__rank-badge mono">{index + 1}</span>
                    <span className="health-tx__method">{tx.method || '*'}</span>
                    <span className="mono health-lat__card-uri" title={tx.uri}>
                      {tx.uri}
                    </span>
                    <span className={`health-lat__zone-tag health-lat__zone-tag--${zone}`}>
                      {zone}
                    </span>
                  </div>
                  <div className="health-lat__failbar" aria-hidden="true" title="% of Apdex T">
                    <i style={{ width: `${vsT}%` }} />
                  </div>
                  <div className="health-lat__card-stats">
                    <span>
                      avg <strong className="mono">{formatNum(tx.avgMs, 0)}</strong> ms
                    </span>
                    <span>
                      max <strong className="mono">{formatNum(tx.maxMs, 0)}</strong> ms
                    </span>
                    <span>
                      <strong className="mono">{tx.count}</strong> calls
                    </span>
                    <span className={apdexClass(tx.apdex)}>
                      Apdex <strong className="mono">{formatNum(tx.apdex, 2)}</strong>
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </div>
  );
}

/**
 * Graphical live metrics command center.
 * @param {{
 *   snapshot: import('../services/healthService.js').ApmSnapshot | null,
 *   samples: any[],
 *   transactions: any[],
 *   activeAlertCount?: number,
 *   activeAlerts?: any[],
 *   live?: boolean,
 *   metricsLive?: boolean,
 *   alertsLive?: boolean,
 *   paused?: boolean,
 *   tick?: number,
 *   title?: string,
 *   subtitle?: string,
 * }} props
 */
function MetricsLiveBoard({
  snapshot,
  samples = [],
  transactions = [],
  activeAlertCount = 0,
  activeAlerts = [],
  live = false,
  metricsLive = false,
  alertsLive = false,
  paused = false,
  tick = 0,
  title = 'Overview',
  subtitle = 'Command center · updates about every second',
}) {
  const streaming = !paused && (live || metricsLive || alertsLive);
  const rpm = Number(snapshot?.requests?.requestsPerMinute) || 0;
  const totalReq = Number(snapshot?.requests?.totalRequests) || 0;
  const reqDelta = Number(snapshot?.requests?.requestsDelta) || 0;
  const errorRate = Number(snapshot?.requests?.errorRatePercent) || 0;
  const errorCount = Number(snapshot?.requests?.errorRequests) || 0;
  const errDelta = Number(snapshot?.requests?.errorsDelta) || 0;
  const avgMs = Number(snapshot?.latency?.avgMs ?? snapshot?.requests?.avgResponseTimeMs) || 0;
  const maxMs = Number(snapshot?.latency?.maxMs) || 0;
  const threshold = Number(snapshot?.latency?.apdexThresholdMs ?? snapshot?.apdex?.thresholdMs) || 500;
  const apdex = Number(snapshot?.apdex?.score);
  const apdexScore = Number.isFinite(apdex) ? apdex : 0;
  const heapPct = Number(snapshot?.heap?.usedPercent) || 0;
  const cpuPct = Math.max(0, Number(snapshot?.load?.processCpuLoad) || 0);
  const sysCpu = Number(snapshot?.load?.systemCpuLoad);
  const threadsLive = Number(snapshot?.threads?.live) || 0;
  const threadsRunnable = Number(snapshot?.threads?.runnable) || 0;
  const threadsBlocked = Number(snapshot?.threads?.blocked) || 0;
  const threadsWaiting = Number(snapshot?.threads?.waiting) || 0;
  const threadsPeak = Number(snapshot?.threads?.peak) || threadsLive;
  const healthStatus = snapshot?.health?.status ?? '—';

  const rpmTrend = trendFromSamples(samples, (s) => s.requestsPerMinute);
  const errTrend = trendFromSamples(samples, (s) => s.errorRatePercent);
  const latTrend = trendFromSamples(samples, (s) => s.avgLatencyMs ?? 0);
  const apxTrend = trendFromSamples(samples, (s) => (s.apdex ?? 0) * 100);

  const rpmFloor = Math.max(10, ...samples.map((s) => Number(s.requestsPerMinute) || 0), rpm);
  const topTx = transactions.slice(0, 4);

  const toneForError = errorRate >= 5 ? 'danger' : errorRate > 0 ? 'warn' : 'ok';
  const toneForHeap = heapPct >= 85 ? 'danger' : heapPct >= 70 ? 'warn' : 'ok';
  const toneForCpu = cpuPct >= 85 ? 'danger' : cpuPct >= 70 ? 'warn' : 'ok';
  const toneForApdex = apdexScore < 0.7 ? 'danger' : apdexScore < 0.85 ? 'warn' : 'ok';

  return (
    <div className={`health-ml${streaming ? ' health-ml--live' : ''}`}>
      <div className="health-ml__toolbar">
        <div>
          <h3 className="health-stack__title">{title}</h3>
          <p className="health-metric__sub">
            {subtitle} · {formatRelative(snapshot?.timestamp)}
          </p>
        </div>
        <div className="health-ml__toolbar-right">
          <span className="health-ml__tick mono" key={tick}>
            #{tick}
          </span>
          <span className={streaming ? 'health-live health-live--on' : 'health-live health-live--off'}>
            <span className="health-live__dot" aria-hidden="true" />
            {paused ? 'Paused' : streaming ? 'Streaming' : 'Waiting…'}
          </span>
        </div>
      </div>

      <OverviewRealtimeChart samples={samples} live={streaming} tick={tick} />

      <div className="health-ml__pulses">
        <article className={`health-ml__pulse health-ml__pulse--blue${rpm > 0 ? '' : ' health-ml__pulse--idle'}`}>
          <div className="health-ml__pulse-top">
            <span>Throughput</span>
            <span className={`health-signal__trend health-signal__trend--${rpmTrend}`}>
              {rpmTrend === 'up' ? '▲' : rpmTrend === 'down' ? '▼' : '●'}
            </span>
          </div>
          <div className="health-ml__pulse-body">
            <RingMeter
              value={rpm}
              max={rpmFloor}
              color="#2563eb"
              label="rpm"
              display={formatNum(rpm, 1)}
              flashKey={tick}
            />
            <div className="health-ml__pulse-meta">
              <strong className="mono" key={`rpm-${tick}`}>
                {formatNum(rpm, 1)}
                <small> rpm</small>
              </strong>
              <em>
                {rpm > 0
                  ? `Δ ${reqDelta} · ${totalReq} total`
                  : totalReq > 0
                    ? `Idle · ${totalReq} total`
                    : 'Waiting for traffic'}
              </em>
              <Sparkline
                samples={samples}
                getValue={(s) => s.requestsPerMinute}
                color="#2563eb"
                floorMax={10}
                height={36}
                live
              />
            </div>
          </div>
        </article>

        <article className={`health-ml__pulse health-ml__pulse--rose health-ml__pulse--${toneForError}`}>
          <div className="health-ml__pulse-top">
            <span>Error rate</span>
            <span className={`health-signal__trend health-signal__trend--${errTrend}`}>
              {errTrend === 'up' ? '▲' : errTrend === 'down' ? '▼' : '●'}
            </span>
          </div>
          <div className="health-ml__pulse-body">
            <RingMeter
              value={errorRate}
              max={Math.max(5, errorRate)}
              color="#c62828"
              label="%"
              display={formatNum(errorRate, 1)}
              flashKey={tick}
            />
            <div className="health-ml__pulse-meta">
              <strong className="mono" key={`err-${tick}`}>
                {formatNum(errorRate, 1)}
                <small>%</small>
              </strong>
              <em>
                {errorCount} errors · Δ {errDelta}
              </em>
              <Sparkline
                samples={samples}
                getValue={(s) => s.errorRatePercent}
                color="#c62828"
                floorMax={5}
                height={36}
                live
              />
            </div>
          </div>
        </article>

        <article className="health-ml__pulse health-ml__pulse--amber">
          <div className="health-ml__pulse-top">
            <span>Latency</span>
            <span className={`health-signal__trend health-signal__trend--${latTrend}`}>
              {latTrend === 'up' ? '▲' : latTrend === 'down' ? '▼' : '●'}
            </span>
          </div>
          <div className="health-ml__pulse-body">
            <RingMeter
              value={avgMs}
              max={Math.max(threshold * 4, avgMs, 50)}
              color="#b45309"
              label="ms"
              display={formatNum(avgMs, 0)}
              flashKey={tick}
            />
            <div className="health-ml__pulse-meta">
              <strong className="mono" key={`lat-${tick}`}>
                {formatNum(avgMs, 0)}
                <small> ms</small>
              </strong>
              <em>
                max {formatNum(maxMs, 0)} · T={formatNum(threshold, 0)}
              </em>
              <Sparkline
                samples={samples}
                getValue={(s) => s.avgLatencyMs ?? 0}
                color="#b45309"
                floorMax={50}
                height={36}
                live
              />
            </div>
          </div>
        </article>

        <article className={`health-ml__pulse health-ml__pulse--teal health-ml__pulse--${toneForApdex}`}>
          <div className="health-ml__pulse-top">
            <span>Apdex</span>
            <span className={`health-signal__trend health-signal__trend--${apxTrend}`}>
              {apxTrend === 'up' ? '▲' : apxTrend === 'down' ? '▼' : '●'}
            </span>
          </div>
          <div className="health-ml__pulse-body">
            <RingMeter
              value={apdexScore * 100}
              max={100}
              color="#0f766e"
              label="score"
              display={formatNum(apdexScore, 2)}
              flashKey={tick}
            />
            <div className="health-ml__pulse-meta">
              <strong className={`mono ${apdexClass(apdexScore)}`} key={`apx-${tick}`}>
                {formatNum(apdexScore, 2)}
              </strong>
              <em>{snapshot?.apdex?.rating ?? '—'}</em>
              <Sparkline
                samples={samples}
                getValue={(s) => (s.apdex ?? 0) * 100}
                color="#0f766e"
                floorMax={100}
                height={36}
                live
              />
            </div>
          </div>
        </article>
      </div>

      <div className="health-ml__runtime">
        <article className={`health-ml__res health-ml__res--${toneForHeap}`}>
          <header className="health-ml__res-head">
            <h4>Heap</h4>
            <span>{formatBytes(snapshot?.heap?.usedBytes)} / {formatBytes(snapshot?.heap?.maxBytes)}</span>
          </header>
          <div className="health-ml__res-body">
            <RingMeter
              value={heapPct}
              max={100}
              color={toneForHeap === 'danger' ? '#c62828' : toneForHeap === 'warn' ? '#b45309' : '#0f766e'}
              label="used"
              display={`${formatNum(heapPct, 0)}%`}
              size={100}
            />
            <div className="health-ml__res-side">
              <div className="health-bar" aria-hidden="true">
                <div className="health-bar__fill" style={{ width: `${Math.min(100, heapPct)}%` }} />
              </div>
              <Sparkline samples={samples} getValue={(s) => s.heapUsedPercent} color="#0f766e" height={40} live />
              <em>
                {toneForHeap === 'danger'
                  ? 'Critical pressure'
                  : toneForHeap === 'warn'
                    ? 'Elevated usage'
                    : 'Healthy headroom'}
              </em>
            </div>
          </div>
        </article>

        <article className={`health-ml__res health-ml__res--${toneForCpu}`}>
          <header className="health-ml__res-head">
            <h4>CPU</h4>
            <span>
              System {formatCpu(sysCpu)} · {snapshot?.load?.availableProcessors ?? '—'} cores
            </span>
          </header>
          <div className="health-ml__res-body">
            <RingMeter
              value={cpuPct}
              max={100}
              color={toneForCpu === 'danger' ? '#c62828' : toneForCpu === 'warn' ? '#b45309' : '#2563eb'}
              label="proc"
              display={formatCpu(cpuPct)}
              size={100}
            />
            <div className="health-ml__res-side">
              <div className="health-bar health-bar--blue" aria-hidden="true">
                <div className="health-bar__fill" style={{ width: `${Math.min(100, cpuPct)}%` }} />
              </div>
              <Sparkline
                samples={samples}
                getValue={(s) => s.processCpuLoad}
                color="#2563eb"
                floorMax={100}
                height={40}
                live
              />
              <em>Process load</em>
            </div>
          </div>
        </article>

        <article className="health-ml__res health-ml__res--threads">
          <header className="health-ml__res-head">
            <h4>Threads</h4>
            <span>peak {threadsPeak || '—'}</span>
          </header>
          <div className="health-ml__threads">
            <strong className="mono health-ml__threads-live">{threadsLive || '—'}</strong>
            <span className="health-ml__threads-label">live</span>
            <ul className="health-ml__thread-bars">
              <li>
                <span>Runnable</span>
                <div className="health-ml__tbar">
                  <i
                    style={{
                      width: `${threadsLive ? Math.min(100, (threadsRunnable / threadsLive) * 100) : 0}%`,
                      background: '#16a34a',
                    }}
                  />
                </div>
                <strong className="mono">{threadsRunnable || 0}</strong>
              </li>
              <li>
                <span>Blocked</span>
                <div className="health-ml__tbar">
                  <i
                    style={{
                      width: `${threadsLive ? Math.min(100, (threadsBlocked / threadsLive) * 100) : 0}%`,
                      background: '#c62828',
                    }}
                  />
                </div>
                <strong className="mono">{threadsBlocked || 0}</strong>
              </li>
              <li>
                <span>Waiting</span>
                <div className="health-ml__tbar">
                  <i
                    style={{
                      width: `${threadsLive ? Math.min(100, (threadsWaiting / threadsLive) * 100) : 0}%`,
                      background: '#b45309',
                    }}
                  />
                </div>
                <strong className="mono">{threadsWaiting || 0}</strong>
              </li>
            </ul>
          </div>
        </article>

        <article
          className={
            activeAlertCount > 0
              ? 'health-ml__res health-ml__res--alerts health-ml__res--danger'
              : 'health-ml__res health-ml__res--alerts health-ml__res--ok'
          }
        >
          <header className="health-ml__res-head">
            <h4>Alerts & health</h4>
            <span className={`health-status ${statusClass(healthStatus)}`}>{healthStatus}</span>
          </header>
          <div className="health-ml__alerts">
            <strong className="mono" key={`al-${tick}`}>
              {activeAlertCount}
            </strong>
            <span>{activeAlertCount === 1 ? 'active condition' : 'active conditions'}</span>
            <p>
              {activeAlertCount > 0
                ? activeAlerts[0]?.message || `${activeAlertCount} condition(s) firing`
                : streaming
                  ? 'All quiet — no open conditions'
                  : 'Connecting alert feed…'}
            </p>
            <div className="health-ml__probe-row">
              <ProbeBadge label="Live" status={snapshot?.probes?.liveness} />
              <ProbeBadge label="Ready" status={snapshot?.probes?.readiness} />
            </div>
          </div>
        </article>
      </div>

      <div className="health-ml__top">
        <div className="health-ml__top-head">
          <h4 className="health-stack__title">Hottest endpoints</h4>
          <p className="health-metric__sub">By call volume right now</p>
        </div>
        {!topTx.length ? (
          <div className="health-empty health-ml__empty">
            <p>No transaction samples yet</p>
            <span>Hit app APIs (not /actuator) to populate live endpoint cards.</span>
          </div>
        ) : (
          <ul className="health-ml__top-list">
            {topTx.map((tx, index) => {
              const maxCount = Math.max(1, Number(topTx[0]?.count) || 1);
              return (
                <li key={`ml-tx-${tx.uri}`} style={{ '--stagger': `${index * 40}ms` }}>
                  <span className="health-ml__top-rank mono">{index + 1}</span>
                  <div className="health-ml__top-body">
                    <div className="health-ml__top-row">
                      <span className="health-tx__method">{tx.method || '*'}</span>
                      <span className="mono" title={tx.uri}>
                        {shortUri(tx.uri)}
                      </span>
                      <strong className="mono">{tx.count}</strong>
                    </div>
                    <div className="health-ml__top-track">
                      <i style={{ width: `${((Number(tx.count) || 0) / maxCount) * 100}%` }} />
                    </div>
                    <div className="health-ml__top-stats">
                      <span>
                        {formatNum(tx.avgMs, 0)} ms
                      </span>
                      <span className={tx.errorCount > 0 ? 'health-ml__top-err' : ''}>
                        {tx.errorCount} err · {formatNum(tx.errorRatePercent, 0)}%
                      </span>
                      <span className={apdexClass(tx.apdex)}>Apdex {formatNum(tx.apdex, 2)}</span>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

/**
 * Circular progress ring for live metric values.
 * @param {{
 *   value: number,
 *   max?: number,
 *   color: string,
 *   label?: string,
 *   display?: string,
 *   size?: number,
 *   flashKey?: number,
 * }} props
 */
function RingMeter({ value, max = 100, color, label = '', display, size = 88, flashKey = 0 }) {
  const r = 32;
  const c = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(1, (Number(value) || 0) / (max || 1)));
  const dash = pct * c;
  return (
    <svg
      className="health-ml__ring"
      width={size}
      height={size}
      viewBox="0 0 88 88"
      aria-hidden="true"
    >
      <circle cx="44" cy="44" r={r} fill="none" stroke="#e8edf3" strokeWidth="8" />
      <circle
        cx="44"
        cy="44"
        r={r}
        fill="none"
        stroke={color}
        strokeWidth="8"
        strokeLinecap="round"
        strokeDasharray={`${dash} ${c}`}
        transform="rotate(-90 44 44)"
        className="health-ml__ring-arc"
      />
      <text x="44" y="42" textAnchor="middle" className="health-ml__ring-value" key={flashKey}>
        {display ?? formatNum(value, 0)}
      </text>
      {label ? (
        <text x="44" y="56" textAnchor="middle" className="health-ml__ring-label">
          {label}
        </text>
      ) : null}
    </svg>
  );
}

/**
 * @param {Object} props
 * @param {import('../services/healthService.js').ApmSnapshot | null} props.snapshot
 * @param {import('../services/healthService.js').ApmAlertEvent[]} [props.alerts]
 * @param {import('../services/healthService.js').ApmAlertEvent[]} [props.activeAlerts]
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
  activeAlerts = [],
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

  useEffect(() => {
    if (tab === 'metrics') setTab('overview');
  }, [tab]);
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

  useEffect(() => {
    if (tab === 'stack' && onRefreshStack) {
      onRefreshStack();
    }
  }, [tab, onRefreshStack]);

  const isStreaming = !paused && (live || metricsLive || alertsLive);
  const rpm = snapshot?.requests?.requestsPerMinute ?? 0;
  const errPct = snapshot?.requests?.errorRatePercent ?? 0;

  return (
    <section
      className={`alert-source-panel alert-source-panel--health health-shell health-shell--${tone}${isStreaming ? ' health-shell--live' : ''}`}
      id={NAV_SECTION.HEALTH_CHECK}
      aria-labelledby="health-check-heading"
    >
      <div className="health-live-bar" aria-live="polite">
        <div className="health-live-bar__left">
          <span
            className={
              paused
                ? 'health-live health-live--paused'
                : isStreaming
                  ? 'health-live health-live--on'
                  : 'health-live health-live--off'
            }
          >
            <span className="health-live__dot" aria-hidden="true" />
            {paused ? 'Paused' : isStreaming ? (mode === 'poll' ? 'Live · polling' : 'Live · streaming') : 'Connecting…'}
          </span>
          <span className="health-live-bar__tick mono" key={metricsTick}>
            tick #{metricsTick}
          </span>
        </div>
        <div className="health-live-bar__right">
          <span className="health-live-bar__meta">Updated {formatRelative(snapshot?.timestamp)}</span>
          {activeAlertCount > 0 ? (
            <button type="button" className="health-live-bar__alert" onClick={() => setTab('alerts')}>
              {activeAlertCount} active alert{activeAlertCount === 1 ? '' : 's'}
            </button>
          ) : (
            <span className="health-live-bar__quiet">All clear</span>
          )}
        </div>
      </div>

      <div className="health-hero">
        <div className="health-hero__copy">
          <p className="health-hero__eyebrow">Realtime APM</p>
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
          <p className="health-hero__hint">
            Live throughput, errors, latency, and Apdex for your Actuator target — updates about every second.
          </p>
        </div>

        <div className="health-hero__gauge">
          <ApdexRing score={snapshot?.apdex?.score} rating={snapshot?.apdex?.rating ?? '—'} />
        </div>

        <div className="health-hero__side">
          <div className={`health-status-orb health-status-orb--${tone}`}>
            <div className="health-status-orb__row">
              <span className={`health-status ${statusClass(status)}`}>{status}</span>
              {isStreaming ? <span className="health-status-orb__live">realtime</span> : null}
            </div>
            <span className="health-status-orb__meta">
              uptime {formatUptime(snapshot?.uptimeMs)}
              <br />
              {formatRelative(snapshot?.timestamp)} · {formatTime(snapshot?.timestamp)}
            </span>
          </div>
          <div className="health-check-panel__actions">
            {onTogglePause ? (
              <button type="button" className="btn health-btn health-btn--primary" onClick={onTogglePause}>
                {paused ? 'Resume live' : 'Pause'}
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
            <strong className="health-toast__label">New alert</strong>
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
        <SignalTile
          label="Throughput"
          value={formatNum(rpm, 1)}
          unit="rpm"
          samples={samples}
          getValue={(s) => s.requestsPerMinute}
          color="#2563eb"
          floorMax={10}
          title="App requests per minute"
        />
        <SignalTile
          label="Errors"
          value={formatNum(errPct, 1)}
          unit="%"
          samples={samples}
          getValue={(s) => s.errorRatePercent}
          color="#c62828"
          floorMax={5}
          warn={errPct >= 5}
          title="HTTP 4xx/5xx error rate"
        />
        <SignalTile
          label="Latency"
          value={formatNum(snapshot?.latency?.avgMs ?? snapshot?.requests?.avgResponseTimeMs, 0)}
          unit="ms"
          samples={samples}
          getValue={(s) => s.avgLatencyMs}
          color="#b45309"
          floorMax={50}
          title="Average response time"
        />
        <SignalTile
          label="Heap"
          value={formatNum(snapshot?.heap?.usedPercent, 0)}
          unit="%"
          samples={samples}
          getValue={(s) => s.heapUsedPercent}
          color="#0f766e"
          floorMax={20}
          warn={(snapshot?.heap?.usedPercent ?? 0) >= 85}
          title="Heap used percent"
        />
        <SignalTile
          label="CPU"
          value={
            snapshot?.load?.processCpuLoad == null || snapshot.load.processCpuLoad < 0
              ? '—'
              : formatNum(snapshot.load.processCpuLoad, 1)
          }
          unit={snapshot?.load?.processCpuLoad == null || snapshot.load.processCpuLoad < 0 ? '' : '%'}
          samples={samples}
          getValue={(s) => s.processCpuLoad}
          color="#4f46e5"
          floorMax={10}
          title="Process CPU"
        />
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
                  {item.id === 'overview' && metricsLive ? (
                    <span className="health-apm-tab__live">live</span>
                  ) : null}
                </button>
              ))}
            </div>
          </div>
        ))}
      </nav>

      <div key={tab} className="health-tab-stage">
        {tab === 'overview' ? (
          <MetricsLiveBoard
            snapshot={snapshot}
            samples={samples}
            transactions={sortedTransactions}
            activeAlertCount={activeAlertCount}
            activeAlerts={activeAlerts}
            live={live}
            metricsLive={metricsLive}
            alertsLive={alertsLive}
            paused={paused}
            tick={metricsTick}
          />
        ) : null}

        {tab === 'transactions' ? (
          <TransactionsBoard
            transactions={sortedTransactions}
            sort={txSort}
            onSort={setTxSort}
          />
        ) : null}

        {tab === 'latency' ? (
          <LatencyBoard
            snapshot={snapshot}
            transactions={sortedTransactions}
            samples={samples}
            live={isStreaming}
            tick={metricsTick}
          />
        ) : null}

        {tab === 'errors' ? (
          <ErrorsBoard
            snapshot={snapshot}
            transactions={sortedTransactions}
            samples={samples}
            live={isStreaming}
            tick={metricsTick}
          />
        ) : null}

        {tab === 'alerts' ? (
          <div className="health-alerts">
            <div className="health-metrics-live__head">
              <div>
                <h3 className="health-stack__title">Realtime alerts</h3>
                <p className="health-metric__sub">
                  Active {activeAlertCount} · history {alerts.length} · {formatRelative(snapshot?.timestamp)}
                </p>
              </div>
              <span
                className={
                  (alertsLive || live || metricsLive) && !paused
                    ? 'health-live health-live--on'
                    : 'health-live health-live--off'
                }
              >
                <span className="health-live__dot" aria-hidden="true" />
                {(alertsLive || live || metricsLive) && !paused ? 'Live feed' : 'Waiting…'}
              </span>
            </div>
            {activeAlerts.length ? (
              <ul className="health-alerts__list" style={{ marginBottom: '1rem' }}>
                {activeAlerts.map((alert, index) => (
                  <li
                    key={`active-${alert.code}-${index}`}
                    className={`health-alert ${severityClass(alert.severity)} health-alert--fresh`}
                  >
                    <div className="health-alert__top">
                      <span className="health-alert__severity">{alert.severity}</span>
                      <span className="health-alert__code mono">{alert.code}</span>
                      <span className="health-alert__fresh-tag">active</span>
                    </div>
                    <p className="health-alert__msg">{alert.message}</p>
                  </li>
                ))}
              </ul>
            ) : null}
            {!alerts.length && !activeAlerts.length ? (
              <div className="health-empty">
                <p>All quiet</p>
                <span>Alerts fire when status is DEGRADED/DOWN, heap ≥ 85%, error rate ≥ 5%, or Apdex &lt; 0.7.</span>
              </div>
            ) : alerts.length ? (
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
            ) : null}
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
