// src/api/auth.js — 认证相关 API
import api, { refreshSessionRequest } from './index'

export const authApi = {
  register: (data) => api.post('/api/auth/register', data),
  login: (data) => api.post('/api/auth/login', data),
  refresh: () => refreshSessionRequest(),
  logout: () => api.post('/api/auth/logout'),
  me: () => api.get('/api/auth/me'),
  guest: () => api.post('/api/auth/guest')
}
