<!-- src/components/chat/MessageItem.vue — 单条消息（亮色气泡） -->
<template>
  <!-- 撤回消息：居中提示 -->
  <div v-if="message.recalled" class="flex justify-center py-1 px-4 msg-enter">
    <span class="text-xs px-3 py-1 rounded-full" style="background: var(--color-card); color: var(--color-ink-faint)">
      {{ message.content }}
    </span>
  </div>

  <!-- 正常消息（右键菜单：NDropdown manual 模式，手动定位） -->
  <n-dropdown v-else trigger="manual" :show="menuVisible" :x="menuX" :y="menuY"
              :options="menuOptions" @select="handleMenuSelect"
              @update:show="(v) => { if (!v) menuVisible = false }">
    <div class="flex gap-2dot5 px-4 py-1dot5 group msg-enter" :class="isSelf ? 'flex-row-reverse' : ''"
         @contextmenu.prevent="openMenu">
    <!-- 头像 -->
    <UserAvatar :user="message.sender" size="md" class="mt-1" />

    <div class="min-w-0 flex flex-col" :class="isSelf ? 'items-end' : 'items-start'">
      <!-- 发送者 + 时间 -->
      <div class="text-xs mb-0dot5 px-1 flex items-center gap-2" style="color: var(--color-ink-faint)">
        <template v-if="message.aiReply">
          <span class="text-xs font-semibold px-1.5 py-0.5 rounded" style="color: var(--color-signal); background: var(--color-signal-ghost)">AI</span>
        </template>
        <span>{{ message.sender?.name }}</span>
        <span class="tabular">{{ formatTime(message.createdAt) }}</span>
      </div>

      <!-- 引用摘要 -->
      <div v-if="quotedMsg" class="mb-1 px-2dot5 py-1 rounded text-xs truncate max-w-[400px]"
           style="background: var(--color-card); border-left: 2px solid var(--color-signal); color: var(--color-ink-soft)">
        <span style="color: var(--color-signal)">{{ quotedMsg.senderName }}：</span>{{ quotedMsg.content }}
      </div>

      <!-- AI 消息（信号签名竖线） -->
      <div v-if="message.aiReply"
           class="ai-bubble rounded-lg px-3 py-2 break-words text-sm max-w-[480px]"
           style="background: var(--color-signal-ghost); color: var(--color-signal); border-left: 2px solid var(--color-signal)">
        {{ message.content }}
      </div>

      <!-- 文本消息 -->
      <div v-else-if="message.messageType === 'text'"
           class="rounded-lg px-3 py-2 break-words text-sm max-w-[480px]"
           :class="isSelf ? 'bubble-self' : 'bubble-other'"
           v-html="safeContent"></div>

      <!-- 图片消息 -->
      <div v-else-if="message.messageType === 'image'">
        <img :src="message.filePath" :alt="message.fileName"
             class="max-w-[240px] rounded-lg cursor-pointer hover:opacity-90 transition-opacity"
             @click="showPreview = true" />
      </div>

      <!-- 视频消息 -->
      <div v-else-if="message.messageType === 'file' && isVideo(message.fileName)"
           class="rounded-lg overflow-hidden max-w-[400px]" style="background: var(--color-card); border: 1px solid var(--color-border)">
        <video controls preload="metadata" class="w-full rounded-t-lg max-h-[300px]">
          <source :src="message.filePath" />
          您的浏览器不支持视频播放
        </video>
        <div class="px-3 py-2 text-xs truncate flex items-center gap-1dot5" style="color: var(--color-ink-soft); border-top: 1px solid var(--color-border)">
          <AppIcon name="video" :size="13" />{{ message.fileName }}
        </div>
      </div>

      <!-- 文件消息 -->
      <div v-else-if="message.messageType === 'file'"
           class="rounded-lg px-3 py-2 max-w-[480px]" style="background: var(--color-card); border: 1px solid var(--color-border)">
        <div class="flex items-center gap-2 text-sm" style="color: var(--color-ink)">
          <AppIcon name="file-text" :size="15" style="color: var(--color-signal)" />
          <span class="truncate">{{ message.fileName }}</span>
          <span class="text-xs tabular" style="color: var(--color-ink-faint)">{{ formatFileSize(message.fileSize) }}</span>
        </div>
      </div>

      <!-- 兜底 -->
      <div v-else class="rounded-lg px-3 py-2 break-words text-sm max-w-[480px]"
           :class="isSelf ? 'bubble-self' : 'bubble-other'">
        {{ message.content }}
      </div>
    </div>

    <!-- 图片预览 -->
    <ImagePreview :visible="showPreview" :src="message.filePath"
                  :fileName="message.fileName" @close="showPreview = false" />
    </div>
  </n-dropdown>
