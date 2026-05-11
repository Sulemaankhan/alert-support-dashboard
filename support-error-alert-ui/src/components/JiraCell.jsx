import './JiraCell.css';

/**
 * @param {Object} props
 * @param {string} [props.batchId]
 * @param {string[]} [props.issueKeys]
 */
export function JiraCell({ batchId, issueKeys }) {
  const keys = Array.isArray(issueKeys) ? issueKeys : [];
  const batch = batchId && String(batchId).trim() ? batchId : null;

  return (
    <td className="alerts-table__jira">
      <div className="jira-cell">
        <span
          className="jira-cell__batch"
          title="Each set of four error-detail rows (by arrival order) shares this Jira number"
        >
          {batch ?? '—'}
        </span>
        {keys.length > 0 ? (
          <ul className="jira-cell__issues">
            {keys.map((k) => (
              <li key={k} className="mono">
                {k}
              </li>
            ))}
          </ul>
        ) : (
          <span className="jira-cell__empty muted">No linked key</span>
        )}
      </div>
    </td>
  );
}
