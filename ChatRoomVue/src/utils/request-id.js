// src/utils/request-id.js — X-Request-Id 幂等防重

/**
 * 生成请求幂等 ID
 * 同一操作（如同一好友申请、同一文件上传）复用同一 id
 * 操作完成后调用 clearRequestId(key) 清除
 */
export function getRequestId(key) {
  let id = sessionStorage.getItem(`req-${key}`)
  if (!id) {
    id = crypto.randomUUID()
    sessionStorage.setItem(`req-${key}`, id)
  }
  return id
}

export function clearRequestId(key) {
  sessionStorage.removeItem(`req-${key}`)
}
