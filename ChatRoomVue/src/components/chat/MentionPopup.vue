<!-- src/components/chat/MentionPopup.vue — @提及弹窗（键盘导航由父组件 MessageInput 统一协调） -->
<template>
  <!-- maxHeight 由 visualViewport 实时计算：键盘弹起后弹窗只占可视区上方剩余空间，
       内容超出时在弹窗内部滚动，避免顶部候选被顶到屏幕外。 -->
  <div ref="popupRef"
       class="absolute bottom-full mb-2 w-60 rounded-lg py-1 z-20 overflow-x-hidden overflow-y-auto overscroll-contain scroll-thin shadow-xl"
       :style="{ background: 'var(--color-card)', border: '1px solid var(--color-border)', maxHeight: maxHeight + 'px' }">
    <div v-if="filteredUsers.length === 0" class="px-3 py-2 text-sm" style="color: var(--color-ink-faint)">
      无匹配用户
    </div>
    <div v-for="(user, index) in filteredUsers" :key="user.username"
         :data-mention-index="index"
         :class="['px-3 py-2 flex items-center gap-2 text-sm cursor-pointer transition-colors',
                  index === selectedIndex ? 'is-selected' : 'hover:bg-white/5']"
         @click="select(user)"
         @mouseenter="selectedIndex = index">
      <UserAvatar :user="user" size="sm" />
      <span style="color: var(--color-ink)">{{ user.name }}</span>
      <span v-if="user.isAi" class="text-xs font-medium ml-auto"
            style="color: var(--color-signal)">AI</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import { userApi } from '@/api/user'

const props = defineProps({
  keyword: { type: String, default: '' }
})
const emit = defineEmits(['select', 'close'])

const users = ref([])
const selectedIndex = ref(0)
const popupRef = ref(null)
const maxHeight = ref(280)

const filteredUsers = computed(() => {
  if (!props.keyword) return users.value
  return users.value.filter(u =>
    u.name.includes(props.keyword) || u.username.includes(props.keyword)
  )
})

/**
 * 计算弹窗可用的最大高度。
 * 弹窗锚定在输入行上沿，因此可用空间 = 输入行上沿到可视区顶部的距离。
 * 移动端键盘弹起后布局视口（尤其 iOS）不一定缩小，必须用 visualViewport
 * 的 offsetTop/height 校正，否则算出来的空间会偏大、顶部候选仍会被裁掉。
 */
function updateMaxHeight() {
  const popup = popupRef.value
  const anchor = popup?.parentElement
  if (!anchor) return

  const rect = anchor.getBoundingClientRect()
  const vv = window.visualViewport
  const visibleTop = vv ? vv.offsetTop : 0
  const available = rect.top - visibleTop - 12
  const cap = (vv ? vv.height : window.innerHeight) * 0.6
  maxHeight.value = Math.max(96, Math.min(available, cap, 360))
}

// 键盘导航时把选中项滚进可视区（不能用 scrollIntoView，会连带滚动页面）
function scrollSelectedIntoView() {
  nextTick(() => {
    const popup = popupRef.value
    const el = popup?.querySelector(`[data-mention-index="${selectedIndex.value}"]`)
    if (!popup || !el) return
    const top = el.offsetTop
    const bottom = top + el.offsetHeight
    if (top < popup.scrollTop) popup.scrollTop = top
    else if (bottom > popup.scrollTop + popup.clientHeight) {
      popup.scrollTop = bottom - popup.clientHeight
    }
  })
}

watch(selectedIndex, scrollSelectedIntoView)
watch(() => props.keyword, () => {
  selectedIndex.value = 0
  // 关键字变化后列表内容整体换了一批，滚动位置回到顶部，避免首项被留在可视区外
  nextTick(() => {
    if (popupRef.value) popupRef.value.scrollTop = 0
  })
})
watch(() => filteredUsers.value.length, (len) => {
  if (len === 0) selectedIndex.value = 0
  else if (selectedIndex.value >= len) selectedIndex.value = len - 1
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
  nextTick(updateMaxHeight)

  window.addEventListener('resize', updateMaxHeight)
  // iOS/Android 键盘弹起时 visualViewport 会变化，但不一定触发 window.resize
  window.visualViewport?.addEventListener('resize', updateMaxHeight)
  window.visualViewport?.addEventListener('scroll', updateMaxHeight)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateMaxHeight)
  window.visualViewport?.removeEventListener('resize', updateMaxHeight)
  window.visualViewport?.removeEventListener('scroll', updateMaxHeight)
})

defineExpose({ moveSelection, selectCurrent, filteredUsers })
</script>

<style scoped>
.is-selected {
  background: var(--color-signal-ghost);
}
</style>
