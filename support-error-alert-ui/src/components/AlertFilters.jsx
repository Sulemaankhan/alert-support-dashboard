import { ALERT_SEVERITY_OPTIONS } from '../constants/alertSeverity.js';
import { ALERT_STATUS_OPTIONS } from '../constants/alertStatus.js';
import './AlertFilters.css';

const ALL = 'ALL';

/**
 * @param {Object} props
 * @param {string} props.severityFilter
 * @param {string} props.statusFilter
 * @param {string} props.jiraFilter
 * @param {string[]} props.jiraOptions - distinct batch ids from loaded alerts
 * @param {function(string): void} props.onSeverityChange
 * @param {function(string): void} props.onStatusChange
 * @param {function(string): void} props.onJiraChange
 * @param {function(): void} [props.onClear]
 * @param {boolean} [props.hasActiveFilters]
 */
export function AlertFilters({
  severityFilter,
  statusFilter,
  jiraFilter,
  jiraOptions,
  onSeverityChange,
  onStatusChange,
  onJiraChange,
  onClear,
  hasActiveFilters,
}) {
  return (
    <div className="alert-filters" aria-label="Filter alerts">
      <div className="alert-filters__field">
        <label className="alert-filters__label" htmlFor="filter-severity">
          Severity
        </label>
        <select
          id="filter-severity"
          className="alert-filters__select"
          value={severityFilter}
          onChange={(e) => onSeverityChange(e.target.value)}
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
        >
          <option value={ALL}>All</option>
          {jiraOptions.map((id) => (
            <option key={id} value={id}>
              {id}
            </option>
          ))}
        </select>
      </div>
      {hasActiveFilters && onClear ? (
        <button type="button" className="alert-filters__clear btn" onClick={onClear}>
          Clear filters
        </button>
      ) : null}
    </div>
  );
}
