<!-- src/components/chat/MessageItem.vue — 单条消息（亮色气泡 + 自绘右键菜单） -->
<template>
  <!-- 撤回消息：居中提示 -->
  <div v-if="message.recalled" class="flex justify-center py-1 px-4 msg-enter">
    <span class="text-xs px-3 py-1 rounded-full" style="background: var(--color-card); color: var(--color-ink-faint)">
      {{ message.content }}
    </span>
  </div>

  <!-- 正常消息（纯 flex 行，避免组件包裹破坏布局） -->
  <div v-else class="flex gap-2dot5 px-4 py-1dot5 group msg-enter msg-touchable" :class="isSelf ? 'flex-row-reverse' : ''"
       @contextmenu.prevent="openMenu"
       @touchstart="onTouchStart" @touchmove="onTouchMove" @touchend="onTouchEnd" @touchcancel="onTouchCancel">
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
      <div v-if="quotedMsg" class="mb-1 px-2dot5 py-1 rounded text-xs line-clamp-2 break-words max-w-[75vw] sm:max-w-[400px]"
           style="background: var(--color-card); border-left: 2px solid var(--color-signal); color: var(--color-ink-soft)">
        <span style="color: var(--color-signal)">{{ quotedMsg.senderName }}：</span>{{ quotedMsg.content }}
      </div>

      <!-- AI 消息（信号签名竖线） -->
      <div v-if="message.aiReply"
           class="ai-bubble rounded-lg px-3 py-2 break-words text-sm max-w-[75vw] sm:max-w-[480px]"
           style="background: var(--color-signal-ghost); color: var(--color-signal); border-left: 2px solid var(--color-signal)">
        {{ message.content }}
      </div>

      <!-- 文本消息 -->
      <div v-else-if="message.messageType === 'text'"
           class="rounded-lg px-3 py-2 break-words text-sm max-w-[75vw] sm:max-w-[480px]"
           :class="isSelf ? 'bubble-self' : 'bubble-other'"
           v-html="safeContent"></div>

      <!-- 图片消息 -->
      <div v-else-if="message.messageType === 'image'">
        <img :src="message.filePath" :alt="message.fileName"
             loading="lazy" decoding="async"
             class="max-w-[60vw] sm:max-w-[240px] rounded-lg cursor-pointer hover:opacity-90 transition-opacity"
             @click="onImageClick" />
      </div>

      <!-- 视频消息 -->
      <div v-else-if="message.messageType === 'file' && isVideo(message.fileName)"
           class="rounded-lg overflow-hidden max-w-[75vw] sm:max-w-[400px]" style="background: var(--color-card); border: 1px solid var(--color-border)">
        <video controls preload="none" class="w-full rounded-t-lg max-h-[300px]">
          <source :src="message.filePath" />
          您的浏览器不支持视频播放
        </video>
        <div class="px-3 py-2 text-xs truncate flex items-center gap-1dot5 min-w-0" style="color: var(--color-ink-soft); border-top: 1px solid var(--color-border)">
          <AppIcon name="video" :size="13" class="shrink-0" />
          <span class="truncate">{{ message.fileName }}</span>
        </div>
      </div>

      <!-- 文件消息 -->
      <div v-else-if="message.messageType === 'file'"
           class="rounded-lg px-3 py-2 max-w-[75vw] sm:max-w-[480px]" style="background: var(--color-card); border: 1px solid var(--color-border)">
        <div class="flex items-center gap-2 text-sm min-w-0" style="color: var(--color-ink)">
          <AppIcon name="file-text" :size="15" style="color: var(--color-signal)" class="shrink-0" />
          <span class="truncate min-w-0 break-all">{{ message.fileName }}</span>
          <span class="text-xs tabular shrink-0" style="color: var(--color-ink-faint)">{{ formatFileSize(message.fileSize) }}</span>
        </div>
      </div>

      <!-- 兜底 -->
      <div v-else class="rounded-lg px-3 py-2 break-words text-sm max-w-[75vw] sm:max-w-[480px]"
           :class="isSelf ? 'bubble-self' : 'bubble-other'">
        {{ message.content }}
      </div>
    </div>

    <!-- 图片预览 -->
    <ImagePreview :visible="showPreview" :src="message.filePath"
                  :fileName="message.fileName" @close="showPreview = false" />
  </div>

  <!-- 自绘右键菜单 -->
  <Teleport to="body">
    <template v-if="menuVisible">
      <div class="fixed inset-0 z-40" @click="closeMenu"></div>
      <div class="fixed rounded-lg overflow-hidden z-50 shadow-lg" :style="menuStyle"
           style="background: var(--color-card); border: 1px solid var(--color-border); min-width: 132px; padding: 4px">
        <button v-for="item in menuItems" :key="item.key"
                class="w-full flex items-center gap-2.5 px-3 py-2 text-sm rounded-md ctx-menu-item"
                :style="item.key === 'recall' ? 'color: var(--color-alarm)' : 'color: var(--color-ink)'"
                @click="handleMenu(item.key)">
          <AppIcon :name="item.icon" :size="15" />
          {{ item.label }}
        </button>
      </div>
    </template>
  </Teleport>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
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

