import { useCallback, useState } from 'react';
import { SECTION } from '../constants/branding.js';
import { NAV_SECTION } from '../constants/nav.js';
import { ConnectGmailBanner } from './ConnectGmailBanner.jsx';
import { useAuth } from '../hooks/useAuth.jsx';
import { useGoogleGmailAccess } from '../hooks/useGoogleGmailAccess.js';
import * as emailService from '../services/emailService.js';
import './InboxLookup.css';

const INBOX_SUBJECT_PARAM = 'inboxSubject';

function readInboxSubjectFromUrl() {
  if (typeof window === 'undefined') return '';
  const q = new URLSearchParams(window.location.search).get(INBOX_SUBJECT_PARAM);
  return q?.trim() ?? '';
}

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

/**
 * @param {Object} props
 * @param {boolean} [props.embedded] - inside EmailAlertPanel (no outer section chrome)
 * @param {boolean} [props.busy]
 * @param {(message: import('../types/inbox').InboxMessage) => void | Promise<void>} [props.onUseAsAlert]
 * @param {string} [props.defaultEmail] - pre-fill from signed-in user
 */
export function InboxLookup({ embedded = false, busy = false, onUseAsAlert, defaultEmail = '' }) {
  const { user, refreshUser, googleClientId, googleSsoOnly } = useAuth();
  const { requestAccess, googleConfigured } = useGoogleGmailAccess('connect');
  const gmailInboxAccess = Boolean(user?.gmailInboxAccess);

  const [password, setPassword] = useState('');
  const [subject, setSubject] = useState(() => readInboxSubjectFromUrl());
  const [loading, setLoading] = useState(false);
  const [connectingGoogle, setConnectingGoogle] = useState(false);
  /** @type {[import('../types/inbox').InboxSearchResponse | null, import('react').Dispatch<any>]} */
  const [result, setResult] = useState(null);
  const [clientError, setClientError] = useState(/** @type {string | null} */ (null));

  const sessionEmail = user?.email?.trim() ?? '';
  const mailboxEmail = sessionEmail || defaultEmail.trim();

  const canSkipPassword = gmailInboxAccess && Boolean(mailboxEmail);

  const connectGoogleForInbox = useCallback(async () => {
    if (!googleConfigured) {
      throw new Error(
        'Google OAuth is not configured. Set GOOGLE_CLIENT_ID on the backend and VITE_GOOGLE_CLIENT_ID in the UI (same OAuth client).'
      );
    }
    setConnectingGoogle(true);
    try {
      await new Promise((resolve, reject) => {
        requestAccess(
          () => resolve(),
          (err) => reject(new Error(typeof err === 'string' ? err : 'Google sign-in was cancelled.'))
        );
      });
      const me = await refreshUser();
      if (!me?.gmailInboxAccess) {
        throw new Error(
          'Gmail access was not granted. When Google asks, allow access to your mailbox, then try again.'
        );
      }
      return me;
    } finally {
      setConnectingGoogle(false);
    }
  }, [googleConfigured, requestAccess, refreshUser]);

  const executeSearch = useCallback(async (emailTrim, subjectTrim, passwordValue) => {
    const data = await emailService.searchInbox({
      email: emailTrim,
      password: passwordValue || undefined,
      subject: subjectTrim,
    });
    setResult(data);
    if (data.status === 'ERROR' && data.detail) {
      setClientError(null);
    }
    return data;
  }, []);

  const runLookup = useCallback(async () => {
    const emailTrim = mailboxEmail;
    const subjectTrim = subject.trim();
    if (!emailTrim) {
      setClientError('Sign in with Google to search your inbox.');
      setResult(null);
      return;
    }
    if (!subjectTrim) {
      setClientError('Enter a subject (or part of it) to search.');
      setResult(null);
      return;
    }

    setClientError(null);

    let passwordToUse = password;

    if (!passwordToUse) {
      setLoading(true);
      try {
        let me = await refreshUser();
        if (!me?.gmailInboxAccess) {
          if (!googleConfigured) {
            setClientError(
              'Sign in with Google (Gmail access) to search inbox without a password.'
            );
            setResult(null);
            return;
          }
          await connectGoogleForInbox();
          me = await refreshUser();
          if (!me?.gmailInboxAccess) {
            setClientError(
              'Gmail access is required. Sign out, sign in with Google again, and allow Gmail when prompted.'
            );
            setResult(null);
            return;
          }
        }
        passwordToUse = '';
      } catch (e) {
        setResult(null);
        setClientError(e instanceof Error ? e.message : String(e));
        return;
      } finally {
        setLoading(false);
      }
    }

    setLoading(true);
    try {
      let data = await executeSearch(emailTrim, subjectTrim, passwordToUse);
      if (
        !passwordToUse &&
        data?.status === 'ERROR' &&
        data?.detail?.includes('Gmail access expired') &&
        googleConfigured
      ) {
        await connectGoogleForInbox();
        data = await executeSearch(emailTrim, subjectTrim, '');
      }
    } catch (e) {
      setResult(null);
      setClientError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [
    mailboxEmail,
    password,
    subject,
    gmailInboxAccess,
    googleConfigured,
    connectGoogleForInbox,
    executeSearch,
    refreshUser,
  ]);

  const onConnectGmailClick = async () => {
    setClientError(null);
    try {
      await connectGoogleForInbox();
      setClientError(null);
    } catch (e) {
      setClientError(e instanceof Error ? e.message : String(e));
    }
  };

  const onSubmit = (e) => {
    e.preventDefault();
    runLookup();
  };

  const detail = result?.detail ?? null;
  const messages = result?.messages ?? [];
  const showServerError = result?.status === 'ERROR' && detail;

  const Tag = embedded ? 'div' : 'section';
  const rootClass = embedded ? 'inbox-lookup inbox-lookup--embedded' : 'inbox-lookup';
  const formBusy = loading || busy || connectingGoogle;

  return (
    <Tag
      className={rootClass}
      id={embedded ? undefined : NAV_SECTION.EMAIL_ALERT}
      aria-labelledby={embedded ? undefined : 'inbox-lookup-heading'}
    >
      {!embedded ? (
        <div className="inbox-lookup__head">
          <h2 id="inbox-lookup-heading" className="inbox-lookup__title">
            {SECTION.EMAIL.title}
          </h2>
        </div>
      ) : null}

      {gmailInboxAccess ? (
        <p className="inbox-lookup__notice" role="status">
          Signed in with <strong>{sessionEmail}</strong>.
        </p>
      ) : (
        <ConnectGmailBanner onConnect={onConnectGmailClick} connecting={connectingGoogle} />
      )}

      {!googleConfigured && googleClientId === '' ? (
        <p className="inbox-lookup__notice inbox-lookup__notice--warn" role="status">
          Google sign-in is off until <span className="mono">GOOGLE_CLIENT_ID</span> and{' '}
          <span className="mono">VITE_GOOGLE_CLIENT_ID</span> are set to the same OAuth client ID.
        </p>
      ) : null}

      <form className="inbox-lookup__form" onSubmit={onSubmit}>
        <div className="inbox-lookup__fields">
          {!googleSsoOnly ? (
            <div className="inbox-lookup__field">
              <label className="inbox-lookup__label" htmlFor="inbox-password">
                Password {canSkipPassword ? '(optional)' : googleConfigured ? '(optional — use Google)' : ''}
              </label>
              <input
                id="inbox-password"
                className="inbox-lookup__input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={
                  canSkipPassword
                    ? 'Leave empty — Gmail connected via Google'
                    : googleConfigured
                      ? 'Leave empty to connect Gmail with Google'
                      : 'Gmail App Password'
                }
                autoComplete="current-password"
                disabled={formBusy}
              />
            </div>
          ) : null}
          <div className="inbox-lookup__field inbox-lookup__field--wide">
            <label className="inbox-lookup__label" htmlFor="inbox-subject">
              Email Subject
            </label>
            <input
              id="inbox-subject"
              className="inbox-lookup__input"
              type="search"
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              placeholder="e.g. unable to fetch info from invox"
              autoComplete="off"
              disabled={formBusy}
            />
          </div>
        </div>
        <div className="inbox-lookup__actions">
          <button type="submit" className="btn btn--primary" disabled={formBusy}>
            {connectingGoogle ? 'Connecting Gmail…' : loading ? 'Searching…' : 'Search inbox'}
          </button>
          {!gmailInboxAccess && googleConfigured ? (
            <button
              type="button"
              className="btn inbox-lookup__connect-btn"
              disabled={formBusy}
              onClick={onConnectGmailClick}
            >
              {connectingGoogle ? 'Connecting…' : 'Connect Gmail with Google'}
            </button>
          ) : null}
        </div>
      </form>

      {clientError ? (
        <div className="inbox-lookup__banner inbox-lookup__banner--error" role="alert">
          {clientError}
        </div>
      ) : null}

      {showServerError ? (
        <div className="inbox-lookup__banner inbox-lookup__banner--error" role="status">
          <strong>Server responded (ERROR).</strong> {detail}
        </div>
      ) : null}

      {result && result.status === 'OK' && messages.length === 0 && !clientError && !showServerError ? (
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
                <th scope="col">Action</th>
              </tr>
            </thead>
            <tbody>
              {messages.map((m, idx) => (
                <tr key={m.messageId || `${m.subject}-${idx}`}>
                  <td className="inbox-lookup__cell-subject">{m.subject || '—'}</td>
                  <td>{m.from || '—'}</td>
                  <td className="inbox-lookup__cell-nowrap">{formatSent(m.sentAt)}</td>
                  <td className="inbox-lookup__cell-preview">{m.preview || '—'}</td>
                  <td>
                    <button
                      type="button"
                      className="btn btn--primary inbox-lookup__use-btn"
                      disabled={busy || !onUseAsAlert}
                      onClick={() => onUseAsAlert?.(m)}
                    >
                      Use as alert
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </Tag>
  );
}
