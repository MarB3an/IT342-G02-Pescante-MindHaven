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
};
