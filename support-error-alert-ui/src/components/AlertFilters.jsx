import { ALERT_SEVERITY_OPTIONS } from '../constants/alertSeverity.js';
import { ALERT_STATUS_OPTIONS } from '../constants/alertStatus.js';
import './AlertFilters.css';

const ALL = 'ALL';

/**
 * @param {Object} props
 * @param {boolean} [props.showFilterFields] - severity/status/jira selects (false = actions-only row)
 * @param {string} props.severityFilter
 * @param {string} props.statusFilter
 * @param {string} props.jiraFilter
 * @param {string[]} props.jiraOptions - distinct batch ids from loaded alerts
 * @param {function(string): void} props.onSeverityChange
 * @param {function(string): void} props.onStatusChange
 * @param {function(string): void} props.onJiraChange
 * @param {function(): void} [props.onClearFilters]
 * @param {boolean} [props.hasActiveFilters]
 * @param {function(): void} props.onClear
 * @param {function(): void} props.onRefresh
 * @param {boolean} props.loading
 * @param {boolean} props.actionsBusy
 * @param {boolean} props.canClear
 * @param {string} props.clearLabel
 */
export function AlertFilters({
  showFilterFields = true,
  severityFilter,
  statusFilter,
  jiraFilter,
  jiraOptions,
  onSeverityChange,
  onStatusChange,
  onJiraChange,
  onClearFilters,
  hasActiveFilters,
  onClear,
  onRefresh,
  loading,
  actionsBusy,
  canClear,
  clearLabel,
}) {
  const busy = loading || actionsBusy;
  const rootClass = [
    'alert-filters',
    !showFilterFields ? 'alert-filters--actions-only' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={rootClass} aria-label={showFilterFields ? `Filter ${clearLabel}` : `${clearLabel} actions`}>
      {showFilterFields ? (
        <>
          <div className="alert-filters__field">
            <label className="alert-filters__label" htmlFor="filter-severity">
              Severity
            </label>
            <select
              id="filter-severity"
              className="alert-filters__select"
              value={severityFilter}
              onChange={(e) => onSeverityChange(e.target.value)}
              disabled={loading}
            >
              <option value={ALL}>All</option>
              {ALERT_SEVERITY_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          <div className="alert-filters__field">
            <label className="alert-filters__label" htmlFor="filter-status">
              Status
            </label>
            <select
              id="filter-status"
              className="alert-filters__select"
              value={statusFilter}
              onChange={(e) => onStatusChange(e.target.value)}
              disabled={loading}
            >
              <option value={ALL}>All</option>
              {ALERT_STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          <div className="alert-filters__field">
            <label className="alert-filters__label" htmlFor="filter-jira">
              Jira
            </label>
            <select
              id="filter-jira"
              className="alert-filters__select"
              value={jiraFilter}
              onChange={(e) => onJiraChange(e.target.value)}
              disabled={loading}
            >
              <option value={ALL}>All</option>
              {jiraOptions.map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </div>
          <div className="alert-filters__trailing">
            {hasActiveFilters && onClearFilters ? (
              <button type="button" className="alert-filters__clear btn" onClick={onClearFilters}>
                Clear filters
              </button>
            ) : null}
            <div className="alert-filters__actions">
              <button type="button" className="btn btn--danger" onClick={onClear} disabled={!canClear || busy}>
                {clearLabel}
              </button>
              <button type="button" className="btn btn--primary" onClick={onRefresh} disabled={loading}>
                {loading ? 'Refreshing…' : 'Refresh'}
              </button>
            </div>
          </div>
        </>
      ) : (
        <div className="alert-filters__actions">
          <button type="button" className="btn btn--danger" onClick={onClear} disabled={!canClear || busy}>
            {clearLabel}
          </button>
          <button type="button" className="btn btn--primary" onClick={onRefresh} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      )}
    </div>
  );
}
