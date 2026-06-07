import { NAV_LABEL, NAV_SECTION } from '../constants/nav.js';
import './OverviewPanel.css';

export function OverviewPanel() {
  return (
    <section className="overview-panel" id={NAV_SECTION.HOME} aria-labelledby="overview-heading">
      <h2 id="overview-heading" className="overview-panel__title">
        Overview
      </h2>
      <p className="overview-panel__text">Choose how you want to load a support alert.</p>
      <div className="overview-panel__choices">
        <a className="overview-panel__card" href={`#${NAV_SECTION.JSON_ALERT}`}>
          <span className="overview-panel__card-title">{NAV_LABEL.JSON_ALERT}</span>
          <span className="overview-panel__card-sub">Upload a monitoring JSON export.</span>
        </a>
        <a className="overview-panel__card" href={`#${NAV_SECTION.EMAIL_ALERT}`}>
          <span className="overview-panel__card-title">{NAV_LABEL.EMAIL_ALERT}</span>
          <span className="overview-panel__card-sub">Search Gmail and pick a message.</span>
        </a>
      </div>
    </section>
  );
}
