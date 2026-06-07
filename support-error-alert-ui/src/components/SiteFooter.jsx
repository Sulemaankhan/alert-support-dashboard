import { APP_NAME, SUPPORT_CONTACT } from '../constants/branding.js';
import { AlertLogo } from './AlertLogo.jsx';
import './SiteFooter.css';

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <div className="site-footer__brand">
          <AlertLogo size="sm" />
          <p className="site-footer__product">{APP_NAME}</p>
        </div>
        <p className="site-footer__support">
          {SUPPORT_CONTACT.role}: <strong>{SUPPORT_CONTACT.email}</strong>
        </p>
      </div>
    </footer>
  );
}
