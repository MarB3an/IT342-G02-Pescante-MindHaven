import { useState } from 'react';
import { Link } from 'react-router-dom';
import { authApi } from '../../api/authApi';
import { extractErrorMessage, extractFieldErrors } from '../../api/axiosClient';
import { isValidEmail } from '../../utils/validators';
import FormInput from '../../components/FormInput';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [errorMessage, setErrorMessage] = useState('');
  const [result, setResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setErrorMessage('');

    const errors = {};
    if (!email.trim()) {
      errors.email = 'Email address is required.';
    } else if (!isValidEmail(email)) {
      errors.email = 'Please enter a valid email address.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);
    try {
      const data = await authApi.forgotPassword({ email: email.trim() });
      setResult(data);
    } catch (err) {
      setFieldErrors(extractFieldErrors(err));
      setErrorMessage(extractErrorMessage(err, 'Could not process your password reset request.'));
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
          <h1>Recover your account safely and privately.</h1>
          <p>
            Enter the campus or institutional email associated with your MindHaven account. We will
            issue a secure, single-use reset token that expires in 15 minutes.
          </p>
          <ul className="hero-points">
            <li>
              <strong>Privacy-Preserving Lookup:</strong> Student emails are matched using one-way
              HMAC-SHA256 digests without exposing plaintext PII.
            </li>
            <li>
              <strong>Single-Use Expiring Tokens:</strong> Reset tokens are stored SHA-256 hashed
              and automatically revoke previous sessions upon reset.
            </li>
          </ul>
        </div>
        <div style={{ fontSize: '0.82rem', color: '#99f6e4' }}>
          IT342-G02 · MindHaven Project Increment 2 (Profile &amp; Password Management)
        </div>
      </section>

      <section className="auth-card-wrap">
        <div className="auth-card">
          <h2>Forgot Password</h2>
          <p className="auth-subtitle">
            Enter your registered email address to request a password reset link.
          </p>

          {errorMessage && (
            <div className="alert alert-error" role="alert">
              {errorMessage}
            </div>
          )}

          {result && (
            <div className="alert alert-success" role="status">
              <div>{result.message}</div>
              {result.resetToken && (
                <div
                  style={{
                    marginTop: '0.75rem',
                    paddingTop: '0.75rem',
                    borderTop: '1px solid rgba(15, 118, 110, 0.25)',
                  }}
                >
                  <div style={{ fontSize: '0.8rem', fontWeight: 700, marginBottom: '0.35rem' }}>
                    Local Testing Recovery Link (Single-Use · {result.expiresInMinutes} min):
                  </div>
                  <code
                    style={{
                      display: 'block',
                      wordBreak: 'break-all',
                      fontSize: '0.76rem',
                      padding: '0.4rem 0.5rem',
                      background: 'rgba(255,255,255,0.75)',
                      borderRadius: '6px',
                      marginBottom: '0.6rem',
                    }}
                  >
                    {result.resetToken}
                  </code>
                  <Link
                    to={result.resetPath || `/reset-password?token=${encodeURIComponent(result.resetToken)}`}
                    className="btn btn-primary btn-sm"
                  >
                    Continue to Reset Password →
                  </Link>
                </div>
              )}
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            <FormInput
              id="reset-email"
              label="Registered Email Address"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="student@cit.edu"
              autoComplete="email"
              required
              error={fieldErrors.email}
            />

            <button type="submit" className="btn btn-primary btn-full" disabled={submitting}>
              {submitting ? 'Generating reset link...' : 'Send Reset Link'}
            </button>
          </form>

          <p style={{ marginTop: '1.25rem', fontSize: '0.9rem', color: 'var(--text-muted)' }}>
            Remembered your password? <Link to="/login">Back to Sign In</Link>
          </p>
        </div>
      </section>
    </div>
  );
}