</template>

<script setup>
import { ref, computed, h } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import ImagePreview from '@/components/common/ImagePreview.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
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
  if (myId != null) return props.message.sender?.id === myId
  return props.message.sender?.name === authStore.user?.name
})

const hasFile = computed(() => {
  return props.message.messageType === 'image' || props.message.messageType === 'file'
})

const quotedMsg = computed(() => {
  if (!props.message.replyToId) return null
  const found = chatStore.messages.find(m => m.id === props.message.replyToId)
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

// 安全渲染：先 HTML 转义，再高亮 @提及（防 XSS）
const safeContent = computed(() => {
  return highlightMentions(props.message.content, isSelf.value)
})

function escapeHtml(str) {
  if (!str) return ''
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// @提及高亮：自己气泡为蓝底白字（高亮用白/加粗保证可见），他人气泡用品牌蓝
function highlightMentions(content, self) {
  if (!content) return ''
  const escaped = escapeHtml(content)
  const highlightColor = self ? '#FFFFFF' : 'var(--color-signal)'
  return escaped.replace(/@([^<>\s]+)/g, (match, name) => {
    return `<span style="color: ${highlightColor}; font-weight: 600">@${escapeHtml(name)}</span>`
  })
}

function isVideo(fileName) {
  if (!fileName) return false
  const ext = fileName.split('.').pop().toLowerCase()
  return ['mp4', 'webm', 'ogg', 'mov', 'avi'].includes(ext)
}

// NDropdown 菜单项（带图标，动态生成）
const menuOptions = computed(() => {
  const options = [
    {
      label: '引用',
      key: 'quote',
      icon: () => h(AppIcon, { name: 'corner-up-left', size: 15 })
    }
  ]
  if (hasFile.value) {
    options.push({
      label: '下载',
      key: 'download',
      icon: () => h(AppIcon, { name: 'download', size: 15 })
    })
  }
  if (canRecall.value) {
    options.push({
      label: '撤回',
      key: 'recall',
      icon: () => h(AppIcon, { name: 'rotate-ccw', size: 15 }),
      props: { style: 'color: var(--color-alarm)' }
    })
  }
  return options
})

function openMenu(e) {
  menuX.value = e.clientX
  menuY.value = e.clientY
  menuVisible.value = true
}

function handleMenuSelect(key) {
  if (key === 'quote') handleQuote()
  else if (key === 'download') {
    // 触发下载链接
    const a = document.createElement('a')
    a.href = props.message.filePath + '?name=' + encodeURIComponent(props.message.fileName || '')
    a.download = ''
    a.click()
  } else if (key === 'recall') handleRecall()
}

function handleQuote() {
  chatStore.setReplyTo(props.message)
}

async function handleRecall() {
  const dialog = window.$dialog
  if (dialog) {
    dialog.warning({
      title: '撤回消息',
      content: '确定要撤回这条消息吗？',
      positiveText: '撤回',
      negativeText: '取消',
      onPositiveClick: () => doRecall()
    })
  } else {
    doRecall()
  }
}

async function doRecall() {
  try {
    await messageApi.recall(props.message.id)
  } catch (e) {
    window.$message?.error(e.response?.data?.message || '撤回失败')
  }
}
</script>

<style scoped>
.bubble-self {
  background: var(--color-signal);
  color: #FFFFFF;
}
.bubble-other {
  background: var(--color-card);
  color: var(--color-ink);
  border: 1px solid var(--color-border);
}
</style>

