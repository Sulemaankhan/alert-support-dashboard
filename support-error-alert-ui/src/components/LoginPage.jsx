import { useState } from 'react';
import { APP_NAME, APP_NAME_CLASS } from '../constants/branding.js';
import { AlertLogo } from './AlertLogo.jsx';
import { SiteFooter } from './SiteFooter.jsx';
import { useAuth } from '../hooks/useAuth.jsx';
import { useGoogleGmailAccess } from '../hooks/useGoogleGmailAccess.js';
import './LoginPage.css';

export function LoginPage() {
  const { googleSsoOnly } = useAuth();
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
    <div className="login-page-wrap">
      <div className="login-page">
      <div className="login-card login-card--sso">
        <div className="login-card__brand">
          <AlertLogo size="lg" />
          <h1 className={`login-card__title ${APP_NAME_CLASS}`}>{APP_NAME}</h1>
        </div>
        <p className="login-card__sub">
          {googleSsoOnly
            ? 'Sign in with your Google account to access the dashboard and Gmail inbox.'
            : 'Sign in to load support alerts from JSON or your Gmail inbox.'}
        </p>

        {googleConfigured ? (
          <div className="login-card__google login-card__google--primary">
            <button
              type="button"
              className="btn login-card__google-signin login-card__google-signin--hero"
              onClick={onGoogleSignIn}
              disabled={busy}
            >
              {busy ? 'Signing in…' : 'Sign in with Google'}
            </button>
            <p className="login-card__google-hint">
              Uses your Gmail account — inbox search works without an app password.
            </p>
          </div>
        ) : (
          <div className="login-card__google-setup" role="alert">
            <p className="login-card__google-setup-title">Google SSO is not configured yet</p>
            <p className="login-card__setup-lead">
              The backend could not read a Google Client ID. Fix this in <strong>one file</strong>:
            </p>
            <ol className="login-card__setup-steps">
              <li>
                In Google Cloud Console, create an OAuth <strong>Web client</strong> (
                <a href="https://console.cloud.google.com/apis/credentials" target="_blank" rel="noreferrer">
                  Credentials
                </a>
                )
              </li>
              <li>
                Enable <strong>Gmail API</strong> (APIs &amp; Services → Library → search &quot;Gmail API&quot; → Enable)
              </li>
              <li>
                OAuth consent screen → <strong>Data access</strong> (or Scopes) → <strong>Add or remove scopes</strong>:
                <ul className="login-card__setup-sublist">
                  <li>
                    Filter <strong>Gmail API</strong> and check{' '}
                    <strong>…/auth/gmail.readonly</strong> (label: &quot;See, read, download, and permanently delete
                    your email&quot;)
                  </li>
                  <li>
                    If you do not see it: scroll to <strong>Manually add scopes</strong> and paste:
                    <pre className="login-card__setup-code">https://mail.google.com/</pre>
                  </li>
                </ul>
              </li>
              <li>
                Authorized JavaScript origin: <span className="mono">http://localhost:5173</span>
              </li>
              <li>
                Open <span className="mono">google-oauth.properties</span> (folder with <span className="mono">pom.xml</span>)
                and set:
                <pre className="login-card__setup-code">
                  support.auth.google-client-id=YOUR_ID.apps.googleusercontent.com
                </pre>
                (Copy <span className="mono">google-oauth.properties.example</span> if the file is missing.)
              </li>
              <li>
                <strong>Restart the backend</strong> — log must show:{' '}
                <span className="mono">Gmail SSO ready</span>
              </li>
              <li>Refresh this page — the <strong>Sign in with Google</strong> button should appear</li>
            </ol>
            <p className="login-card__setup-verify">
              Verify: open{' '}
              <a href="http://localhost:8081/api/auth/config" target="_blank" rel="noreferrer">
                http://localhost:8081/api/auth/config
              </a>{' '}
              — <span className="mono">googleClientId</span> must not be empty.
            </p>
          </div>
        )}

        {error ? (
          <p className="login-card__error" role="alert">
            {error}
          </p>
        ) : null}
      </div>
      </div>
      <SiteFooter />
    </div>
  );
}
