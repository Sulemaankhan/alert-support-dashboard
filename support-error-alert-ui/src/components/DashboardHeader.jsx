import { JsonFileUpload } from './JsonFileUpload.jsx';
import './DashboardHeader.css';

export function DashboardHeader({
  loading,
  mutationBusy,
  canClearJson,
  onRefresh,
  onJsonSelected,
  onClearJson,
}) {
  const busy = loading || mutationBusy;
  return (
    <header className="dashboard-header">
      <div className="dashboard-header__intro">
        <h1 className="dashboard-header__title">Error alert dashboard</h1>
        <p className="dashboard-header__sub">
          Ingest JSON via the API; rows mirror backend{' '}
          <span className="mono">AlertRecord</span>.
        </p>
      </div>
      <div className="dashboard-header__toolbar">
        <JsonFileUpload onFileSelected={onJsonSelected} />
        <button type="button" className="btn btn--danger" onClick={onClearJson} disabled={!canClearJson || busy}>
          Clear JSON
        </button>
        <button type="button" className="btn btn--primary" onClick={onRefresh} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>
    </header>
  );
}
