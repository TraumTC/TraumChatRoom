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
  const replyTo = ref(null)             // 引用消息 { id, senderName, content }
  const friendRequestCount = ref(0)     // 未处理的好友申请数量
  const privateUnreadSenders = ref({})  // 有未读私聊消息的发送者 { name: { name, username } }
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

  // 添加私聊消息（用对方的名字作为 key）
  function addPrivateMessage(msg) {
    const myId = JSON.parse(localStorage.getItem('myId') || 'null')
    const myName = localStorage.getItem('myName')
    // 优先用 ID 比较，ID 不可用时用用户名比较（游客无 ID）
    const isMyMsg = myId != null ? msg.sender?.id === myId : msg.sender?.name === myName
    // 对方的名字：我发的 → receiver.name；别人发的 → sender.name
    const otherName = isMyMsg ? (msg.receiver?.name || 'unknown') : (msg.sender?.name || 'unknown')

    const isCurrent = currentChat.value.type === 'private' && currentChat.value.name === otherName

    // 确保私聊消息容器存在
    if (!privateMessages.value[otherName]) {
      privateMessages.value[otherName] = []
    }

    // 去重 + 替换临时消息
    const arr = privateMessages.value[otherName]
    const existingIdx = arr.findIndex(m => m.id === msg.id)
    if (existingIdx >= 0) return  // 完全重复，跳过

    // 如果是后端回传的真实消息（正 ID），替换对应的临时消息（负 ID）
    if (msg.id > 0 && isMyMsg) {
      const tempIdx = arr.findIndex(m =>
        m.id < 0 && m.content === msg.content && m.sender?.name === msg.sender?.name
      )
      if (tempIdx >= 0) {
        arr.splice(tempIdx, 1, msg)  // 替换临时消息
        return
      }
    }

    arr.push(msg)

    // 不是当前聊天对象时增加未读计数（仅他人消息）
    if (!isCurrent && !isMyMsg) {
      unreadCounts.value[otherName] = (unreadCounts.value[otherName] || 0) + 1
      // 记录发送者（用于红点显示）
      privateUnreadSenders.value[otherName] = {
        name: otherName,
        username: msg.sender?.username || otherName
      }
      if (isPageHidden.value) startTitleFlash()
    }

    // 自动打开私聊标签（如果是对方发来的新消息）
    if (!isCurrent && !isMyMsg && !privateTabs.value.some(t => t.name === otherName)) {
      privateTabs.value.push({ name: otherName, id: msg.sender?.id })
    }
  }

  // 处理消息撤回
  function handleMessageRecalled(data) {
    updateMessage(data.messageId, {
      content: data.senderName + ' 撤回了一条消息',
      recalled: true
    })
  }

  // 设置引用消息
  function setReplyTo(msg) {
    replyTo.value = { id: msg.id, senderName: msg.sender?.name, content: msg.content }
  }

  // 清除引用
  function clearReplyTo() {
    replyTo.value = null
  }

  // 好友申请计数
  function setFriendRequestCount(count) {
    friendRequestCount.value = count
  }
  function incrementFriendRequestCount() {
    friendRequestCount.value++
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
    currentChat.value = { type: 'private', name: user.name, username: user.username || user.name, id: user.id }
    if (user.name) {
      // 确保标签存在
      if (!privateTabs.value.some(t => t.name === user.name)) {
        privateTabs.value.push({ name: user.name, id: user.id })
      }
      unreadCounts.value[user.name] = 0  // 清除未读
      delete privateUnreadSenders.value[user.name]  // 清除红点
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
           currentChat, currentPrivateChat, unreadCounts, privateUnreadSenders,
           totalUnread, loading, error, notifications, replyTo, friendRequestCount,
           addMessage, addPrivateMessage, updateMessage,
           handleMessageRecalled, setOnlineUsers,
           openGroupChat, openPrivateChat, closePrivateTab,
           setPrivateMessages, addNotification,
           setLoading, setError, setPageHidden,
           setReplyTo, clearReplyTo,
           setFriendRequestCount, incrementFriendRequestCount,
           startTitleFlash, stopTitleFlash }
})
