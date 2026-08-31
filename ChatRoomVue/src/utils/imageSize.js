// src/utils/imageSize.js — 聊天图片原始尺寸缓存
//
// 为什么需要它：聊天列表是虚拟滚动，消息行会被回收，图片 <img> 随之反复卸载重建。
// 每次重建时浏览器还不知道图片尺寸，元素高度先塌成 0、拿到图片后再撑开；
// 只要撑开的那一行位于视口上方，下方内容就被整体推下去 —— 这就是滚动时"抖动"的直接来源。
// 把图片的原始宽高按路径记下来，重建时先按真实比例把占位框撑好，行高自始至终不变，抖动消失。
//
// 尺寸和文件路径一一对应且永不变化（文件名带时间戳+UUID，内容一经写入不再改动），
// 所以可以安全持久化到 localStorage：刷新后首屏就能精确占位，不必等图片下载完。

const STORAGE_KEY = 'chat:image_dimensions:v1'
const MAX_ENTRIES = 400   // 上限，超出按插入顺序淘汰最旧的，避免无限增长占满配额

const cache = new Map()   // path -> { w, h }

// 启动时载入已持久化的尺寸
try {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (raw) {
    const saved = JSON.parse(raw)
    if (saved && typeof saved === 'object') {
      for (const [path, wh] of Object.entries(saved)) {
        if (Array.isArray(wh) && wh[0] > 0 && wh[1] > 0) {
          cache.set(path, { w: wh[0], h: wh[1] })
        }
      }
    }
  }
} catch (e) {
  // 缓存损坏不影响功能，只是退化成"首次见到该图才知道尺寸"
}

// 合并写入：连续记录多张图时只落盘一次，避免滚动中频繁序列化
let flushTimer = null
function scheduleFlush() {
  if (flushTimer) return
  flushTimer = setTimeout(() => {
    flushTimer = null
    try {
      const obj = {}
      cache.forEach((v, k) => { obj[k] = [v.w, v.h] })
      localStorage.setItem(STORAGE_KEY, JSON.stringify(obj))
    } catch (e) {
      // 配额写满等：忽略，内存缓存本会话内仍然生效
    }
  }, 1000)
}

/**
 * 取图片的原始尺寸
 * @param {string} path 消息里的 filePath（未拼 API base 的原始路径，作为稳定 key）
 * @returns {{ w: number, h: number } | null} 没见过这张图时返回 null
 */
export function getImageSize(path) {
  if (!path) return null
  return cache.get(path) || null
}

/**
 * 记录图片的原始尺寸（<img> 的 load 事件里调用）
 */
export function rememberImageSize(path, w, h) {
  if (!path || !(w > 0) || !(h > 0)) return
  const existing = cache.get(path)
  if (existing && existing.w === w && existing.h === h) return
  cache.delete(path)   // 先删再插，让 Map 的插入顺序近似 LRU
  cache.set(path, { w, h })
  while (cache.size > MAX_ENTRIES) {
    cache.delete(cache.keys().next().value)
  }
  scheduleFlush()
}
