import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { AVATAR_META } from '../utils/validators';

const ROLE_LABELS = {
  ROLE_STUDENT: 'Student Portal',
  ROLE_COUNSELOR: 'Counselor Portal',
  ROLE_ADMIN: 'System Admin Portal',
};

export default function AppLayout({ title, subtitle, navItems, activeTab, onTabChange, children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    setLoggingOut(true);
    await logout();
    navigate('/login', { replace: true });
  }

  const avatar = user?.avatarCode ? AVATAR_META[user.avatarCode] : null;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <div className="sidebar-brand">
            <span className="brand-dot">🌿</span>
            <div>
              <div style={{ fontWeight: 700, color: '#fff', lineHeight: 1.2 }}>MindHaven</div>
              <div className="sidebar-role">{ROLE_LABELS[user?.role] || 'Workspace'}</div>
            </div>
          </div>

          <nav className="sidebar-nav" aria-label="Role navigation">
            {navItems.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => onTabChange(item.id)}
                className={`nav-item ${activeTab === item.id ? 'active' : ''}`}
              >
                <span>{item.label}</span>
                {item.badge !== undefined && (
                  <span className="badge badge-neutral" style={{ fontSize: '0.7rem' }}>
                    {item.badge}
                  </span>
                )}
              </button>
            ))}
          </nav>
        </div>

        <div className="sidebar-footer">
          <div className="sidebar-user">
            <div className="sidebar-user-name">
              {avatar ? `${avatar.icon} ` : ''}
              {user?.displayName}
            </div>
            <div className="sidebar-user-meta">
              {user?.role === 'ROLE_STUDENT'
                ? 'Pseudonymous Identity Active'
                : user?.email || user?.role}
            </div>
          </div>
          <button
            type="button"
            className="btn btn-secondary btn-full btn-sm"
            onClick={handleLogout}
            disabled={loggingOut}
          >
            {loggingOut ? 'Signing out...' : 'Sign Out'}
          </button>
        </div>
      </aside>

      <div className="main-area">
        <header className="topbar">
          <div>
            <h1>{title}</h1>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <span className="badge badge-primary">{user?.role}</span>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}
