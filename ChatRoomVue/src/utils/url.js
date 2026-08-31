// src/utils/url.js — 后端文件 URL 解析
// 头像、聊天图片/视频/文件等通过 <img>/<video>/<a> 直接加载，不经过 axios，
// 因此必须和 XHR 使用同一个 API base 拼成绝对地址；否则打包部署（没有 vite dev 代理）时，
// 相对路径 /api/... 会打到前端自身的源，导致 404、头像与图片加载不出来。

// 与 api/index.js 的 axios baseURL 保持同一取值来源，避免二者不一致
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

/**
 * 把后端返回的文件路径解析为可直接加载的绝对地址。
 * - 已是绝对地址(http/https/协议相对)、blob:、data: → 原样返回（本地预览、外链等）
 * - 以 / 开头的后端相对路径（含带 ?t= 缓存参数）→ 前缀 API base
 * - 其他情况原样返回
 */
export function resolveFileUrl(path) {
  if (!path) return path
  if (/^(https?:)?\/\//i.test(path) || path.startsWith('blob:') || path.startsWith('data:')) {
    return path
  }
  if (path.startsWith('/')) {
    return API_BASE + path
  }
  return path
}

export { API_BASE }
