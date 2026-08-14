// src/composables/useWebSocket.js — STOMP 连接管理（模块级单例）
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useWebSocketStore } from '@/stores/websocket'
import { registerWsCleanup } from '@/utils/ws-cleanup'

// 模块级变量：所有 useWebSocket() 调用共享同一个连接
let stompClient = null
let heartbeatTimer = null

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
    // Token 通过 STOMP connectHeaders 传递（不进 URL，避免进入代理/访问日志）
    const wsUrl = `${baseUrl}/ws`

    stompClient = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
      maxReconnectAttempts: 10,
      // 每次连接（含重连）前刷新 token，避免复用旧凭据导致连接失败
      beforeConnect: () => {
        const current = authStore.accessToken
        stompClient.connectHeaders = current ? { Authorization: `Bearer ${current}` } : {}
      },

      onConnect: () => {
        console.log('WebSocket 已连接')
        wsStore.connected = true
        wsStore.connecting = false
        wsStore.reconnectCount = 0
        subscribeAll()
        syncState()
        startHeartbeat()
        // 回补 @提及未读：覆盖首连与断线重连场景（离线期间被@，上线后补提醒）
        fetchMentionUnread()
        // 订阅/推送就绪后延迟重试一次，提高重连场景下的成功率
        setTimeout(fetchMentionUnread, 3000)
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
        stopHeartbeat()
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
      // 非当前活跃会话时弹 Toast 通知
      const isMyMsg = data.sender?.id && String(data.sender.id) === localStorage.getItem('myId')
      if (!isMyMsg) {
        const otherUsername = data.sender?.username || data.sender?.name || '未知用户'
        const isCurrent = chatStore.currentChat.type === 'private'
          && chatStore.currentChat.username === otherUsername
        if (!isCurrent) {
          chatStore.addNotification({
            type: 'private_message',
            message: `${data.sender?.name || otherUsername} 发来一条私聊消息`,
            data
          })
        }
      }
    })

    stompClient.subscribe('/topic/private-notifications', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addNotification({ type: 'info', message: data.message })
    })

    stompClient.subscribe('/user/queue/friend-request', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.incrementFriendRequestCount()
      chatStore.incrementFriendListVersion()
      chatStore.addNotification({
        type: 'friend_request',
        message: `${data.sender?.name} 请求添加你为好友`,
        data
      })
    })

    stompClient.subscribe('/user/queue/friend-accepted', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.incrementFriendListVersion()
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

    // 群聊 @提及提醒（后端推送对象，STOMP body 为 JSON，解析一次即得对象）
    stompClient.subscribe('/user/queue/mention-notice', (msg) => {
      const data = JSON.parse(msg.body)
      chatStore.addMentionNotice(data)
    })

    stompClient.subscribe('/user/queue/send-error', (msg) => {
      const data = JSON.parse(msg.body)
      console.error('发送失败:', data.message)
      chatStore.setError(data.message)
      // 移除本地乐观更新的临时消息（后端 send-error 携带 clientId 时精确定位）
      if (data.clientId) {
        chatStore.removePendingMessage(data.clientId)
      }
      // 所有发送错误都弹窗提醒（Toast 容易被忽略）
      const isBlocked = data.subtype === 'blocked'
      window.$dialog?.warning({
        title: isBlocked ? '消息被拦截' : '发送失败',
        content: data.message || '发送失败，请稍后重试',
        positiveText: '我知道了',
      })
    })
  }

  // 拉取群聊 @提及未读并合并（游客跳过；失败保留日志，不阻塞连接）
  // 动态 import messageApi：避免顶层静态依赖把 useWebSocket 拉入
  // api/index ↔ router ↔ stores/auth ↔ api/auth 循环（Vite 分块下导致 pinia 双实例、路由切换白屏）
  async function fetchMentionUnread() {
    if (authStore.isGuest) return
    const { messageApi } = await import('@/api/message')
    messageApi.getMentionUnread()
      .then(res => {
        if (res.data.code === 200) chatStore.mergeMentions(res.data.data)
      })
      .catch(e => {
        console.warn('拉取 @提及未读失败', e)
      })
  }

  // 发送群聊消息（带 clientId 幂等，防重连/双击重复）
  function sendGroupMessage(content) {
    if (!stompClient || !stompClient.connected) {
      chatStore.setError('连接已断开，正在重连...')
      connect()
      return false
    }
    const replyToId = chatStore.replyTo?.id || null
    const clientId = crypto.randomUUID()
    stompClient.publish({
      destination: '/app/space',
      body: JSON.stringify({ content, replyToId: replyToId ? String(replyToId) : null, clientId })
    })
    chatStore.clearReplyTo()
    return true
  }

  // 发送私聊消息（receiver 传 username）
  function sendPrivateMessage(receiver, content) {
    if (!stompClient || !stompClient.connected) {
      chatStore.setError('连接已断开，正在重连...')
      connect()
      return false
    }
    const replyToId = chatStore.replyTo?.id || null
    const clientId = crypto.randomUUID()

    // 乐观更新：立即在本地显示自己发的消息（不依赖后端回传）
    const localMsg = {
      id: -Date.now(),  // 负数临时 ID，不会与数据库 ID 冲突
      sender: { id: authStore.user?.id, username: authStore.user?.username, name: authStore.user?.name, avatar: authStore.user?.avatar },
      receiver: { id: null, username: receiver, name: receiver },
      content,
      messageType: 'text',
      aiReply: false,
      recalled: false,
      replyToId: replyToId || null,
      createdAt: new Date().toISOString(),
      _tempCreatedAt: Date.now(),  // 临时创建时间戳，用于后端回传时匹配替换
      _clientId: clientId          // 关联本次发送，后端 send-error 携带时用于移除临时消息
    }
    chatStore.addPrivateMessage(localMsg)

    stompClient.publish({
      destination: '/app/private.message',
      body: JSON.stringify({ receiver, content, replyToId: replyToId ? String(replyToId) : null, clientId })
    })
    chatStore.clearReplyTo()
    return true
  }

  // 应用层心跳：更新服务端在线 ZSet 分数（STOMP 协议心跳不触发 /app/heartbeat）
  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      sendHeartbeat()
    }, 20000)
  }
  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
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
    stopHeartbeat()
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
      wsStore.connected = false
      wsStore.connecting = false
    }
  }

  // 注册全局清理回调：api 拦截器 401 踢出时清理残留连接
  registerWsCleanup(() => {
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    wsStore.connected = false
    wsStore.connecting = false
  })

  return { connect, disconnect, sendGroupMessage, sendPrivateMessage, sendHeartbeat, syncState }
}
