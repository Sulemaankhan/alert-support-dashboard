import { request } from './httpClient.js';

/**
 * @returns {Promise<import('../types/alerts').Alert[]>}
 */
export async function fetchAlerts() {
  const res = await request('/api/alerts');
  return res.json();
}

/** Clears all alerts ingested on the server (in-memory store). */
export async function clearAllAlerts() {
  await request('/api/alerts', { method: 'DELETE' });
}

/**
 * @param {string} jsonBody - raw JSON string
 * @returns {Promise<import('../types/alerts').Alert[]>}
 */
export async function ingestAlertsJson(jsonBody) {
  const res = await request('/api/alerts/ingest', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: jsonBody,
  });
  return res.json();
}

/**
 * @param {string} alertId
 * @param {import('../types/alerts').AlertStatus} status
 */
export async function updateAlertStatus(alertId, status) {
  const res = await request(`/api/alerts/${alertId}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  });
  return res.json();
}
