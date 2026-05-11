/** @typedef {'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'} AlertStatus */

/**
 * @typedef {Object} Alert
 * @property {string} id
 * @property {string} errorDetails
 * @property {string} functionPath
 * @property {string} filePath
 * @property {string} serviceName
 * @property {string} jiraBatchId
 * @property {string[]} jiraIssueKeys - usually one shared key per batch (ALRT-####)
 * @property {string} severity
 * @property {AlertStatus} status
 * @property {string} [createdAt]
 */

export {};
