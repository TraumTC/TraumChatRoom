// src/composables/useWebSocket.js — STOMP 连接管理
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useWebSocketStore } from '@/stores/websocket'

export function useWebSocket() {
  const authStore = useAuthStore()
  const chatStore = useChatStore()
  const wsStore = useWebSocketStore()
  let stompClient = null

  function connect() {
    if (wsStore.connecting || wsStore.connected) return
    wsStore.connecting = true

    const token = authStore.accessToken
    const baseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080'

    // SockJS 不支持自定义 HTTP header，token 通过 URL 参数传递（后端握手拦截器从 URL 提取）
    const wsUrl = token ? `${baseUrl}/ws?token=${encodeURIComponent(token)}` : `${baseUrl}/ws`

    stompClient = new Client({
      // SockJS 连接（支持降级）
      webSocketFactory: () => new SockJS(wsUrl),
      // STOMP 层也带一份（双重保障）
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      // 心跳
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // 自动重连
      reconnectDelay: 5000,
      maxReconnectAttempts: 10,

      onConnect: () => {
        console.log('WebSocket 已连接')
        wsStore.connected = true
        wsStore.connecting = false
        wsStore.reconnectCount = 0
        subscribeAll()
        // 重连后同步在线状态
        syncState()
      },

      onStompError: (frame) => {
        console.error('STOMP 错误:', frame.headers['message'])
        wsStore.error = frame.headers['message']
      },

      onWebSocketError: (e) => {
        console.error('WebSocket 连接错误:', e)
        wsStore.error = 'WebSocket 连接失败'
      },

      onDisconnect: () => {
        wsStore.connected = false
        wsStore.connecting = false
      }
    })

    stompClient.activate()
  }

  // 订阅所有频道
  function subscribeAll() {
    // 群聊消息
    stompClient.subscribe('/topic/messages', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addMessage(data)
    })

    // 在线用户列表
    stompClient.subscribe('/topic/onlineUsers', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.setOnlineUsers(data.onlineUsers || [])
    })

    // 私聊消息
    stompClient.subscribe('/user/queue/private-messages', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addPrivateMessage(data)
    })

    // 上下线通知
    stompClient.subscribe('/topic/private-notifications', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addNotification({
        type: 'info',
        message: data.message
      })
    })

    // 好友申请通知
    stompClient.subscribe('/user/queue/friend-request', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addNotification({
        type: 'friend_request',
        message: `${data.sender?.name} 请求添加你为好友`,
        data
      })
    })

    // 好友同意通知
    stompClient.subscribe('/user/queue/friend-accepted', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addNotification({
        type: 'friend_accepted',
        message: `${data.friend?.name} 通过了你的好友申请`,
        data
      })
    })

    // 消息撤回通知
    stompClient.subscribe('/user/queue/message-recalled', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.handleMessageRecalled(data)
    })

    // 发送错误通知（敏感词拦截等）
    stompClient.subscribe('/user/queue/send-error', (msg) => {
      const data = JSON.parse(msg.body)
      console.error('发送失败:', data.message)
      chatStore.setError(data.message)
      chatStore.addNotification({
        type: 'error',
        message: data.message || '发送失败'
      })
    })
  }

  // 发送群聊消息
  function sendGroupMessage(content) {
    if (!stompClient || !stompClient.connected) {
      chatStore.setError('连接已断开，正在重连...')
      return false
    }
    stompClient.publish({
      destination: '/app/space',
      body: JSON.stringify({ content })
    })
    return true
  }

  // 发送私聊消息
  function sendPrivateMessage(receiver, content) {
    if (!stompClient || !stompClient.connected) {
      chatStore.setError('连接已断开，正在重连...')
      return false
    }
    stompClient.publish({
      destination: '/app/private.message',
      body: JSON.stringify({ receiver, content })
    })
    return true
  }

  // 发送心跳
  function sendHeartbeat() {
    if (!stompClient || !stompClient.connected) return
    stompClient.publish({ destination: '/app/heartbeat' })
  }

  // 同步状态（触发服务端广播在线用户列表）
  function syncState() {
    if (!stompClient || !stompClient.connected) return
    stompClient.publish({ destination: '/app/sync-state', body: '{}' })
  }

  // 断开连接
  function disconnect() {
    if (stompClient) {
      stompClient.deactivate()
      wsStore.connected = false
    }
  }

  return { connect, disconnect, sendGroupMessage, sendPrivateMessage, sendHeartbeat, syncState }
}
