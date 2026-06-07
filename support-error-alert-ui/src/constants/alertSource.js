/** @typedef {'json' | 'email'} AlertSource */

export const ALERT_SOURCE = {
  JSON: 'json',
  EMAIL: 'email',
};

export const ALERT_SOURCE_LABEL = {
  [ALERT_SOURCE.JSON]: 'JSON file',
  [ALERT_SOURCE.EMAIL]: 'Email inbox',
};
