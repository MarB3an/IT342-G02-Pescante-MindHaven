import { useEffect, useState } from 'react';
import { dashboardApi } from '../api/dashboardApi';
import { extractErrorMessage } from '../api/axiosClient';
import { useAuth } from '../hooks/useAuth';
import { AVATAR_META } from '../utils/validators';
import AppLayout from '../components/AppLayout';
import CalmToolkitPreview from '../components/CalmToolkitPreview';
import FormInput from '../components/FormInput';

export default function StudentDashboard() {
  const { updateUserState } = useAuth();

  const [activeTab, setActiveTab] = useState('overview');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [pseudonymInput, setPseudonymInput] = useState('');
  const [avatarInput, setAvatarInput] = useState('sparrow');
  const [profileStatus, setProfileStatus] = useState({ type: '', text: '' });
  const [savingProfile, setSavingProfile] = useState(false);

  async function loadDashboard() {
    setLoading(true);
    setError('');
    try {
      const res = await dashboardApi.getStudentDashboard();
      setData(res);
      setPseudonymInput(res.profile.displayName || '');
      setAvatarInput(res.profile.avatarCode || 'sparrow');
      updateUserState(res.profile);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load student dashboard.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadDashboard();
  }, []);

  async function handleSaveProfile(e) {
    e.preventDefault();
    setProfileStatus({ type: '', text: '' });
    setSavingProfile(true);
    try {
      const updated = await dashboardApi.updateStudentProfile({
        pseudonymHandle: pseudonymInput.trim(),
        avatarCode: avatarInput,
      });
      setData((prev) => ({ ...prev, profile: updated }));
      updateUserState(updated);
      setProfileStatus({ type: 'success', text: 'Pseudonymous profile updated in database.' });
    } catch (err) {
      setProfileStatus({ type: 'error', text: extractErrorMessage(err, 'Could not update profile.') });
    } finally {
      setSavingProfile(false);
    }
  }

  async function handleRegenerate() {
    setProfileStatus({ type: '', text: '' });
    setSavingProfile(true);
    try {
      const updated = await dashboardApi.regeneratePseudonym();
      setPseudonymInput(updated.displayName);
      setData((prev) => ({ ...prev, profile: updated }));
      updateUserState(updated);
      setProfileStatus({ type: 'success', text: `Generated new handle: ${updated.displayName}` });
    } catch (err) {
      setProfileStatus({ type: 'error', text: extractErrorMessage(err, 'Could not regenerate handle.') });
    } finally {
      setSavingProfile(false);
    }
  }

  const navItems = [
    { id: 'overview', label: 'Overview' },
    { id: 'profile', label: 'Pseudonymous Profile' },
    { id: 'rooms', label: 'Topic Rooms', badge: data?.activeTopicRooms?.length ?? 0 },
    { id: 'toolkit', label: 'Calm Toolkit' },
  ];

  const profile = data?.profile;
  const avatarInfo = AVATAR_META[profile?.avatarCode] || AVATAR_META.sparrow;

  return (
    <AppLayout
      title="Student Peer Support Space"
      subtitle="Protected pseudonymous environment — your real campus email is never exposed."
      navItems={navItems}
      activeTab={activeTab}
      onTabChange={setActiveTab}
    >
      {error && <div className="alert alert-error">{error}</div>}
      {loading && <div className="card">Loading your MindHaven space...</div>}

      {!loading && data && activeTab === 'overview' && (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-label">Your Pseudonym</div>
              <div className="stat-value" style={{ fontSize: '1.35rem' }}>
                {avatarInfo.icon} {profile.displayName}
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Privacy Protection</div>
              <div className="stat-value" style={{ fontSize: '1.1rem', color: 'var(--success)' }}>
                Zero-PII Active
              </div>
              <div className="field-hint">{data.privacyStatus.hashingAlgorithm}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Active Topic Rooms</div>
              <div className="stat-value">{data.activeTopicRooms.length}</div>
              <div className="field-hint">Live chat launching in Increment 2</div>
            </div>
          </div>

          <div className="card">
            <h3>Calm Space Quick Access</h3>
            <p className="card-subtitle">
              Take a moment to ground yourself anytime (SRS Feature 4 preview).
            </p>
            <CalmToolkitPreview />
          </div>

          <div className="card">
            <h3>Available Support Topic Categories</h3>
            <p className="card-subtitle">
              Loaded directly from the MindHaven database (managed by System Administrators).
            </p>
            <div className="rooms-grid">
              {data.activeTopicRooms.map((room) => (
                <div key={room.roomId} className="room-card">
                  <h4>{room.roomName}</h4>
                  <p>{room.description}</p>
                  <span className="badge badge-primary">WebSocket Chat · Increment 2</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {!loading && data && activeTab === 'profile' && (
        <div className="card" style={{ maxWidth: '640px' }}>
          <h3>Customize Pseudonymous Identity (FR-006)</h3>
          <p className="card-subtitle">
            Choose a calming pseudonym and avatar. Real names, emails, and student numbers are
            blocked automatically to keep you anonymous.
          </p>

          {profileStatus.text && (
            <div className={`alert ${profileStatus.type === 'success' ? 'alert-success' : 'alert-error'}`}>
              {profileStatus.text}
            </div>
          )}

          <form onSubmit={handleSaveProfile}>
            <FormInput
              id="pseudonymHandle"
              label="Pseudonym Handle"
              value={pseudonymInput}
              onChange={(e) => setPseudonymInput(e.target.value)}
              hint="4-30 characters, starts with a letter, no email or numeric ID patterns."
              required
            />

            <div className="form-group">
              <span className="form-label">Avatar Icon</span>
              <div className="avatar-grid">
                {(data.availableAvatars || Object.keys(AVATAR_META)).map((code) => (
                  <button
                    key={code}
                    type="button"
                    onClick={() => setAvatarInput(code)}
                    className={`avatar-option ${avatarInput === code ? 'selected' : ''}`}
                  >
                    <span style={{ fontSize: '1.35rem' }}>{AVATAR_META[code]?.icon || '🌿'}</span>
                    <span>{AVATAR_META[code]?.label || code}</span>
                  </button>
                ))}
              </div>
            </div>

            <div className="actions-row">
              <button type="submit" className="btn btn-primary" disabled={savingProfile}>
                {savingProfile ? 'Saving...' : 'Save Changes'}
              </button>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={handleRegenerate}
                disabled={savingProfile}
              >
                Regenerate Random Handle
              </button>
            </div>
          </form>
        </div>
      )}

      {!loading && data && activeTab === 'rooms' && (
        <div className="card">
          <h3>Topic-Based Peer Chatrooms Directory</h3>
          <p className="card-subtitle">
            Real-time STOMP WebSocket messaging will connect directly into these database-managed
            rooms in Increment 2.
          </p>
          <div className="rooms-grid">
            {data.activeTopicRooms.map((room) => (
              <div key={room.roomId} className="room-card">
                <h4>{room.roomName}</h4>
                <p>{room.description}</p>
                <span className="badge badge-success">Active Room</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {!loading && data && activeTab === 'toolkit' && (
        <div className="card">
          <h3>In-App De-Escalation Toolkit</h3>
          <p className="card-subtitle">
            Interactive box breathing and verified crisis support numbers.
          </p>
          <CalmToolkitPreview />
          <div className="alert alert-info" style={{ marginTop: '1rem', marginBottom: 0 }}>
            <strong>5-4-3-2-1 Sensory Grounding:</strong> Name 5 things you see, 4 things you can
            touch, 3 things you hear, 2 things you smell, and 1 thing you appreciate right now.
          </div>
        </div>
      )}
    </AppLayout>
  );
}
