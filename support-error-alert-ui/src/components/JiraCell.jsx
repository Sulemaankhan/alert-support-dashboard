import './JiraCell.css';

function browseUrl(siteUrl, issueKey) {
  const base = siteUrl && String(siteUrl).trim().replace(/\/$/, '');
  if (!base || !issueKey) return null;
  return `${base}/browse/${encodeURIComponent(issueKey)}`;
}

/**
 * @param {Object} props
 * @param {string} props.alertId
 * @param {string[]} [props.issueKeys]
 * @param {boolean} props.jiraConfigured
 * @param {string} [props.jiraSiteUrl]
 * @param {boolean} props.busy
 * @param {function(string, Record<string, string>): void | Promise<void>} props.onCreateJiraIssue
 */
export function JiraCell({
  alertId,
  issueKeys,
  jiraConfigured,
  jiraSiteUrl,
  busy,
  onCreateJiraIssue,
}) {
  const keys = Array.isArray(issueKeys) ? issueKeys.filter(Boolean) : [];
  const hasIssue = keys.length > 0;

  if (hasIssue) {
    return (
      <td className="alerts-table__jira">
        <div className="jira-cell">
          <ul className="jira-cell__issues jira-cell__issues--plain">
            {keys.map((k) => {
              const href = browseUrl(jiraSiteUrl, k);
              return (
                <li key={k} className="mono">
                  {href ? (
                    <a className="jira-cell__link" href={href} target="_blank" rel="noopener noreferrer">
                      {k}
                    </a>
                  ) : (
                    k
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      </td>
    );
  }

  return (
    <td className="alerts-table__jira">
      <div className="jira-cell jira-cell--actions">
        <button
          type="button"
          className="btn btn--primary jira-cell__create"
          disabled={busy || !jiraConfigured}
          title={
            jiraConfigured
              ? 'Create a Jira issue using this row (description from error details, priority from severity)'
              : 'Configure support.jira.* on the server to enable Jira creation'
          }
          onClick={() => onCreateJiraIssue(alertId, {})}
        >
          {busy ? 'Working…' : 'Create Jira ticket'}
        </button>
        {!jiraConfigured ? (
          <span className="jira-cell__hint muted">Jira not configured</span>
        ) : null}
      </div>
    </td>
  );
}
