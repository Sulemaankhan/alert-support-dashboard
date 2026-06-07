import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authService from '../services/authService.js';

/** @typedef {{ email: string, displayName: string }} AuthUser */

const AuthContext = createContext(/** @type {null | object} */ (null));

export function AuthProvider({ children }) {
  const [user, setUser] = useState(/** @type {AuthUser | null} */ (null));
  const [loading, setLoading] = useState(true);
  const [googleClientId, setGoogleClientId] = useState('');
  const [devSignInCode, setDevSignInCode] = useState(/** @type {string | null} */ (null));
  const [emailOtpConfigured, setEmailOtpConfigured] = useState(false);
  const [googleSsoOnly, setGoogleSsoOnly] = useState(true);
  const [authEnabled, setAuthEnabled] = useState(true);

  const refreshUser = useCallback(async () => {
    try {
      const me = await authService.fetchCurrentUser();
      setUser(me);
      return me;
    } catch {
      setUser(null);
      return null;
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const config = await authService.fetchAuthConfig();
        if (cancelled) return;
        setAuthEnabled(Boolean(config.enabled));
        setGoogleClientId(typeof config.googleClientId === 'string' ? config.googleClientId : '');
        setDevSignInCode(typeof config.devSignInCode === 'string' ? config.devSignInCode : null);
        setEmailOtpConfigured(Boolean(config.emailOtpConfigured));
        setGoogleSsoOnly(config.googleSsoOnly !== false);
        if (!config.enabled) {
          setUser({ email: 'local@dev', displayName: 'Local user' });
        } else {
          await refreshUser();
        }
      } catch {
        if (!cancelled) setUser(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refreshUser]);

  const signInWithEmailCode = useCallback(async (email, code, google) => {
    const u = await authService.verifyEmailCode(email, code, google);
    setUser(u);
    return u;
  }, []);

  const sendEmailCode = useCallback((email) => authService.sendEmailCode(email), []);

  const signInWithGoogleToken = useCallback(async (tokens) => {
    const u = await authService.signInWithGoogle(tokens);
    setUser(u);
    return u;
  }, []);

  const connectGmail = useCallback(async (accessToken, scope) => {
    const u = await authService.connectGmail(accessToken, scope);
    setUser(u);
    return u;
  }, []);

  const signOut = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      authEnabled,
      googleClientId,
      devSignInCode,
      emailOtpConfigured,
      googleSsoOnly,
      sendEmailCode,
      signInWithEmailCode,
      signInWithGoogleToken,
      connectGmail,
      signOut,
      refreshUser,
    }),
    [
      user,
      loading,
      authEnabled,
      googleClientId,
      devSignInCode,
      emailOtpConfigured,
      googleSsoOnly,
      sendEmailCode,
      signInWithEmailCode,
      signInWithGoogleToken,
      connectGmail,
      signOut,
      refreshUser,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
