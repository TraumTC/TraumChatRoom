// src/utils/avatar.js — 默认首字头像（8 色哈希）

// 8 种固定背景色
const COLORS = [
  '#F44336', '#E91E63', '#9C27B0', '#3F51B5',
  '#03A9F4', '#009688', '#FF9800', '#795548'
]

// 根据字符串哈希取颜色
function hashString(str) {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  return Math.abs(hash)
}

// 生成默认头像配置
export function getDefaultAvatar(name) {
  const color = COLORS[hashString(name) % COLORS.length]
  const initial = name ? name.charAt(0) : '?'
  return { color, initial }
}
