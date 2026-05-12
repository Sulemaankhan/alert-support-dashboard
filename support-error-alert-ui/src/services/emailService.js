import { request } from './httpClient.js';

/**
 * @param {string} subject - text matched against message subjects (server-side IMAP search)
 * @returns {Promise<import('../types/inbox').InboxSearchResponse>}
 */
export async function fetchInboxBySubject(subject) {
  const params = new URLSearchParams({ subject });
  const res = await request(`/api/email/inbox?${params.toString()}`);
  return res.json();
}
