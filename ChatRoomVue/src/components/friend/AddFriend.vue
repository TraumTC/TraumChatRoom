<!-- src/components/friend/AddFriend.vue — 添加好友弹窗（v-if 挂载模式） -->
<template>
  <n-modal v-model:show="visible" preset="card" title="添加好友" class="max-w-md"
           :style="{ width: '90%', maxWidth: '28rem' }"
           @after-leave="onAfterLeave">
    <n-input v-model:value="keyword" placeholder="输入用户名或昵称搜索" clearable @input="debouncedSearch">
      <template #prefix><AppIcon name="search" :size="14" /></template>
    </n-input>

    <div class="mt-3 max-h-64 overflow-y-auto scroll-thin">
      <div v-if="searching" class="py-8 text-center text-sm" style="color: var(--color-ink-faint)">搜索中...</div>
      <div v-else-if="results.length === 0 && keyword" class="py-8 text-center text-sm" style="color: var(--color-ink-faint)">
        未找到用户
      </div>

      <div v-for="user in results" :key="user.id"
           class="flex items-center gap-3 px-3 py-2 rounded-lg user-item">
        <UserAvatar :user="user" size="md" />
        <div class="flex-1 min-w-0">
          <div class="text-sm truncate" style="color: var(--color-ink)">{{ user.name }}</div>
          <div class="text-xs" style="color: var(--color-ink-faint)">{{ user.username }}</div>
        </div>

        <n-button v-if="user.friendStatus === 'none'" type="primary" size="small" @click="showApply(user)">
          添加
        </n-button>
        <span v-else-if="user.friendStatus === 'friend'" class="text-xs" style="color: var(--color-ink-faint)">已是好友</span>
        <span v-else-if="user.friendStatus === 'pending_sent'" class="text-xs" style="color: var(--color-warn)">已发送</span>
        <n-button v-else-if="user.friendStatus === 'pending_received'" type="success" size="small" @click="handleAccept(user)">
          同意
        </n-button>
      </div>
    </div>

    <!-- 申请附言 -->
    <n-modal :show="!!applyTarget" preset="dialog" :title="`添加 ${applyTarget?.name || ''}`"
             :positive-text="'发送申请'" negative-text="取消"
             @update:show="(s) => { if (!s) applyTarget = null }"
             @positive-click="sendApply" @negative-click="applyTarget = null">
      <n-input v-model:value="applyMessage" type="textarea" placeholder="附言（可选，最多100字）"
               maxlength="100" :rows="3" />
    </n-modal>
  </n-modal>
</template>

<script setup>
import { NButton, NInput, NModal } from 'naive-ui'
import { ref, onMounted, nextTick } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { friendApi } from '@/api/friend'
import { clearRequestId } from '@/utils/request-id'
import { useChatStore } from '@/stores/chat'

const emit = defineEmits(['close', 'added'])

const chatStore = useChatStore()

const visible = ref(false)
const keyword = ref('')
const results = ref([])
const searching = ref(false)
const applyTarget = ref(null)
const applyMessage = ref('')

function onAfterLeave() {
  emit('close')
}

let timer = null
function debouncedSearch() {
  clearTimeout(timer)
  if (!keyword.value.trim()) {
    results.value = []
    return
  }
  timer = setTimeout(search, 300)
}

async function search() {
  searching.value = true
  try {
    const res = await friendApi.search(keyword.value.trim())
    if (res.data.code === 200) {
      results.value = res.data.data
    }
  } finally {
    searching.value = false
  }
}

function showApply(user) {
  applyTarget.value = user
  applyMessage.value = ''
}

async function sendApply() {
  try {
    const res = await friendApi.sendRequest({
      receiverId: applyTarget.value.id,
      message: applyMessage.value
    })
    if (res.data.code === 200) {
      clearRequestId('friend-request')
      chatStore.addNotification({ type: 'success', message: '申请已发送' })
      chatStore.incrementFriendListVersion()
      applyTarget.value = null
      search()
    } else {
      window.$message?.error(res.data.message)
    }
  } catch (e) {
    window.$message?.error(e.response?.data?.message || '发送失败')
  }
}

async function handleAccept(user) {
  try {
    const res = await friendApi.getRequests({ type: 'received', status: 'pending', page: 1, size: 100 })
    if (res.data.code === 200) {
      const request = res.data.data.items.find(r => r.sender?.id === user.id)
      if (request) {
        await friendApi.handleRequest(request.id, { action: 'accept' })
        window.$message?.success(`已添加 ${user.name}`)
        search()
        emit('added')
      } else {
        window.$message?.warning('未找到该申请，可能已过期或被处理')
      }
    }
  } catch (e) {
    window.$message?.error(e.response?.data?.message || '操作失败')
  }
}

onMounted(async () => {
  await nextTick()
  visible.value = true
})
</script>
