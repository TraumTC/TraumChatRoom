<!-- src/components/friend/FriendItem.vue — 单个好友项 -->
<template>
  <div class="flex items-center gap-3 px-3 py-2 cursor-pointer hover:bg-gray-100 transition-colors"
       @click="$emit('openChat', friend)">
    <div class="relative">
      <UserAvatar :user="friend" size="md" />
      <span v-if="friend.online"
            class="absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full bg-emerald-500 ring-2 ring-white" />
    </div>
    <div class="flex-1 min-w-0">
      <div class="text-sm text-gray-900 truncate">{{ friend.remark || friend.name }}</div>
      <div class="text-xs text-gray-400">{{ friend.online ? '在线' : formatTime(friend.lastActiveTime) }}</div>
    </div>

    <!-- 更多操作 -->
    <div class="relative shrink-0">
      <button class="p-1.5 rounded-full text-gray-400 hover:bg-gray-200 hover:text-gray-600"
              @click.stop="showMenu = !showMenu" aria-label="更多操作">
        <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
          <path d="M6 10a2 2 0 11-4 0 2 2 0 014 0zm6 0a2 2 0 11-4 0 2 2 0 014 0zm6 0a2 2 0 11-4 0 2 2 0 014 0z" />
        </svg>
      </button>

      <!-- 菜单 -->
      <div v-if="showMenu" class="absolute right-0 mt-1 bg-white shadow-lg rounded-lg py-1 min-w-36 z-30 text-sm"
           @click.stop>
        <button class="w-full px-3 py-2 text-left text-gray-700 hover:bg-gray-50" @click="emit('openChat', friend)">
          发消息
        </button>
        <button class="w-full px-3 py-2 text-left text-gray-700 hover:bg-gray-50" @click="showRemark = true">
          改备注
        </button>
        <div class="my-1 border-t border-gray-100"></div>
        <button class="w-full px-3 py-2 text-left text-red-500 hover:bg-red-50" @click="handleDelete">
          删除好友
        </button>
      </div>
    </div>

    <!-- 修改备注弹窗 -->
    <div v-if="showRemark" class="fixed inset-0 z-50 flex items-center justify-center p-4" @click.self="showRemark = false">
      <div class="fixed inset-0 bg-black/40 z-40"></div>
      <div class="bg-white rounded-lg shadow-lg w-full max-w-sm relative z-50">
        <div class="px-5 pt-5 text-lg font-semibold text-gray-900">修改备注</div>
        <div class="px-5 py-4">
          <input v-model="remark" placeholder="输入备注名（留空清除）" maxlength="20"
                 class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
        </div>
        <div class="px-5 pb-5 pt-2 flex justify-end gap-2">
          <button class="px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 rounded" @click="showRemark = false">取消</button>
          <button class="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600" @click="saveRemark">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import { friendApi } from '@/api/friend'
import { formatTime } from '@/utils/format'

const props = defineProps({ friend: Object })
const emit = defineEmits(['openChat', 'deleted'])

const showMenu = ref(false)
const showRemark = ref(false)
const remark = ref('')

async function saveRemark() {
  try {
    await friendApi.updateRemark(props.friend.id, { remark: remark.value })
    showRemark.value = false
    emit('deleted')  // 触发列表刷新
  } catch (e) {
    alert(e.response?.data?.message || '修改失败')
  }
}

async function handleDelete() {
  if (!confirm(`确定要删除好友「${props.friend.name}」吗？`)) return
  try {
    await friendApi.delete(props.friend.id)
    emit('deleted')
  } catch (e) {
    alert(e.response?.data?.message || '删除失败')
  }
}
</script>
