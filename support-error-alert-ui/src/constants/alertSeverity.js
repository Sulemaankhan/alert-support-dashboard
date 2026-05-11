/** @typedef {'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'} AlertSeverityLevel */

/** @type {readonly AlertSeverityLevel[]} */
export const ALERT_SEVERITY_OPTIONS = Object.freeze([
  'INFO',
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
]);
