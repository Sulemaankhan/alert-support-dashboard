import { useEffect, useMemo, useState } from 'react';
import { AlertFilters } from './AlertFilters.jsx';
import { AlertTableRow } from './AlertTableRow.jsx';
import { distinctJiraBatchIds, filterAlerts } from '../lib/filterAlerts.js';
import './AlertsTable.css';

const COLUMNS = [
  { key: 'details', label: 'Error details' },
  { key: 'fn', label: 'Function path' },
  { key: 'file', label: 'File path' },
  { key: 'service', label: 'Service name' },
  { key: 'jira', label: 'Jira' },
  { key: 'sev', label: 'Severity' },
  { key: 'status', label: 'Status' },
];

const ALL = 'ALL';

/**
 * @param {Object} props
 * @param {import('../types/alerts').Alert[]} props.alerts
 * @param {boolean} props.loading
 * @param {function(string, string): void} props.onStatusChange
 * @param {boolean} [props.rowActionsBusy]
 * @param {function(): void} props.onClearJson
 * @param {function(): void} props.onRefresh
 * @param {boolean} props.canClearJson
 * @param {boolean} props.jiraConfigured
 * @param {string} [props.jiraSiteUrl]
 * @param {function(string, Record<string, string>): void | Promise<void>} props.onCreateJiraIssue
 */
export function AlertsTable({
  alerts,
  loading,
  onStatusChange,
  rowActionsBusy,
  onClearJson,
  onRefresh,
  canClearJson,
  jiraConfigured,
  jiraSiteUrl,
  onCreateJiraIssue,
}) {
  const [severityFilter, setSeverityFilter] = useState(ALL);
  const [statusFilter, setStatusFilter] = useState(ALL);
  const [jiraFilter, setJiraFilter] = useState(ALL);

  const jiraOptions = useMemo(() => distinctJiraBatchIds(alerts), [alerts]);

  useEffect(() => {
    if (jiraFilter === ALL) return;
    if (!jiraOptions.includes(jiraFilter)) {
      setJiraFilter(ALL);
    }
  }, [jiraFilter, jiraOptions]);

  const filteredAlerts = useMemo(
    () => filterAlerts(alerts, severityFilter, statusFilter, jiraFilter),
    [alerts, severityFilter, statusFilter, jiraFilter]
  );

  const hasActiveFilters =
    severityFilter !== ALL || statusFilter !== ALL || jiraFilter !== ALL;

  const clearFilters = () => {
    setSeverityFilter(ALL);
    setStatusFilter(ALL);
    setJiraFilter(ALL);
  };

  /**
   * After the first time the list finishes loading, keep the filter bar visible even when
   * `loading` is true again (e.g. poll refresh) with zero rows — otherwise Clear JSON / Refresh disappear.
   */
  const [hasCompletedInitialFetch, setHasCompletedInitialFetch] = useState(false);
  useEffect(() => {
    if (!loading) {
      setHasCompletedInitialFetch(true);
    }
  }, [loading]);

  const showFilterBar = hasCompletedInitialFetch;
  const showFilterFields = alerts.length > 0;

  const filterBarProps = {
    showFilterFields,
    severityFilter,
    statusFilter,
    jiraFilter,
    jiraOptions,
    onSeverityChange: setSeverityFilter,
    onStatusChange: setStatusFilter,
    onJiraChange: setJiraFilter,
    onClearFilters: clearFilters,
    hasActiveFilters,
    onClearJson,
    onRefresh,
    loading,
    actionsBusy: rowActionsBusy,
    canClearJson,
  };

  return (
    <div className="alerts-table-wrap">
      {loading && alerts.length === 0 ? (
        <div className="alerts-table__empty">Loading alerts…</div>
      ) : alerts.length === 0 ? (
        <>
          {showFilterBar ? <AlertFilters {...filterBarProps} /> : null}
          <div className="alerts-table__empty">
            No alerts yet. Upload a JSON file or POST to <span className="mono">/api/alerts/ingest</span>.
          </div>
        </>
      ) : (
        <>
          {showFilterBar ? <AlertFilters {...filterBarProps} /> : null}
          {filteredAlerts.length === 0 ? (
            <div className="alerts-table__empty alerts-table__empty--filtered">
              No alerts match the selected filters.
            </div>
          ) : (
            <div className="alerts-table__scroll">
              <table className="alerts-table">
                <thead>
                  <tr>
                    {COLUMNS.map((c) => (
                      <th key={c.key} scope="col">
                        {c.label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filteredAlerts.map((alert) => (
                    <AlertTableRow
                      key={alert.id}
                      alert={alert}
                      onStatusChange={onStatusChange}
                      busy={rowActionsBusy}
                      jiraConfigured={jiraConfigured}
                      jiraSiteUrl={jiraSiteUrl}
                      onCreateJiraIssue={onCreateJiraIssue}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}
