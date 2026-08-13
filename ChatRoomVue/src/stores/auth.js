// src/stores/auth.js — 认证状态
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, clearToken, getCachedUser, setCachedUser, clearCachedUser } from '@/utils/token'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const user = ref(getCachedUser())  // 从 localStorage 恢复（含 role）
  const accessToken = ref(getToken())  // 从 localStorage 恢复
  const loading = ref(false)
  const error = ref(null)

  // 计算属性
  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.role === 'ROLE_ADMIN')
  const isGuest = computed(() => user.value?.role === 'ROLE_GUEST')
  const displayName = computed(() => user.value?.name || '未登录')

  function setUser(newUser) {
    user.value = newUser
    if (newUser) {
      setCachedUser(newUser)
    } else {
      clearCachedUser()
    }
  }

  // 登录
  async function login(username, password) {
    loading.value = true
    error.value = null
    try {
      const res = await authApi.login({ username, password })
      if (res.data.code === 200) {
        const { accessToken: token, refreshToken: refresh, user: userData } = res.data.data
        accessToken.value = token
        setToken(token, refresh)
        setUser(userData)
        return true
      }
      error.value = res.data.message
      return false
    } catch (e) {
      error.value = e.response?.data?.message || '登录失败，请稍后再试'
      return false
    } finally {
      loading.value = false
    }
  }

  // 游客进入
  async function loginAsGuest() {
    loading.value = true
    try {
      const res = await authApi.guest()
      if (res.data.code === 200) {
        const { accessToken: token, refreshToken: refresh, user: userData } = res.data.data
        accessToken.value = token
        setToken(token, refresh)
        setUser(userData)
        return true
      }
      return false
    } catch (e) {
      error.value = '游客进入失败'
      return false
    } finally {
      loading.value = false
    }
  }

  // 刷新 Token
  async function refresh() {
    const refresh = localStorage.getItem('refreshToken')
    if (!refresh) return false
    try {
      const res = await authApi.refresh({ refreshToken: refresh })
      if (res.data.code === 200) {
        accessToken.value = res.data.data.accessToken
        setToken(res.data.data.accessToken, refresh)
        if (res.data.data.user) {
          setUser(res.data.data.user)
        }
        return true
      }
    } catch (e) {
      console.error('Token 刷新失败', e)
    }
    return false
  }

  // 登出（本地状态清理 + 可选后端注销）
  async function logout() {
    // 先保存 refreshToken（clearToken 会删除它）
    const refresh = localStorage.getItem('refreshToken')

    // 先发起后端注销请求（此刻 token 未清除，拦截器可附加认证头，避免 401）
    if (refresh) {
      authApi.logout({ refreshToken: refresh }).catch(() => {})
    }

    // 清理本地状态（不影响已发出的请求）
    accessToken.value = null
    setUser(null)
    clearToken()
    clearCachedUser()
  }

  // 获取当前用户信息（可强制刷新，同步更新缓存）
  async function fetchUser(force = false) {
    if (!force && user.value) return user.value
    try {
      const res = await authApi.me()
      if (res.data.code === 200) {
        setUser(res.data.data)
        return user.value
      }
    } catch (e) {
      console.warn('获取用户信息失败', e)
    }
    return null
  }

  return { user, accessToken, loading, error,
           isAuthenticated, isAdmin, isGuest, displayName,
           login, loginAsGuest, refresh, logout, fetchUser, setUser }
})
