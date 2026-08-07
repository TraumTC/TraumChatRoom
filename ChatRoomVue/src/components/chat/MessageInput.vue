<!-- src/components/chat/MessageInput.vue — 消息输入框 -->
<template>
  <div class="border-t border-gray-200 bg-white relative">
    <!-- 引用预览条 -->
    <div v-if="chatStore.replyTo"
         class="flex items-center gap-2 px-3 py-2 bg-gray-50 border-b border-gray-100">
      <div class="w-0.5 h-4 bg-blue-400 rounded-full shrink-0"></div>
      <div class="flex-1 min-w-0">
        <span class="text-xs text-blue-500 font-medium">{{ chatStore.replyTo.senderName }}</span>
        <span class="text-xs text-gray-400 ml-1 truncate">{{ chatStore.replyTo.content }}</span>
      </div>
      <button @click="chatStore.clearReplyTo()"
              class="text-gray-400 hover:text-gray-600 shrink-0 p-0.5">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>

    <!-- 输入区 -->
    <div class="p-3 flex items-end gap-2 relative">
      <!-- @提及弹窗 -->
      <MentionPopup v-if="showMention"
                    :keyword="mentionKeyword"
                    @select="handleMentionSelect"
                    @close="showMention = false" />

      <!-- 文件上传按钮 -->
      <label class="text-gray-500 hover:text-blue-500 text-lg cursor-pointer shrink-0">
        📎
        <input type="file" class="hidden" :accept="acceptTypes" @change="handleFileUpload" />
      </label>

      <!-- 输入框 -->
      <textarea
        v-model="inputText"
        @keydown.enter.exact.prevent="sendMessage"
        @input="handleInput"
        @compositionstart="isComposing = true"
        @compositionend="handleCompositionEnd"
        placeholder="输入消息... (Enter发送, Shift+Enter换行)"
        maxlength="2000"
        rows="1"
        ref="inputRef"
        class="flex-1 resize-none rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 max-h-40"
      />

      <!-- 字数提示 -->
      <div v-if="inputText.length >= 2000" class="absolute bottom-full mb-1 text-xs text-amber-500">
        已达 2000 字上限
      </div>

      <!-- 发送按钮 -->
      <button @click="sendMessage"
              :disabled="!inputText.trim()"
              class="shrink-0 bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed">
        发送
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import MentionPopup from './MentionPopup.vue'
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

const props = defineProps({
  chatType: { type: String, default: 'group' },
  receiver: { type: String, default: null }
})

const acceptTypes = computed(() => {
  if (authStore.isGuest) return 'image/*'
  return 'image/*,.pdf,.doc,.docx,.zip,.rar,.md,video/mp4,video/webm,video/ogg,video/quicktime'
})

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

function handleMentionSelect(user) {
  const lastAtIndex = inputText.value.lastIndexOf('@')
  inputText.value = inputText.value.substring(0, lastAtIndex) + `@${user.name} `
  showMention.value = false
  inputRef.value?.focus()
}

function handleFileUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  emit('fileUpload', file)
  e.target.value = ''
}
</script>
