import axiosClient from './axiosClient';

export const authApi = {
  register(payload) {
    return axiosClient.post('/auth/register', payload).then((res) => res.data);
  },

  login(payload) {
    return axiosClient.post('/auth/login', payload).then((res) => res.data);
  },

  refresh() {
    return axiosClient.post('/auth/refresh', {}).then((res) => res.data);
  },

  logout() {
    return axiosClient.post('/auth/logout', {}).then((res) => res.data);
  },

  me() {
    return axiosClient.get('/auth/me').then((res) => res.data);
  },

  forgotPassword(payload) {
    return axiosClient.post('/auth/forgot-password', payload).then((res) => res.data);
  },

  validateResetToken(token) {
    return axiosClient
      .get('/auth/reset-password/validate', { params: { token } })
      .then((res) => res.data);
  },

  resetPassword(payload) {
    return axiosClient.post('/auth/reset-password', payload).then((res) => res.data);
  },

  changePassword(payload) {
    return axiosClient.post('/auth/change-password', payload).then((res) => res.data);
  },

  getProfile() {
    return axiosClient.get('/profile').then((res) => res.data);
  },

  updateProfile(payload) {
    return axiosClient.patch('/profile', payload).then((res) => res.data);
  },
};
