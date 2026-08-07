<!-- src/components/chat/PrivateChatTab.vue — 私聊标签条（亮色） -->
<template>
  <div class="h-10 px-2 flex items-center gap-1 overflow-x-auto"
       style="background: var(--color-ghost); border-bottom: 1px solid var(--color-border)">
    <button v-for="tab in privateTabs" :key="tab.username"
            @click="selectTab(tab)"
            @auxclick.prevent="closeTab(tab)"
            class="px-3 py-1.5 text-sm rounded-t whitespace-nowrap shrink-0 transition-colors"
            :class="isActive(tab) ? 'is-active' : ''"
            :style="isActive(tab) ? 'color: var(--color-signal)' : 'color: var(--color-ink-soft)'">
      {{ tab.name }}
      <span v-if="getUnread(tab.username) > 0"
            class="ml-1 min-w-4 h-4 px-1 rounded-full text-white text-[10px] leading-none inline-flex items-center justify-center tabular"
            style="background: var(--color-alarm)">
        {{ getUnread(tab.username) }}
      </span>
      <span @click.stop.prevent="closeTab(tab)" class="ml-1 cursor-pointer"
            style="color: var(--color-ink-faint)">×</span>
    </button>

    <span v-if="privateTabs.length === 0" class="text-xs px-2" style="color: var(--color-ink-faint)">
      点击在线用户或好友开始私聊
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()

const privateTabs = computed(() => chatStore.privateTabs || [])

function isActive(tab) {
  return chatStore.currentPrivateChat?.username === tab.username
}

function getUnread(username) {
  return chatStore.unreadCounts[username] || 0
}

function selectTab(tab) {
  chatStore.openPrivateChat(tab)
}

function closeTab(tab) {
  const wasActive = isActive(tab)
  chatStore.closePrivateTab(tab.username)
  if (wasActive) {
    if (privateTabs.value.length > 0) {
      chatStore.openPrivateChat(privateTabs.value[privateTabs.value.length - 1])
    } else {
      chatStore.openGroupChat()
    }
  }
}
</script>

<style scoped>
.is-active {
  border-bottom: 2px solid var(--color-signal);
}
</style>
