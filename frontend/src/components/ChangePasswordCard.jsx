import { useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import { extractErrorMessage, extractFieldErrors } from '../api/axiosClient';
import { getPasswordChecks, isStrongPassword } from '../utils/validators';
import FormInput from './FormInput';

export default function ChangePasswordCard() {
  const { changePassword } = useAuth();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [status, setStatus] = useState({ type: '', text: '' });
  const [saving, setSaving] = useState(false);

  const checks = getPasswordChecks(newPassword);

  async function handleSubmit(e) {
    e.preventDefault();
    setStatus({ type: '', text: '' });

    const errors = {};
    if (!currentPassword) {
      errors.currentPassword = 'Current password is required.';
    }
    if (!newPassword) {
      errors.newPassword = 'New password is required.';
    } else if (!isStrongPassword(newPassword)) {
      errors.newPassword = 'New password must be 8+ chars with uppercase, lowercase, and a number.';
    } else if (newPassword === currentPassword) {
      errors.newPassword = 'New password must be different from your current password.';
    }
    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your new password.';
    } else if (newPassword !== confirmPassword) {
      errors.confirmPassword = 'New passwords do not match.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSaving(true);
    try {
      await changePassword({
        currentPassword,
        newPassword,
        confirmPassword,
      });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setStatus({
        type: 'success',
        text: 'Password changed successfully. Your previous password is no longer valid.',
      });
    } catch (err) {
      setFieldErrors(extractFieldErrors(err));
      setStatus({
        type: 'error',
        text: extractErrorMessage(err, 'Could not change password.'),
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="card">
      <h3>Change Password</h3>
      <p className="card-subtitle">
        Verify your current password and choose a strong new password. Other active sessions will be
        revoked automatically.
      </p>

      {status.text && (
        <div
          className={`alert ${status.type === 'success' ? 'alert-success' : 'alert-error'}`}
          role="alert"
        >
          {status.text}
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <FormInput
          id="currentPassword"
          label="Current Password"
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          placeholder="Enter your current password"
          autoComplete="current-password"
          required
          error={fieldErrors.currentPassword}
        />

        <FormInput
          id="changeNewPassword"
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
          <li className={checks.length ? 'ok' : ''}>{checks.length ? '✓' : '○'} 8–72 characters</li>
          <li className={checks.upper ? 'ok' : ''}>
            {checks.upper ? '✓' : '○'} 1 uppercase letter
          </li>
          <li className={checks.lower ? 'ok' : ''}>
            {checks.lower ? '✓' : '○'} 1 lowercase letter
          </li>
          <li className={checks.digit ? 'ok' : ''}>{checks.digit ? '✓' : '○'} 1 number</li>
        </ul>

        <FormInput
          id="changeConfirmPassword"
          label="Confirm New Password"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          placeholder="Re-enter your new password"
          autoComplete="new-password"
          required
          error={fieldErrors.confirmPassword}
        />

        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Updating Password...' : 'Update Password'}
        </button>
      </form>
    </div>
  );
}
