import { AlertsTable } from './components/AlertsTable.jsx';
import { ErrorBanner } from './components/ErrorBanner.jsx';
import { InboxLookup } from './components/InboxLookup.jsx';
import { SiteHeader } from './components/SiteHeader.jsx';
import { NAV_SECTION } from './constants/nav.js';
import { useAlertsDashboard } from './hooks/useAlertsDashboard.js';
import './styles/buttons.css';
import './App.css';

export default function App() {
  const {
    alerts,
    loading,
    error,
    mutationBusy,
    jiraConfigured,
    jiraSiteUrl,
    refresh,
    ingestFromFile,
    updateStatus,
    clearIngestedAlerts,
    createJiraIssue,
    dismissError,
  } = useAlertsDashboard();

  return (
    <>
      <SiteHeader onJsonSelected={ingestFromFile} jsonBusy={loading || mutationBusy} />
      <div className="app" id={NAV_SECTION.HOME}>
        <ErrorBanner message={error} onDismiss={dismissError} />
        <InboxLookup />
        <AlertsTable
          alerts={alerts}
          loading={loading}
          rowActionsBusy={mutationBusy}
          onStatusChange={updateStatus}
          onClearJson={clearIngestedAlerts}
          onRefresh={refresh}
          canClearJson={alerts.length > 0}
          jiraConfigured={jiraConfigured}
          jiraSiteUrl={jiraSiteUrl}
          onCreateJiraIssue={createJiraIssue}
        />
      </div>
    </>
  );
}
