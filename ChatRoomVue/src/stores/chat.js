// src/stores/chat.js — 聊天状态
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useChatStore = defineStore('chat', () => {
  // 状态
  const messages = ref([])              // 群聊消息
  const onlineUsers = ref([])           // 在线用户列表
  const privateMessages = ref({})       // 私聊消息 { username: [message, ...] }
  const privateTabs = ref([])           // 私聊标签列表 [{ name, id }]
  const currentChat = ref({ type: 'group' })  // 当前会话：{type:'group'} 或 {type:'private', name, id}
  const unreadCounts = ref({})          // 未读消息计数 { username: count }
  const loading = ref(false)
  const error = ref(null)
  const notifications = ref([])         // 通知队列（Toast）
  const isPageHidden = ref(false)       // 页面是否隐藏（用于标题闪烁）
  const originalTitle = document.title

  // 计算属性
  const currentPrivateChat = computed(() => {
    return currentChat.value.type === 'private' ? currentChat.value : null
  })
  const totalUnread = computed(() => {
    return Object.values(unreadCounts.value).reduce((sum, n) => sum + n, 0)
  })

  // 添加群聊消息（去重）
  function addMessage(msg) {
    if (messages.value.some(m => m.id === msg.id)) return
    messages.value.push(msg)
    // 页面隐藏时标题闪烁
    if (isPageHidden.value) startTitleFlash()
  }

  // 添加私聊消息
  function addPrivateMessage(msg) {
    const senderName = msg.sender?.name || 'unknown'
    const isCurrent = currentChat.value.type === 'private' &&
                      (currentChat.value.name === senderName ||
                       (msg.receiver?.name && currentChat.value.name === msg.receiver.name))

    // 确保私聊消息容器存在
    if (!privateMessages.value[senderName]) {
      privateMessages.value[senderName] = []
    }

    // 去重
    if (!privateMessages.value[senderName].some(m => m.id === msg.id)) {
      privateMessages.value[senderName].push(msg)
    }

    // 不是当前聊天对象时增加未读计数（仅他人消息）
    const isOther = msg.sender?.id !== JSON.parse(localStorage.getItem('myId') || 'null')
    if (!isCurrent && isOther) {
      unreadCounts.value[senderName] = (unreadCounts.value[senderName] || 0) + 1
      if (isPageHidden.value) startTitleFlash()
    }

    // 自动打开私聊标签（如果是对方发来的新消息）
    if (!isCurrent && msg.sender && !privateTabs.value.some(t => t.name === senderName)) {
      privateTabs.value.push({ name: senderName, id: msg.sender.id })
    }
  }

  // 处理消息撤回
  function handleMessageRecalled(data) {
    updateMessage(data.messageId, {
      content: data.senderName + ' 撤回了一条消息',
      isRecalled: true
    })
  }

  // 更新消息（撤回、敏感词替换等）
  function updateMessage(messageId, updates) {
    const msg = messages.value.find(m => m.id === messageId)
    if (msg) {
      Object.assign(msg, updates)
    }
    // 也检查私聊消息
    for (const chat of Object.values(privateMessages.value)) {
      const pmsg = chat.find(m => m.id === messageId)
      if (pmsg) {
        Object.assign(pmsg, updates)
        break
      }
    }
  }

  // 设置在线用户
  function setOnlineUsers(users) {
    onlineUsers.value = users
  }

  // 打开群聊
  function openGroupChat() {
    currentChat.value = { type: 'group' }
  }

  // 打开私聊
  function openPrivateChat(user) {
    currentChat.value = { type: 'private', name: user.name, id: user.id }
    if (user.name) {
      // 确保标签存在
      if (!privateTabs.value.some(t => t.name === user.name)) {
        privateTabs.value.push({ name: user.name, id: user.id })
      }
      unreadCounts.value[user.name] = 0  // 清除未读
    }
  }

  // 关闭私聊标签
  function closePrivateTab(name) {
    privateTabs.value = privateTabs.value.filter(t => t.name !== name)
  }

  // 加载私聊历史到 store
  function setPrivateMessages(name, msgs) {
    privateMessages.value[name] = msgs
  }

  // 添加通知（Toast）
  function addNotification(notification) {
    const id = Date.now() + Math.random()
    notifications.value.push({ id, ...notification })
    setTimeout(() => {
      notifications.value = notifications.value.filter(n => n.id !== id)
    }, 3000)
  }

  // 标题闪烁
  let flashTimer = null
  function startTitleFlash() {
    if (flashTimer) return
    let toggle = false
    flashTimer = setInterval(() => {
      toggle = !toggle
      document.title = toggle ? '🔴 新消息' : originalTitle
    }, 1000)
  }
  function stopTitleFlash() {
    if (flashTimer) {
      clearInterval(flashTimer)
      flashTimer = null
    }
    document.title = originalTitle
  }

  function setLoading(val) { loading.value = val }
  function setError(msg) { error.value = msg }
  function setPageHidden(val) {
    isPageHidden.value = val
    if (!val) stopTitleFlash()  // 页面可见时停止闪烁
  }

  return { messages, onlineUsers, privateMessages, privateTabs,
           currentChat, currentPrivateChat, unreadCounts,
           totalUnread, loading, error, notifications,
           addMessage, addPrivateMessage, updateMessage,
           handleMessageRecalled, setOnlineUsers,
           openGroupChat, openPrivateChat, closePrivateTab,
           setPrivateMessages, addNotification,
           setLoading, setError, setPageHidden,
           startTitleFlash, stopTitleFlash }
})
