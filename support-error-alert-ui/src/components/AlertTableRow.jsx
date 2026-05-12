import { useCallback, useEffect, useRef, useState } from 'react';
import { JiraCell } from './JiraCell.jsx';
import { AlertRowHoverPopover } from './AlertRowHoverPopover.jsx';
import { SeverityBadge } from './SeverityBadge.jsx';
import { StatusSelect } from './StatusSelect.jsx';

const HOVER_CLOSE_MS = 220;
const POPOVER_GAP = 6;
const ESTIMATED_POPOVER_H = 260;

function CellMono({ children }) {
  const text = children && String(children).trim() ? children : '—';
  return <td className="alerts-table__mono">{text}</td>;
}

/**
 * @param {Object} props
 * @param {import('../types/alerts').Alert} props.alert
 * @param {function(string, string): void} props.onStatusChange
 * @param {boolean} [props.busy]
 * @param {boolean} props.jiraConfigured
 * @param {string} [props.jiraSiteUrl]
 * @param {function(string, Record<string, string>): void | Promise<void>} props.onCreateJiraIssue
 */
export function AlertTableRow({ alert, onStatusChange, busy, jiraConfigured, jiraSiteUrl, onCreateJiraIssue }) {
  const trRef = useRef(/** @type {HTMLTableRowElement | null} */ (null));
  const panelRef = useRef(/** @type {HTMLDivElement | null} */ (null));
  const closeTimerRef = useRef(/** @type {number | undefined} */ (undefined));

  const [hoverOpen, setHoverOpen] = useState(false);
  /** @type {[{ top: number, left: number, width: number } | null, import('react').Dispatch<any>]} */
  const [panelPos, setPanelPos] = useState(null);

  const clearCloseTimer = useCallback(() => {
    if (closeTimerRef.current != null) {
      window.clearTimeout(closeTimerRef.current);
      closeTimerRef.current = undefined;
    }
  }, []);

  const scheduleClose = useCallback(() => {
    clearCloseTimer();
    closeTimerRef.current = window.setTimeout(() => {
      setHoverOpen(false);
      setPanelPos(null);
    }, HOVER_CLOSE_MS);
  }, [clearCloseTimer]);

  const updatePanelPosition = useCallback(() => {
    const el = trRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const minW = 280;
    const maxW = 560;
    let width = Math.min(maxW, Math.max(minW, r.width));
    let left = r.left;
    if (left + width > window.innerWidth - 8) {
      left = window.innerWidth - width - 8;
    }
    if (left < 8) left = 8;

    let top = r.bottom + POPOVER_GAP;
    if (top + ESTIMATED_POPOVER_H > window.innerHeight - 8) {
      top = r.top - ESTIMATED_POPOVER_H - POPOVER_GAP;
    }
    if (top < 8) top = 8;

    setPanelPos({ top, left, width });
  }, []);

  const handleRowEnter = useCallback(() => {
    clearCloseTimer();
    updatePanelPosition();
    setHoverOpen(true);
  }, [clearCloseTimer, updatePanelPosition]);

  const handleRowLeave = useCallback(() => {
    scheduleClose();
  }, [scheduleClose]);

  const handlePanelEnter = useCallback(() => {
    clearCloseTimer();
  }, [clearCloseTimer]);

  const handlePanelLeave = useCallback(() => {
    scheduleClose();
  }, [scheduleClose]);

  useEffect(() => {
    return () => clearCloseTimer();
  }, [clearCloseTimer]);

  useEffect(() => {
    if (!hoverOpen) return undefined;
    const onScrollOrResize = () => {
      updatePanelPosition();
    };
    window.addEventListener('scroll', onScrollOrResize, true);
    window.addEventListener('resize', onScrollOrResize);
    return () => {
      window.removeEventListener('scroll', onScrollOrResize, true);
      window.removeEventListener('resize', onScrollOrResize);
    };
  }, [hoverOpen, updatePanelPosition]);

  useEffect(() => {
    if (!hoverOpen) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') {
        clearCloseTimer();
        setHoverOpen(false);
        setPanelPos(null);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [hoverOpen, clearCloseTimer]);

  return [
    <tr
      key={alert.id}
      ref={trRef}
      className={hoverOpen ? 'alerts-table__tr--hover-active' : undefined}
      onMouseEnter={handleRowEnter}
      onMouseLeave={handleRowLeave}
    >
      <td className="alerts-table__details">{alert.errorDetails}</td>
      <CellMono>{alert.functionPath}</CellMono>
      <CellMono>{alert.filePath}</CellMono>
      <td>{alert.serviceName}</td>
      <JiraCell
        alertId={alert.id}
        issueKeys={alert.jiraIssueKeys}
        jiraConfigured={jiraConfigured}
        jiraSiteUrl={jiraSiteUrl}
        busy={Boolean(busy)}
        onCreateJiraIssue={onCreateJiraIssue}
      />
      <td>
        <SeverityBadge severity={alert.severity} />
      </td>
      <td>
        <StatusSelect
          value={alert.status}
          disabled={busy}
          onChange={(status) => onStatusChange(alert.id, status)}
        />
      </td>
    </tr>,
    <AlertRowHoverPopover
      key={`${alert.id}-hover-popover`}
      alert={alert}
      open={hoverOpen}
      panelRef={panelRef}
      position={panelPos}
      onPanelMouseEnter={handlePanelEnter}
      onPanelMouseLeave={handlePanelLeave}
    />,
  ];
}
