<!-- src/components/friend/FriendRequest.vue — 好友申请列表弹窗（v-if 挂载模式） -->
<template>
  <n-modal v-model:show="visible" preset="card" title="好友申请"
           class="max-w-md" :style="{ width: '90%', maxWidth: '28rem' }"
           @after-leave="onAfterLeave">
    <!-- 分类切换 -->
    <n-tabs v-model:value="type" type="line" size="small" :animated="false" class="mb-2"
            @update:value="loadRequests">
      <n-tab name="received">收到的申请</n-tab>
      <n-tab name="sent">发出的申请</n-tab>
    </n-tabs>

    <!-- 状态筛选：默认仅待处理（与红点计数一致），可切换查看全部（含已处理，可删除记录） -->
    <div class="flex justify-end mb-2">
      <n-radio-group v-model:value="statusFilter" size="small" @update:value="loadRequests">
        <n-radio-button value="pending">待处理</n-radio-button>
        <n-radio-button value="all">全部</n-radio-button>
      </n-radio-group>
    </div>

    <!-- 列表 -->
    <div class="max-h-[60vh] overflow-y-auto scroll-thin">
      <div v-if="loading" class="py-8 text-center text-sm" style="color: var(--color-ink-faint)">加载中...</div>
      <div v-else-if="items.length === 0" class="py-8 text-center text-sm" style="color: var(--color-ink-faint)">
        暂无申请
      </div>

      <div v-for="item in items" :key="item.id"
           class="flex items-center gap-3 px-3 py-2 rounded-lg user-item">
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
import { NButton, NModal, NRadioButton, NRadioGroup, NTab, NTabs } from 'naive-ui'
import { ref, onMounted, nextTick } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { friendApi } from '@/api/friend'
import { useChatStore } from '@/stores/chat'

const emit = defineEmits(['close', 'changed'])

const chatStore = useChatStore()

const visible = ref(false)
const type = ref('received')
const statusFilter = ref('pending')   // pending=仅待处理（默认，与红点一致）；all=全部（含已处理）
const items = ref([])
const loading = ref(false)

// 弹窗完全关闭后（遮罩点击/ESC/X），通知父组件卸载
function onAfterLeave() {
  emit('close')
}

async function loadRequests() {
  loading.value = true
  try {
    const params = { type: type.value, page: 1, size: 50 }
    // 仅待处理时带 status=pending；全部时不传（后端查全部状态）
    if (statusFilter.value === 'pending') {
      params.status = 'pending'
    }
    const res = await friendApi.getRequests(params)
    if (res.data.code === 200) {
      items.value = res.data.data.items
    }
  } finally {
    loading.value = false
  }
}

async function handleRequest(id, action) {
  try {
    const res = await friendApi.handleRequest(id, { action })
    if (res.data.code === 200) {
      window.$message?.success(action === 'accept' ? '已同意' : '已拒绝')
      loadRequests()
      emit('changed')
      chatStore.incrementFriendListVersion()
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

// 组件挂载后，先渲染 modal（show=false），再切为 true 触发显示动画
onMounted(async () => {
  await nextTick()
  visible.value = true
  loadRequests()
})
</script>
