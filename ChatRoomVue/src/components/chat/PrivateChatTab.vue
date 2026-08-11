<!-- src/components/chat/PrivateChatTab.vue — 私聊标签条（NTabs line 现代化） -->
<template>
  <div class="px-2" style="background: var(--color-ghost); border-bottom: 1px solid var(--color-border)">
    <n-tabs v-model:value="activeKey" type="line" size="small" :animated="false"
            @update:value="onTabChange">
      <n-tab v-for="tab in privateTabs" :key="tab.username" :name="tab.username">
        <span class="inline-flex items-center gap-1.5">
          {{ tab.name }}
          <n-badge v-if="getUnread(tab.username) > 0" :value="getUnread(tab.username)" type="error" />
          <button @click.stop="closeTab(tab)" aria-label="关闭标签"
                  class="close-btn-inline">
            <svg viewBox="0 0 12 12" width="12" height="12" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
              <g stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
                <g fill="currentColor" fill-rule="nonzero">
                  <path d="M2.08859116,2.2156945 L2.14644661,2.14644661 C2.32001296,1.97288026 2.58943736,1.95359511 2.7843055,2.08859116 L2.85355339,2.14644661 L6,5.293 L9.14644661,2.14644661 C9.34170876,1.95118446 9.65829124,1.95118446 9.85355339,2.14644661 C10.0488155,2.34170876 10.0488155,2.65829124 9.85355339,2.85355339 L6.707,6 L9.85355339,9.14644661 C10.0271197,9.32001296 10.0464049,9.58943736 9.91140884,9.7843055 L9.85355339,9.85355339 C9.67998704,10.0271197 9.41056264,10.0464049 9.2156945,9.91140884 L9.14644661,9.85355339 L6,6.707 L2.85355339,9.85355339 C2.65829124,10.0488155 2.34170876,10.0488155 2.14644661,9.85355339 C1.95118446,9.65829124 1.95118446,9.34170876 2.14644661,9.14644661 L5.293,6 L2.14644661,2.85355339 C1.97288026,2.67998704 1.95359511,2.41056264 2.08859116,2.2156945 L2.14644661,2.14644661 L2.08859116,2.2156945 Z"/>
                </g>
              </g>
            </svg>
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

<style scoped>
</style>
