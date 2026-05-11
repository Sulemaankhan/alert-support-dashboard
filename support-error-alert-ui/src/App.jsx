import { AlertsTable } from './components/AlertsTable.jsx';
import { DashboardHeader } from './components/DashboardHeader.jsx';
import { ErrorBanner } from './components/ErrorBanner.jsx';
import { useAlertsDashboard } from './hooks/useAlertsDashboard.js';
import './styles/buttons.css';
import './App.css';

export default function App() {
  const {
    alerts,
    loading,
    error,
    mutationBusy,
    refresh,
    ingestFromFile,
    updateStatus,
    clearIngestedAlerts,
    dismissError,
  } = useAlertsDashboard();

  return (
    <div className="app">
      <DashboardHeader
        loading={loading}
        mutationBusy={mutationBusy}
        canClearJson={alerts.length > 0}
        onRefresh={refresh}
        onJsonSelected={ingestFromFile}
        onClearJson={clearIngestedAlerts}
      />
      <ErrorBanner message={error} onDismiss={dismissError} />
      <AlertsTable
        alerts={alerts}
        loading={loading}
        rowActionsBusy={mutationBusy}
        onStatusChange={updateStatus}
      />
    </div>
  );
}
