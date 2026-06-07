import { request } from './httpClient.js';

/**
 * @param {{ email: string, password?: string, subject: string, host?: string, port?: number }} params
 * @returns {Promise<import('../types/inbox').InboxSearchResponse>}
 */
export async function searchInbox(params) {
  const body = {
    email: params.email.trim(),
    subject: params.subject.trim(),
    ...(params.password ? { password: params.password } : {}),
    ...(params.host?.trim() ? { host: params.host.trim() } : {}),
    ...(params.port ? { port: params.port } : {}),
  };
  const res = await request('/api/email/inbox/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return res.json();
}
