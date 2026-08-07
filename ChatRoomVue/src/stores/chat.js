// src/stores/chat.js — 聊天状态
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// 私聊数据一律以 username 为 key（name 可重复，username 唯一），防止重名串消息
export const useChatStore = defineStore('chat', () => {
  // 状态
  const messages = ref([])              // 群聊消息
  const onlineUsers = ref([])           // 在线用户列表
  const privateMessages = ref({})       // 私聊消息 { username: [message, ...] }
  const privateTabs = ref([])           // 私聊标签列表 [{ username, name, id }]
  const currentChat = ref({ type: 'group' })  // 当前会话：{type:'group'} 或 {type:'private', username, name, id}
  const unreadCounts = ref({})          // 未读消息计数 { username: count }
  const loading = ref(false)
  const error = ref(null)
  const notifications = ref([])         // 通知队列（Toast）
  const replyTo = ref(null)             // 引用消息 { id, senderName, content }
  const friendRequestCount = ref(0)     // 未处理的好友申请数量
  const privateUnreadSenders = ref({})  // 有未读私聊消息的发送者 { username: { name, username } }
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

  // 判断消息是否为自己发出
  function isMyMessage(msg) {
    const myId = localStorage.getItem('myId')
    if (myId) {
      return msg.sender?.id != null && String(msg.sender.id) === myId
    }
    return msg.sender?.name === localStorage.getItem('myName')
  }

  // 添加私聊消息（以 username 为 key 去重）
  function addPrivateMessage(msg) {
    const isMyMsg = isMyMessage(msg)
    // 对方 username：我发的 → receiver.username；别人发的 → sender.username
    const otherUsername = isMyMsg
      ? (msg.receiver?.name || 'unknown')   // 后端回显 receiver_name 即对方 username
      : (msg.sender?.name || 'unknown')
    const otherName = isMyMsg
      ? (msg.receiver?.name || otherUsername)
      : (msg.sender?.name || otherUsername)

    const isCurrent = currentChat.value.type === 'private'
      && currentChat.value.username === otherUsername

    // 确保私聊消息容器存在（key = username）
    if (!privateMessages.value[otherUsername]) {
      privateMessages.value[otherUsername] = []
    }

    // 去重
    const arr = privateMessages.value[otherUsername]
    const existingIdx = arr.findIndex(m => m.id === msg.id)
    if (existingIdx >= 0) return  // 完全重复，跳过

    // 后端回传的真实消息（正 ID）替换对应的临时消息（负 ID）
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
      unreadCounts.value[otherUsername] = (unreadCounts.value[otherUsername] || 0) + 1
      privateUnreadSenders.value[otherUsername] = {
        name: otherName,
        username: otherUsername
      }
      if (isPageHidden.value) startTitleFlash()
    }

    // 自动打开私聊标签
    if (!isCurrent && !isMyMsg && !privateTabs.value.some(t => t.username === otherUsername)) {
      privateTabs.value.push({ username: otherUsername, name: otherName, id: msg.sender?.id })
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

  // 打开私聊（入参统一 { username, name, id }）
  function openPrivateChat(user) {
    const username = user.username || user.name
    const name = user.name || username
    currentChat.value = { type: 'private', username, name, id: user.id }
    if (username) {
      if (!privateTabs.value.some(t => t.username === username)) {
        privateTabs.value.push({ username, name, id: user.id })
      }
      unreadCounts.value[username] = 0  // 清除未读
      delete privateUnreadSenders.value[username]  // 清除红点
    }
  }

  // 关闭私聊标签
  function closePrivateTab(username) {
    privateTabs.value = privateTabs.value.filter(t => t.username !== username)
  }

  // 加载私聊历史到 store（key = username）
  function setPrivateMessages(username, msgs) {
    privateMessages.value[username] = msgs
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
      document.title = toggle ? '· 新消息' : originalTitle
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
