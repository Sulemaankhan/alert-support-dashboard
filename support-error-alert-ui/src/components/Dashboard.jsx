import { ALERT_SOURCE } from '../constants/alertSource.js';

import { EmailAlertPanel } from './EmailAlertPanel.jsx';

import { ErrorBanner } from './ErrorBanner.jsx';

import { JsonAlertPanel } from './JsonAlertPanel.jsx';

import { OverviewPanel } from './OverviewPanel.jsx';

import { SiteFooter } from './SiteFooter.jsx';
import { SiteHeader } from './SiteHeader.jsx';

import { useAuth } from '../hooks/useAuth.jsx';

import { useAlertsDashboard } from '../hooks/useAlertsDashboard.js';

import { useNavView } from '../hooks/useNavView.js';



export function Dashboard() {

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
      <SiteHeader user={user} onSignOut={signOut} activeView={navView} />
      <div className="app">

        <ErrorBanner message={error} onDismiss={dismissError} />

        {navView === 'home' ? <OverviewPanel /> : null}

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

          <EmailAlertPanel

            alerts={emailAlerts}

            onEmailSelected={ingestFromEmail}

            busy={busy}

            defaultMailboxEmail={user?.email ?? ''}

            onClear={() => clearIngestedAlerts(ALERT_SOURCE.EMAIL)}

            {...sharedTableProps}

          />

        ) : null}

      </div>
      <SiteFooter />
    </div>
  );

}


