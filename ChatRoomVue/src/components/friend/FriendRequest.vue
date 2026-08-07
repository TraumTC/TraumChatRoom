<!-- src/components/friend/FriendRequest.vue — 好友申请列表弹窗（亮色） -->
<template>
  <n-modal v-model:show="visible" preset="card" title="好友申请"
           class="max-w-md" :style="{ width: '90%', maxWidth: '28rem' }" @close="$emit('close')">
    <!-- 分类切换（与私聊标签条一致的 tab 风格） -->
    <div class="flex gap-1 mb-3" style="border-bottom: 1px solid var(--color-border)">
      <button :class="['px-3 py-1.5 text-sm transition-colors', type === 'received' ? 'is-active' : '']"
              :style="type === 'received' ? 'color: var(--color-signal)' : 'color: var(--color-ink-faint)'"
              @click="switchType('received')">收到的申请</button>
      <button :class="['px-3 py-1.5 text-sm transition-colors', type === 'sent' ? 'is-active' : '']"
              :style="type === 'sent' ? 'color: var(--color-signal)' : 'color: var(--color-ink-faint)'"
              @click="switchType('sent')">发出的申请</button>
    </div>

    <!-- 列表 -->
    <div class="max-h-[60vh] overflow-y-auto scroll-thin">
      <div v-if="loading" class="py-8 text-center text-sm" style="color: var(--color-ink-faint)">加载中...</div>
      <div v-else-if="items.length === 0" class="py-8 text-center text-sm" style="color: var(--color-ink-faint)">
        暂无申请
      </div>

      <div v-for="item in items" :key="item.id"
           class="flex items-center gap-3 px-3 py-2 rounded-lg transition-colors hover:bg-white/5">
        <UserAvatar :user="type === 'received' ? item.sender : item.receiver" size="md" />
        <div class="flex-1 min-w-0">
          <div class="text-sm truncate" style="color: var(--color-ink)">
            {{ (type === 'received' ? item.sender : item.receiver)?.name }}
          </div>
          <div class="text-xs truncate" style="color: var(--color-ink-faint)">{{ item.message || '无附言' }}</div>
        </div>

        <template v-if="item.status === 'accepted'">
          <span class="text-xs" style="color: var(--color-ink-faint)">已同意</span>
          <n-button text size="tiny" @click="deleteRequest(item.id)" title="删除记录">
            <AppIcon name="x" :size="12" />
          </n-button>
        </template>
        <template v-else-if="item.status === 'rejected'">
          <span class="text-xs" style="color: var(--color-ink-faint)">已拒绝</span>
          <n-button text size="tiny" @click="deleteRequest(item.id)" title="删除记录">
            <AppIcon name="x" :size="12" />
          </n-button>
        </template>
        <template v-else-if="item.status === 'expired'">
          <span class="text-xs" style="color: var(--color-ink-faint)">已过期</span>
          <n-button text size="tiny" @click="deleteRequest(item.id)" title="删除记录">
            <AppIcon name="x" :size="12" />
          </n-button>
        </template>

        <template v-else-if="type === 'received'">
          <n-button type="success" size="small" @click="handleRequest(item.id, 'accept')">同意</n-button>
          <n-button size="small" @click="handleRequest(item.id, 'reject')">拒绝</n-button>
        </template>

        <span v-else class="text-xs" style="color: var(--color-warn)">等待处理</span>
      </div>
    </div>
  </n-modal>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { friendApi } from '@/api/friend'

const emit = defineEmits(['close', 'changed'])

const visible = ref(true)
const type = ref('received')
const items = ref([])
const loading = ref(false)

async function loadRequests() {
  loading.value = true
  try {
    const res = await friendApi.getRequests({ type: type.value, page: 1, size: 50 })
    if (res.data.code === 200) {
      items.value = res.data.data.items
    }
  } finally {
    loading.value = false
  }
}

function switchType(t) {
  type.value = t
  loadRequests()
}

async function handleRequest(id, action) {
  try {
    const res = await friendApi.handleRequest(id, { action })
    if (res.data.code === 200) {
      window.$message?.success(action === 'accept' ? '已同意' : '已拒绝')
      loadRequests()
      emit('changed')
    } else {
      window.$message?.error(res.data.message)
    }
  } catch (e) {
    window.$message?.error(e.response?.data?.message || '操作失败')
  }
}

async function deleteRequest(id) {
  try {
    const res = await friendApi.deleteRequest(id)
    if (res.data.code === 200) {
      loadRequests()
      emit('changed')
    } else {
      window.$message?.error(res.data.message)
    }
  } catch (e) {
    window.$message?.error(e.response?.data?.message || '删除失败')
  }
}

onMounted(loadRequests)
</script>

<style scoped>
.is-active {
  border-bottom: 2px solid var(--color-signal);
}
</style>
