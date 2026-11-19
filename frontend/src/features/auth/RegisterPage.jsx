import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { extractErrorMessage, extractFieldErrors } from '../../api/axiosClient';
import {
  AVATAR_META,
  getDashboardPathForRole,
  getPasswordChecks,
  isCampusEmail,
  isStrongPassword,
  isValidEmail,
} from '../../utils/validators';
import FormInput from '../../components/FormInput';

const AVATAR_KEYS = Object.keys(AVATAR_META);

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [preferredAvatar, setPreferredAvatar] = useState('sparrow');
  const [fieldErrors, setFieldErrors] = useState({});
  const [errorMessage, setErrorMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const checks = getPasswordChecks(password);

  async function handleSubmit(e) {
    e.preventDefault();
    setErrorMessage('');

    const errors = {};
    if (!email.trim()) {
      errors.email = 'Campus email is required.';
    } else if (!isValidEmail(email)) {
      errors.email = 'Please enter a valid email address.';
    } else if (!isCampusEmail(email)) {
      errors.email = 'Please use an authorized university email (e.g., @cit.edu or *.edu).';
    }

    if (!password) {
      errors.password = 'Password is required.';
    } else if (!isStrongPassword(password)) {
      errors.password = 'Password must be 8+ chars with uppercase, lowercase, and a number.';
    }

    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your password.';
    } else if (password !== confirmPassword) {
      errors.confirmPassword = 'Passwords do not match.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);
    try {
      const user = await register({
        email: email.trim(),
        password,
        confirmPassword,
        preferredAvatar,
      });
      navigate(getDashboardPathForRole(user.role), { replace: true });
    } catch (err) {
      setFieldErrors(extractFieldErrors(err));
      setErrorMessage(extractErrorMessage(err, 'Registration failed. Please check your inputs.'));
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
          <h1>Join anonymously with your campus identity protected.</h1>
          <p>
            Upon registration, MindHaven hashes your university email with HMAC-SHA256, secures your
            password with BCrypt, and generates a calming pseudonym (like QuietSparrow42).
          </p>
          <ul className="hero-points">
            <li>
              <strong>No Plaintext Emails Stored:</strong> Your real name and email are never saved
              in your public profile.
            </li>
            <li>
              <strong>Instant Student Access:</strong> Customize your pseudonym and avatar anytime
              from your Student Dashboard.
            </li>
          </ul>
        </div>
        <div style={{ fontSize: '0.82rem', color: '#99f6e4' }}>
          Authorized Campus Domains: @cit.edu · *.edu · *.edu.ph
        </div>
      </section>

      <section className="auth-card-wrap">
        <div className="auth-card">
          <h2>Create Student Account</h2>
          <p className="auth-subtitle">
            Register with your university email to get a private pseudonymous profile.
          </p>

          {errorMessage && (
            <div className="alert alert-error" role="alert">
              {errorMessage}
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            <FormInput
              id="email"
              label="University Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="juan.delacruz@cit.edu"
              autoComplete="email"
              hint="Used only as a one-way cryptographic hash for login and duplicate prevention."
              required
              error={fieldErrors.email}
            />

            <FormInput
              id="password"
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Create a strong password"
              autoComplete="new-password"
              required
              error={fieldErrors.password}
            />
            <ul className="password-rules" style={{ marginBottom: '1rem' }}>
              <li className={checks.length ? 'ok' : ''}>
                {checks.length ? '✓' : '○'} 8–72 characters
              </li>
              <li className={checks.upper ? 'ok' : ''}>
                {checks.upper ? '✓' : '○'} 1 uppercase letter
              </li>
              <li className={checks.lower ? 'ok' : ''}>
                {checks.lower ? '✓' : '○'} 1 lowercase letter
              </li>
              <li className={checks.digit ? 'ok' : ''}>
                {checks.digit ? '✓' : '○'} 1 number
              </li>
            </ul>

            <FormInput
              id="confirmPassword"
              label="Confirm Password"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="Re-enter your password"
              autoComplete="new-password"
              required
              error={fieldErrors.confirmPassword}
            />

            <div className="form-group">
              <span className="form-label">Choose Starting Avatar</span>
              <div className="avatar-grid">
                {AVATAR_KEYS.map((key) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setPreferredAvatar(key)}
                    className={`avatar-option ${preferredAvatar === key ? 'selected' : ''}`}
                  >
                    <span style={{ fontSize: '1.25rem' }}>{AVATAR_META[key].icon}</span>
                    <span>{AVATAR_META[key].label}</span>
                  </button>
                ))}
              </div>
            </div>

            <button type="submit" className="btn btn-primary btn-full" disabled={submitting}>
              {submitting ? 'Creating account...' : 'Create Student Account'}
            </button>
          </form>

          <p style={{ marginTop: '1.25rem', fontSize: '0.9rem', color: 'var(--text-muted)' }}>
            Already registered? <Link to="/login">Sign in here</Link>
          </p>
        </div>
      </section>
    </div>
  );
}
