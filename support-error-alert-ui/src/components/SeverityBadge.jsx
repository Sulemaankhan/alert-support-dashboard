import './SeverityBadge.css';

export function SeverityBadge({ severity }) {
  const key = severity ?? 'MEDIUM';
  return (
    <span className={`severity-badge severity-badge--${key}`} title={severity}>
      {severity}
    </span>
  );
}
