// src/api/auth.js — 认证相关 API
import api from './index'

export const authApi = {
  register: (data) => api.post('/api/auth/register', data),
  login: (data) => api.post('/api/auth/login', data),
  refresh: (data) => api.post('/api/auth/refresh', data),
  logout: (data) => api.post('/api/auth/logout', data),
  me: () => api.get('/api/auth/me'),
  guest: () => api.post('/api/auth/guest')
}
