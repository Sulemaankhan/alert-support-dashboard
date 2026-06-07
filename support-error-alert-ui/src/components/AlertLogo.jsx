import './AlertLogo.css';

/**
 * @param {Object} props
 * @param {'sm' | 'md' | 'lg'} [props.size]
 * @param {string} [props.className]
 */
export function AlertLogo({ size = 'md', className = '' }) {
  const rootClass = ['alert-logo', `alert-logo--${size}`, className].filter(Boolean).join(' ');

  return (
    <span className={rootClass} aria-hidden="true">
      <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="2" y="2" width="28" height="28" rx="8" className="alert-logo__bg" />
        <path
          d="M16 9L22.2 21H9.8L16 9Z"
          className="alert-logo__triangle"
          strokeWidth="1.4"
          strokeLinejoin="round"
        />
        <path
          d="M16 13.25V17"
          className="alert-logo__mark"
          strokeWidth="1.75"
          strokeLinecap="round"
        />
        <circle cx="16" cy="19.75" r="1" className="alert-logo__dot" />
      </svg>
    </span>
  );
}
