import { ALERT_STATUS_OPTIONS } from '../constants/alertStatus.js';
import './StatusSelect.css';

export function StatusSelect({ value, onChange, disabled }) {
  return (
    <select
      className="status-select"
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
      aria-label="Alert status"
    >
      {ALERT_STATUS_OPTIONS.map((s) => (
        <option key={s} value={s}>
          {s}
        </option>
      ))}
    </select>
  );
}
