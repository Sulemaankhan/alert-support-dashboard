/**
 * Distinct non-empty Jira batch ids, sorted numerically when possible.
 * @param {import('../types/alerts').Alert[]} alerts
 * @returns {string[]}
 */
export function distinctJiraBatchIds(alerts) {
  const ids = [...new Set(alerts.map((a) => a.jiraBatchId).filter(Boolean))];
  ids.sort((a, b) => {
    const na = parseInt(a, 10);
    const nb = parseInt(b, 10);
    if (!Number.isNaN(na) && !Number.isNaN(nb)) {
      return na - nb;
    }
    return String(a).localeCompare(String(b));
  });
  return ids;
}

/**
 * @param {import('../types/alerts').Alert[]} alerts
 * @param {string} severityFilter - 'ALL' or severity key
 * @param {string} statusFilter - 'ALL' or status key
 * @param {string} jiraFilter - 'ALL' or jiraBatchId
 * @returns {import('../types/alerts').Alert[]}
 */
export function filterAlerts(alerts, severityFilter, statusFilter, jiraFilter) {
  const ALL = 'ALL';
  return alerts.filter((a) => {
    const sevOk = severityFilter === ALL || a.severity === severityFilter;
    const stOk = statusFilter === ALL || a.status === statusFilter;
    const jiraOk =
      jiraFilter === ALL ||
      (a.jiraBatchId && String(a.jiraBatchId) === jiraFilter);
    return sevOk && stOk && jiraOk;
  });
}
