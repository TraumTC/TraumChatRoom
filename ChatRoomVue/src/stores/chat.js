// src/stores/chat.js — 聊天状态
import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { messageApi } from '@/api/message'
import { useAuthStore } from '@/stores/auth'
import { SESSION_KEYS, LEGACY_SESSION_KEYS } from '@/utils/session-keys'
import { registerSessionCleanup } from '@/utils/session-cleanup'

// 私聊数据一律以 username 为 key（name 可重复，username 唯一），防止重名串消息
export const useChatStore = defineStore('chat', () => {
  // 状态
  const messages = ref([])              // 群聊消息
  const onlineUsers = ref([])           // 在线用户列表
  const privateMessages = ref({})       // 私聊消息 { username: [message, ...] }
  const privateTabs = ref([])           // 私聊标签列表 [{ username, name, id }]
  const currentChat = ref({ type: 'group' })  // 当前会话：{type:'group'} 或 {type:'private', username, name, id}
  const unreadCounts = ref({})          // 未读消息计数 { username: count }
  // 群聊/私聊各自独立的加载标志：共用一个标志时，私聊拉历史会把群聊分页的守卫一起挡掉（反之亦然）
  const groupLoading = ref(false)
  const privateLoading = ref(false)
  const error = ref(null)
  const notifications = ref([])         // 通知队列（Toast）
  const mentionNotices = ref([])        // 群聊 @提及未读 [{ senderName, messageId, content, createdAt }]
  const replyTo = ref(null)             // 引用消息 { id, senderName, content }
  const friendRequestCount = ref(0)     // 未处理的好友申请数量
  const friendListVersion = ref(0)      // 好友列表刷新版本号（监听变化自动刷新）
  const privateUnreadSenders = ref({})  // 有未读私聊消息的发送者 { username: { name, username } }
  const unreadBaseIds = ref({})         // 未读汇总已统计的最大消息 id { username: maxId }，防止 WebSocket 推送重复计数
  const isPageHidden = ref(false)       // 页面是否隐藏（用于标题闪烁）
  const originalTitle = document.title

  // 计算属性
  // loading 语义 = “当前会话是否在加载”，切会话时不会串用另一侧的加载态（骨架屏/顶部 spinner 用它）
  const loading = computed(() => {
    return currentChat.value.type === 'private' ? privateLoading.value : groupLoading.value
  })
  const currentPrivateChat = computed(() => {
    return currentChat.value.type === 'private' ? currentChat.value : null
  })
  const totalUnread = computed(() => {
    return Object.values(unreadCounts.value).reduce((sum, n) => sum + n, 0)
  })
  const totalPrivateUnread = computed(() => {
    return Object.values(unreadCounts.value).reduce((sum, n) => sum + n, 0)
  })

  // ---------- 会话持久化（刷新后自动恢复私聊会话与页签） ----------
  // v2：旧版本存在「同一个人被昵称拆成两个页签」的脏数据，升版一次性丢弃旧列表
  // key 名由 utils/session-keys.js 统一定义，登出清理复用同一份，避免两处漏掉
  const TABS_KEY = SESSION_KEYS.privateTabs
  const CHAT_KEY = SESSION_KEYS.currentChat

  function clearPersistedSession() {
    try {
      localStorage.removeItem(TABS_KEY)
      localStorage.removeItem(CHAT_KEY)
    } catch (e) { /* 忽略 */ }
  }

  // store 初始化时从 localStorage 恢复（仅恢复私聊会话；校验结构避免损坏数据）
  try {
    LEGACY_SESSION_KEYS.forEach(k => localStorage.removeItem(k))  // 清理旧版脏数据
    const tabsRaw = localStorage.getItem(TABS_KEY)
    if (tabsRaw) {
      const tabs = JSON.parse(tabsRaw)
      if (Array.isArray(tabs)) {
        // 按 username 去重（同一个人只保留第一个页签）
        const byUsername = new Map()
        tabs.filter(t => t && t.username).forEach(t => {
          if (!byUsername.has(t.username)) byUsername.set(t.username, t)
        })
        privateTabs.value = [...byUsername.values()]
      }
    }
    const chatRaw = localStorage.getItem(CHAT_KEY)
    if (chatRaw) {
      const c = JSON.parse(chatRaw)
      if (c && c.type === 'private' && c.username) currentChat.value = c
    }
  } catch (e) { /* 忽略损坏数据 */ }

  // 变更时持久化（不在初始化时触发，避免把初始状态写回）
  watch(privateTabs, (val) => {
    try { localStorage.setItem(TABS_KEY, JSON.stringify(val || [])) } catch (e) { /* 忽略 */ }
  }, { deep: true })
  watch(currentChat, (val) => {
    try { localStorage.setItem(CHAT_KEY, JSON.stringify(val)) } catch (e) { /* 忽略 */ }
  }, { deep: true })

  // 游客强制回到群聊并清除已持久化的会话状态（游客不恢复私聊会话）
  function resetSessionState() {
    currentChat.value = { type: 'group' }
    privateTabs.value = []
    clearPersistedSession()
  }

  // 刷新恢复私聊会话后，若该会话有未读则视为已读（本地清零 + 有未读时推进后端游标），避免红点残留
  function clearCurrentUnread() {
    const c = currentChat.value
    if (!c || c.type !== 'private' || !c.username) return
    const hadUnread = (unreadCounts.value[c.username] || 0) > 0 || !!privateUnreadSenders.value[c.username]
    unreadCounts.value[c.username] = 0
    delete privateUnreadSenders.value[c.username]
    if (hadUnread) {
      messageApi.markRead(c.username).catch(() => {})
    }
  }

  // 添加群聊消息（去重）
  function addMessage(msg) {
    if (messages.value.some(m => m.id === msg.id)) return
    messages.value.push(msg)
    // 页面隐藏时标题闪烁
    if (isPageHidden.value) startTitleFlash()
  }

  // 私聊页签唯一入口：一个 username 只允许一个页签，已存在则原地更新展示信息
  function upsertPrivateTab({ username, name, id }) {
    if (!username || username === 'unknown') return  // 无法确定身份时不建页签，避免产生幽灵会话
    const existing = privateTabs.value.find(t => t.username === username)
    if (existing) {
      if (name) existing.name = name
      if (id != null) existing.id = id
      return
    }
    privateTabs.value.push({ username, name: name || username, id })
  }

  // 解析对方 username：优先用 username 字段；缺失时按 id 反查已知会话/在线列表，
  // 避免退化成昵称做 key（昵称可重复，会把同一个人拆成多个会话与页签）
  function resolveUsername(userLike) {
    if (!userLike) return null
    if (userLike.username) return userLike.username
    if (userLike.id != null) {
      const fromTab = privateTabs.value.find(t => String(t.id) === String(userLike.id))
      if (fromTab) return fromTab.username
      const fromOnline = onlineUsers.value.find(u => String(u.id) === String(userLike.id))
      if (fromOnline?.username) return fromOnline.username
    }
    return userLike.name || null
  }

  /**
   * 判断消息是否为自己发出 —— 全项目唯一的归属判断入口。
   *
   * 身份只认 authStore.user。早期版本另在 localStorage 存了一份 myId/myName，
   * 与这里构成两个数据源：一旦不一致，同一条消息会在渲染层算「别人的」、
   * 在归档层算「我的」。而下面 addPrivateMessage 用这个结果决定消息归到哪个会话
   * （other = isMyMsg ? receiver : sender），判断反了会把自己发出的消息
   * 归档成「和自己的私聊」，并连带影响临时消息替换与未读计数。
   *
   * 优先比 id。游客没有 id（不入库，见 AuthServiceImpl.loginAsGuest）时退回比
   * username —— 不能比 name：昵称可重复，重名用户会互相误判（同见下方 resolveUsername）。
   */
  function isMyMessage(msg) {
    const me = useAuthStore().user
    if (!me || !msg?.sender) return false
    if (me.id != null && msg.sender.id != null) {
      return String(msg.sender.id) === String(me.id)
    }
    return !!me.username && msg.sender.username === me.username
  }

  // 添加私聊消息（以 username 为 key 去重）
  function addPrivateMessage(msg) {
    const isMyMsg = isMyMessage(msg)
    // 对方 username：统一走 resolveUsername（name 是昵称，可能与 username 不同且可重复）
    const other = isMyMsg ? msg.receiver : msg.sender
    const otherUsername = resolveUsername(other) || 'unknown'
    const otherName = other?.name || otherUsername

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
      // 查找最近 5 秒内创建的临时消息，按时间倒序替换（支持敏感词替换导致 content 变化）
      const now = Date.now()
      const tempIdx = arr.findIndex(m =>
        m.id < 0 &&
        m.sender?.name === msg.sender?.name &&
        m.receiver?.username === msg.receiver?.username &&
        m._tempCreatedAt && (now - m._tempCreatedAt) < 5000
      )
      if (tempIdx >= 0) {
        arr.splice(tempIdx, 1, msg)  // 替换临时消息
        return
      }
    }

    arr.push(msg)

    // 当前会话收到对方消息 → 防抖推进已读游标（用户正在查看，刷新后不再误报未读）
    if (isCurrent && !isMyMsg) {
      scheduleReadPush(otherUsername)
    }

    // 不是当前聊天对象时增加未读计数（仅他人消息）
    // 防重：id 已包含在未读汇总（unreadBaseIds）中的消息不再重复计数
    if (!isCurrent && !isMyMsg) {
      const baseId = unreadBaseIds.value[otherUsername] || 0
      if (msg.id > baseId) {
        unreadCounts.value[otherUsername] = (unreadCounts.value[otherUsername] || 0) + 1
      }
      privateUnreadSenders.value[otherUsername] = {
        name: otherName,
        username: otherUsername
      }
      if (isPageHidden.value) startTitleFlash()
    }

    // 自动打开私聊标签（upsert 保证同一 username 只有一个页签）
    if (!isCurrent && !isMyMsg) {
      upsertPrivateTab({ username: otherUsername, name: otherName, id: msg.sender?.id })
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
    // NaN 防御：初始拉取失败/字段缺失时计数可能为 undefined，++ 会变 NaN 导致红点永不显示
    friendRequestCount.value = (Number(friendRequestCount.value) || 0) + 1
  }

  // 好友列表刷新版本号
  function incrementFriendListVersion() {
    friendListVersion.value++
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
  // 打开会话即视为已读：清除本地未读；若该会话此前有未读，则异步推进后端已读游标
  function openPrivateChat(user) {
    const username = resolveUsername(user)
    if (!username) return  // 身份不明时不切换会话，避免写入损坏的会话状态
    const name = user.name || username
    // 在清除本地未读之前记录是否有未读，决定是否推进后端游标（小优化：无未读不发请求）
    const hadUnread = (unreadCounts.value[username] || 0) > 0 || !!privateUnreadSenders.value[username]
    currentChat.value = { type: 'private', username, name, id: user.id }
    upsertPrivateTab({ username, name, id: user.id })
    unreadCounts.value[username] = 0  // 清除未读
    delete privateUnreadSenders.value[username]  // 清除红点
    if (hadUnread) {
      messageApi.markRead(username).catch(() => {})
    }
  }

  // 关闭私聊标签
  function closePrivateTab(username) {
    privateTabs.value = privateTabs.value.filter(t => t.username !== username)
  }

  // 合并后端未读汇总到本地状态（上线时调用一次，覆盖式权威同步）
  // items: [{ senderUsername, senderName, unreadCount, lastMessageId }]
  function mergeUnreadSummary(items) {
    if (!Array.isArray(items)) return
    items.forEach(it => {
      const username = it.senderUsername
      if (!username) return
      unreadCounts.value[username] = it.unreadCount || 0
      privateUnreadSenders.value[username] = {
        name: it.senderName || username,
        username
      }
      if (it.lastMessageId) {
        unreadBaseIds.value[username] = it.lastMessageId
      }
    })
  }

  // 防抖推进当前会话的已读游标（合并连续消息，避免频繁请求）
  // 用于「当前会话收到对方消息」时实时标记已读，使刷新后不会将已看过的消息误报为未读
  let readPushTimer = null
  let readPushUsername = null
  function scheduleReadPush(username) {
    if (!username) return
    readPushUsername = username
    clearTimeout(readPushTimer)
    readPushTimer = setTimeout(() => {
      const target = readPushUsername
      readPushUsername = null
      if (target) messageApi.markRead(target).catch(() => {})
    }, 800)
  }

  // 移除发送失败的本地乐观更新临时消息（负数 id，由 send-error 携带 clientId 精确定位）
  function removePendingMessage(clientId) {
    if (!clientId) return
    for (const chat of Object.values(privateMessages.value)) {
      const idx = chat.findIndex(m => m.id < 0 && m._clientId === clientId)
      if (idx >= 0) {
        chat.splice(idx, 1)
        return
      }
    }
  }

  // 加载私聊历史到 store（key = username）
  function setPrivateMessages(username, msgs) {
    privateMessages.value[username] = msgs
  }

  // 清除所有消息缓存（个人资料变更后调用，强制下次重新拉取以显示最新昵称）
  function clearMessages() {
    messages.value = []
    privateMessages.value = {}
    unreadCounts.value = {}
    privateUnreadSenders.value = {}
    clearPersistedSession()
  }

  /**
   * 登出时的彻底重置：把 store 恢复到「刚打开应用」的状态。
   *
   * 与 clearMessages() 的区别很关键 —— clearMessages 只清消息，
   * privateTabs / currentChat 等仍留在内存里。而 SPA 登出走 router.replace 不重载页面，
   * Pinia store 会活过登出，所以下一个登录者能看到上一个人的私聊页签；
   * 更隐蔽的是 watch(privateTabs) 会在下次变更时把旧页签重新写回 localStorage，
   * 光删 localStorage 根本删不掉。因此这里必须逐项复位内存状态。
   */
  function resetAll() {
    messages.value = []
    onlineUsers.value = []
    privateMessages.value = {}
    privateTabs.value = []
    currentChat.value = { type: 'group' }
    unreadCounts.value = {}
    privateUnreadSenders.value = {}
    unreadBaseIds.value = {}
    groupLoading.value = false
    privateLoading.value = false
    error.value = null
    notifications.value = []
    mentionNotices.value = []
    replyTo.value = null
    friendRequestCount.value = 0
    friendListVersion.value = 0
    isPageHidden.value = false
    stopTitleFlash()          // 恢复原始标题，避免带着上一个会话的闪烁状态
    clearPersistedSession()
  }

  // 注册到会话清理表：authStore.logout() 与 401 拦截器触发时自动复位
  registerSessionCleanup(resetAll)

  // 群聊 @提及未读计数
  const mentionUnreadCount = computed(() => mentionNotices.value.length)

  // 收到实时 @提及推送：去重入队 + 页面隐藏时标题闪烁
  function addMentionNotice(payload) {
    if (!payload?.messageId) return
    if (mentionNotices.value.some(n => n.messageId === payload.messageId)) return
    mentionNotices.value.unshift(payload)
    if (mentionNotices.value.length > 50) mentionNotices.value.length = 50
    if (isPageHidden.value) startTitleFlash()
  }

  // 上线拉取离线 @提及未读（合并去重）
  function mergeMentions(list) {
    if (!Array.isArray(list)) return
    list.forEach(n => {
      if (n?.messageId && !mentionNotices.value.some(x => x.messageId === n.messageId)) {
        mentionNotices.value.push(n)
      }
    })
    if (mentionNotices.value.length > 50) mentionNotices.value.length = 50
    if (mentionNotices.value.length > 0 && isPageHidden.value) startTitleFlash()
  }

  // 清除全部 @提及未读（点击提示条后调用）
  function clearMentions() {
    mentionNotices.value = []
  }

  // 清除单条 @提及（点击并定位消息后调用）
  function removeMention(messageId) {
    mentionNotices.value = mentionNotices.value.filter(n => n.messageId !== messageId)
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

  function setGroupLoading(val) { groupLoading.value = val }
  function setPrivateLoading(val) { privateLoading.value = val }
  function setError(msg) { error.value = msg }
  function setPageHidden(val) {
    isPageHidden.value = val
    if (!val) stopTitleFlash()  // 页面可见时停止闪烁
  }

  return { messages, onlineUsers, privateMessages, privateTabs,
           currentChat, currentPrivateChat, unreadCounts, privateUnreadSenders,
           totalUnread, totalPrivateUnread, loading, groupLoading, privateLoading, error, notifications, mentionNotices, mentionUnreadCount, replyTo, friendRequestCount, friendListVersion,
           addMessage, addPrivateMessage, updateMessage,
           handleMessageRecalled, setOnlineUsers,
           openGroupChat, openPrivateChat, closePrivateTab,
           setPrivateMessages, clearMessages, resetAll, addNotification,
           isMyMessage,
           addMentionNotice, mergeMentions, clearMentions, removeMention,
           setGroupLoading, setPrivateLoading, setError, setPageHidden,
           setReplyTo, clearReplyTo,
           setFriendRequestCount, incrementFriendRequestCount, incrementFriendListVersion,
           mergeUnreadSummary, removePendingMessage,
           clearCurrentUnread, resetSessionState,
           startTitleFlash, stopTitleFlash }
})
