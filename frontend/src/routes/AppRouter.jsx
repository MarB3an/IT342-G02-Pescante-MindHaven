import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getDashboardPathForRole } from '../utils/validators';
import ProtectedRoute from './ProtectedRoute';
import RoleRoute from './RoleRoute';
import LoginPage from '../features/auth/LoginPage';
import RegisterPage from '../features/auth/RegisterPage';
import StudentDashboard from '../pages/StudentDashboard';
import CounselorDashboard from '../pages/CounselorDashboard';
import AdminDashboard from '../pages/AdminDashboard';
import UnauthorizedPage from '../pages/UnauthorizedPage';

function HomeRedirect() {
  const { user, initializing } = useAuth();
  if (initializing) {
    return (
      <div className="center-screen">
        <div className="card">Loading MindHaven...</div>
      </div>
    );
  }
  return <Navigate to={user ? getDashboardPathForRole(user.role) : '/login'} replace />;
}

function GuestOnly({ children }) {
  const { user, initializing } = useAuth();
  if (initializing) {
    return (
      <div className="center-screen">
        <div className="card">Loading MindHaven...</div>
      </div>
    );
  }
  if (user) {
    return <Navigate to={getDashboardPathForRole(user.role)} replace />;
  }
  return children;
}

export default function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />

      <Route
        path="/login"
        element={
          <GuestOnly>
            <LoginPage />
          </GuestOnly>
        }
      />
      <Route
        path="/register"
        element={
          <GuestOnly>
            <RegisterPage />
          </GuestOnly>
        }
      />

      <Route element={<ProtectedRoute />}>
        <Route path="/unauthorized" element={<UnauthorizedPage />} />

        <Route element={<RoleRoute allowedRoles={['ROLE_STUDENT']} />}>
          <Route path="/student/dashboard" element={<StudentDashboard />} />
        </Route>

        <Route element={<RoleRoute allowedRoles={['ROLE_COUNSELOR']} />}>
          <Route path="/counselor/dashboard" element={<CounselorDashboard />} />
        </Route>

        <Route element={<RoleRoute allowedRoles={['ROLE_ADMIN']} />}>
          <Route path="/admin/dashboard" element={<AdminDashboard />} />
        </Route>
      </Route>

      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}
