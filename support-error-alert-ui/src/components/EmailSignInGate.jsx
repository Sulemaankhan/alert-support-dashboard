import { useState } from 'react';
import { NAV_SECTION } from '../constants/nav.js';
import { GoogleIcon } from './GoogleIcon.jsx';
import { useGoogleGmailAccess } from '../hooks/useGoogleGmailAccess.js';
import './EmailSignInGate.css';

/** Shown when a guest opens the email alert section — triggers Google SSO here only. */
export function EmailSignInGate() {
  const { requestAccess, googleConfigured } = useGoogleGmailAccess('signin');
  const [error, setError] = useState(/** @type {string | null} */ (null));
  const [busy, setBusy] = useState(false);

  const onGoogleSignIn = () => {
    setError(null);
    setBusy(true);
    requestAccess(
      () => setBusy(false),
      (err) => {
        setError(typeof err === 'string' ? err : String(err));
        setBusy(false);
      }
    );
  };

  return (
    <section
      className="email-signin-gate"
      id={NAV_SECTION.EMAIL_ALERT}
      aria-labelledby="email-signin-gate-heading"
    >
      <div className="email-signin-gate__card">
        <h2 id="email-signin-gate-heading" className="email-signin-gate__title">
          Email alerts require sign-in
        </h2>
        <p className="email-signin-gate__text">
          Searching Gmail and loading alerts from your inbox uses Google SSO. Overview and JSON alerts work without
          signing in.
        </p>
        {googleConfigured ? (
          <button
            type="button"
            className="btn btn--primary email-signin-gate__btn email-signin-gate__google-btn"
            onClick={onGoogleSignIn}
            disabled={busy}
          >
            {busy ? (
              'Signing in…'
            ) : (
              <>
                <GoogleIcon />
                Sign in with Google
              </>
            )}
          </button>
        ) : (
          <p className="email-signin-gate__text">
            Google SSO is not configured on the server. Set <span className="mono">GOOGLE_CLIENT_ID</span> and restart
            the backend.
          </p>
        )}
        {error ? (
          <p className="email-signin-gate__error" role="alert">
            {error}
          </p>
        ) : null}
      </div>
    </section>
  );
}
