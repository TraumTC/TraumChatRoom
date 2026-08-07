// src/composables/useWebSocket.js — STOMP 连接管理（模块级单例）
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useWebSocketStore } from '@/stores/websocket'

// 模块级变量：所有 useWebSocket() 调用共享同一个连接
let stompClient = null

export function useWebSocket() {
  const authStore = useAuthStore()
  const chatStore = useChatStore()
  const wsStore = useWebSocketStore()

  function connect() {
    // 已连接 → 跳过
    if (wsStore.connected && stompClient?.connected) return

    // 正在连接 → 跳过
    if (wsStore.connecting) return

    // 已有实例但断开 → 重新激活
    if (stompClient && !stompClient.connected) {
      wsStore.connecting = true
      stompClient.activate()
      return
    }

    // 首次连接
    wsStore.connecting = true

    const token = authStore.accessToken
    const baseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080'
    const wsUrl = token ? `${baseUrl}/ws?token=${encodeURIComponent(token)}` : `${baseUrl}/ws`

    stompClient = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
      maxReconnectAttempts: 10,

      onConnect: () => {
        console.log('WebSocket 已连接')
        wsStore.connected = true
        wsStore.connecting = false
        wsStore.reconnectCount = 0
        subscribeAll()
        syncState()
      },

      onStompError: (frame) => {
        console.error('STOMP 错误:', frame.headers['message'])
        wsStore.error = frame.headers['message']
        wsStore.connecting = false
      },

      onWebSocketError: (e) => {
        console.error('WebSocket 连接错误:', e)
        wsStore.error = 'WebSocket 连接失败'
        wsStore.connecting = false
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
    stompClient.subscribe('/topic/messages', (msg) => {
      const data = JSON.parse(msg.body)
      // 群聊撤回通知（后端广播到同一频道，用 type 区分）
      if (data.type === 'message_recalled') {
        chatStore.handleMessageRecalled(data)
      } else {
        chatStore.addMessage(data)
      }
    })

    stompClient.subscribe('/topic/onlineUsers', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.setOnlineUsers(data.onlineUsers || [])
    })

    stompClient.subscribe('/user/queue/private-messages', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addPrivateMessage(data)
    })

    stompClient.subscribe('/topic/private-notifications', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addNotification({ type: 'info', message: data.message })
    })

    stompClient.subscribe('/user/queue/friend-request', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.incrementFriendRequestCount()
      chatStore.addNotification({
        type: 'friend_request',
        message: `${data.sender?.name} 请求添加你为好友`,
        data
      })
    })

    stompClient.subscribe('/user/queue/friend-accepted', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addNotification({
        type: 'friend_accepted',
        message: `${data.friend?.name} 通过了你的好友申请`,
        data
      })
    })

    stompClient.subscribe('/user/queue/message-recalled', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.handleMessageRecalled(data)
    })

    stompClient.subscribe('/user/queue/send-error', (msg) => {
      const data = JSON.parse(msg.body)
      console.error('发送失败:', data.message)
      chatStore.setError(data.message)
      chatStore.addNotification({ type: 'error', message: data.message || '发送失败' })
    })
  }

  // 发送群聊消息
  function sendGroupMessage(content) {
    if (!stompClient || !stompClient.connected) {
      chatStore.setError('连接已断开，正在重连...')
      connect()
      return false
    }
    const replyToId = chatStore.replyTo?.id || null
    stompClient.publish({
      destination: '/app/space',
      body: JSON.stringify({ content, replyToId: replyToId ? String(replyToId) : null })
    })
    chatStore.clearReplyTo()
    return true
  }

  // 发送私聊消息
  function sendPrivateMessage(receiver, content) {
    if (!stompClient || !stompClient.connected) {
      chatStore.setError('连接已断开，正在重连...')
      connect()
      return false
    }
    const replyToId = chatStore.replyTo?.id || null

    // 乐观更新：立即在本地显示自己发的消息（不依赖后端回传）
    const localMsg = {
      id: -Date.now(),  // 负数临时 ID，不会与数据库 ID 冲突
      sender: { id: authStore.user?.id, name: authStore.user?.name, avatar: authStore.user?.avatar },
      receiver: { id: null, name: chatStore.currentChat.name || receiver },
      content,
      messageType: 'text',
      aiReply: false,
      recalled: false,
      replyToId: replyToId || null,
      createdAt: new Date().toISOString()
    }
    chatStore.addPrivateMessage(localMsg)

    stompClient.publish({
      destination: '/app/private.message',
      body: JSON.stringify({ receiver, content, replyToId: replyToId ? String(replyToId) : null })
    })
    chatStore.clearReplyTo()
    return true
  }

  // 发送心跳
  function sendHeartbeat() {
    if (!stompClient || !stompClient.connected) return
    stompClient.publish({ destination: '/app/heartbeat' })
  }

  // 同步状态
  function syncState() {
    if (!stompClient || !stompClient.connected) return
    stompClient.publish({ destination: '/app/sync-state', body: '{}' })
  }

  // 断开连接
  function disconnect() {
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
      wsStore.connected = false
      wsStore.connecting = false
    }
  }

  return { connect, disconnect, sendGroupMessage, sendPrivateMessage, sendHeartbeat, syncState }
}
