import { NAV_LABEL, NAV_SECTION } from '../constants/nav.js';
import './OverviewPanel.css';

export function OverviewPanel({ guestMode = false }) {
  return (
    <section className="overview-panel" id={NAV_SECTION.HOME} aria-labelledby="overview-heading">
      <h2 id="overview-heading" className="overview-panel__title">
        Overview
      </h2>
      <p className="overview-panel__text">
        {guestMode
          ? 'Browse Overview, JSON alerts, and HealthCheck without signing in. Email alerts require Google SSO when you open that section.'
          : 'Choose how you want to load a support alert, or open HealthCheck for realtime APM.'}
      </p>
      <div className="overview-panel__choices">
        <a className="overview-panel__card" href={`#${NAV_SECTION.JSON_ALERT}`}>
          <span className="overview-panel__card-title">{NAV_LABEL.JSON_ALERT}</span>
          <span className="overview-panel__card-sub">Upload a monitoring JSON export.</span>
        </a>
        <a
          className={
            guestMode
              ? 'overview-panel__card overview-panel__card--locked'
              : 'overview-panel__card'
          }
          href={`#${NAV_SECTION.EMAIL_ALERT}`}
        >
          <span className="overview-panel__card-title">{NAV_LABEL.EMAIL_ALERT}</span>
          <span className="overview-panel__card-sub">
            {guestMode ? 'Sign in with Google to search Gmail.' : 'Search Gmail and pick a message.'}
          </span>
        </a>
        <a className="overview-panel__card" href={`#${NAV_SECTION.HEALTH_CHECK}`}>
          <span className="overview-panel__card-title">{NAV_LABEL.HEALTH_CHECK}</span>
          <span className="overview-panel__card-sub">
            Live APM: transactions, Apdex, latency, errors, alerts, heap, stack.
          </span>
        </a>
      </div>
    </section>
  );
}
