import { useEffect, useState } from 'react';
import { dashboardApi } from '../api/dashboardApi';
import { extractErrorMessage } from '../api/axiosClient';
import { useAuth } from '../hooks/useAuth';
import AppLayout from '../components/AppLayout';
import FormInput from '../components/FormInput';

export default function CounselorDashboard() {
  const { updateUserState } = useAuth();

  const [activeTab, setActiveTab] = useState('overview');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [onCall, setOnCall] = useState(false);
  const [mobileNumber, setMobileNumber] = useState('');
  const [statusMsg, setStatusMsg] = useState({ type: '', text: '' });
  const [saving, setSaving] = useState(false);

  async function loadDashboard() {
    setLoading(true);
    setError('');
    try {
      const res = await dashboardApi.getCounselorDashboard();
      setData(res);
      setOnCall(Boolean(res.counselor.onCall));
      setMobileNumber(res.counselor.mobileNumber || '');
      updateUserState(res.counselor);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load counselor dashboard.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadDashboard();
  }, []);

  async function handleSaveAvailability(e) {
    e.preventDefault();
    setStatusMsg({ type: '', text: '' });
    setSaving(true);
    try {
      const updated = await dashboardApi.updateCounselorOnCall({
        onCall,
        mobileNumber: mobileNumber.trim(),
      });
      setData((prev) => ({
        ...prev,
        counselor: updated,
      }));
      updateUserState(updated);
      setStatusMsg({
        type: 'success',
        text: `Availability updated: ${updated.onCall ? 'On-Call Active' : 'Off-Duty'}.`,
      });
      loadDashboard();
    } catch (err) {
      setStatusMsg({
        type: 'error',
        text: extractErrorMessage(err, 'Could not update on-call availability.'),
      });
    } finally {
      setSaving(false);
    }
  }

  const navItems = [
    { id: 'overview', label: 'Triage Overview' },
    { id: 'availability', label: 'On-Call Roster' },
    { id: 'rooms', label: 'Monitored Rooms', badge: data?.topicRooms?.length ?? 0 },
  ];

  return (
    <AppLayout
      title="Counselor Case Management Portal"
      subtitle="Authorized campus counselor workspace for crisis triage readiness and intervention tracking."
      navItems={navItems}
      activeTab={activeTab}
      onTabChange={setActiveTab}
    >
      {error && <div className="alert alert-error">{error}</div>}
      {loading && <div className="card">Loading counselor dashboard...</div>}

      {!loading && data && activeTab === 'overview' && (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-label">Registered Students</div>
              <div className="stat-value">{data.summary.activeStudents}</div>
              <div className="field-hint">Pseudonymous accounts</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">On-Call Counselors</div>
              <div className="stat-value">{data.summary.onCallCounselors}</div>
              <div className="field-hint">Active SMS alert recipients</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Active Topic Rooms</div>
              <div className="stat-value">{data.summary.activeTopicRooms}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Your Duty Status</div>
              <div style={{ marginTop: '0.5rem' }}>
                <span className={`badge ${data.counselor.onCall ? 'badge-success' : 'badge-neutral'}`}>
                  {data.counselor.onCall ? 'On-Call Active' : 'Off-Duty'}
                </span>
              </div>
            </div>
          </div>

          <div className="card">
            <h3>Crisis Triage Queue (SRS Feature 6)</h3>
            <p className="card-subtitle">
              Real-time AI-flagged Moderate/Amber and Acute/Red crisis incidents will stream here
              once the chat and Gemini triage engine are connected in later increments.
            </p>
            <div className="alert alert-info" style={{ marginBottom: 0 }}>
              No open crisis incidents at this time. Your counselor account is authenticated with{' '}
              <strong>ROLE_COUNSELOR</strong> and isolated from Student and Admin endpoints.
            </div>
          </div>
        </>
      )}

      {!loading && data && activeTab === 'availability' && (
        <div className="card" style={{ maxWidth: '580px' }}>
          <h3>On-Call Emergency Alert Settings</h3>
          <p className="card-subtitle">
            Configure your on-call status and E.164 mobile number for automated Acute/Red SMS
            escalations (SRS FR-027 &amp; FR-038).
          </p>

          {statusMsg.text && (
            <div className={`alert ${statusMsg.type === 'success' ? 'alert-success' : 'alert-error'}`}>
              {statusMsg.text}
            </div>
          )}

          <form onSubmit={handleSaveAvailability}>
            <FormInput
              id="mobileNumber"
              label="Emergency Alert Mobile Number"
              value={mobileNumber}
              onChange={(e) => setMobileNumber(e.target.value)}
              placeholder="+639171234567"
              hint="Format: E.164 cellular number for urgent after-hours SMS dispatch."
            />

            <div className="form-group">
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={onCall}
                  onChange={(e) => setOnCall(e.target.checked)}
                />
                <span style={{ fontWeight: 600, fontSize: '0.92rem' }}>
                  Mark myself as On-Call for emergency alerts
                </span>
              </label>
            </div>

            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Saving...' : 'Update Availability'}
            </button>
          </form>
        </div>
      )}

      {!loading && data && activeTab === 'rooms' && (
        <div className="card">
          <h3>Monitored Campus Peer Chatrooms</h3>
          <p className="card-subtitle">
            Active topic spaces currently configured for student peer discussions.
          </p>
          <div className="rooms-grid">
            {data.topicRooms.map((room) => (
              <div key={room.roomId} className="room-card">
                <h4>{room.roomName}</h4>
                <p>{room.description}</p>
                <span className="badge badge-primary">{room.categorySlug}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </AppLayout>
  );
}
