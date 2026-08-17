<!-- src/components/user/AvatarPreview.vue — 头像预览 + 裁剪 + 更换 -->
<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-[100] flex items-center justify-center"
         @click.self="$emit('close')">
      <!-- 遮罩 -->
      <div class="absolute inset-0 bg-black/70"></div>

      <!-- 内容 -->
      <div class="relative flex flex-col items-center gap-4">
        <!-- 关闭按钮 -->
        <button @click="$emit('close')" title="关闭" aria-label="关闭"
                class="close-btn-overlay">
          <svg width="20" height="20" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <!-- 头像大图（预览态显示裁剪后的图，否则显示当前头像） -->
        <div class="w-40 h-40 rounded-full overflow-hidden shadow-2xl ring-4 ring-white/20">
          <img v-if="previewUrl" :src="previewUrl" alt="预览"
               class="w-full h-full object-cover" />
          <img v-else-if="user?.avatar" :src="user.avatar" :alt="user?.name"
               class="w-full h-full object-cover" />
          <div v-else class="w-full h-full flex items-center justify-center text-white text-5xl font-bold"
               :style="{ backgroundColor: avatarConfig.color }">
            {{ avatarConfig.initial }}
          </div>
        </div>

        <!-- 用户名 -->
        <div class="text-white text-lg font-medium">{{ user?.name }}</div>

        <!-- 错误提示 -->
        <div v-if="errorMsg" class="text-sm" style="color: #FCA5A5">{{ errorMsg }}</div>

        <!-- 操作区：根据状态切换 -->
        <template v-if="uploading">
          <!-- 上传中 -->
          <div class="flex items-center gap-2 text-white/80 text-sm">
            <n-spin size="small" />
            上传中...
          </div>
        </template>

        <template v-else-if="previewUrl">
          <!-- 预览态：确认 / 重选 -->
          <div class="flex items-center gap-3">
            <button @click="confirmUpload" class="avatar-btn-primary">确认上传</button>
            <button @click="resetPreview" class="avatar-btn-ghost">重选</button>
          </div>
        </template>

        <template v-else>
          <!-- 默认态：选择图片 -->
          <label class="avatar-btn-primary cursor-pointer">
            更换头像
            <input type="file" class="hidden" accept="image/*" @change="handleFileSelect" />
          </label>

          <!-- 删除头像（始终显示，无自定义头像时点击无副作用） -->
          <button @click="handleDelete" class="avatar-btn-text">
            恢复默认头像
          </button>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getDefaultAvatar } from '@/utils/avatar'
import { validateAvatarFile, compressAvatar, readImageAsDataURL } from '@/utils/compressAvatar'

const props = defineProps({
  visible: { type: Boolean, default: false },
  user: { type: Object, default: null }
})

const emit = defineEmits(['close', 'change', 'delete'])

const avatarConfig = computed(() => getDefaultAvatar(props.user?.name || '?'))

const previewUrl = ref('')     // 裁剪预览 DataURL
const compressedBlob = ref(null) // 压缩后的 Blob
const uploading = ref(false)
const errorMsg = ref('')

// 弹窗关闭时重置状态
watch(() => props.visible, (val) => {
  if (!val) {
    resetPreview()
    errorMsg.value = ''
    uploading.value = false
  }
})

async function handleFileSelect(e) {
  const file = e.target.files[0]
  e.target.value = ''

  errorMsg.value = ''

  // 校验
  const validation = validateAvatarFile(file)
  if (!validation.valid) {
    errorMsg.value = validation.error
    return
  }

  try {
    // 读取原图 DataURL 用于预览
    const dataUrl = await readImageAsDataURL(file)
    previewUrl.value = dataUrl

    // 压缩为 256x256 JPEG Blob
    compressedBlob.value = await compressAvatar(file)
  } catch (err) {
    errorMsg.value = err.message || '图片处理失败'
    previewUrl.value = ''
    compressedBlob.value = null
  }
}

function resetPreview() {
  previewUrl.value = ''
  compressedBlob.value = null
  errorMsg.value = ''
}

async function confirmUpload() {
  if (!compressedBlob.value) return

  uploading.value = true
  errorMsg.value = ''

  try {
    emit('change', compressedBlob.value)
  } catch (err) {
    errorMsg.value = '上传失败，请重试'
    uploading.value = false
  }
}

// 父组件上传完成后调用此方法关闭弹窗
function uploadDone() {
  uploading.value = false
  resetPreview()
  emit('close')
}

// 父组件上传失败时调用此方法
function uploadFailed(message) {
  uploading.value = false
  errorMsg.value = message || '上传失败'
}

defineExpose({ uploadDone, uploadFailed })

function handleDelete() {
  emit('delete')
  emit('close')
}
</script>

<style scoped>
.avatar-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  font-size: 14px;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.9);
  backdrop-filter: blur(8px);
  color: #fff;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: all 0.2s ease;
}
.avatar-btn-primary:hover {
  background: rgba(59, 130, 246, 1);
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.4);
}

.avatar-btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  font-size: 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.15);
  transition: all 0.2s ease;
}
.avatar-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.22);
  transform: translateY(-1px);
}

.avatar-btn-text {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  transition: color 0.15s ease;
}
.avatar-btn-text:hover {
  color: rgba(255, 255, 255, 0.9);
}
</style>
