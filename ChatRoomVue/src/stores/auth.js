// src/stores/auth.js — 认证状态
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, clearToken, subscribeToken, getCachedUser, setCachedUser, clearCachedUser } from '@/utils/token'
import { runSessionCleanup } from '@/utils/session-cleanup'
import { clearSessionKeys } from '@/utils/session-keys'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const user = ref(getCachedUser())  // 从 localStorage 恢复（含 role）
  const accessToken = ref(getToken())  // 仅保存在当前页面内存
  const initialized = ref(false)
  const loading = ref(false)
  const error = ref(null)

  // 计算属性
  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.role === 'ROLE_ADMIN')
  const isGuest = computed(() => user.value?.role === 'ROLE_GUEST')
  const displayName = computed(() => user.value?.name || '未登录')

  // Axios 拦截器刷新 Token 后，同步 Pinia，确保路由和 WebSocket 使用新值
  subscribeToken(token => { accessToken.value = token })

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
        const { accessToken: token, user: userData } = res.data.data
        accessToken.value = token
        setToken(token)
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
        const { accessToken: token, user: userData } = res.data.data
        accessToken.value = token
        setToken(token)
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
    try {
      const res = await authApi.refresh()
      if (res.data.code === 200) {
        accessToken.value = res.data.data.accessToken
        setToken(res.data.data.accessToken)
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

  async function initialize() {
    if (initialized.value) return isAuthenticated.value
    initialized.value = true
    const restored = await refresh()
    if (!restored) {
      clearToken()
      setUser(null)
    }
    return restored
  }

  /**
   * 登出：注销服务端会话 + 拆掉本地全部会话状态。
   *
   * 所有清理都收在这里，而不是让调用方各自清 —— 原先 AppHeader 记得 disconnect() 和
   * clearMessages()，ProfileView 的改密码登出两件都漏了，401 拦截器只断连接不清 store。
   * 同一个 bug 出现三次，根因就是清理写在调用方：每新增一个登出入口都会重犯。
   * 调用方现在只需要 await logout() 然后跳转。
   */
  async function logout() {
    // 等待服务端清理 Cookie；即使服务端失败，也必须清理本地状态
    try {
      await authApi.logout()
    } catch (e) {
      console.warn('服务端会话注销失败，本地状态已清理', e)
    }

    // 拆掉 WebSocket 连接与 chat store 状态（由各自模块注册，避免循环依赖）
    runSessionCleanup()

    // 兜底清 localStorage：用户在 /profile 刷新后直接改密码登出时，
    // chat store 可能本次页面加载中从未实例化，注册的清理函数就不会跑到
    clearSessionKeys()

    // 清理认证状态（不影响已发出的请求）
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
           login, loginAsGuest, refresh, initialize, logout, fetchUser, setUser }
})
