// src/api/admin.js — 管理员相关 API
import api from './index'

export const adminApi = {
  getUsers: (params) => api.get('/api/admin/users', { params }),
  updateRole: (id, data) => api.put(`/api/admin/users/${id}/role`, data),
  updateUser: (id, data) => api.put(`/api/admin/users/${id}`, data),
  deleteUser: (id) => api.delete(`/api/admin/users/${id}`),
  getLogs: (params) => api.get('/api/admin/logs', { params }),
  getSensitiveWords: (params) => api.get('/api/admin/sensitive-words', { params }),
  addSensitiveWord: (data) => api.post('/api/admin/sensitive-words', data),
  deleteSensitiveWord: (id) => api.delete(`/api/admin/sensitive-words/${id}`),
  refreshSensitiveWords: () => api.post('/api/admin/sensitive-words/refresh')
}
