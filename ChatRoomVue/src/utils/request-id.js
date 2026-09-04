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

/**
 * 文件上传专用的幂等 ID —— 从文件自身派生，不进 sessionStorage。
 *
 * 为什么不用 getRequestId('file-upload')：那是「同 key 复用同一 UUID」，存在 sessionStorage
 * 里等着 clearRequestId 来清，而 'file-upload' 这个 key 全项目没有任何地方清除。
 * 结果整个浏览器会话的所有上传共用同一个 id，后端 @Idempotent(timeout=5) 的 Redis key
 * 成功后只等 5 秒过期 —— 于是 5 秒内传第二个文件（哪怕是完全不同的文件）
 * 会被判成重复提交，返回 429「请勿重复提交，请稍后再试」。
 *
 * 改为按「文件 + 目标会话」派生，恰好还原该注解本来的语义：
 * - 同一个文件发给同一个人重复提交（双击、误触重传）→ id 相同 → 5 秒内仍被挡住
 * - 换文件、或同一文件发给不同人 → id 不同 → 互不干扰
 *
 * 全 ASCII：文件名经 djb2 压成 36 进制，中文文件名不会破坏 HTTP 头。
 */
export function fileRequestId(file, scope = '') {
  return `up-${file.size}-${file.lastModified}-${djb2(`${file.name}|${scope}`)}`
}

// djb2：同步、无依赖，仅用于把「文件名 + 会话」压成短 ASCII 串，非安全用途。
// 极小概率的哈希碰撞需要同时撞上相同 size 与 lastModified，后果也仅是 5 秒内少传一次。
function djb2(str) {
  let h = 5381
  for (let i = 0; i < str.length; i++) {
    h = ((h << 5) + h + str.charCodeAt(i)) | 0
  }
  return (h >>> 0).toString(36)
}
