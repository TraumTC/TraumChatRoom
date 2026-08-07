// src/api/friend.js — 好友相关 API
import api from './index'
import { getRequestId } from '@/utils/request-id'

export const friendApi = {
  search: (keyword) => api.get('/api/friend/search', { params: { keyword } }),
  // 幂等：同一申请复用同一 X-Request-Id，防重复提交
  sendRequest: (data) => api.post('/api/friend/request', data, {
    headers: { 'X-Request-Id': getRequestId('friend-request') }
  }),
  getRequests: (params) => api.get('/api/friend/requests', { params }),
  handleRequest: (id, data) => api.put(`/api/friend/requests/${id}`, data),
  deleteRequest: (id) => api.delete(`/api/friend/requests/${id}`),
  getList: (params) => api.get('/api/friends', { params }),
  updateRemark: (id, data) => api.put(`/api/friends/${id}/remark`, data),
  delete: (id) => api.delete(`/api/friends/${id}`)
}
