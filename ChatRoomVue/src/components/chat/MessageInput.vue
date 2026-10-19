<!-- src/components/chat/MessageInput.vue — 消息输入区（亮色） -->
<template>
  <div ref="inputContainerRef" class="relative" style="background: var(--color-card); border-top: 1px solid var(--color-border)">
    <!-- 引用预览条 -->
    <div v-if="chatStore.replyTo"
         class="flex items-center gap-2 px-2 sm:px-3 py-1dot5 sm:py-2"
         style="background: var(--color-hover); border-bottom: 1px solid var(--color-border)">
      <div class="w-0.5 h-4 rounded-full shrink-0" style="background: var(--color-signal)"></div>
      <div class="flex-1 min-w-0 flex items-start gap-1">
        <span class="text-xs font-medium shrink-0" style="color: var(--color-signal)">{{ chatStore.replyTo.senderName }}</span>
        <span class="text-xs flex-1 min-w-0 line-clamp-2 break-all" style="color: var(--color-ink-soft)">{{ chatStore.replyTo.content }}</span>
      </div>
      <button @click="chatStore.clearReplyTo()" class="close-btn-inline">
        <AppIcon name="x" :size="14" />
      </button>
    </div>

    <!-- 输入区 -->
    <div class="p-2 sm:p-3 flex items-end gap-2 relative">
      <!-- @提及弹窗 -->
      <MentionPopup v-if="showMention"
                    ref="mentionPopupRef"
                    :keyword="mentionKeyword"
                    @select="handleMentionSelect"
                    @close="showMention = false" />

      <!-- 文件上传 -->
      <label class="input-icon-btn cursor-pointer shrink-0" style="color: var(--color-ink-soft)" title="上传文件">
        <AppIcon name="paperclip" :size="20" />
        <input type="file" class="hidden" :accept="acceptTypes" @change="handleFileUpload" />
      </label>

      <!-- 表情包 -->
      <div ref="emojiWrapRef" class="relative shrink-0">
        <button @click="showEmoji = !showEmoji"
                class="input-icon-btn"
                :style="{ color: showEmoji ? 'var(--color-signal)' : 'var(--color-ink-soft)' }"
                title="表情">
          <AppIcon name="smile" :size="20" />
        </button>
        <EmojiPicker v-if="showEmoji" @select="handleEmojiSelect" />
      </div>

      <!-- 输入框 -->
      <textarea
        v-model="inputText"
        @keydown="handleKeydown"
        @input="handleInput"
        @paste="handlePaste"
        @compositionstart="isComposing = true"
        @compositionend="handleCompositionEnd"
        @focus="onInputFocus"
        @blur="onInputBlur"
        :placeholder="placeholderText"
        maxlength="2000"
        rows="1"
        ref="inputRef"
        class="flex-1 resize-none rounded-lg px-3 py-2 text-base sm:text-sm min-h-[40px] sm:min-h-0 focus:outline-none transition-colors message-textarea"
        style="background: var(--color-hover); color: var(--color-ink); border: 1px solid var(--color-border); overflow-y: hidden"
      />

      <!-- 字数提示 -->
      <div v-if="inputText.length >= 2000" class="absolute bottom-full mb-1 text-xs" style="color: var(--color-warn)">
        已达 2000 字上限
      </div>

      <!-- 发送按钮：移动端仅图标，桌面端仅文字 -->
      <button class="send-btn shrink-0" :disabled="!inputText.trim()" @click="sendMessage">
        <AppIcon name="send" :size="20" class="sm:hidden" />
        <span class="send-btn-text hidden sm:inline">发送</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import MentionPopup from './MentionPopup.vue'
import EmojiPicker from './EmojiPicker.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

const emit = defineEmits(['fileUpload'])
const { sendGroupMessage, sendPrivateMessage } = useWebSocket()
const authStore = useAuthStore()
const chatStore = useChatStore()

const inputText = ref('')
const inputRef = ref(null)
const inputContainerRef = ref(null)
const emojiWrapRef = ref(null)
const showMention = ref(false)
const showEmoji = ref(false)
const mentionKeyword = ref('')
const isComposing = ref(false)
const mentionPopupRef = ref(null)
const isMobile = ref(window.innerWidth < 640)

