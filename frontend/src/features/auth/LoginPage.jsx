import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { extractErrorMessage, extractFieldErrors } from '../../api/axiosClient';
import { getDashboardPathForRole, isValidEmail } from '../../utils/validators';
import FormInput from '../../components/FormInput';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [errorMessage, setErrorMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setErrorMessage('');

    const errors = {};
    if (!email.trim()) {
      errors.email = 'Email is required.';
    } else if (!isValidEmail(email)) {
      errors.email = 'Please enter a valid email address.';
    }
    if (!password) {
      errors.password = 'Password is required.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);
    try {
      const user = await login({ email: email.trim(), password });
      navigate(getDashboardPathForRole(user.role), { replace: true });
    } catch (err) {
      setFieldErrors(extractFieldErrors(err));
      setErrorMessage(extractErrorMessage(err, 'Invalid email or password.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-hero">
        <div>
          <div className="brand-mark">
            <span className="brand-dot">🌿</span>
            <span>MindHaven</span>
          </div>
          <h1>A calm, stigma-free space for student peer support.</h1>
          <p>
            Sign in securely as a university student, campus counselor, or system administrator.
            You will automatically be directed to your role-specific MindHaven workspace.
          </p>
          <ul className="hero-points">
            <li>
              <strong>Zero-PII Student Pseudonymity:</strong> Student emails are stored only as
              salted HMAC-SHA256 hashes — never shown in peer spaces.
            </li>
            <li>
              <strong>Role-Based Security:</strong> Strict JWT + HttpOnly session protection across
              Student, Counselor, and Admin dashboards.
            </li>
          </ul>
        </div>
        <div style={{ fontSize: '0.82rem', color: '#99f6e4' }}>
          IT342-G02 · MindHaven Project Increment 1 (User Accounts &amp; Authentication)
        </div>
      </section>

      <section className="auth-card-wrap">
        <div className="auth-card">
          <h2>Welcome back</h2>
          <p className="auth-subtitle">Enter your registered MindHaven credentials to continue.</p>

          {errorMessage && (
            <div className="alert alert-error" role="alert">
              {errorMessage}
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            <FormInput
              id="email"
              label="Email Address"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="student@cit.edu"
              autoComplete="email"
              required
              error={fieldErrors.email}
            />

            <FormInput
              id="password"
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              autoComplete="current-password"
              required
              error={fieldErrors.password}
            />

            <button type="submit" className="btn btn-primary btn-full" disabled={submitting}>
              {submitting ? 'Signing in...' : 'Sign In'}
            </button>
          </form>

          <p style={{ marginTop: '1.25rem', fontSize: '0.9rem', color: 'var(--text-muted)' }}>
            New student on campus? <Link to="/register">Create a pseudonymous account</Link>
          </p>
        </div>
      </section>
    </div>
  );
}
