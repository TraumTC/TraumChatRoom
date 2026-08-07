<!-- src/components/chat/MessageInput.vue — 消息输入区（亮色） -->
<template>
  <div class="relative" style="background: var(--color-card); border-top: 1px solid var(--color-border)">
    <!-- 引用预览条 -->
    <div v-if="chatStore.replyTo"
         class="flex items-center gap-2 px-3 py-2"
         style="background: var(--color-hover); border-bottom: 1px solid var(--color-border)">
      <div class="w-0.5 h-4 rounded-full shrink-0" style="background: var(--color-signal)"></div>
      <div class="flex-1 min-w-0">
        <span class="text-xs font-medium" style="color: var(--color-signal)">{{ chatStore.replyTo.senderName }}</span>
        <span class="text-xs ml-1 truncate" style="color: var(--color-ink-soft)">{{ chatStore.replyTo.content }}</span>
      </div>
      <button @click="chatStore.clearReplyTo()"
              class="shrink-0 p-0.5 transition-colors" style="color: var(--color-ink-faint)" @mouseenter="$event.target.style.color='var(--color-ink)'" @mouseleave="$event.target.style.color='var(--color-ink-faint)'">
        <AppIcon name="x" :size="14" />
      </button>
    </div>

    <!-- 输入区 -->
    <div class="p-3 flex items-end gap-2 relative">
      <!-- @提及弹窗 -->
      <MentionPopup v-if="showMention"
                    ref="mentionPopupRef"
                    :keyword="mentionKeyword"
                    @select="handleMentionSelect"
                    @close="showMention = false" />

      <!-- 文件上传 -->
      <label class="cursor-pointer shrink-0 transition-colors p-1" style="color: var(--color-ink-soft)">
        <AppIcon name="paperclip" :size="18" />
        <input type="file" class="hidden" :accept="acceptTypes" @change="handleFileUpload" />
      </label>

      <!-- 输入框 -->
      <textarea
        v-model="inputText"
        @keydown="handleKeydown"
        @input="handleInput"
        @compositionstart="isComposing = true"
        @compositionend="handleCompositionEnd"
        placeholder="输入消息...（Enter 发送，Shift+Enter 换行）"
        maxlength="2000"
        rows="1"
        ref="inputRef"
        class="flex-1 resize-none rounded-lg px-3 py-2 text-sm max-h-40 focus:outline-none"
        style="background: var(--color-hover); color: var(--color-ink); border: 1px solid var(--color-border)"
      />

      <!-- 字数提示 -->
      <div v-if="inputText.length >= 2000" class="absolute bottom-full mb-1 text-xs" style="color: var(--color-warn)">
        已达 2000 字上限
      </div>

      <!-- 发送按钮 -->
      <n-button type="primary" :disabled="!inputText.trim()" @click="sendMessage">
        发送
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import MentionPopup from './MentionPopup.vue'
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
const showMention = ref(false)
const mentionKeyword = ref('')
const isComposing = ref(false)
const mentionPopupRef = ref(null)

const props = defineProps({
  chatType: { type: String, default: 'group' },
  receiver: { type: String, default: null }
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
}

function handleInput() {
  if (isComposing.value) return
  detectMention()
}

function handleCompositionEnd() {
  isComposing.value = false
  detectMention()
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

function handleFileUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  emit('fileUpload', file)
  e.target.value = ''
}
</script>
