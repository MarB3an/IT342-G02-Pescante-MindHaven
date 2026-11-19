import { useEffect, useState } from 'react';
import { dashboardApi } from '../api/dashboardApi';
import { extractErrorMessage, extractFieldErrors } from '../api/axiosClient';
import { isStrongPassword, isValidEmail } from '../utils/validators';
import AppLayout from '../components/AppLayout';
import FormInput from '../components/FormInput';

export default function AdminDashboard() {
  const [activeTab, setActiveTab] = useState('staff');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Create staff form state
  const [staffForm, setStaffForm] = useState({
    fullName: '',
    email: '',
    password: '',
    role: 'ROLE_COUNSELOR',
    mobileNumber: '',
    onCall: true,
  });
  const [staffFieldErrors, setStaffFieldErrors] = useState({});
  const [staffFeedback, setStaffFeedback] = useState({ type: '', text: '' });
  const [creatingStaff, setCreatingStaff] = useState(false);

  // Create topic room form state
  const [roomForm, setRoomForm] = useState({ roomName: '', description: '' });
  const [roomFeedback, setRoomFeedback] = useState({ type: '', text: '' });
  const [creatingRoom, setCreatingRoom] = useState(false);

  async function loadDashboard() {
    setLoading(true);
    setError('');
    try {
      const res = await dashboardApi.getAdminDashboard();
      setData(res);
    } catch (err) {
      setError(extractErrorMessage(err, 'Failed to load administrator dashboard.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadDashboard();
  }, []);

  async function handleCreateStaff(e) {
    e.preventDefault();
    setStaffFeedback({ type: '', text: '' });

    const errs = {};
    if (!staffForm.fullName.trim()) errs.fullName = 'Full name is required.';
    if (!isValidEmail(staffForm.email)) errs.email = 'Valid email is required.';
    if (!isStrongPassword(staffForm.password)) {
      errs.password = 'Password must be 8+ chars with uppercase, lowercase, and a number.';
    }
    if (Object.keys(errs).length > 0) {
      setStaffFieldErrors(errs);
      return;
    }

    setStaffFieldErrors({});
    setCreatingStaff(true);
    try {
      await dashboardApi.createStaff({
        ...staffForm,
        fullName: staffForm.fullName.trim(),
        email: staffForm.email.trim(),
        mobileNumber: staffForm.mobileNumber.trim(),
      });
      setStaffForm({
        fullName: '',
        email: '',
        password: '',
        role: 'ROLE_COUNSELOR',
        mobileNumber: '',
        onCall: true,
      });
      setStaffFeedback({ type: 'success', text: 'Staff account created and saved to PostgreSQL.' });
      await loadDashboard();
    } catch (err) {
      setStaffFieldErrors(extractFieldErrors(err));
      setStaffFeedback({
        type: 'error',
        text: extractErrorMessage(err, 'Failed to create staff account.'),
      });
    } finally {
      setCreatingStaff(false);
    }
  }

  async function handleToggleStaff(staff, changes) {
    setStaffFeedback({ type: '', text: '' });
    try {
      await dashboardApi.updateStaffStatus(staff.staffId, changes);
      await loadDashboard();
    } catch (err) {
      setStaffFeedback({
        type: 'error',
        text: extractErrorMessage(err, 'Could not update staff status.'),
      });
    }
  }

  async function handleCreateRoom(e) {
    e.preventDefault();
    setRoomFeedback({ type: '', text: '' });
    setCreatingRoom(true);
    try {
      await dashboardApi.createTopicRoom({
        roomName: roomForm.roomName.trim(),
        description: roomForm.description.trim(),
      });
      setRoomForm({ roomName: '', description: '' });
      setRoomFeedback({ type: 'success', text: 'Topic chatroom category created.' });
      await loadDashboard();
    } catch (err) {
      setRoomFeedback({
        type: 'error',
        text: extractErrorMessage(err, 'Could not create topic room.'),
      });
    } finally {
      setCreatingRoom(false);
    }
  }

  async function handleToggleRoom(roomId) {
    try {
      await dashboardApi.toggleTopicRoom(roomId);
      await loadDashboard();
    } catch (err) {
      setRoomFeedback({
        type: 'error',
        text: extractErrorMessage(err, 'Could not update topic room.'),
      });
    }
  }

  const navItems = [
    { id: 'staff', label: 'Staff & Counselors', badge: data?.staffUsers?.length ?? 0 },
    { id: 'rooms', label: 'Topic Rooms', badge: data?.topicRooms?.length ?? 0 },
    { id: 'audit', label: 'Audit Logs', badge: data?.auditLogs?.length ?? 0 },
  ];

  return (
    <AppLayout
      title="System Administration Portal"
      subtitle="Manage counselor accounts, topic chatroom categories, and security audit logs (SRS Feature 7)."
      navItems={navItems}
      activeTab={activeTab}
      onTabChange={setActiveTab}
    >
      {error && <div className="alert alert-error">{error}</div>}
      {loading && <div className="card">Loading administration metrics...</div>}

      {!loading && data && (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-label">Student Accounts</div>
              <div className="stat-value">{data.metrics.totalStudents}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Counselors</div>
              <div className="stat-value">{data.metrics.totalCounselors}</div>
              <div className="field-hint">{data.metrics.onCallCounselors} currently on-call</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Administrators</div>
              <div className="stat-value">{data.metrics.totalAdmins}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Active Topic Rooms</div>
              <div className="stat-value">{data.metrics.activeTopicRooms}</div>
            </div>
          </div>

          {activeTab === 'staff' && (
            <div className="two-col">
              <div className="card">
                <h3>Provision Counselor / Staff Account (FR-038)</h3>
                <p className="card-subtitle">
                  Create real database-persisted counselor or administrator credentials with BCrypt
                  password hashing.
                </p>

                {staffFeedback.text && (
                  <div
                    className={`alert ${
                      staffFeedback.type === 'success' ? 'alert-success' : 'alert-error'
                    }`}
                  >
                    {staffFeedback.text}
                  </div>
                )}

                <form onSubmit={handleCreateStaff} noValidate>
                  <FormInput
                    id="fullName"
                    label="Full Name"
                    value={staffForm.fullName}
                    onChange={(e) => setStaffForm({ ...staffForm, fullName: e.target.value })}
                    placeholder="Dr. Maria Santos"
                    required
                    error={staffFieldErrors.fullName}
                  />
                  <FormInput
                    id="staffEmail"
                    label="Institutional Email"
                    type="email"
                    value={staffForm.email}
                    onChange={(e) => setStaffForm({ ...staffForm, email: e.target.value })}
                    placeholder="maria.santos@cit.edu"
                    required
                    error={staffFieldErrors.email}
                  />
                  <FormInput
                    id="staffPassword"
                    label="Temporary Password"
                    type="password"
                    value={staffForm.password}
                    onChange={(e) => setStaffForm({ ...staffForm, password: e.target.value })}
                    placeholder="Min 8 chars, upper, lower, digit"
                    required
                    error={staffFieldErrors.password}
                  />
                  <div className="form-group">
                    <label className="form-label" htmlFor="staffRole">
                      Assigned Role *
                    </label>
                    <select
                      id="staffRole"
                      className="form-select"
                      value={staffForm.role}
                      onChange={(e) => setStaffForm({ ...staffForm, role: e.target.value })}
                    >
                      <option value="ROLE_COUNSELOR">Campus Counselor (ROLE_COUNSELOR)</option>
                      <option value="ROLE_ADMIN">System Administrator (ROLE_ADMIN)</option>
                    </select>
                  </div>
                  <FormInput
                    id="staffMobile"
                    label="Mobile Number (for SMS Alerts)"
                    value={staffForm.mobileNumber}
                    onChange={(e) => setStaffForm({ ...staffForm, mobileNumber: e.target.value })}
                    placeholder="+639171234567"
                    error={staffFieldErrors.mobileNumber}
                  />
                  {staffForm.role === 'ROLE_COUNSELOR' && (
                    <div className="form-group">
                      <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <input
                          type="checkbox"
                          checked={staffForm.onCall}
                          onChange={(e) => setStaffForm({ ...staffForm, onCall: e.target.checked })}
                        />
                        <span style={{ fontSize: '0.88rem', fontWeight: 600 }}>
                          Place on active on-call SMS roster
                        </span>
                      </label>
                    </div>
                  )}
                  <button type="submit" className="btn btn-primary btn-full" disabled={creatingStaff}>
                    {creatingStaff ? 'Creating Account...' : 'Create Staff Account'}
                  </button>
                </form>
              </div>

              <div className="card">
                <h3>Registered Staff Accounts</h3>
                <p className="card-subtitle">Live records from mindhaven.staff_user.</p>
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Staff Member</th>
                        <th>Role</th>
                        <th>Status</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.staffUsers.map((staff) => (
                        <tr key={staff.staffId}>
                          <td>
                            <div style={{ fontWeight: 600 }}>{staff.fullName}</div>
                            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                              {staff.email}
                            </div>
                          </td>
                          <td>
                            <span className="badge badge-primary">{staff.role}</span>
                          </td>
                          <td>
                            <span
                              className={`badge ${
                                staff.active ? 'badge-success' : 'badge-danger'
                              }`}
                            >
                              {staff.active ? 'Active' : 'Disabled'}
                            </span>
                            {staff.role === 'ROLE_COUNSELOR' && staff.onCall && (
                              <span
                                className="badge badge-warning"
                                style={{ marginLeft: '0.35rem' }}
                              >
                                On-Call
                              </span>
                            )}
                          </td>
                          <td>
                            <div className="actions-row">
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                onClick={() =>
                                  handleToggleStaff(staff, { active: !staff.active })
                                }
                              >
                                {staff.active ? 'Disable' : 'Enable'}
                              </button>
                              {staff.role === 'ROLE_COUNSELOR' && staff.active && (
                                <button
                                  type="button"
                                  className="btn btn-secondary btn-sm"
                                  onClick={() =>
                                    handleToggleStaff(staff, { onCall: !staff.onCall })
                                  }
                                >
                                  {staff.onCall ? 'Off-Call' : 'Set On-Call'}
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'rooms' && (
            <div className="two-col">
              <div className="card">
                <h3>Add Topic Chatroom Category (FR-037)</h3>
                <p className="card-subtitle">
                  Categories appear immediately on Student and Counselor dashboards.
                </p>

                {roomFeedback.text && (
                  <div
                    className={`alert ${
                      roomFeedback.type === 'success' ? 'alert-success' : 'alert-error'
                    }`}
                  >
                    {roomFeedback.text}
                  </div>
                )}

                <form onSubmit={handleCreateRoom}>
                  <FormInput
                    id="roomName"
                    label="Topic Room Title"
                    value={roomForm.roomName}
                    onChange={(e) => setRoomForm({ ...roomForm, roomName: e.target.value })}
                    placeholder="Exam Anxiety & Study Stress"
                    required
                  />
                  <div className="form-group">
                    <label className="form-label" htmlFor="roomDesc">
                      Description *
                    </label>
                    <textarea
                      id="roomDesc"
                      rows={3}
                      className="form-textarea"
                      value={roomForm.description}
                      onChange={(e) => setRoomForm({ ...roomForm, description: e.target.value })}
                      placeholder="Describe the supportive focus of this peer chatroom..."
                      required
                    />
                  </div>
                  <button type="submit" className="btn btn-primary btn-full" disabled={creatingRoom}>
                    {creatingRoom ? 'Adding...' : 'Create Topic Room'}
                  </button>
                </form>
              </div>

              <div className="card">
                <h3>Configured Topic Categories</h3>
                <p className="card-subtitle">Toggle availability without changing code.</p>
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Room</th>
                        <th>Status</th>
                        <th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.topicRooms.map((room) => (
                        <tr key={room.roomId}>
                          <td>
                            <div style={{ fontWeight: 600 }}>{room.roomName}</div>
                            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                              {room.description}
                            </div>
                          </td>
                          <td>
                            <span
                              className={`badge ${
                                room.active ? 'badge-success' : 'badge-neutral'
                              }`}
                            >
                              {room.active ? 'Active' : 'Archived'}
                            </span>
                          </td>
                          <td>
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              onClick={() => handleToggleRoom(room.roomId)}
                            >
                              {room.active ? 'Archive' : 'Activate'}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'audit' && (
            <div className="card">
              <h3>Immutable Security Audit Logs (FR-038)</h3>
              <p className="card-subtitle">
                Most recent authentication, account provisioning, and configuration events.
              </p>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Time (UTC)</th>
                      <th>Role</th>
                      <th>Actor</th>
                      <th>Action</th>
                      <th>Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.auditLogs.map((log) => (
                      <tr key={log.auditId}>
                        <td style={{ whiteSpace: 'nowrap', fontSize: '0.8rem' }}>
                          {new Date(log.createdAt).toLocaleString()}
                        </td>
                        <td>
                          <span className="badge badge-neutral">{log.actorRole}</span>
                        </td>
                        <td>{log.actorLabel}</td>
                        <td style={{ fontWeight: 600 }}>{log.action}</td>
                        <td style={{ color: 'var(--text-muted)' }}>{log.details}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </AppLayout>
  );
}
