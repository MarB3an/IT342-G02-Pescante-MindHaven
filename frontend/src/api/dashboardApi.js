import axiosClient from './axiosClient';

export const dashboardApi = {
  getStudentDashboard() {
    return axiosClient.get('/student/dashboard').then((res) => res.data);
  },

  updateStudentProfile(payload) {
    return axiosClient.patch('/student/profile', payload).then((res) => res.data);
  },

  regeneratePseudonym() {
    return axiosClient.post('/student/profile/regenerate-pseudonym', {}).then((res) => res.data);
  },

  getCounselorDashboard() {
    return axiosClient.get('/counselor/dashboard').then((res) => res.data);
  },

  updateCounselorOnCall(payload) {
    return axiosClient.patch('/counselor/on-call', payload).then((res) => res.data);
  },

  getAdminDashboard() {
    return axiosClient.get('/admin/dashboard').then((res) => res.data);
  },

  createStaff(payload) {
    return axiosClient.post('/admin/staff', payload).then((res) => res.data);
  },

  updateStaffStatus(staffId, payload) {
    return axiosClient.patch(`/admin/staff/${staffId}/status`, payload).then((res) => res.data);
  },

  createTopicRoom(payload) {
    return axiosClient.post('/admin/topic-rooms', payload).then((res) => res.data);
  },

  toggleTopicRoom(roomId) {
    return axiosClient.patch(`/admin/topic-rooms/${roomId}/toggle`, {}).then((res) => res.data);
  },
};
