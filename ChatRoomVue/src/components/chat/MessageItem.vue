<!-- src/components/chat/MessageItem.vue — 单条消息 -->
<template>
  <div class="flex gap-3 px-4 py-1.5 group" :class="isSelf ? 'flex-row-reverse' : ''">
    <!-- 头像 -->
    <UserAvatar :user="message.sender" size="md" />

    <!-- 消息内容 -->
    <div class="max-w-[70%] min-w-0">
      <!-- 发送者名称 + 时间 -->
      <div class="text-xs text-gray-400 mb-1" :class="isSelf ? 'text-right' : ''">
        <span v-if="message.isAiReply" class="text-blue-500 font-medium mr-1">AI</span>
        <span>{{ message.sender?.name }}</span>
        <span class="ml-2">{{ formatTime(message.createdAt) }}</span>
      </div>

      <!-- 撤回消息 -->
      <div v-if="message.isRecalled" class="text-gray-400 italic text-sm">
        {{ message.content }}
      </div>

      <!-- AI 消息 -->
      <div v-else-if="message.isAiReply"
           class="bg-blue-50 text-blue-800 rounded-lg px-3 py-2 break-words">
        {{ message.content }}
      </div>

      <!-- 文本消息 -->
      <div v-else-if="message.messageType === 'text'"
           class="rounded-lg px-3 py-2 break-words"
           :class="isSelf ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-900'"
           v-html="highlightMentions(message.content)">
      </div>

      <!-- 图片消息 -->
      <img v-else-if="message.messageType === 'image'"
           :src="message.filePath"
           :alt="message.fileName"
           class="max-w-full rounded-lg cursor-pointer"
           @click="previewImage(message.filePath)" />

      <!-- 文件消息 -->
      <div v-else-if="message.messageType === 'file'"
           class="flex items-center gap-2 text-sm text-gray-700 bg-gray-100 rounded-lg px-3 py-2">
        <span>📄 {{ message.fileName }}</span>
        <span class="text-xs text-gray-400">{{ formatFileSize(message.fileSize) }}</span>
        <a :href="message.filePath" target="_blank" class="text-blue-500 underline">下载</a>
      </div>

      <!-- 操作按钮：撤回 -->
      <div v-if="canRecall" class="mt-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button @click="handleRecall" class="text-xs text-gray-400 hover:text-red-500">撤回</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { messageApi } from '@/api/message'
import { formatTime, formatFileSize } from '@/utils/format'

const props = defineProps({ message: Object })
const authStore = useAuthStore()

const isSelf = computed(() => props.message.sender?.id === authStore.user?.id)

// 是否可以撤回：自己的消息（或管理员） + 2分钟内 + 未撤回
const canRecall = computed(() => {
  if (props.message.isRecalled) return false
  const isSelf = props.message.sender?.id === authStore.user?.id
  const isAdmin = authStore.user?.role === 'ROLE_ADMIN'
  if (!isSelf && !isAdmin) return false
  const sentTime = new Date(props.message.createdAt).getTime()
  const now = Date.now()
  return (now - sentTime) < 2 * 60 * 1000  // 2分钟
})

// @提及高亮（仅渲染昵称部分，防 XSS）
function highlightMentions(content) {
  if (!content) return ''
  return content.replace(/@([^\s]+)/g, (match, name) => {
    return `<span class="text-blue-500 font-medium">@${name}</span>`
  })
}

// 图片预览（简单实现，可扩展 ImagePreview 组件）
function previewImage(url) {
  window.open(url, '_blank')
}

async function handleRecall() {
  if (!confirm('确定要撤回这条消息吗？')) return
  try {
    await messageApi.recall(props.message.id)
  } catch (e) {
    alert(e.response?.data?.message || '撤回失败')
  }
}
</script>
