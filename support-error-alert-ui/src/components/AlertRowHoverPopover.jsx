import { createPortal } from 'react-dom';
import './AlertRowHoverPopover.css';

function formatCreated(iso) {
  if (!iso) return '—';
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
  } catch {
    return iso;
  }
}

/**
 * @param {Object} props
 * @param {import('../types/alerts').Alert} props.alert
 * @param {boolean} props.open
 * @param {import('react').RefObject<HTMLDivElement | null>} props.panelRef
 * @param {{ top: number, left: number, width: number } | null} props.position
 * @param {function(): void} props.onPanelMouseEnter
 * @param {function(): void} props.onPanelMouseLeave
 */
export function AlertRowHoverPopover({
  alert,
  open,
  panelRef,
  position,
  onPanelMouseEnter,
  onPanelMouseLeave,
}) {
  if (!open || !position) {
    return null;
  }

  const keys = Array.isArray(alert.jiraIssueKeys) ? alert.jiraIssueKeys.filter(Boolean) : [];

  return createPortal(
    <div
      ref={panelRef}
      className="alert-row-hover"
      style={{
        top: position.top,
        left: position.left,
        width: position.width,
      }}
      role="tooltip"
      onMouseEnter={onPanelMouseEnter}
      onMouseLeave={onPanelMouseLeave}
    >
      <div className="alert-row-hover__head">
        <span>Expanded row</span>
        <span className="alert-row-hover__hint">Hover to keep open</span>
      </div>
      <div className="alert-row-hover__body">
        <dl className="alert-row-hover__meta">
          <dt>Service</dt>
          <dd>{alert.serviceName || '—'}</dd>
          <dt>Status</dt>
          <dd>{alert.status}</dd>
          <dt>Severity</dt>
          <dd>{alert.severity}</dd>
          <dt>Created</dt>
          <dd>{formatCreated(alert.createdAt)}</dd>
          {keys.length > 0 ? (
            <>
              <dt>Jira</dt>
              <dd className="mono">{keys.join(', ')}</dd>
            </>
          ) : null}
          {alert.functionPath ? (
            <>
              <dt>Function</dt>
              <dd className="mono">{alert.functionPath}</dd>
            </>
          ) : null}
          {alert.filePath ? (
            <>
              <dt>File</dt>
              <dd className="mono">{alert.filePath}</dd>
            </>
          ) : null}
        </dl>
        <p className="alert-row-hover__details-label">Error details</p>
        <pre className="alert-row-hover__pre">{alert.errorDetails?.trim() || '—'}</pre>
      </div>
    </div>,
    document.body
  );
}
