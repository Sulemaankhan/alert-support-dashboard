import { useCallback, useEffect, useRef, useState } from 'react';
import { ALERT_SOURCE } from '../constants/alertSource.js';
import * as alertsService from '../services/alertsService.js';

const POLL_INTERVAL_MS = 15000;

function formatError(e) {
  return e instanceof Error ? e.message : String(e);
}

/** @param {import('../types/alerts').Alert[]} list */
function inferAlertSource(list) {
  if (!list.length) return null;
  if (list.length === 1 && list[0].jiraBatchId === 'email') return ALERT_SOURCE.EMAIL;
  return ALERT_SOURCE.JSON;
}

export function useAlertsDashboard() {
  /** @type {[import('../types/alerts').Alert[], import('react').Dispatch<any>]} */
  const [jsonAlerts, setJsonAlerts] = useState([]);
  /** @type {[import('../types/alerts').Alert[], import('react').Dispatch<any>]} */
  const [emailAlerts, setEmailAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [mutationBusy, setMutationBusy] = useState(false);
  const [error, setError] = useState(/** @type {string | null} */ (null));
  const [jiraConfigured, setJiraConfigured] = useState(false);
  const [jiraSiteUrl, setJiraSiteUrl] = useState('');
  /** @type {[import('../constants/alertSource').AlertSource | null, import('react').Dispatch<any>]} */
  const [serverAlertSource, setServerAlertSource] = useState(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const loadJiraStatus = useCallback(async () => {
    try {
      const s = await alertsService.fetchJiraStatus();
      if (mounted.current) {
        setJiraConfigured(Boolean(s.configured && s.enabled));
        const u = typeof s.siteUrl === 'string' ? s.siteUrl.trim().replace(/\/$/, '') : '';
        setJiraSiteUrl(u);
      }
    } catch {
      if (mounted.current) {
        setJiraConfigured(false);
        setJiraSiteUrl('');
      }
    }
  }, []);

  const applyServerAlerts = useCallback((/** @type {import('../types/alerts').Alert[]} */ data) => {
    if (!mounted.current) return;
    if (data.length === 0) {
      setServerAlertSource(null);
      return;
    }
    const source = inferAlertSource(data);
    setServerAlertSource(source);
    if (source === ALERT_SOURCE.EMAIL) {
      setEmailAlerts(data);
    } else {
      setJsonAlerts(data);
    }
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const data = await alertsService.fetchAlerts();
      if (mounted.current) {
        applyServerAlerts(data);
        setError(null);
      }
      await loadJiraStatus();
    } catch (e) {
      if (mounted.current) setError(formatError(e));
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, [applyServerAlerts, loadJiraStatus]);

  useEffect(() => {
    refresh();
    const id = window.setInterval(refresh, POLL_INTERVAL_MS);
    return () => window.clearInterval(id);
  }, [refresh]);

  const ingestFromFile = useCallback(
    async (/** @type {File} */ file) => {
      setMutationBusy(true);
      setError(null);
      try {
        const text = await file.text();
        const ingested = await alertsService.ingestAlertsJson(text);
        if (mounted.current) {
          setJsonAlerts(ingested);
          setServerAlertSource(ALERT_SOURCE.JSON);
        }
        await loadJiraStatus();
      } catch (e) {
        if (mounted.current) setError(formatError(e));
      } finally {
        if (mounted.current) setMutationBusy(false);
      }
    },
    [loadJiraStatus]
  );

  const updateStatus = useCallback(
    async (/** @type {string} */ id, /** @type {string} */ status) => {
      setMutationBusy(true);
      setError(null);
      try {
        const updated = await alertsService.updateAlertStatus(id, status);
        if (mounted.current) {
          const source = inferAlertSource([updated]);
          if (source === ALERT_SOURCE.EMAIL) {
            setEmailAlerts([updated]);
          } else {
            setJsonAlerts([updated]);
          }
        }
      } catch (e) {
        if (mounted.current) setError(formatError(e));
      } finally {
        if (mounted.current) setMutationBusy(false);
      }
    },
    []
  );

  const dismissError = useCallback(() => setError(null), []);

  const ingestFromEmail = useCallback(
    async (/** @type {import('../types/inbox').InboxMessage} */ message) => {
      setMutationBusy(true);
      setError(null);
      try {
        const ingested = await alertsService.ingestAlertFromEmail(message);
        if (mounted.current) {
          setEmailAlerts([ingested]);
          setServerAlertSource(ALERT_SOURCE.EMAIL);
        }
        await loadJiraStatus();
      } catch (e) {
        if (mounted.current) setError(formatError(e));
      } finally {
        if (mounted.current) setMutationBusy(false);
      }
    },
    [loadJiraStatus]
  );

  const clearIngestedAlerts = useCallback(
    async (/** @type {import('../constants/alertSource').AlertSource} */ source) => {
      if (!window.confirm('Clear the current alert? This removes it from the dashboard.')) {
        return;
      }
      setMutationBusy(true);
      setError(null);
      try {
        if (source === ALERT_SOURCE.JSON) {
          setJsonAlerts([]);
        } else {
          setEmailAlerts([]);
        }
        if (serverAlertSource === source) {
          await alertsService.clearAllAlerts();
          if (mounted.current) setServerAlertSource(null);
        }
      } catch (e) {
        if (mounted.current) setError(formatError(e));
      } finally {
        if (mounted.current) setMutationBusy(false);
      }
    },
    [serverAlertSource]
  );

  const createJiraIssue = useCallback(
    async (/** @type {string} */ alertId, /** @type {Record<string, string>} */ payload = {}) => {
      setMutationBusy(true);
      setError(null);
      try {
        const result = await alertsService.createJiraIssueForAlert(alertId, payload);
        const updated = result.alert;
        if (mounted.current && updated) {
          const source = inferAlertSource([updated]);
          if (source === ALERT_SOURCE.EMAIL) {
            setEmailAlerts([updated]);
          } else {
            setJsonAlerts([updated]);
          }
        }
      } catch (e) {
        if (mounted.current) setError(formatError(e));
      } finally {
        if (mounted.current) setMutationBusy(false);
      }
    },
    []
  );

  return {
    jsonAlerts,
    emailAlerts,
    loading,
    error,
    mutationBusy,
    jiraConfigured,
    jiraSiteUrl,
    serverAlertSource,
    refresh,
    ingestFromFile,
    ingestFromEmail,
    updateStatus,
    clearIngestedAlerts,
    createJiraIssue,
    dismissError,
  };
}
