// src/utils/token.js — accessToken 仅保存在内存；refreshToken 由 HttpOnly Cookie 管理
let accessToken = null
const listeners = new Set()

// 一次性移除旧版本遗留的明文 Token，避免迁移后仍可被脚本读取
try {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
} catch { /* 忽略 */ }

export function getToken() {
  return accessToken
}

export function getRefreshToken() {
  // HttpOnly Cookie 对 JavaScript 不可读，刷新请求由浏览器自动携带
  return null
}

export function setToken(token) {
  accessToken = token || null
  listeners.forEach(listener => listener(accessToken))
}

export function clearToken() {
  accessToken = null
  listeners.forEach(listener => listener(null))
}

export function subscribeToken(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

// 缓存用户信息（含 role），用于导航守卫等同步场景
const USER_KEY = 'auth_user'

export function getCachedUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setCachedUser(user) {
  try {
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } else {
      localStorage.removeItem(USER_KEY)
    }
  } catch { /* 忽略 */ }
}

export function clearCachedUser() {
  localStorage.removeItem(USER_KEY)
}
