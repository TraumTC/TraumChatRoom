// src/utils/format.js — 时间 / 文件大小格式化

/**
 * 格式化时间为可读字符串
 * 今天的消息显示 "HH:mm"，昨天显示 "昨天 HH:mm"，更早显示 "MM-DD HH:mm"
 */
export function formatTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()

  const pad = (n) => String(n).padStart(2, '0')
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`

  // 今天
  if (date.toDateString() === now.toDateString()) {
    return time
  }

  // 昨天
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return `昨天 ${time}`
  }

  // 更早
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${time}`
}

/**
 * 格式化文件大小
 */
export function formatFileSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
