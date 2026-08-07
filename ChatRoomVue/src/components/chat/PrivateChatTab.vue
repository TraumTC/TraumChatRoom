<!-- src/components/chat/PrivateChatTab.vue — 私聊标签条（NTabs line 现代化） -->
<template>
  <div class="px-2" style="background: var(--color-ghost); border-bottom: 1px solid var(--color-border)">
    <n-tabs v-model:value="activeKey" type="line" size="small" :animated="false"
            @update:value="onTabChange">
      <n-tab v-for="tab in privateTabs" :key="tab.username" :name="tab.username">
        <span class="inline-flex items-center gap-1.5">
          {{ tab.name }}
          <n-badge v-if="getUnread(tab.username) > 0" :value="getUnread(tab.username)" type="error" />
          <button class="ml-0.5 rounded-full transition-colors" style="color: var(--color-ink-faint)"
                  @click.stop="closeTab(tab)" aria-label="关闭标签">
            <AppIcon name="x" :size="12" />
          </button>
        </span>
      </n-tab>
    </n-tabs>
    <span v-if="privateTabs.length === 0" class="text-xs py-2 inline-block" style="color: var(--color-ink-faint)">
      点击在线用户或好友开始私聊
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()

const privateTabs = computed(() => chatStore.privateTabs || [])

// 激活标签 = 当前私聊会话 username
const activeKey = computed({
  get: () => chatStore.currentPrivateChat?.username || null,
  set: () => {}
})

function getUnread(username) {
  return chatStore.unreadCounts[username] || 0
}

function onTabChange(username) {
  const tab = privateTabs.value.find(t => t.username === username)
  if (tab) chatStore.openPrivateChat(tab)
}

function closeTab(tab) {
  const wasActive = chatStore.currentPrivateChat?.username === tab.username
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
