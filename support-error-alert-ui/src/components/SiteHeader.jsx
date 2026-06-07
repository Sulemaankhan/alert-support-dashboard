import { APP_NAME, APP_NAME_CLASS } from '../constants/branding.js';
import { NAV_ITEMS, NAV_VIEW_BY_KEY } from '../constants/nav.js';
import { AlertLogo } from './AlertLogo.jsx';
import './SiteHeader.css';

/**
 * @param {Object} props
 * @param {{ email: string, displayName?: string }} props.user
 * @param {() => void | Promise<void>} props.onSignOut
 * @param {'home' | 'json' | 'email'} [props.activeView]
 */
export function SiteHeader({ user, onSignOut, activeView = 'home' }) {
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <a
          href={`#${NAV_ITEMS[0].sectionId}`}
          className={`site-header__brand ${APP_NAME_CLASS}`}
          title={APP_NAME}
        >
          <AlertLogo size="sm" />
          <span className="site-header__brand-text">{APP_NAME}</span>
        </a>
        <div className="site-header__right">
          <nav className="site-header__nav" aria-label="Primary">
            <ul className="site-header__nav-list">
              {NAV_ITEMS.map((item) => {
                const isActive = NAV_VIEW_BY_KEY[item.sectionKey] === activeView;
                return (
                  <li key={item.sectionKey}>
                    <a
                      className={
                        isActive
                          ? 'site-header__nav-link site-header__nav-link--active'
                          : 'site-header__nav-link'
                      }
                      href={`#${item.sectionId}`}
                      aria-current={isActive ? 'page' : undefined}
                    >
                      {item.label}
                    </a>
                  </li>
                );
              })}
            </ul>
          </nav>
          <div className="site-header__user">
            <span className="site-header__email" title={user.email}>
              {user.displayName || user.email}
            </span>
            <button type="button" className="btn site-header__sign-out" onClick={onSignOut}>
              Sign out
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}
