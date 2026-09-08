import { useState } from 'react';
import { APP_NAME_CLASS } from '../constants/branding.js';
import { AlertLogo } from './AlertLogo.jsx';
import { GoogleIcon } from './GoogleIcon.jsx';
import { useAuth } from '../hooks/useAuth.jsx';
import { useGoogleGmailAccess } from '../hooks/useGoogleGmailAccess.js';
import './LoginPage.css';

export function LoginPage() {
  const { googleSignInOnly, jsonAlertsWithoutSignIn, enterGuestMode } = useAuth();
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
    <div className="login-page">
      <div className="login-card">
        <div className="login-card__brand">
          <AlertLogo size="lg" />
          <h1 className={`login-card__title ${APP_NAME_CLASS}`}>
            <span className="login-card__title-line">Support Error Alert</span>
            <span className="login-card__title-line">Dashboard</span>
          </h1>
        </div>

        <p className="login-card__sub">
          {googleSignInOnly
            ? 'Sign in with your Google account to access the dashboard.'
            : 'Sign in to load support alerts from JSON.'}
        </p>

        {googleConfigured ? (
          <div className="login-card__google">
            <button
              type="button"
              className="login-card__google-btn"
              onClick={onGoogleSignIn}
              disabled={busy}
            >
              {busy ? (
                <>
                  <span className="login-card__spinner" aria-hidden="true" />
                  Signing in…
                </>
              ) : (
                <>
                  <GoogleIcon />
                  Sign in with Google
                </>
              )}
            </button>
            <p className="login-card__footnote">Uses your Google account for dashboard access.</p>
            {jsonAlertsWithoutSignIn ? (
              <button type="button" className="login-card__json-only" onClick={enterGuestMode}>
                Continue with JSON alerts only
              </button>
            ) : null}
          </div>
        ) : (
          <details className="login-card__setup">
            <summary>Google sign-in is not configured yet</summary>
            <ol className="login-card__setup-steps">
              <li>
                In Google Cloud Console, create an OAuth <strong>Web client</strong> (
                <a href="https://console.cloud.google.com/apis/credentials" target="_blank" rel="noreferrer">
                  Credentials
                </a>
                )
              </li>
              <li>
                Enable <strong>Gmail API</strong> (APIs &amp; Services → Library → search &quot;Gmail API&quot; →
                Enable)
              </li>
              <li>
                OAuth consent screen → <strong>Data access</strong> → <strong>Add or remove scopes</strong>:
                <ul className="login-card__setup-sublist">
                  <li>
                    Filter <strong>Gmail API</strong> and check <strong>…/auth/gmail.readonly</strong>
                  </li>
                  <li>
                    Or paste in <strong>Manually add scopes</strong>:
                    <pre className="login-card__setup-code">https://mail.google.com/</pre>
                  </li>
                </ul>
              </li>
              <li>
                Authorized JavaScript origin: <span className="mono">http://localhost:5173</span>
              </li>
              <li>
                Set <span className="mono">support.auth.google-client-id</span> (or{' '}
                <span className="mono">GOOGLE_CLIENT_ID</span>):
                <pre className="login-card__setup-code">
                  support.auth.google-client-id=YOUR_ID.apps.googleusercontent.com
                </pre>
              </li>
              <li>
                <strong>Restart the backend</strong> — log must show{' '}
                <span className="mono">Google sign-in ready</span>
              </li>
            </ol>
            <p className="login-card__setup-verify">
              Verify:{' '}
              <a href="/api/auth/config" target="_blank" rel="noreferrer">
                /api/auth/config
              </a>{' '}
              — <span className="mono">googleClientId</span> must not be empty.
            </p>
          </details>
        )}

        {!googleConfigured && jsonAlertsWithoutSignIn ? (
          <button type="button" className="login-card__json-only login-card__json-only--solo" onClick={enterGuestMode}>
            Continue with JSON alerts only
          </button>
        ) : null}

        {error ? (
          <p className="login-card__error" role="alert">
            {error}
          </p>
        ) : null}
      </div>
    </div>
  );
}
