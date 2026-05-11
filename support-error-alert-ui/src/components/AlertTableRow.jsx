import { JiraCell } from './JiraCell.jsx';
import { SeverityBadge } from './SeverityBadge.jsx';
import { StatusSelect } from './StatusSelect.jsx';

function CellMono({ children }) {
  const text = children && String(children).trim() ? children : '—';
  return <td className="alerts-table__mono">{text}</td>;
}

/**
 * @param {Object} props
 * @param {import('../types/alerts').Alert} props.alert
 * @param {function(string, string): void} props.onStatusChange
 * @param {boolean} [props.busy]
 */
export function AlertTableRow({ alert, onStatusChange, busy }) {
  return (
    <tr>
      <td className="alerts-table__details">{alert.errorDetails}</td>
      <CellMono>{alert.functionPath}</CellMono>
      <CellMono>{alert.filePath}</CellMono>
      <td>{alert.serviceName}</td>
      <JiraCell batchId={alert.jiraBatchId} issueKeys={alert.jiraIssueKeys} />
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
    </tr>
  );
}
