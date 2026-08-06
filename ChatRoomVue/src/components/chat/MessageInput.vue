<!-- src/components/chat/MessageInput.vue — 消息输入框 -->
<template>
  <div class="border-t border-gray-200 p-3 bg-white flex items-end gap-2 relative">
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
</template>

<script setup>
import { ref, computed } from 'vue'
import MentionPopup from './MentionPopup.vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits(['fileUpload'])
const { sendGroupMessage, sendPrivateMessage } = useWebSocket()
const authStore = useAuthStore()

const inputText = ref('')
const inputRef = ref(null)
const showMention = ref(false)
const mentionKeyword = ref('')
const isComposing = ref(false)  // 中文输入法状态

const props = defineProps({
  chatType: { type: String, default: 'group' },  // 'group' 或 'private'
  receiver: { type: String, default: null }
})

// 游客仅允许上传图片
const acceptTypes = computed(() => {
  if (authStore.isGuest) return 'image/*'
  return 'image/*,.pdf,.doc,.docx,.zip,.rar'
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
  // 中文输入法 composition 期间不触发 @检测
  if (isComposing.value) return

  detectMention()
}

function handleCompositionEnd() {
  isComposing.value = false
  detectMention()
}

// 检测 @ 触发
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
  e.target.value = ''  // 重置 input，允许再次选择同一文件
}
</script>