// 输入框自适应高度上限：五行。行高按实际计算样式动态取，
// 这样桌面端 text-sm 与移动端 text-base 各自按自己的行高封顶。
const MAX_INPUT_LINES = 5

const placeholderText = computed(() =>
  isMobile.value ? '输入消息...' : '输入消息...（Enter 发送，Shift+Enter 换行）'
)

/**
 * 根据内容撑高输入框，最多五行，超出后内部滚动。
 * scrollHeight 含 padding 不含 border，而 textarea 是 border-box，
 * 因此设置高度时要补上 border，否则最后一行会被裁掉 1~2px。
 */
function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  const style = window.getComputedStyle(el)
  const lineHeight = parseFloat(style.lineHeight) || 20
  const paddingY = (parseFloat(style.paddingTop) || 0) + (parseFloat(style.paddingBottom) || 0)
  const borderY = (parseFloat(style.borderTopWidth) || 0) + (parseFloat(style.borderBottomWidth) || 0)
  const maxContentHeight = lineHeight * MAX_INPUT_LINES + paddingY
  el.style.height = `${Math.min(el.scrollHeight, maxContentHeight) + borderY}px`
  el.style.overflowY = el.scrollHeight > maxContentHeight ? 'auto' : 'hidden'
}

function handleResize() {
  isMobile.value = window.innerWidth < 640
  nextTick(autoResize)
}

// 点击弹窗外任意区域 → 关闭表情选择弹窗（图标按钮与弹窗本身在 emojiWrapRef 内，不触发关闭）
function onOutsideClick(e) {
  if (!showEmoji.value) return
  if (emojiWrapRef.value && !emojiWrapRef.value.contains(e.target)) {
    showEmoji.value = false
  }
}

function onInputFocus() {
  nextTick(() => {
    if (inputContainerRef.value) {
      inputContainerRef.value.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    }
  })
}

function onInputBlur() {
  // 失焦时不做特殊处理，键盘收起后布局自动恢复
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  document.addEventListener('mousedown', onOutsideClick)
  nextTick(autoResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('mousedown', onOutsideClick)
})

const props = defineProps({
  chatType: { type: String, default: 'group' },
  receiver: { type: String, default: null }
})

// 输入内容变化（打字、表情、@、程序清空）都重新计算高度
watch(inputText, () => nextTick(autoResize))

// 移动端长按消息头像 → 在光标处插入 @昵称
watch(() => chatStore.mentionRequest, (req) => {
  if (!req) return
  chatStore.clearMentionRequest()
  // @ 只对群聊有意义，私聊下直接丢弃请求
  if (props.chatType !== 'group') return
  insertMention(req.name)
})

const acceptTypes = computed(() => {
  if (authStore.isGuest) return 'image/*'
  return 'image/*,.pdf,.doc,.docx,.zip,.rar,.md,video/mp4,video/webm,video/ogg,video/quicktime'
})

// 统一键盘处理：IME 组合期间不发送；@弹窗打开时 Enter/方向键交给弹窗导航
function handleKeydown(e) {
  if (isComposing.value) return  // 中文输入法上屏 Enter 不触发任何行为

  // @弹窗打开：Enter/↑↓/Esc 由弹窗导航，不再发送
  if (showMention.value && mentionPopupRef.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      mentionPopupRef.value.moveSelection(1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      mentionPopupRef.value.moveSelection(-1)
      return
    }
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      if (mentionPopupRef.value.selectCurrent()) {
        // 已选择用户，不再发送
      }
      return
    }
    if (e.key === 'Escape') {
      showMention.value = false
      return
    }
  }

  // Esc：关闭表情/@弹窗
  if (e.key === 'Escape') {
    if (showEmoji.value || showMention.value) {
      showEmoji.value = false
      showMention.value = false
      return
    }
  }

  // 普通 Enter（非 shift）→ 发送
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    sendMessage()
  }
}

function sendMessage() {
  const text = inputText.value.trim()
  if (!text) return

  if (props.chatType === 'group') {
    sendGroupMessage(text)
  } else {
    sendPrivateMessage(props.receiver, text)
  }

  inputText.value = ''
  showMention.value = false
  showEmoji.value = false
}

function handleInput() {
  if (isComposing.value) return
  detectMention()
}

