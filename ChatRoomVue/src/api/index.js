// src/api/index.js — Axios 实例 + 拦截器
import axios from 'axios'
import { getToken, setToken, clearToken } from '@/utils/token'
import router from '@/router'
import { runSessionCleanup } from '@/utils/session-cleanup'
import { clearSessionKeys } from '@/utils/session-keys'
import { API_BASE } from '@/utils/url'

const api = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
  withCredentials: true
})

let refreshPromise = null

function requestRefresh() {
  return axios.post(
    `${api.defaults.baseURL}/api/auth/refresh`,
    null,
    { withCredentials: true }
  )
}

export function refreshSessionRequest() {
  if (!refreshPromise) {
    // Web Locks 将同源多个标签页的 Cookie 轮换串行化，避免旧 Token 并发重放。
    const request = globalThis.navigator?.locks
      ? navigator.locks.request('traum-refresh-token', requestRefresh)
      : requestRefresh()
    refreshPromise = request.finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

// 请求拦截器：自动附加 JWT Token + X-Request-Id（幂等）
api.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 幂等防重：后端 @Idempotent 接口依赖该头（好友申请/文件上传）
  if (!config.headers['X-Request-Id']) {
    config.headers['X-Request-Id'] = crypto.randomUUID()
  }
  return config
})

// 响应拦截器：401 时自动刷新 Token，统一错误处理
api.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config
    const isRefreshRequest = originalRequest?.url?.includes('/api/auth/refresh')
    const isPublicAuthRequest = ['/api/auth/login', '/api/auth/register', '/api/auth/guest',
      '/api/auth/refresh', '/api/auth/logout'].some(path => originalRequest?.url?.includes(path))

    // 401 且不是刷新请求本身 → 尝试刷新
    if (error.response?.status === 401 && !isPublicAuthRequest && !originalRequest._retry) {
      originalRequest._retry = true

      try {
        const res = await refreshSessionRequest()
        if (res.data.code === 200) {
          const newToken = res.data.data.accessToken
          setToken(newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return api(originalRequest)  // 重试原请求
        }
      } catch (e) {
        // 刷新也失败 → 跳转登录
      }

      clearToken()
      // 会话已失效 → 与主动登出走同一套清理：断开残留 WebSocket（复用旧 token 会
      // 导致重连失败、@未读回补失效）并复位 chat store，避免上一个会话的私聊页签
      // 残留给下一个登录者
      runSessionCleanup()
      clearSessionKeys()
      router.push('/')
    }

    return Promise.reject(error)
  }
)

export default api
