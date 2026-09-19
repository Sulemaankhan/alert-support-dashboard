/** Sentinel value for the Application picker: every configured service. */
export const ALL_APPLICATIONS = 'all';

export function isAllApplications(id) {
  return String(id || '').toLowerCase() === ALL_APPLICATIONS;
}

/**
 * Distinct environments across the catalog, first-seen label wins.
 * @param {Array<{ environments?: Array<{ id?: string }> }> } [applications]
 */
export function uniqueEnvironments(applications = []) {
  const seen = new Map();
  for (const app of applications) {
    for (const env of app.environments ?? []) {
      if (env?.id && !seen.has(env.id)) {
        seen.set(env.id, env);
      }
    }
  }
  return [...seen.values()];
}
