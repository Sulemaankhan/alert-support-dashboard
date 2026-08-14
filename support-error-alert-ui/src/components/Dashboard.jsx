import { ALERT_SOURCE } from '../constants/alertSource.js';
import { EmailAlertPanel } from './EmailAlertPanel.jsx';
import { EmailSignInGate } from './EmailSignInGate.jsx';
import { ErrorBanner } from './ErrorBanner.jsx';
import { HealthCheckPanel } from './HealthCheckPanel.jsx';
import { JsonAlertPanel } from './JsonAlertPanel.jsx';
import { OverviewPanel } from './OverviewPanel.jsx';
import { SiteFooter } from './SiteFooter.jsx';
import { SiteHeader } from './SiteHeader.jsx';
import { useAuth } from '../hooks/useAuth.jsx';
import { useAlertsDashboard } from '../hooks/useAlertsDashboard.js';
import { useHealthCheck } from '../hooks/useHealthCheck.js';
import { useNavView } from '../hooks/useNavView.js';

export function Dashboard({ guestMode = false }) {
  const { user, signOut } = useAuth();
  const navView = useNavView();
  const {
    jsonAlerts,
    emailAlerts,
    loading,
    error,
    mutationBusy,
    jiraConfigured,
    jiraSiteUrl,
    refresh,
    ingestFromFile,
    ingestFromEmail,
    updateStatus,
    clearIngestedAlerts,
    createJiraIssue,
    dismissError,
  } = useAlertsDashboard();

  const health = useHealthCheck({ enabled: navView === 'health' });

  const busy = loading || mutationBusy;

  const sharedTableProps = {
    loading,
    onStatusChange: updateStatus,
    onRefresh: refresh,
    jiraConfigured,
    jiraSiteUrl,
    onCreateJiraIssue: createJiraIssue,
  };

  return (
    <div className="app-shell">
      <SiteHeader
        user={user}
        guestMode={guestMode}
        onSignOut={signOut}
        activeView={navView}
      />
      <div className="app">
        <ErrorBanner message={error} onDismiss={dismissError} />

        {navView === 'home' ? <OverviewPanel guestMode={guestMode} /> : null}

        {navView === 'json' ? (
          <JsonAlertPanel
            alerts={jsonAlerts}
            onJsonSelected={ingestFromFile}
            busy={busy}
            onClear={() => clearIngestedAlerts(ALERT_SOURCE.JSON)}
            {...sharedTableProps}
          />
        ) : null}

        {navView === 'email' ? (
          guestMode ? (
            <EmailSignInGate />
          ) : (
            <EmailAlertPanel
              alerts={emailAlerts}
              onEmailSelected={ingestFromEmail}
              busy={busy}
              defaultMailboxEmail={user?.email ?? ''}
              onClear={() => clearIngestedAlerts(ALERT_SOURCE.EMAIL)}
              {...sharedTableProps}
            />
          )
        ) : null}

        {navView === 'health' ? (
          <HealthCheckPanel
            snapshot={health.snapshot}
            alerts={health.alerts}
            activeAlerts={health.activeAlerts}
            alertToast={health.alertToast}
            activeAlertCount={health.activeAlertCount}
            fullStacks={health.fullStacks}
            loading={health.loading}
            stackLoading={health.stackLoading}
            live={health.live}
            metricsLive={health.metricsLive}
            alertsLive={health.alertsLive}
            metricsTick={health.metricsTick}
            paused={health.paused}
            mode={health.mode}
            onRefresh={health.refresh}
            onRefreshStack={health.refreshStack}
            onTogglePause={health.togglePause}
            error={health.error}
            onDismissError={health.dismissError}
            onDismissToast={health.dismissToast}
          />
        ) : null}
      </div>
      <SiteFooter />
    </div>
  );
}
