import './ErrorBanner.css';

export function ErrorBanner({ message, onDismiss }) {
  if (!message) return null;
  return (
    <div className="error-banner" role="alert">
      <span className="error-banner__text">{message}</span>
      {onDismiss ? (
        <button type="button" className="error-banner__dismiss" onClick={onDismiss} aria-label="Dismiss">
          ×
        </button>
      ) : null}
    </div>
  );
}
