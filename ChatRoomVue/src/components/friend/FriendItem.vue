<!-- src/components/friend/FriendItem.vue — 单个好友项（亮色） -->
<template>
  <div class="flex items-center gap-3 px-3 py-2 cursor-pointer user-item"
       @click="$emit('openChat', friend)">
    <div class="relative shrink-0">
      <UserAvatar :user="friend" size="md" />
      <span v-if="friend.online" class="absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full signal-dot"
            style="border: 2px solid var(--color-card)"></span>
    </div>
    <div class="flex-1 min-w-0">
      <div class="text-sm truncate" style="color: var(--color-ink)">{{ friend.remark || friend.name }}</div>
      <div class="text-xs" style="color: var(--color-ink-faint)">
        {{ friend.online ? '在线' : formatTime(friend.lastActiveTime) }}
      </div>
    </div>

    <span v-if="hasUnread" class="rounded-full shrink-0" style="width:5px;height:5px;background:#FF3B30"></span>

    <div class="relative shrink-0">
      <n-button quaternary circle size="small" @click.stop="showMenu = !showMenu" aria-label="更多操作">
        <template #icon><AppIcon name="ellipsis" :size="16" /></template>
      </n-button>

      <n-dropdown v-model:show="showMenu" trigger="click" :options="menuOptions"
                  placement="bottom-end" @select="onMenuSelect" />
    </div>

    <n-modal v-model:show="showRemark" preset="dialog" title="修改备注"
             :positive-text="'保存'" negative-text="取消"
             @positive-click="saveRemark" @negative-click="showRemark = false">
      <n-input v-model:value="remark" placeholder="输入备注名（留空清除）" maxlength="20" />
    </n-modal>
  </div>
</template>

<script setup>
import { ref, computed, h } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useChatStore } from '@/stores/chat'
import { friendApi } from '@/api/friend'
import { formatTime } from '@/utils/format'

const props = defineProps({ friend: Object })
const emit = defineEmits(['openChat', 'deleted'])
const chatStore = useChatStore()

const showMenu = ref(false)
const showRemark = ref(false)
const remark = ref('')

// 未读红点：以 username 为 key
const hasUnread = computed(() => {
  return !!chatStore.privateUnreadSenders[props.friend.username || props.friend.name]
})

const menuOptions = [
  { label: '发消息', key: 'chat' },
  { label: '改备注', key: 'remark' },
  { type: 'divider', key: 'd1' },
  { label: '删除好友', key: 'delete', props: { style: 'color: var(--color-alarm)' } }
]

function onMenuSelect(key) {
  if (key === 'chat') emit('openChat', props.friend)
  else if (key === 'remark') { remark.value = props.friend.remark || ''; showRemark.value = true }
  else if (key === 'delete') handleDelete()
}

async function saveRemark() {
  try {
    await friendApi.updateRemark(props.friend.id, { remark: remark.value })
    showRemark.value = false
    emit('deleted')
  } catch (e) {
    window.$message?.error(e.response?.data?.message || '修改失败')
  }
}

function handleDelete() {
  const dialog = window.$dialog
  if (dialog) {
    dialog.warning({
      title: '删除好友',
      content: `确定要删除好友「${props.friend.name}」吗？`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await friendApi.delete(props.friend.id)
          emit('deleted')
        } catch (e) {
          window.$message?.error(e.response?.data?.message || '删除失败')
        }
      }
    })
  } else {
    if (confirm(`确定要删除好友「${props.friend.name}」吗？`)) {
      friendApi.delete(props.friend.id).then(() => emit('deleted')).catch(e => window.$message?.error(e.response?.data?.message || '删除失败'))
    }
  }
}
</script>
