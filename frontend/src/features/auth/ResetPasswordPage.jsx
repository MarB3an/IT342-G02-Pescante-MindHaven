import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../../api/authApi';
import { extractErrorMessage, extractFieldErrors } from '../../api/axiosClient';
import { getPasswordChecks, isStrongPassword } from '../../utils/validators';
import FormInput from '../../components/FormInput';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const initialToken = searchParams.get('token') || '';

  const [token, setToken] = useState(initialToken);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [tokenStatus, setTokenStatus] = useState({
    checking: Boolean(initialToken),
    valid: !initialToken,
    message: '',
  });
  const [fieldErrors, setFieldErrors] = useState({});
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const checks = getPasswordChecks(newPassword);

  useEffect(() => {
    if (!initialToken) return;
    let active = true;
    async function checkToken() {
      try {
        const res = await authApi.validateResetToken(initialToken);
        if (active) {
          setTokenStatus({
            checking: false,
            valid: Boolean(res.valid),
            message: res.valid ? '' : res.message || 'Invalid or expired reset token.',
          });
        }
      } catch {
        if (active) {
          setTokenStatus({
            checking: false,
            valid: false,
            message: 'Could not validate this reset token.',
          });
        }
      }
    }
    checkToken();
    return () => {
      active = false;
    };
  }, [initialToken]);

  async function handleSubmit(e) {
    e.preventDefault();
    setErrorMessage('');
    setSuccessMessage('');

    const errors = {};
    if (!token.trim()) {
      errors.token = 'Reset token is required.';
    }
    if (!newPassword) {
      errors.newPassword = 'New password is required.';
    } else if (!isStrongPassword(newPassword)) {
      errors.newPassword = 'Password must be 8+ chars with uppercase, lowercase, and a number.';
    }
    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your new password.';
    } else if (newPassword !== confirmPassword) {
      errors.confirmPassword = 'Passwords do not match.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);
    try {
      const res = await authApi.resetPassword({
        token: token.trim(),
        newPassword,
        confirmPassword,
      });
      setSuccessMessage(res.message || 'Password reset successfully.');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      setFieldErrors(extractFieldErrors(err));
      setErrorMessage(extractErrorMessage(err, 'Failed to reset password.'));
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
          <h1>Choose a strong new password.</h1>
          <p>
            Once confirmed, your new password is secured with BCrypt (strength 12), this reset
            token is permanently invalidated, and any previous sessions are signed out.
          </p>
        </div>
        <div style={{ fontSize: '0.82rem', color: '#99f6e4' }}>
          IT342-G02 · MindHaven Project Increment 2 (Profile &amp; Password Management)
        </div>
      </section>

      <section className="auth-card-wrap">
        <div className="auth-card">
          <h2>Reset Password</h2>
          <p className="auth-subtitle">Enter a new strong password for your MindHaven account.</p>

          {tokenStatus.checking && (
            <div className="alert alert-info">Verifying your reset token...</div>
          )}

          {!tokenStatus.checking && !tokenStatus.valid && !successMessage && (
            <div className="alert alert-error" role="alert">
              {tokenStatus.message}{' '}
              <Link to="/forgot-password" style={{ fontWeight: 700 }}>
                Request a new reset link
              </Link>
              .
            </div>
          )}

          {errorMessage && (
            <div className="alert alert-error" role="alert">
              {errorMessage}
            </div>
          )}

          {successMessage ? (
            <div className="alert alert-success" role="status">
              <div style={{ marginBottom: '0.75rem' }}>{successMessage}</div>
              <Link to="/login" className="btn btn-primary btn-sm">
                Sign In with New Password →
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit} noValidate>
              {!initialToken && (
                <FormInput
                  id="resetToken"
                  label="Reset Token"
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                  placeholder="Paste your single-use reset token"
                  required
                  error={fieldErrors.token}
                />
              )}

              <FormInput
                id="newPassword"
                label="New Password"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Create a strong new password"
                autoComplete="new-password"
                required
                error={fieldErrors.newPassword}
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
                id="confirmNewPassword"
                label="Confirm New Password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Re-enter your new password"
                autoComplete="new-password"
                required
                error={fieldErrors.confirmPassword}
              />

              <button
                type="submit"
                className="btn btn-primary btn-full"
                disabled={submitting || tokenStatus.checking || !tokenStatus.valid}
              >
                {submitting ? 'Updating password...' : 'Reset Password'}
              </button>
            </form>
          )}

          <p style={{ marginTop: '1.25rem', fontSize: '0.9rem', color: 'var(--text-muted)' }}>
            <Link to="/login">Back to Sign In</Link>
          </p>
        </div>
      </section>
    </div>
  );
}
