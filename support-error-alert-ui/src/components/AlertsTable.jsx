import { useEffect, useMemo, useState } from 'react';
import { AlertFilters } from './AlertFilters.jsx';
import { AlertTableRow } from './AlertTableRow.jsx';
import { distinctJiraBatchIds, filterAlerts } from '../lib/filterAlerts.js';
import './AlertsTable.css';

const JSON_COLUMNS = [
  { key: 'details', label: 'Error details' },
  { key: 'fn', label: 'Function path' },
  { key: 'file', label: 'File path' },
  { key: 'service', label: 'Service name' },
  { key: 'jira', label: 'Jira' },
  { key: 'sev', label: 'Severity' },
  { key: 'status', label: 'Status' },
];

const EMAIL_COLUMNS = [
  { key: 'details', label: 'Inbox msg' },
  { key: 'fn', label: 'From email' },
  { key: 'file', label: 'Date received' },
  { key: 'service', label: 'Subject' },
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
 * @param {string} props.title
 * @param {string} props.emptyMessage
 * @param {string} props.clearLabel
 * @param {function(): void} props.onClear
 * @param {function(): void} props.onRefresh
 * @param {boolean} props.canClear
 * @param {boolean} [props.embedded]
 * @param {boolean} props.jiraConfigured
 * @param {string} [props.jiraSiteUrl]
 * @param {function(string, Record<string, string>): void | Promise<void>} props.onCreateJiraIssue
 */
export function AlertsTable({
  title,
  emptyMessage,
  clearLabel,
  alerts,
  loading,
  onStatusChange,
  rowActionsBusy,
  onClear,
  onRefresh,
  canClear,
  jiraConfigured,
  jiraSiteUrl,
  onCreateJiraIssue,
  embedded = false,
  variant = 'json',
}) {
  const columns = variant === 'email' ? EMAIL_COLUMNS : JSON_COLUMNS;
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
    onClear,
    onRefresh,
    loading,
    actionsBusy: rowActionsBusy,
    canClear,
    clearLabel,
  };

  const Tag = embedded ? 'div' : 'section';
  const rootClass = embedded ? 'alerts-table-wrap alerts-table-wrap--embedded' : 'alerts-table-wrap';

  return (
    <Tag className={rootClass} aria-labelledby={embedded ? undefined : 'alerts-table-heading'}>
      {!embedded ? (
        <div className="alerts-table__head">
          <h2 id="alerts-table-heading" className="alerts-table__title">
            {title}
          </h2>
        </div>
      ) : (
        <div className="alerts-table__head alerts-table__head--embedded">
          <h3 className="alerts-table__title alerts-table__title--embedded">{title} details</h3>
        </div>
      )}
      {loading && alerts.length === 0 ? (
        <div className="alerts-table__empty">Loading {title.toLowerCase()}…</div>
      ) : alerts.length === 0 ? (
        <>
          {showFilterBar ? <AlertFilters {...filterBarProps} /> : null}
          <div className="alerts-table__empty">{emptyMessage}</div>
        </>
      ) : (
        <>
          {showFilterBar ? <AlertFilters {...filterBarProps} /> : null}
          {filteredAlerts.length === 0 ? (
            <div className="alerts-table__empty alerts-table__empty--filtered">
              No rows match the selected filters.
            </div>
          ) : (
            <div className="alerts-table__scroll">
              <table className="alerts-table">
                <thead>
                  <tr>
                    {columns.map((c) => (
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
    </Tag>
  );
}
