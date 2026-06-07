import { SECTION } from '../constants/branding.js';

import { NAV_SECTION } from '../constants/nav.js';

import { AlertsTable } from './AlertsTable.jsx';

import { JsonFileUpload } from './JsonFileUpload.jsx';

import './AlertSourcePanel.css';



/**

 * @param {Object} props

 * @param {import('../types/alerts').Alert[]} props.alerts

 * @param {(file: File) => void | Promise<void>} props.onJsonSelected

 * @param {boolean} [props.busy]

 * @param {boolean} props.loading

 * @param {function(string, string): void} props.onStatusChange

 * @param {function(): void | Promise<void>} props.onClear

 * @param {function(): void} props.onRefresh

 * @param {boolean} props.jiraConfigured

 * @param {string} [props.jiraSiteUrl]

 * @param {function(string, Record<string, string>): void | Promise<void>} props.onCreateJiraIssue

 */

export function JsonAlertPanel({

  alerts,

  onJsonSelected,

  busy = false,

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

      className="alert-source-panel alert-source-panel--json"

      id={NAV_SECTION.JSON_ALERT}

      aria-labelledby="json-alert-heading"

    >

      <div className="alert-source-panel__head">

        <h2 id="json-alert-heading" className="alert-source-panel__title">

          {SECTION.JSON.title}

        </h2>

      </div>



      <div className="alert-source-panel__load">

        <p className="alert-source-panel__hint">{SECTION.JSON.loadHint}</p>

        <JsonFileUpload

          onFileSelected={onJsonSelected}

          label="Choose JSON file"

          className="json-upload--alert-source"

          disabled={busy}

          title="Import the first error from a JSON file."

        />

      </div>



      <div className="alert-source-panel__detail">

        <AlertsTable

          title={SECTION.JSON.title}

          emptyMessage={SECTION.JSON.emptyDetail}

          clearLabel="Clear JSON alert"

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

          variant="json"

        />

      </div>

    </section>

  );

}


