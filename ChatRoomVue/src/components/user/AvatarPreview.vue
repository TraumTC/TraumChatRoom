<!-- src/components/user/AvatarPreview.vue — 头像预览 + 更换 -->
<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-[100] flex items-center justify-center"
         @click.self="$emit('close')">
      <!-- 遮罩 -->
      <div class="absolute inset-0 bg-black/70"></div>

      <!-- 内容 -->
      <div class="relative flex flex-col items-center gap-4">
        <!-- 关闭按钮 -->
        <button @click="$emit('close')"
                class="absolute -top-10 right-0 w-8 h-8 flex items-center justify-center text-white/80 hover:text-white transition-colors">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <!-- 头像大图 -->
        <div class="w-40 h-40 rounded-full overflow-hidden shadow-2xl ring-4 ring-white/20">
          <img v-if="user?.avatar" :src="user.avatar" :alt="user?.name"
               class="w-full h-full object-cover" />
          <div v-else class="w-full h-full flex items-center justify-center text-white text-5xl font-bold"
               :style="{ backgroundColor: avatarConfig.color }">
            {{ avatarConfig.initial }}
          </div>
        </div>

        <!-- 用户名 -->
        <div class="text-white text-lg font-medium">{{ user?.name }}</div>

        <!-- 更换头像按钮 -->
        <label class="px-4 py-2 bg-white/20 hover:bg-white/30 text-white text-sm rounded-lg cursor-pointer transition-colors backdrop-blur-sm">
          更换头像
          <input type="file" class="hidden" accept="image/*" @change="handleChange" />
        </label>

        <!-- 删除头像（仅有自定义头像时显示） -->
        <button v-if="user?.avatar" @click="handleDelete"
                class="text-sm text-white/60 hover:text-white/90 transition-colors">
          恢复默认头像
        </button>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
import { getDefaultAvatar } from '@/utils/avatar'

const props = defineProps({
  visible: { type: Boolean, default: false },
  user: { type: Object, default: null }
})

const emit = defineEmits(['close', 'change', 'delete'])

const avatarConfig = computed(() => getDefaultAvatar(props.user?.name || '?'))

function handleChange(e) {
  const file = e.target.files[0]
  if (file) {
    emit('change', file)
    emit('close')
  }
  e.target.value = ''
}

function handleDelete() {
  emit('delete')
  emit('close')
}
</script>
