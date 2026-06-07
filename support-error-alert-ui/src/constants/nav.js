/** Main navigation labels (site header). */
export const NAV_LABEL = {
  OVERVIEW: 'Overview',
  JSON_ALERT: 'JSON alert',
  EMAIL_ALERT: 'Email alert',
};

/** DOM ids for in-page scroll targets. */
export const NAV_SECTION = {
  HOME: 'section-home',
  JSON_ALERT: 'section-json-alert',
  EMAIL_ALERT: 'section-email-alert',
};

/** Maps nav section keys to dashboard view ids. */
export const NAV_VIEW_BY_KEY = {
  OVERVIEW: 'home',
  JSON_ALERT: 'json',
  EMAIL_ALERT: 'email',
};

/** Ordered nav entries for rendering. */
export const NAV_ITEMS = [
  { sectionKey: 'OVERVIEW', label: NAV_LABEL.OVERVIEW, sectionId: NAV_SECTION.HOME },
  { sectionKey: 'JSON_ALERT', label: NAV_LABEL.JSON_ALERT, sectionId: NAV_SECTION.JSON_ALERT },
  { sectionKey: 'EMAIL_ALERT', label: NAV_LABEL.EMAIL_ALERT, sectionId: NAV_SECTION.EMAIL_ALERT },
];
