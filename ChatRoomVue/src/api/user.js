// src/api/user.js — 用户相关 API
import api from './index'

export const userApi = {
  updateProfile: (data) => api.put('/api/user/profile', data),
  updatePassword: (data) => api.put('/api/user/password', data),
  uploadAvatar: (formData) => api.post('/api/user/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  deleteAvatar: () => api.delete('/api/user/avatar'),
  getMentionable: () => api.get('/api/user/mentionable')
}
