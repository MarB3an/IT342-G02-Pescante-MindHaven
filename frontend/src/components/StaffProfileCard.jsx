import { useEffect, useState } from 'react';
import { authApi } from '../api/authApi';
import { extractErrorMessage, extractFieldErrors } from '../api/axiosClient';
import { useAuth } from '../hooks/useAuth';
import FormInput from './FormInput';

export default function StaffProfileCard({ onProfileUpdated }) {
  const { user, updateUserState } = useAuth();

  const [fullName, setFullName] = useState(user?.displayName || '');
  const [mobileNumber, setMobileNumber] = useState(user?.mobileNumber || '');
  const [bio, setBio] = useState(user?.bio || '');
  const [onCall, setOnCall] = useState(Boolean(user?.onCall));
  const [fieldErrors, setFieldErrors] = useState({});
  const [status, setStatus] = useState({ type: '', text: '' });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    async function loadProfile() {
      try {
        const profile = await authApi.getProfile();
        if (active && profile) {
          setFullName(profile.displayName || '');
          setMobileNumber(profile.mobileNumber || '');
          setBio(profile.bio || '');
          setOnCall(Boolean(profile.onCall));
          updateUserState(profile);
        }
      } catch {
        // Fallback to existing AuthContext user state
      }
    }
    loadProfile();
    return () => {
      active = false;
    };
  }, [updateUserState]);

  async function handleSubmit(e) {
    e.preventDefault();
    setStatus({ type: '', text: '' });

    const errors = {};
    if (!fullName.trim() || fullName.trim().length < 2) {
      errors.fullName = 'Full name must be at least 2 characters.';
    }
    if (bio.trim().length > 200) {
      errors.bio = 'Bio must not exceed 200 characters.';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setSaving(true);
    try {
      const payload = {
        fullName: fullName.trim(),
        mobileNumber: mobileNumber.trim(),
        bio: bio.trim(),
      };
      if (user?.role === 'ROLE_COUNSELOR') {
        payload.onCall = onCall;
      }
      const updated = await authApi.updateProfile(payload);
      updateUserState(updated);
      setStatus({ type: 'success', text: 'Your profile has been updated in PostgreSQL.' });
      if (onProfileUpdated) {
        onProfileUpdated(updated);
      }
    } catch (err) {
      setFieldErrors(extractFieldErrors(err));
      setStatus({
        type: 'error',
        text: extractErrorMessage(err, 'Could not update profile.'),
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="card">
      <h3>My Profile</h3>
      <p className="card-subtitle">
        View and update your personal staff details. Only you can modify your own profile.
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
        <div className="form-group">
          <span className="form-label">Institutional Email (Read-Only)</span>
          <input
            type="text"
            className="form-input"
            value={user?.email || ''}
            disabled
            readOnly
          />
        </div>

        <FormInput
          id="staffProfileFullName"
          label="Full Name"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          placeholder="Dr. Maria Santos"
          required
          error={fieldErrors.fullName}
        />

        <FormInput
          id="staffProfileMobile"
          label="Mobile Number"
          value={mobileNumber}
          onChange={(e) => setMobileNumber(e.target.value)}
          placeholder="+639171234567"
          hint="Optional E.164 format for urgent notifications."
          error={fieldErrors.mobileNumber}
        />

        <div className="form-group">
          <label className="form-label" htmlFor="staffProfileBio">
            Bio / Office Hours (Optional)
          </label>
          <textarea
            id="staffProfileBio"
            rows={3}
            maxLength={200}
            className="form-textarea"
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            placeholder="e.g., Campus Guidance Counselor · Mon–Fri 8:00 AM–5:00 PM"
          />
          <div className="field-hint">{bio.length}/200 characters</div>
          {fieldErrors.bio && <div className="field-error">{fieldErrors.bio}</div>}
        </div>

        {user?.role === 'ROLE_COUNSELOR' && (
          <div className="form-group">
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={onCall}
                onChange={(e) => setOnCall(e.target.checked)}
              />
              <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>
                Available On-Call for Emergency Alerts
              </span>
            </label>
          </div>
        )}

        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Saving Profile...' : 'Save Profile Changes'}
        </button>
      </form>
    </div>
  );
}
