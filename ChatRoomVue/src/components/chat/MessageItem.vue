<!-- src/components/chat/MessageItem.vue — 单条消息（微信风格气泡） -->
<template>
  <!-- 撤回消息：系统提示样式 -->
  <div v-if="message.recalled"
       class="flex justify-center py-1 px-4">
    <span class="text-xs text-gray-400 bg-gray-50/80 px-3 py-1 rounded-full">
      {{ message.content }}
    </span>
  </div>

  <!-- 正常消息 -->
  <div v-else class="flex gap-3 px-4 py-1.5 group" :class="isSelf ? 'flex-row-reverse' : ''"
       @contextmenu.prevent="onContextMenu">
    <!-- 头像 -->
    <UserAvatar :user="message.sender" size="md" />

    <!-- 消息内容 -->
    <div class="min-w-0" :class="isSelf ? 'flex flex-col items-end' : 'flex flex-col items-start'">
      <!-- 发送者名称 + 时间 -->
      <div class="text-xs text-gray-400 mb-1 px-1">
        <span v-if="message.aiReply" class="text-blue-500 font-medium mr-1">AI</span>
        <span>{{ message.sender?.name }}</span>
        <span class="ml-2">{{ formatTime(message.createdAt) }}</span>
      </div>

      <!-- 引用摘要 -->
      <div v-if="quotedMsg" class="mb-1 px-2.5 py-1.5 bg-gray-50 border-l-2 border-blue-400 rounded text-xs text-gray-500 max-w-[400px] truncate">
        <span class="text-blue-500 font-medium">{{ quotedMsg.senderName }}:</span>
        {{ quotedMsg.content }}
      </div>

      <!-- AI 消息 -->
      <div v-if="message.aiReply"
           class="relative bubble-other bg-blue-50 text-blue-900 border border-blue-100 rounded-lg px-3 py-2 break-words text-sm max-w-[480px]">
        {{ message.content }}
      </div>

      <!-- 文本消息 -->
      <div v-else-if="message.messageType === 'text'"
           class="relative rounded-lg px-3 py-2 break-words text-sm max-w-[480px]"
           :class="isSelf
             ? 'bubble-self bg-[#95EC69] text-gray-900'
             : 'bubble-other bg-white text-gray-900 border border-gray-200'"
           v-html="highlightMentions(message.content)">
      </div>

      <!-- 图片消息 -->
      <div v-else-if="message.messageType === 'image'">
        <img :src="message.filePath"
             :alt="message.fileName"
             class="max-w-[240px] rounded-lg cursor-pointer hover:opacity-90 transition-opacity"
             @click="showPreview = true" />
      </div>

      <!-- 视频消息 -->
      <div v-else-if="message.messageType === 'file' && isVideo(message.fileName)"
           class="bg-white border border-gray-200 rounded-lg overflow-hidden max-w-[400px]">
        <video controls preload="metadata" class="w-full rounded-t-lg max-h-[300px]">
          <source :src="message.filePath" />
          您的浏览器不支持视频播放
        </video>
        <div class="px-3 py-2 text-xs text-gray-500 border-t border-gray-100 truncate">
          🎬 {{ message.fileName }}
        </div>
      </div>

      <!-- 文件消息 -->
      <div v-else-if="message.messageType === 'file'"
           class="bg-white border border-gray-200 rounded-lg px-3 py-2 max-w-[480px]">
        <div class="flex items-center gap-2 text-sm text-gray-700">
          <span>📄 {{ message.fileName }}</span>
          <span class="text-xs text-gray-400">{{ formatFileSize(message.fileSize) }}</span>
        </div>
      </div>

      <!-- 兜底 -->
      <div v-else
           class="relative rounded-lg px-3 py-2 break-words text-sm max-w-[480px]"
           :class="isSelf
             ? 'bubble-self bg-[#95EC69] text-gray-900'
             : 'bubble-other bg-white text-gray-900 border border-gray-200'">
        {{ message.content }}
      </div>
    </div>

    <!-- 图片预览 -->
    <ImagePreview :visible="showPreview"
                  :src="message.filePath"
                  :fileName="message.fileName"
                  @close="showPreview = false" />
  </div>

  <!-- 右键菜单 -->
  <Teleport to="body">
    <div v-if="menuVisible"
         class="fixed bg-white border border-gray-200 rounded-lg shadow-lg py-1 z-50 min-w-[100px]"
         :style="{ left: menuX + 'px', top: menuY + 'px' }"
         @click="menuVisible = false">
      <button class="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
              @click="handleQuote">
        引用
      </button>
      <!-- 下载（仅文件/图片/视频消息） -->
      <a v-if="hasFile"
         :href="message.filePath + '?name=' + encodeURIComponent(message.fileName)"
         download
         class="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
        下载
      </a>
      <button v-if="canRecall"
              class="w-full text-left px-4 py-2 text-sm text-red-500 hover:bg-red-50 transition-colors"
              @click="handleRecall">
        撤回
      </button>
    </div>
  </Teleport>
  <div v-if="menuVisible" class="fixed inset-0 z-40" @click="menuVisible = false"></div>
