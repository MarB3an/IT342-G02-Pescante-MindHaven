import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getDashboardPathForRole } from '../utils/validators';

export default function UnauthorizedPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const homePath = user ? getDashboardPathForRole(user.role) : '/login';

  return (
    <div className="center-screen">
      <div className="card" style={{ maxWidth: '480px', textAlign: 'center' }}>
        <div className="badge badge-danger" style={{ marginBottom: '0.75rem' }}>
          403 · Access Restricted
        </div>
        <h2 style={{ margin: '0 0 0.5rem' }}>Unauthorized Page Access</h2>
        <p style={{ color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
          Your current account role (<strong>{user?.role || 'Guest'}</strong>) does not have
          permission to open this dashboard or invoke its endpoints.
        </p>
        <div className="actions-row" style={{ justifyContent: 'center' }}>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => navigate(homePath, { replace: true })}
          >
            Return to My Dashboard
          </button>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={async () => {
              await logout();
              navigate('/login', { replace: true });
            }}
          >
            Sign Out
          </button>
        </div>
      </div>
    </div>
  );
}
