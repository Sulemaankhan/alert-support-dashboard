import { NAV_ITEMS } from '../constants/nav.js';
import { JsonFileUpload } from './JsonFileUpload.jsx';
import './SiteHeader.css';

/**
 * @param {Object} props
 * @param {(file: File) => void | Promise<void>} props.onJsonSelected
 * @param {boolean} [props.jsonBusy] - disables file control while ingest / mutations run
 */
export function SiteHeader({ onJsonSelected, jsonBusy = false }) {
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <a href={`#${NAV_ITEMS[0].sectionId}`} className="site-header__brand">
          Error alert dashboard
        </a>
        <nav className="site-header__nav" aria-label="Primary">
          <ul className="site-header__nav-list">
            {NAV_ITEMS.map((item) => (
              <li key={item.sectionKey}>
                {item.sectionKey === 'LOAD_JSON' ? (
                  <div id={item.sectionId} className="site-header__load-json">
                    <JsonFileUpload
                      onFileSelected={onJsonSelected}
                      label={item.label}
                      className="json-upload--in-header"
                      disabled={jsonBusy}
                    />
                  </div>
                ) : (
                  <a className="site-header__nav-link" href={`#${item.sectionId}`}>
                    {item.label}
                  </a>
                )}
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </header>
  );
}
