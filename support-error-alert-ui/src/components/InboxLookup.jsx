import { useCallback, useState } from 'react';
import { NAV_SECTION } from '../constants/nav.js';
import * as emailService from '../services/emailService.js';
import './InboxLookup.css';

function formatSent(iso) {
  if (!iso) return '—';
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  } catch {
    return iso;
  }
}

export function InboxLookup() {
  const [subject, setSubject] = useState('');
  const [loading, setLoading] = useState(false);
  /** @type {[import('../types/inbox').InboxSearchResponse | null, import('react').Dispatch<any>]} */
  const [result, setResult] = useState(null);
  const [clientError, setClientError] = useState(/** @type {string | null} */ (null));

  const runLookup = useCallback(async () => {
    const q = subject.trim();
    if (!q) {
      setClientError('Enter an email subject (or part of it) to search the inbox.');
      setResult(null);
      return;
    }
    setClientError(null);
    setLoading(true);
    try {
      const data = await emailService.fetchInboxBySubject(q);
      setResult(data);
    } catch (e) {
      setResult(null);
      setClientError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [subject]);

  const onSubmit = (e) => {
    e.preventDefault();
    runLookup();
  };

  const detail = result?.detail ?? null;
  const messages = result?.messages ?? [];

  return (
    <section
      className="inbox-lookup"
      id={NAV_SECTION.PULL_EMAIL}
      aria-labelledby="inbox-lookup-heading"
    >
      <div className="inbox-lookup__head">
        <h2 id="inbox-lookup-heading" className="inbox-lookup__title">
          Inbox by subject
        </h2>
        <p className="inbox-lookup__sub">
          Calls <span className="mono">GET /api/email/inbox?subject=…</span> and lists matching messages from the
          configured IMAP account.
        </p>
      </div>
      <form className="inbox-lookup__form" onSubmit={onSubmit}>
        <label className="inbox-lookup__label" htmlFor="inbox-subject">
          Subject contains
        </label>
        <div className="inbox-lookup__row">
          <input
            id="inbox-subject"
            className="inbox-lookup__input"
            type="search"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            placeholder="e.g. ALERT-1234 or error summary"
            autoComplete="off"
            disabled={loading}
          />
          <button type="submit" className="btn btn--primary" disabled={loading}>
            {loading ? 'Searching…' : 'Search inbox'}
          </button>
        </div>
      </form>

      {clientError ? (
        <div className="inbox-lookup__banner inbox-lookup__banner--error" role="alert">
          {clientError}
        </div>
      ) : null}

      {result && result.status !== 'OK' && detail ? (
        <div
          className={
            result.status === 'ERROR'
              ? 'inbox-lookup__banner inbox-lookup__banner--error'
              : 'inbox-lookup__banner inbox-lookup__banner--info'
          }
          role="status"
        >
          {detail}
        </div>
      ) : null}

      {result && result.status === 'OK' && messages.length === 0 && !clientError ? (
        <p className="inbox-lookup__empty">No messages matched that subject.</p>
      ) : null}

      {result && result.status === 'OK' && messages.length > 0 ? (
        <div className="inbox-lookup__table-wrap">
          <table className="inbox-lookup__table">
            <thead>
              <tr>
                <th scope="col">Subject</th>
                <th scope="col">From</th>
                <th scope="col">Sent</th>
                <th scope="col">Preview</th>
              </tr>
            </thead>
            <tbody>
              {messages.map((m, idx) => (
                <tr key={m.messageId || `${m.subject}-${idx}`}>
                  <td className="inbox-lookup__cell-subject">{m.subject || '—'}</td>
                  <td>{m.from || '—'}</td>
                  <td className="inbox-lookup__cell-nowrap">{formatSent(m.sentAt)}</td>
                  <td className="inbox-lookup__cell-preview">{m.preview || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}
