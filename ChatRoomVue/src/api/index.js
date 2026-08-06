// src/api/index.js — Axios 实例 + 拦截器
import axios from 'axios'
import { getToken, getRefreshToken, setToken, clearToken } from '@/utils/token'
import router from '@/router'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  timeout: 10000
})

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

    // 401 且不是刷新请求本身 → 尝试刷新
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      const refresh = getRefreshToken()
      if (refresh) {
        try {
          const res = await axios.post(
            `${api.defaults.baseURL}/api/auth/refresh`,
            { refreshToken: refresh }
          )
          if (res.data.code === 200) {
            const newToken = res.data.data.accessToken
            setToken(newToken, refresh)
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return api(originalRequest)  // 重试原请求
          }
        } catch (e) {
          // 刷新也失败 → 跳转登录
        }
      }

      clearToken()
      router.push('/login')
    }

    return Promise.reject(error)
  }
)

export default api
