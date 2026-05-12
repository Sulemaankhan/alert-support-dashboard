/**
 * @typedef {'DISABLED' | 'OK' | 'ERROR'} InboxSearchStatus
 */

/**
 * @typedef {Object} InboxMessage
 * @property {string | null} messageId
 * @property {string} subject
 * @property {string} from
 * @property {string | null} sentAt
 * @property {string} preview
 */

/**
 * @typedef {Object} InboxSearchResponse
 * @property {InboxSearchStatus} status
 * @property {string} querySubject
 * @property {InboxMessage[]} messages
 * @property {string | null} [detail]
 */

export {};