</template>

<script setup>
import { ref, computed } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import ImagePreview from '@/components/common/ImagePreview.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { messageApi } from '@/api/message'
import { formatTime, formatFileSize } from '@/utils/format'

const props = defineProps({ message: Object })
const authStore = useAuthStore()
const chatStore = useChatStore()

const showPreview = ref(false)
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)

const isSelf = computed(() => {
  const myId = authStore.user?.id
  // 优先用 ID 比较，ID 不可用时用用户名比较（游客无 ID）
  if (myId != null) return props.message.sender?.id === myId
  return props.message.sender?.name === authStore.user?.name
})

// 是否有文件（图片/文件/视频消息）
const hasFile = computed(() => {
  return props.message.messageType === 'image' || props.message.messageType === 'file'
})

const quotedMsg = computed(() => {
  if (!props.message.replyToId) return null
  const all = chatStore.messages
  const found = all.find(m => m.id === props.message.replyToId)
  if (found) return { senderName: found.sender?.name, content: found.content }
  return { senderName: '', content: '消息已加载...' }
})

const canRecall = computed(() => {
  const isAdmin = authStore.user?.role === 'ROLE_ADMIN'
  if (!isSelf.value && !isAdmin) return false
  const createdAt = props.message.createdAt
  if (!createdAt) return true
  const sentTime = new Date(createdAt).getTime()
  if (isNaN(sentTime)) return true
  return (Date.now() - sentTime) < 2 * 60 * 1000
})

function onContextMenu(e) {
  menuX.value = e.clientX
  menuY.value = e.clientY
  menuVisible.value = true
}

function handleQuote() {
  chatStore.setReplyTo(props.message)
  menuVisible.value = false
}

async function handleRecall() {
  menuVisible.value = false
  if (!confirm('确定要撤回这条消息吗？')) return
  try {
    await messageApi.recall(props.message.id)
  } catch (e) {
    alert(e.response?.data?.message || '撤回失败')
  }
}

function highlightMentions(content) {
  if (!content) return ''
  return content.replace(/@([^\s]+)/g, (match, name) => {
    return `<span class="text-blue-500 font-medium">@${name}</span>`
  })
}

// 判断是否为视频文件
function isVideo(fileName) {
  if (!fileName) return false
  const ext = fileName.split('.').pop().toLowerCase()
  return ['mp4', 'webm', 'ogg', 'mov', 'avi'].includes(ext)
}
</script>

<style scoped>
.bubble-self {
  position: relative;
}
.bubble-self::after {
  content: '';
  position: absolute;
  top: 10px;
  right: -8px;
  border: 8px solid transparent;
  border-left-color: #95EC69;
}
.bubble-other {
  position: relative;
}
.bubble-other::after {
  content: '';
  position: absolute;
  top: 10px;
  left: -8px;
  border: 8px solid transparent;
  border-right-color: white;
}
.bubble-other.bg-blue-50::after {
  border-right-color: #eff6ff;
}
</style>
