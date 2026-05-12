/** Main navigation labels (site header). */
export const NAV_LABEL = {
  HOME: 'Home',
  LOAD_JSON: 'Load Json',
  PULL_EMAIL: 'Pull email',
};

/** DOM ids for in-page scroll targets. */
export const NAV_SECTION = {
  HOME: 'section-home',
  LOAD_JSON: 'section-load-json',
  PULL_EMAIL: 'section-pull-email',
};

/** Ordered nav entries for rendering. */
export const NAV_ITEMS = [
  { sectionKey: 'HOME', label: NAV_LABEL.HOME, sectionId: NAV_SECTION.HOME },
  { sectionKey: 'LOAD_JSON', label: NAV_LABEL.LOAD_JSON, sectionId: NAV_SECTION.LOAD_JSON },
  { sectionKey: 'PULL_EMAIL', label: NAV_LABEL.PULL_EMAIL, sectionId: NAV_SECTION.PULL_EMAIL },
];
