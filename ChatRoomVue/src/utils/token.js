// src/utils/token.js — 双 Token 管理
export function getToken() {
  return localStorage.getItem('accessToken')
}

export function getRefreshToken() {
  return localStorage.getItem('refreshToken')
}

export function setToken(accessToken, refreshToken) {
  localStorage.setItem('accessToken', accessToken)
  localStorage.setItem('refreshToken', refreshToken)
}

export function clearToken() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
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
