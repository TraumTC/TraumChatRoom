<!-- src/components/chat/MentionPopup.vue — @提及弹窗 -->
<template>
  <div class="absolute bottom-full mb-2 w-60 bg-white shadow-lg rounded-lg py-1 z-20 overflow-hidden">
    <div v-if="filteredUsers.length === 0" class="px-3 py-2 text-sm text-gray-400">
      无匹配用户
    </div>
    <div v-for="(user, index) in filteredUsers" :key="user.username"
         :class="['px-3 py-2 flex items-center gap-2 text-sm cursor-pointer',
                  index === selectedIndex ? 'bg-blue-50' : 'hover:bg-gray-50']"
         @click="select(user)"
         @mouseenter="selectedIndex = index">
      <UserAvatar :user="user" size="sm" />
      <span class="text-gray-800">{{ user.name }}</span>
      <span v-if="user.isAi" class="text-xs text-blue-500 font-medium ml-auto">AI</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import { userApi } from '@/api/user'

const props = defineProps({
  keyword: { type: String, default: '' }
})
const emit = defineEmits(['select', 'close'])

const users = ref([])
const selectedIndex = ref(0)

// 按关键词过滤
const filteredUsers = computed(() => {
  if (!props.keyword) return users.value
  return users.value.filter(u =>
    u.name.includes(props.keyword) || u.username.includes(props.keyword)
  )
})

// 加载可@用户
async function loadUsers() {
  try {
    const res = await userApi.getMentionable()
    if (res.data.code === 200) {
      users.value = res.data.data
    }
  } catch (e) { /* 忽略 */ }
}

function select(user) {
  emit('select', user)
}

// 键盘上下选择 + 回车确认
function handleKeydown(e) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    selectedIndex.value = (selectedIndex.value + 1) % filteredUsers.value.length
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    selectedIndex.value = (selectedIndex.value - 1 + filteredUsers.value.length) % filteredUsers.value.length
  } else if (e.key === 'Enter') {
    e.preventDefault()
    if (filteredUsers.value[selectedIndex.value]) {
      select(filteredUsers.value[selectedIndex.value])
    }
  } else if (e.key === 'Escape') {
    emit('close')
  }
}

onMounted(() => {
  loadUsers()
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>
