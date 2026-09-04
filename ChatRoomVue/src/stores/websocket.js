// src/stores/websocket.js — WebSocket 连接状态
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useWebSocketStore = defineStore('websocket', () => {
  const connected = ref(false)
  const connecting = ref(false)
  const error = ref(null)
  const reconnectCount = ref(0)
  // 凭据已失效、已主动放弃重连。与 connecting=false 区分开：
  // 后者表示「断了正在重连」，前者表示「不会再重连了，需要重新登录」，
  // UI 提示文案完全不同（见 ChatView 顶部的连接状态横幅）。
  const authExpired = ref(false)

  return { connected, connecting, error, reconnectCount, authExpired }
})
