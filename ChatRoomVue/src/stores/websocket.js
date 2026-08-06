// src/stores/websocket.js — WebSocket 连接状态
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useWebSocketStore = defineStore('websocket', () => {
  const connected = ref(false)
  const connecting = ref(false)
  const error = ref(null)
  const reconnectCount = ref(0)

  return { connected, connecting, error, reconnectCount }
})
