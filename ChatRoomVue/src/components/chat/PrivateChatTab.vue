<!-- src/components/chat/PrivateChatTab.vue — 私聊标签条 -->
<template>
  <div class="h-10 px-2 flex items-center gap-1 bg-gray-50 border-b border-gray-200 overflow-x-auto">
    <button v-for="tab in privateTabs" :key="tab.name"
            @click="selectTab(tab)"
            @auxclick.prevent="closeTab(tab)"
            class="px-3 py-1.5 text-sm rounded-t whitespace-nowrap shrink-0"
            :class="isActive(tab) ? 'text-blue-600 border-b-2 border-blue-500' : 'text-gray-500 hover:text-gray-700'">
      {{ tab.name }}
      <!-- 未读角标 -->
      <span v-if="getUnread(tab.name) > 0"
            class="ml-1 min-w-4 h-4 px-1 rounded-full bg-red-500 text-white text-[10px] leading-none inline-flex items-center justify-center">
        {{ getUnread(tab.name) }}
      </span>
      <span class="ml-1 text-gray-300 hover:text-red-500">×</span>
    </button>

    <!-- 无标签提示 -->
    <span v-if="privateTabs.length === 0" class="text-xs text-gray-400 px-2">
      点击在线用户或好友开始私聊
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()

// 当前打开的私聊标签
const privateTabs = computed(() => chatStore.privateTabs || [])

function isActive(tab) {
  return chatStore.currentPrivateChat?.name === tab.name
}

function getUnread(name) {
  return chatStore.unreadCounts[name] || 0
}

function selectTab(tab) {
  chatStore.openPrivateChat(tab)
}

function closeTab(tab) {
  chatStore.closePrivateTab(tab.name)
  // 如果关闭的是当前标签，切换回群聊
  if (isActive(tab)) {
    chatStore.openGroupChat()
  }
}
</script>
