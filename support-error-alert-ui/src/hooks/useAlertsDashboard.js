import { useCallback, useEffect, useRef, useState } from 'react';
import * as alertsService from '../services/alertsService.js';

const POLL_INTERVAL_MS = 15000;

function formatError(e) {
  return e instanceof Error ? e.message : String(e);
}

export function useAlertsDashboard() {
  /** @type {[import('../types/alerts').Alert[], import('react').Dispatch<any>]} */
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [mutationBusy, setMutationBusy] = useState(false);
  const [error, setError] = useState(/** @type {string | null} */ (null));
  const [jiraConfigured, setJiraConfigured] = useState(false);
  const [jiraSiteUrl, setJiraSiteUrl] = useState('');
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

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const data = await alertsService.fetchAlerts();
      if (mounted.current) {
        setAlerts(data);
        setError(null);
      }
      await loadJiraStatus();
    } catch (e) {
      if (mounted.current) setError(formatError(e));
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, [loadJiraStatus]);

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
        await alertsService.ingestAlertsJson(text);
        await refresh();
      } catch (e) {
        if (mounted.current) setError(formatError(e));
      } finally {
        if (mounted.current) setMutationBusy(false);
      }
    },
    [refresh]
  );

  const updateStatus = useCallback(
    async (/** @type {string} */ id, /** @type {string} */ status) => {
      setMutationBusy(true);
      setError(null);
      try {
        await alertsService.updateAlertStatus(id, status);
        await refresh();
      } catch (e) {
        if (mounted.current) setError(formatError(e));
      } finally {
        if (mounted.current) setMutationBusy(false);
      }
    },
    [refresh]
  );

  const dismissError = useCallback(() => setError(null), []);

  const clearIngestedAlerts = useCallback(async () => {
    if (
      !window.confirm(
        'Remove all alerts loaded from JSON? This clears the server store and cannot be undone.'
      )
    ) {
      return;
    }
    setMutationBusy(true);
    setError(null);
    try {
      await alertsService.clearAllAlerts();
      await refresh();
    } catch (e) {
      if (mounted.current) setError(formatError(e));
    } finally {
      if (mounted.current) setMutationBusy(false);
    }
  }, [refresh]);

  const createJiraIssue = useCallback(
    async (/** @type {string} */ alertId, /** @type {Record<string, string>} */ payload = {}) => {
      setMutationBusy(true);
      setError(null);
      try {
        await alertsService.createJiraIssueForAlert(alertId, payload);
        await refresh();
      } catch (e) {
        if (mounted.current) setError(formatError(e));
      } finally {
        if (mounted.current) setMutationBusy(false);
      }
    },
    [refresh]
  );

  return {
    alerts,
    loading,
    error,
    mutationBusy,
    jiraConfigured,
    jiraSiteUrl,
    refresh,
    ingestFromFile,
    updateStatus,
    clearIngestedAlerts,
    createJiraIssue,
    dismissError,
  };
}
