import { Dashboard } from './components/Dashboard.jsx';
import { LoginPage } from './components/LoginPage.jsx';
import { useAuth } from './hooks/useAuth.jsx';
import './styles/buttons.css';
import './App.css';

export default function App() {
  const { user, loading, isGuest, authEnabled } = useAuth();

  if (loading) {
    return (
      <div className="app-loading" role="status">
        Loading…
      </div>
    );
  }

  if (authEnabled && !user && !isGuest) {
    return <LoginPage />;
  }

  return <Dashboard guestMode={Boolean(authEnabled && isGuest)} />;
}