function handleCompositionEnd() {
  isComposing.value = false
  detectMention()
  nextTick(autoResize)
}

function detectMention() {
  const lastAtIndex = inputText.value.lastIndexOf('@')
  if (lastAtIndex >= 0) {
    const keyword = inputText.value.substring(lastAtIndex + 1)
    if (!keyword.includes(' ')) {
      showMention.value = true
      mentionKeyword.value = keyword
      return
    }
  }
  showMention.value = false
}

async function handleMentionSelect(user) {
  const lastAtIndex = inputText.value.lastIndexOf('@')
  inputText.value = inputText.value.substring(0, lastAtIndex) + `@${user.name} `
  showMention.value = false
  await nextTick()
  inputRef.value?.focus()
}

/**
 * 在光标处插入 @昵称（移动端长按头像触发）。
 * 若光标前正在输入 @关键词，先把它替换掉，避免出现 "@张三 @李" 这种残留。
 */
function insertMention(name) {
  const mention = `@${name} `
  const textarea = inputRef.value
  if (!textarea) {
    inputText.value += mention
    return
  }
  // 输入框未聚焦时 selectionStart 可能停留在旧位置甚至 0，此时统一追加到末尾
  const focused = document.activeElement === textarea
  const start = focused ? (textarea.selectionStart ?? inputText.value.length) : inputText.value.length
  const end = focused ? (textarea.selectionEnd ?? start) : inputText.value.length
  let before = inputText.value.slice(0, start)
  const after = inputText.value.slice(end)

  before = before.replace(/@[^\s@]*$/, '')
  // 与前面的正文之间补一个空格，避免 "你好@张三"
  const prefix = before && !/\s$/.test(before) ? ' ' : ''
  const inserted = prefix + mention
  inputText.value = before + inserted + after
  showMention.value = false

  const caret = before.length + inserted.length
  nextTick(() => {
    textarea.focus()
    textarea.setSelectionRange(caret, caret)
    autoResize()
  })
}

// 剪贴板图片扩展名白名单（与后端 allowed-types 保持一致）
const PASTE_IMAGE_EXT = {
  'image/jpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/png': 'png',
  'image/gif': 'gif',
  'image/bmp': 'bmp',
  'image/webp': 'webp'
}

/**
 * Ctrl+V 粘贴图片：直接走与「选择文件」相同的上传链路（占位 + 进度 + 发送）。
 * 只有剪贴板里确实有图片时才拦截默认行为，纯文本粘贴保持浏览器原生行为。
 */
function handlePaste(e) {
  const items = e.clipboardData?.items
  if (!items) return

  const imageFiles = []
  for (const item of items) {
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const file = item.getAsFile()
      // 个别浏览器 getAsFile() 返回的 File.type 为空，用 item.type 兜底
      if (file) imageFiles.push({ file, type: file.type || item.type })
    }
  }
  if (imageFiles.length === 0) return

  e.preventDefault()
  imageFiles.forEach(({ file, type }, index) => {
    const ext = PASTE_IMAGE_EXT[type]
    if (!ext) {
      window.$message?.warning('不支持的图片格式')
      return
    }
    emit('fileUpload', new File([file], pastedImageName(ext, index, imageFiles.length), { type }))
  })
}

function pastedImageName(ext, index, total) {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  // 带毫秒：fileRequestId 由「文件名 + 大小 + lastModified」派生，
  // 名字过于接近会让同一秒内两次粘贴被后端幂等注解误判为重复提交。
  const stamp = `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}${String(d.getMilliseconds()).padStart(3, '0')}`
  const suffix = total > 1 ? `-${index + 1}` : ''
  return `粘贴图片-${stamp}${suffix}.${ext}`
}

function handleFileUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  emit('fileUpload', file)
  e.target.value = ''
}

// 表情选择：在光标位置插入 emoji
function handleEmojiSelect(emoji) {
  const textarea = inputRef.value
  if (textarea) {
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    inputText.value = inputText.value.substring(0, start) + emoji + inputText.value.substring(end)
    nextTick(() => {
      textarea.focus()
      textarea.setSelectionRange(start + emoji.length, start + emoji.length)
    })
  } else {
    inputText.value += emoji
  }
}
</script>
