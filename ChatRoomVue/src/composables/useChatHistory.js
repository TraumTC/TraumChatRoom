// src/composables/useChatHistory.js — 历史消息加载、分页、智能滚动
import { ref, computed, nextTick, watch } from 'vue'
import { messageApi } from '@/api/message'

export function useChatHistory(chatStore, authStore) {
  const scrollerRef = ref(null)
  const pageSize = 50
  const hasMore = ref(true)
  const isNearBottom = ref(true)
  const showNewMessageHint = ref(false)
  let suppressScrollWatch = false

  const messages = computed(() => chatStore.messages)
  const currentChat = computed(() => chatStore.currentChat)
  const isPrivateMode = computed(() => currentChat.value.type === 'private')

  // 当前显示的消息（群聊或私聊）
  const displayMessages = computed(() => {
    if (!isPrivateMode.value) return chatStore.messages
    const username = currentChat.value.username
    return chatStore.privateMessages[username] || []
  })

  function scrollToBottom() {
    nextTick(() => {
      scrollerRef.value?.scrollToBottom()
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
        if (scrollerRef.value) {
          scrollerRef.value.scrollToItem(loadedCount)
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

  // 滚动事件处理：检测底部位置 + 顶部加载历史
  function handleScroll() {
    const el = scrollerRef.value?.$el
    if (!el) return
    const threshold = 100
    const isBottom = el.scrollHeight - el.scrollTop - el.clientHeight < threshold
    isNearBottom.value = isBottom
    if (isBottom) {
      showNewMessageHint.value = false
    }
    if (el.scrollTop < 50) {
      loadHistory()
    }
  }

  // 打开私聊并加载历史
  async function startPrivateChat(user) {
    if (authStore.isGuest) {
      window.$message?.error('游客不能发送私聊消息')
      return
    }
    chatStore.openPrivateChat(user)
    const username = user.username || user.name
    if (!chatStore.privateMessages[username] || chatStore.privateMessages[username].length === 0) {
      chatStore.setLoading(true)
      try {
        const res = await messageApi.getPrivateHistory(username, { size: pageSize })
        if (res.data.code === 200) {
          chatStore.setPrivateMessages(username, [...res.data.data.items].reverse())
        }
      } catch (e) {
        chatStore.setError('加载私聊历史失败')
        window.$message?.error('加载私聊历史失败')
      } finally {
        chatStore.setLoading(false)
      }
    }
    isNearBottom.value = true
    showNewMessageHint.value = false
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

  // 监听消息数量变化 → 智能滚动
  watch(() => displayMessages.value.length, (newLen, oldLen) => {
    if (suppressScrollWatch) return
    if (newLen > oldLen) {
      if (isNearBottom.value) {
        scrollToBottom()
      } else {
        showNewMessageHint.value = true
      }
    }
  })

  // 监听会话切换 → 重置滚动状态
  watch(isPrivateMode, () => {
    isNearBottom.value = true
    showNewMessageHint.value = false
    scrollToBottom()
  })

  return {
    scrollerRef, hasMore, isNearBottom, showNewMessageHint,
    messages, currentChat, isPrivateMode, displayMessages,
    scrollToBottom, scrollToBottomAndHideHint,
    loadHistory, handleScroll, startPrivateChat, loadInitialHistory,
  }
}
