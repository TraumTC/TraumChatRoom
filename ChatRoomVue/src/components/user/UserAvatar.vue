<!-- src/components/user/UserAvatar.vue — 头像组件 -->
<template>
  <!-- 有自定义头像：显示图片 -->
  <img v-if="user?.avatar"
       :src="user.avatar"
       :alt="user?.name || '头像'"
       class="rounded-full object-cover shrink-0"
       :class="sizeClass" />

  <!-- 无自定义头像：显示默认首字头像 -->
  <div v-else
       class="rounded-full flex items-center justify-center text-white font-semibold select-none shrink-0"
       :class="sizeClass"
       :style="{ backgroundColor: avatarConfig.color }">
    {{ avatarConfig.initial }}
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getDefaultAvatar } from '@/utils/avatar'

const props = defineProps({
  user: { type: Object, default: null },
  size: { type: String, default: 'md' }  // sm/md/lg
})

const sizeClass = computed(() => ({
  sm: 'w-8 h-8 text-sm',
  md: 'w-10 h-10 text-base',
  lg: 'w-14 h-14 text-xl'
}[props.size]))

const avatarConfig = computed(() => getDefaultAvatar(props.user?.name || '?'))
</script>
