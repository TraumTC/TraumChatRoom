// src/api/message.js — 消息相关 API
import api from './index'

export const messageApi = {
  getHistory: (params) => api.get('/api/messages/history', { params }),
  getPrivateHistory: (username, params) =>
    api.get(`/api/messages/private/${username}`, { params }),
  recall: (id) => api.put(`/api/messages/${id}/recall`)
}
