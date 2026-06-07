import { useCallback, useEffect, useRef } from 'react';
import { useAuth } from './useAuth.jsx';

// gmail.readonly appears in the Cloud Console scope picker; mail.google.com is required for IMAP inbox.
const GMAIL_SCOPES =
  'openid email profile https://www.googleapis.com/auth/gmail.readonly https://mail.google.com/';

/**
 * @param {'signin' | 'connect'} mode - signin: full login; connect: add Gmail to existing session
 */
export function useGoogleGmailAccess(mode = 'connect') {
  const { googleClientId, connectGmail, signInWithGoogleToken } = useAuth();
  const tokenClientRef = useRef(null);
  const pendingRef = useRef(/** @type {{ onSuccess?: () => void, onError?: (msg: string) => void } | null} */ (null));

  const runWithToken = useCallback(
    async (accessToken, scope) => {
      if (mode === 'signin') {
        await signInWithGoogleToken({ accessToken, scope });
      } else {
        await connectGmail(accessToken, scope);
      }
      pendingRef.current?.onSuccess?.();
    },
    [mode, connectGmail, signInWithGoogleToken]
  );

  useEffect(() => {
    if (!googleClientId) return;

    const init = () => {
      if (!window.google?.accounts?.oauth2) return;
      tokenClientRef.current = window.google.accounts.oauth2.initTokenClient({
        client_id: googleClientId,
        scope: GMAIL_SCOPES,
        callback: async (tokenResponse) => {
          const pending = pendingRef.current;
          if (!pending) return;
          if (tokenResponse.error) {
            pending.onError?.(tokenResponse.error);
            return;
          }
          if (!tokenResponse.access_token) {
            pending.onError?.('Google did not return an access token.');
            return;
          }
          try {
            await runWithToken(tokenResponse.access_token, tokenResponse.scope);
          } catch (err) {
            pending.onError?.(err instanceof Error ? err.message : String(err));
          }
        },
      });
    };

    if (window.google?.accounts?.oauth2) {
      init();
      return;
    }

    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = init;
    document.head.appendChild(script);
    return () => script.remove();
  }, [googleClientId, runWithToken]);

  /**
   * Opens Google OAuth. Resolves with { accessToken, scope } for optional combined email-code sign-in.
   */
  const requestAccess = useCallback(
    (onSuccess, onError) => {
      if (!googleClientId) {
        onError?.(
          'Google OAuth is not configured. Set support.auth.google.client-id in application.properties.'
        );
        return;
      }
      if (!tokenClientRef.current) {
        onError?.('Google sign-in is still loading. Try again in a moment.');
        return;
      }
      pendingRef.current = { onSuccess, onError };
      tokenClientRef.current.requestAccessToken({ prompt: 'consent' });
    },
    [googleClientId]
  );

  return { requestAccess, googleConfigured: Boolean(googleClientId) };
}
