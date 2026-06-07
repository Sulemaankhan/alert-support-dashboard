import { API_BASE } from '../config/api.js';

function buildUrl(path) {
  const p = path.startsWith('/') ? path : `/${path}`;
  return `${API_BASE}${p}`;
}

async function readErrorDetail(response) {
  try {
    const data = await response.json();
    if (data && typeof data.error === 'string' && data.error) {
      return data.error;
    }
  } catch {
    /* non-JSON body */
  }
  return null;
}

/**
 * @param {string} path - path starting with /
 * @param {RequestInit} [init]
 * @returns {Promise<Response>}
 */
export async function request(path, init = {}) {
  const url = buildUrl(path);
  const response = await fetch(url, {
    credentials: 'include',
    ...init,
  });
  if (!response.ok) {
    const detail = await readErrorDetail(response);
    const err = new Error(detail || `Request failed (${response.status})`);
    err.status = response.status;
    if (response.status === 401) {
      err.unauthorized = true;
    }
    throw err;
  }
  return response;
}
