import { useState } from 'react';
import { useAuth } from '../hooks/useAuth.jsx';
import { useGoogleGmailAccess } from '../hooks/useGoogleGmailAccess.js';
import './ConnectGmailBanner.css';

/**
 * Shown when signed in but Gmail OAuth is not linked (e.g. after email-code sign-in).
 *
 * @param {Object} [props]
 * @param {() => void | Promise<void>} [props.onConnect] - parent handles connect + refresh (InboxLookup)
 * @param {boolean} [props.connecting]
 */
export function ConnectGmailBanner({ onConnect, connecting: connectingProp = false }) {
  const { refreshUser } = useAuth();
  const { requestAccess, googleConfigured } = useGoogleGmailAccess();
  const [message, setMessage] = useState(/** @type {string | null} */ (null));
  const [error, setError] = useState(/** @type {string | null} */ (null));
  const [busy, setBusy] = useState(false);

  const connecting = connectingProp || busy;

  if (!googleConfigured) {
    return (
      <div className="connect-gmail connect-gmail--info" role="status">
        <p className="connect-gmail__text">
          To search inbox <strong>without a password</strong>, set the same OAuth client ID on backend (
          <span className="mono">GOOGLE_CLIENT_ID</span>) and frontend (
          <span className="mono">VITE_GOOGLE_CLIENT_ID</span>), then use <strong>Connect Gmail</strong> below.
        </p>
        <p className="connect-gmail__text">
          Until then, use a Gmail <a href="https://myaccount.google.com/apppasswords">App Password</a> in the
          password field.
        </p>
      </div>
    );
  }

  const runConnect = async () => {
    setError(null);
    setMessage(null);
    if (onConnect) {
      try {
        await onConnect();
        setMessage('Gmail connected. Leave the password empty and search inbox.');
      } catch (err) {
        setError(err instanceof Error ? err.message : String(err));
      }
      return;
    }
    setBusy(true);
    requestAccess(
      async () => {
        await refreshUser();
        setMessage('Gmail connected. Leave the password field empty when searching inbox.');
        setBusy(false);
      },
      (err) => {
        setError(err);
        setBusy(false);
      }
    );
  };

  return (
    <div className="connect-gmail" role="region" aria-label="Connect Gmail">
      <p className="connect-gmail__text">
        You are signed in with an email code, not Google Gmail. Click below to link Gmail for this account — then
        search with an <strong>empty password</strong>.
      </p>
      <button
        type="button"
        className="btn btn--primary connect-gmail__btn"
        onClick={runConnect}
        disabled={connecting}
      >
        {connecting ? 'Connecting…' : 'Connect Gmail with Google'}
      </button>
      {message ? (
        <p className="connect-gmail__ok" role="status">
          {message}
        </p>
      ) : null}
      {error ? (
        <p className="connect-gmail__err" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
