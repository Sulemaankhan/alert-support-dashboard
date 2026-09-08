import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { NAV_SECTION } from '../constants/nav.js';
import * as authService from '../services/authService.js';

/** @typedef {{ email: string, displayName: string }} AuthUser */

const GUEST_MODE_KEY = 'support-alert-json-guest';

const AuthContext = createContext(/** @type {null | object} */ (null));

function readGuestMode() {
  try {
    return sessionStorage.getItem(GUEST_MODE_KEY) === '1';
  } catch {
    return false;
  }
}

function writeGuestMode(active) {
  try {
    if (active) {
      sessionStorage.setItem(GUEST_MODE_KEY, '1');
    } else {
      sessionStorage.removeItem(GUEST_MODE_KEY);
    }
  } catch {
    /* private browsing */
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(/** @type {AuthUser | null} */ (null));
  const [loading, setLoading] = useState(true);
  const [googleClientId, setGoogleClientId] = useState('');
  const [devSignInCode, setDevSignInCode] = useState(/** @type {string | null} */ (null));
  const [emailOtpConfigured, setEmailOtpConfigured] = useState(false);
  const [googleSignInOnly, setGoogleSignInOnly] = useState(true);
  const [jsonAlertsWithoutSignIn, setJsonAlertsWithoutSignIn] = useState(true);
  const [authEnabled, setAuthEnabled] = useState(true);
  const [guestMode, setGuestMode] = useState(() => readGuestMode());

  const refreshUser = useCallback(async () => {
    try {
      const me = await authService.fetchCurrentUser();
      setUser(me);
      if (me) {
        writeGuestMode(false);
        setGuestMode(false);
      }
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
        setGoogleSignInOnly(config.googleSignInOnly !== false);
        setJsonAlertsWithoutSignIn(config.jsonAlertsWithoutSignIn !== false);
        if (config.jsonAlertsWithoutSignIn === false) {
          writeGuestMode(false);
          setGuestMode(false);
        }
        if (!config.enabled) {
          setUser({ email: 'local@dev', displayName: 'Local user' });
          writeGuestMode(false);
          setGuestMode(false);
        } else {
          const me = await refreshUser();
          if (!me && config.jsonAlertsWithoutSignIn !== false) {
            writeGuestMode(true);
            setGuestMode(true);
          }
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

  const enterGuestMode = useCallback(() => {
    writeGuestMode(true);
    setGuestMode(true);
    window.location.hash = `#${NAV_SECTION.JSON_ALERT}`;
  }, []);

  const exitGuestMode = useCallback(() => {
    writeGuestMode(false);
    setGuestMode(false);
  }, []);

  const signInWithEmailCode = useCallback(async (email, code, google) => {
    const u = await authService.verifyEmailCode(email, code, google);
    writeGuestMode(false);
    setGuestMode(false);
    setUser(u);
    return u;
  }, []);

  const sendEmailCode = useCallback((email) => authService.sendEmailCode(email), []);

  const signInWithGoogleToken = useCallback(async (tokens) => {
    const u = await authService.signInWithGoogle(tokens);
    writeGuestMode(false);
    setGuestMode(false);
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

  const isGuest = guestMode && !user && jsonAlertsWithoutSignIn;

  const value = useMemo(
    () => ({
      user,
      loading,
      authEnabled,
      googleClientId,
      devSignInCode,
      emailOtpConfigured,
      googleSignInOnly,
      jsonAlertsWithoutSignIn,
      isGuest,
      enterGuestMode,
      exitGuestMode,
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
      googleSignInOnly,
      jsonAlertsWithoutSignIn,
      isGuest,
      enterGuestMode,
      exitGuestMode,
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
