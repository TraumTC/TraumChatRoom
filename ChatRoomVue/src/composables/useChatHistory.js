// src/composables/useChatHistory.js — 历史消息加载、分页、智能滚动
import { ref, computed, nextTick, watch } from 'vue'
import { messageApi } from '@/api/message'

export function useChatHistory(chatStore, authStore) {
  // 群聊/私聊各自独立的滚动容器（双容器方案：群聊常驻保持滚动位置，私聊按会话重挂载）
  const groupScrollerRef = ref(null)
  const privateScrollerRef = ref(null)
  const pageSize = 50
  const hasMore = ref(true)
  // 是否已滚动到群聊最顶端（"没有更多消息了"只在真正到顶且无更早历史时显示）
  const isAtTop = ref(false)
  const privateHasMore = ref({})        // 各私聊会话是否还有更早历史 { username: boolean }
  const isNearBottom = ref(true)
  const showNewMessageHint = ref(false)
  let suppressScrollWatch = false

  const messages = computed(() => chatStore.messages)
  const currentChat = computed(() => chatStore.currentChat)
  const isPrivateMode = computed(() => currentChat.value.type === 'private')

  // 群聊消息（群聊容器常驻）
  const groupMessages = computed(() => chatStore.messages)
  // 当前私聊会话的消息数组
  const privateMessages = computed(() => {
    if (!isPrivateMode.value) return []
    return chatStore.privateMessages[currentChat.value.username] || []
  })
  // 当前会话消息（顶部状态栏/骨架屏/空状态判断用）
  const displayMessages = computed(() => isPrivateMode.value ? privateMessages.value : groupMessages.value)

  // 当前会话对应的滚动器
  function currentScroller() {
    return isPrivateMode.value ? privateScrollerRef.value : groupScrollerRef.value
  }

  function scrollToBottom() {
    nextTick(() => {
      currentScroller()?.scrollToBottom()
    })
  }

  function scrollToBottomAndHideHint() {
    scrollToBottom()
    showNewMessageHint.value = false
    isNearBottom.value = true
  }

  // 加载群聊历史消息（游标分页）
  async function loadHistory() {
    if (chatStore.loading || !hasMore.value || isPrivateMode.value) return
    suppressScrollWatch = true
    chatStore.setLoading(true)
    try {
      const cursor = messages.value.length > 0 ? messages.value[0].id : null
      const res = await messageApi.getHistory({ cursor, size: pageSize })
      if (res.data.code === 200) {
        const data = res.data.data
        const loadedCount = data.items.length
        chatStore.messages = [...data.items, ...chatStore.messages]
        await nextTick()
        if (groupScrollerRef.value) {
          groupScrollerRef.value.scrollToItem(loadedCount)
        }
        hasMore.value = data.hasMore
      }
    } catch (e) {
      chatStore.setError('加载历史消息失败')
    } finally {
      chatStore.setLoading(false)
      nextTick(() => { suppressScrollWatch = false })
    }
  }

  // 群聊容器滚动事件：检测底部位置 + 顶部加载更早历史
  function handleGroupScroll() {
    const el = groupScrollerRef.value?.$el
    if (!el) return
    const threshold = 100
    const isBottom = el.scrollHeight - el.scrollTop - el.clientHeight < threshold
    isNearBottom.value = isBottom
    if (isBottom) {
      showNewMessageHint.value = false
    }
    // 顶部判断（10px 内视为到顶）：驱动"没有更多消息了"显示
    isAtTop.value = el.scrollTop < 10
    if (el.scrollTop < 50) {
      loadHistory()
    }
  }

  // 私聊容器滚动事件：检测底部位置 + 顶部加载更早历史
  function handlePrivateScroll() {
    const el = privateScrollerRef.value?.$el
    if (!el) return
    const threshold = 100
    const isBottom = el.scrollHeight - el.scrollTop - el.clientHeight < threshold
    isNearBottom.value = isBottom
    if (isBottom) {
      showNewMessageHint.value = false
    }
    if (el.scrollTop < 50) {
      loadPrivateHistoryMore()
    }
  }

  // 私聊历史加载序号：快速切换会话时用递增序号丢弃过期响应，只保留最后一次
  let historyLoadSeq = 0

  // 加载指定私聊会话的最新历史（无条件覆盖本地，保证历史完整）
  async function loadPrivateHistory(username) {
    if (!username) return
    const seq = ++historyLoadSeq
    chatStore.setLoading(true)
    try {
      const res = await messageApi.getPrivateHistory(username, { size: pageSize })
      if (seq !== historyLoadSeq) return  // 已切换到其他会话，丢弃过期响应
      if (res.data.code === 200) {
        const data = res.data.data
        const msgs = [...data.items].reverse()
        chatStore.setPrivateMessages(username, msgs)
        privateHasMore.value[username] = data.hasMore
        // 等待 DOM 更新 + scroller 重新测量，再滚到底部
        await nextTick()
        await nextTick()
        scrollToBottom()
      }
    } catch (e) {
      if (seq === historyLoadSeq) {
        chatStore.setError('加载私聊历史失败')
        window.$message?.error('加载私聊历史失败')
      }
    } finally {
      if (seq === historyLoadSeq) chatStore.setLoading(false)
    }
  }

  // 私聊历史滚动加载更早（游标分页，前插到数组头部并保持滚动位置）
  async function loadPrivateHistoryMore() {
    if (chatStore.loading) return
    const username = currentChat.value.username
    if (!username || !privateHasMore.value[username]) return
    const arr = chatStore.privateMessages[username] || []
    if (arr.length === 0) return
    // 游标 = 当前最早一条真实消息 id（跳过负数临时消息）
    const cursor = arr.find(m => m.id > 0)?.id
    if (!cursor) return
    suppressScrollWatch = true  // 前插导致 length 增加，不误报"新消息"
    chatStore.setLoading(true)
    try {
      const res = await messageApi.getPrivateHistory(username, { cursor, size: pageSize })
      if (res.data.code === 200) {
        const data = res.data.data
        const more = [...data.items].reverse()
        if (more.length === 0) {
          privateHasMore.value[username] = false
          return
        }
        chatStore.setPrivateMessages(username, [...more, ...arr])
        privateHasMore.value[username] = data.hasMore
        // 前插 more 条后，原第一条位置变为索引 more.length，保持滚动位置不跳回
        await nextTick()
        if (privateScrollerRef.value) {
          privateScrollerRef.value.scrollToItem(more.length)
        }
      }
    } catch (e) {
      chatStore.setError('加载历史消息失败')
    } finally {
      chatStore.setLoading(false)
      nextTick(() => { suppressScrollWatch = false })
    }
  }

  // 打开私聊（切换会话 + 重置滚动；历史由下方 watch(currentChat) 统一加载）
  async function startPrivateChat(user) {
    if (authStore.isGuest) {
      window.$message?.error('游客不能发送私聊消息')
      return
    }
    chatStore.openPrivateChat(user)
    isNearBottom.value = true
    showNewMessageHint.value = false
    // 初始进入新会话时也尝试滚动
    await nextTick()
    scrollToBottom()
  }

  // 初始加载群聊历史
  async function loadInitialHistory() {
    chatStore.setLoading(true)
    try {
      const res = await messageApi.getHistory({ size: pageSize })
      if (res.data.code === 200) {
        const data = res.data.data
        chatStore.messages = [...data.items].reverse()
        hasMore.value = data.hasMore
        isNearBottom.value = true
        showNewMessageHint.value = false
        scrollToBottom()
      }
    } finally {
      chatStore.setLoading(false)
    }
  }

  // 定位一条群聊消息：当前列表没有时，从锚点重新加载一段历史。
  async function locateGroupMessage(messageId) {
    if (!messageId) return false
    chatStore.openGroupChat()
    showNewMessageHint.value = false

    let index = chatStore.messages.findIndex(m => m.id === messageId)
    if (index < 0) {
      chatStore.setLoading(true)
      suppressScrollWatch = true
      try {
        const res = await messageApi.getHistory({ anchorId: messageId, size: pageSize })
        if (res.data.code !== 200 || !Array.isArray(res.data.data?.items)) return false
        const around = res.data.data.items
        const byId = new Map(chatStore.messages.map(m => [m.id, m]))
        around.forEach(m => byId.set(m.id, m))
        chatStore.messages = [...byId.values()].sort((a, b) => a.id - b.id)
        index = chatStore.messages.findIndex(m => m.id === messageId)
      } finally {
        chatStore.setLoading(false)
        await nextTick()
        suppressScrollWatch = false
      }
    }

    if (index < 0) return false
    await nextTick()
    groupScrollerRef.value?.scrollToItem(index)
    return true
  }

  // 群聊新消息（同会话 push 追加；私聊模式不处理）
  watch(() => groupMessages.value.length, (newLen, oldLen) => {
    if (suppressScrollWatch || isPrivateMode.value) return
    if (newLen > oldLen) {
      if (isNearBottom.value) {
        scrollToBottom()
      } else {
        showNewMessageHint.value = true
      }
    }
  })

  // 私聊新消息（当前会话 push 追加；会话切换的长度变化跳过，避免误报）
  let lastPrivateChatKey = null
  watch(() => privateMessages.value.length, (newLen, oldLen) => {
    if (suppressScrollWatch) return
    const key = isPrivateMode.value ? currentChat.value.username : null
    if (key !== lastPrivateChatKey) {
      lastPrivateChatKey = key  // 会话切换：更新 key 并跳过本次变化
      return
    }
    if (newLen > oldLen) {
      if (isNearBottom.value) {
        scrollToBottom()
      } else {
        showNewMessageHint.value = true
      }
    }
  })

  // 监听私聊会话切换 → 无条件加载该会话最新历史（覆盖所有打开入口）
  watch(
    () => (currentChat.value.type === 'private' ? currentChat.value.username : null),
    (username) => {
      if (username) loadPrivateHistory(username)
    },
    { immediate: true }  // immediate：刷新后若恢复了私聊会话，立即加载其历史
  )

  // 监听会话切换 → 重置滚动状态
  // 进入私聊：滚到底部；切回群聊：按群聊容器实际位置校准 isNearBottom（避免从顶部跳到底部的闪烁）
  watch(isPrivateMode, (val) => {
    showNewMessageHint.value = false
    if (val) {
      isNearBottom.value = true
      scrollToBottom()
    } else {
      nextTick(() => {
        const el = groupScrollerRef.value?.$el
        if (el) {
          const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 100
          isNearBottom.value = atBottom
          if (atBottom) scrollToBottom()  // 切走时在看底部 → 滚到最新（无跳变）
        }
      })
    }
  })

  return {
    groupScrollerRef, privateScrollerRef, hasMore, isAtTop, isNearBottom, showNewMessageHint,
    messages, currentChat, isPrivateMode, groupMessages, privateMessages, displayMessages,
    scrollToBottom, scrollToBottomAndHideHint,
    loadHistory, handleGroupScroll, handlePrivateScroll,
    startPrivateChat, loadInitialHistory, locateGroupMessage,
  }
}
