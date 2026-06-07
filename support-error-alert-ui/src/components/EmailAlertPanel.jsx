import { SECTION } from '../constants/branding.js';
import { NAV_SECTION } from '../constants/nav.js';
import { AlertsTable } from './AlertsTable.jsx';
import { InboxLookup } from './InboxLookup.jsx';
import './AlertSourcePanel.css';

/**
 * @param {Object} props
 * @param {import('../types/alerts').Alert[]} props.alerts
 * @param {(message: import('../types/inbox').InboxMessage) => void | Promise<void>} props.onEmailSelected
 * @param {boolean} [props.busy]
 * @param {string} [props.defaultMailboxEmail]
 * @param {boolean} props.loading
 * @param {function(string, string): void} props.onStatusChange
 * @param {function(): void | Promise<void>} props.onClear
 * @param {function(): void} props.onRefresh
 * @param {boolean} props.jiraConfigured
 * @param {string} [props.jiraSiteUrl]
 * @param {function(string, Record<string, string>): void | Promise<void>} props.onCreateJiraIssue
 */
export function EmailAlertPanel({
  alerts,
  onEmailSelected,
  busy = false,
  defaultMailboxEmail = '',
  loading,
  onStatusChange,
  onClear,
  onRefresh,
  jiraConfigured,
  jiraSiteUrl,
  onCreateJiraIssue,
}) {
  return (
    <section
      className="alert-source-panel alert-source-panel--email"
      id={NAV_SECTION.EMAIL_ALERT}
      aria-labelledby="email-alert-heading"
    >
      <div className="alert-source-panel__head">
        <h2 id="email-alert-heading" className="alert-source-panel__title">
          {SECTION.EMAIL.title}
        </h2>
      </div>

      <div className="alert-source-panel__load">
        <InboxLookup
          embedded
          busy={busy}
          onUseAsAlert={onEmailSelected}
          defaultEmail={defaultMailboxEmail}
        />
      </div>

      <div className="alert-source-panel__detail">
        <AlertsTable
          title={SECTION.EMAIL.title}
          emptyMessage={SECTION.EMAIL.emptyDetail}
          clearLabel="Clear email alert"
          alerts={alerts}
          loading={loading && alerts.length === 0}
          onStatusChange={onStatusChange}
          rowActionsBusy={busy}
          onClear={onClear}
          onRefresh={onRefresh}
          canClear={alerts.length > 0}
          jiraConfigured={jiraConfigured}
          jiraSiteUrl={jiraSiteUrl}
          onCreateJiraIssue={onCreateJiraIssue}
          embedded
          variant="email"
        />
      </div>
    </section>
  );
}
