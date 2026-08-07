<!-- src/components/chat/MentionPopup.vue — @提及弹窗（键盘导航由父组件 MessageInput 统一协调） -->
<template>
  <div class="absolute bottom-full mb-2 w-60 rounded-lg py-1 z-20 overflow-hidden shadow-xl"
       style="background: var(--color-night-raise); border: 1px solid var(--color-night-line)">
    <div v-if="filteredUsers.length === 0" class="px-3 py-2 text-sm" style="color: var(--color-paper-faint)">
      无匹配用户
    </div>
    <div v-for="(user, index) in filteredUsers" :key="user.username"
         :class="['px-3 py-2 flex items-center gap-2 text-sm cursor-pointer transition-colors',
                  index === selectedIndex ? 'is-selected' : 'hover:bg-white/5']"
         @click="select(user)"
         @mouseenter="selectedIndex = index">
      <UserAvatar :user="user" size="sm" />
      <span style="color: var(--color-paper)">{{ user.name }}</span>
      <span v-if="user.isAi" class="text-xs font-medium ml-auto"
            style="color: var(--color-amber)">AI</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import { userApi } from '@/api/user'

const props = defineProps({
  keyword: { type: String, default: '' }
})
const emit = defineEmits(['select', 'close'])

const users = ref([])
const selectedIndex = ref(0)

const filteredUsers = computed(() => {
  if (!props.keyword) return users.value
  return users.value.filter(u =>
    u.name.includes(props.keyword) || u.username.includes(props.keyword)
  )
})

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

// 供父组件键盘导航调用
function moveSelection(delta) {
  if (filteredUsers.value.length === 0) return
  selectedIndex.value =
    (selectedIndex.value + delta + filteredUsers.value.length) % filteredUsers.value.length
}
function selectCurrent() {
  if (filteredUsers.value[selectedIndex.value]) {
    select(filteredUsers.value[selectedIndex.value])
    return true
  }
  return false
}

onMounted(() => {
  loadUsers()
  selectedIndex.value = 0
})

defineExpose({ moveSelection, selectCurrent, filteredUsers })
</script>

<style scoped>
.is-selected {
  background: var(--color-amber-ghost);
}
</style>
