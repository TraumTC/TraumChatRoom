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
