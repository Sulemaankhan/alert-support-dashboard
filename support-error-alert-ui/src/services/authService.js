import { request } from './httpClient.js';

/**
 * @returns {Promise<{ enabled: boolean, googleClientId: string }>}
 */
export async function fetchAuthConfig() {
  const res = await request('/api/auth/config');
  return res.json();
}

/**
 * @returns {Promise<{ email: string, displayName: string } | null>}
 */
export async function fetchCurrentUser() {
  const res = await fetch(buildAuthUrl('/api/auth/me'), { credentials: 'include' });
  if (res.status === 401) {
    return null;
  }
  if (!res.ok) {
    const detail = await readError(res);
    throw new Error(detail || `Request failed (${res.status})`);
  }
  return res.json();
}

/**
 * @param {string} email
 */
export async function sendEmailCode(email) {
  const res = await request('/api/auth/email/send-code', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  });
  return res.json();
}

/**
 * @param {string} email
 * @param {string} code
 * @returns {Promise<{ email: string, displayName: string }>}
 */
export async function verifyEmailCode(email, code, google = null) {
  const body = { email, code };
  if (google?.accessToken) {
    body.accessToken = google.accessToken;
    if (google.scope) body.scope = google.scope;
  }
  const res = await request('/api/auth/email/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return res.json();
}

/**
 * @param {{ accessToken?: string, idToken?: string }} tokens
 * @returns {Promise<{ email: string, displayName: string, gmailInboxAccess?: boolean }>}
 */
export async function signInWithGoogle({ accessToken, idToken, scope }) {
  const res = await request('/api/auth/google', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accessToken, idToken, scope }),
  });
  return res.json();
}

export async function logout() {
  await request('/api/auth/logout', { method: 'POST' });
}

/**
 * @param {string} accessToken - Google OAuth token with Gmail scope
 * @returns {Promise<{ email: string, displayName: string, gmailInboxAccess?: boolean }>}
 */
export async function connectGmail(accessToken, scope) {
  const res = await request('/api/auth/gmail/connect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accessToken, scope }),
  });
  return res.json();
}

function buildAuthUrl(path) {
  const base = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '');
  const p = path.startsWith('/') ? path : `/${path}`;
  return `${base}${p}`;
}

async function readError(response) {
  try {
    const data = await response.json();
    if (data?.error) return data.error;
  } catch {
    /* ignore */
  }
  return null;
}
