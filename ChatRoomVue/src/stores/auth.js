// src/stores/auth.js — 认证状态
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, clearToken } from '@/utils/token'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const user = ref(null)
  const accessToken = ref(getToken())  // 从 localStorage 恢复
  const loading = ref(false)
  const error = ref(null)

  // 计算属性
  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.role === 'ROLE_ADMIN')
  const isGuest = computed(() => user.value?.role === 'ROLE_GUEST')
  const displayName = computed(() => user.value?.name || '未登录')

  // 登录
  async function login(username, password) {
    loading.value = true
    error.value = null
    try {
      const res = await authApi.login({ username, password })
      if (res.data.code === 200) {
        const { accessToken: token, refreshToken: refresh, user: userData } = res.data.data
        accessToken.value = token
        user.value = userData
        setToken(token, refresh)
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
        user.value = userData
        setToken(token, refresh)
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
        return true
      }
    } catch (e) {
      console.error('Token 刷新失败', e)
    }
    return false
  }

  // 登出
  async function logout() {
    try {
      await authApi.logout({ refreshToken: localStorage.getItem('refreshToken') })
    } catch (e) { /* 忽略错误 */ }
    accessToken.value = null
    user.value = null
    clearToken()
  }

  // 获取当前用户信息
  async function fetchUser() {
    try {
      const res = await authApi.me()
      if (res.data.code === 200) {
        user.value = res.data.data
      }
    } catch (e) { /* 忽略 */ }
  }

  return { user, accessToken, loading, error,
           isAuthenticated, isAdmin, isGuest, displayName,
           login, loginAsGuest, refresh, logout, fetchUser }
})