// 右键菜单项（动态生成）
const menuItems = computed(() => {
  const items = [
    { key: 'quote', label: '引用', icon: 'corner-up-left' }
  ]
  if (hasFile.value) {
    items.push({ key: 'download', label: '下载', icon: 'download' })
  }
  if (canRecall.value) {
    items.push({ key: 'recall', label: '撤回', icon: 'rotate-ccw' })
  }
  return items
})

// 菜单定位（防越出视口右侧/底部）
const menuStyle = computed(() => {
  const style = { left: menuX.value + 'px', top: menuY.value + 'px' }
  const w = 140
  const h = menuItems.value.length * 38 + 10
  if (menuX.value + w > window.innerWidth) style.left = (window.innerWidth - w - 8) + 'px'
  if (menuY.value + h > window.innerHeight) style.top = (window.innerHeight - h - 8) + 'px'
  return style
})

function openMenu(e) {
  menuX.value = e.clientX
  menuY.value = e.clientY
  menuVisible.value = true
}

// ---------- 移动端长按呼出菜单（触屏无 contextmenu 事件） ----------
let longPressTimer = null
let touchStartPos = null
let suppressImageClick = false

function onTouchStart(e) {
  if (e.touches.length !== 1) return
  const t = e.touches[0]
  touchStartPos = { x: t.clientX, y: t.clientY }
  longPressTimer = setTimeout(() => {
    if (!touchStartPos) return
    menuX.value = touchStartPos.x
    menuY.value = touchStartPos.y
    menuVisible.value = true
    longPressTimer = null
    // 长按后屏蔽一次随后的 click（如图片预览），避免误触
    suppressImageClick = true
    setTimeout(() => { suppressImageClick = false }, 400)
  }, 500)
}

function onTouchMove(e) {
  if (!touchStartPos) return
  const t = e.touches[0]
  const dx = t.clientX - touchStartPos.x
  const dy = t.clientY - touchStartPos.y
  // 位移超过阈值视为滚动，取消长按
  if (Math.abs(dx) > 10 || Math.abs(dy) > 10) clearLongPress()
}

function onTouchEnd() {
  clearLongPress()
}

function onTouchCancel() {
  clearLongPress()
}

function clearLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
  touchStartPos = null
}

function onImageClick() {
  if (suppressImageClick) return
  showPreview.value = true
}

onUnmounted(clearLongPress)

function closeMenu() {
  menuVisible.value = false
}

function handleMenu(key) {
  closeMenu()
  if (key === 'quote') chatStore.setReplyTo(props.message)
  else if (key === 'download') {
    const a = document.createElement('a')
    a.href = props.message.filePath + '?name=' + encodeURIComponent(props.message.fileName || '')
    a.download = ''
    a.click()
  } else if (key === 'recall') handleRecall()
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

function escapeHtml(str) {
  if (!str) return ''
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// @提及高亮：自己气泡为绿底深字（高亮用深绿加粗保证可见），他人气泡用品牌蓝
function highlightMentions(content, self) {
  if (!content) return ''
  const escaped = escapeHtml(content)
  const highlightColor = self ? '#0A7A2F' : 'var(--color-signal)'
  return escaped.replace(/@([^<>\s]+)/g, (match, name) => {
    return `<span style="color: ${highlightColor}; font-weight: 600">@${escapeHtml(name)}</span>`
  })
}

function isVideo(fileName) {
  if (!fileName) return false
  const ext = fileName.split('.').pop().toLowerCase()
  return ['mp4', 'webm', 'ogg', 'mov', 'avi'].includes(ext)
}
</script>

<style scoped>
/* 移动端长按消息行时禁用 iOS 系统 callout（复制/保存等），保证自定义菜单正常呼出 */
.msg-touchable {
  -webkit-touch-callout: none;
}
.bubble-self {
  background: #95EC69;
  color: #1F2328;
  box-shadow: 0 1px 3px rgba(22, 119, 25, 0.12);
}
.bubble-other {
  background: var(--color-card);
  color: var(--color-ink);
  border: 1px solid var(--color-border);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
</style>
