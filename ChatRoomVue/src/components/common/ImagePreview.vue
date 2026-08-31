<!-- src/components/common/ImagePreview.vue — 图片预览模态框 -->
<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-[100] flex items-center justify-center img-preview-overlay"
         @click.self="$emit('close')">
      <!-- 遮罩 -->
      <div class="absolute inset-0 bg-black/80"></div>

      <!-- 关闭按钮（固定在视口右上角） -->
      <button @click="$emit('close')" title="关闭" aria-label="关闭"
              class="close-btn-overlay">
        <svg width="20" height="20" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>

      <!-- 内容 -->
      <div class="relative flex flex-col items-center">
        <!-- 图片 -->
        <img :src="resolveFileUrl(src)" :alt="fileName"
             class="max-w-[90vw] max-h-[82vh] object-contain rounded-xl shadow-2xl" />

        <!-- 文件名 + 下载按钮（图片下方） -->
        <div class="mt-4 flex items-center gap-4">
          <span class="text-sm" style="color: rgba(255,255,255,0.9)">{{ fileName }}</span>
          <a :href="resolveFileUrl(src) + '?name=' + encodeURIComponent(fileName || '')" download
             class="download-btn">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            下载
          </a>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { resolveFileUrl } from '@/utils/url'

defineProps({
  visible: { type: Boolean, default: false },
  src: { type: String, default: '' },
  fileName: { type: String, default: '' }
})

defineEmits(['close'])
</script>

<style scoped>
.download-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 13px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  color: #fff;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.15);
  transition: all 0.2s ease;
  text-decoration: none;
}
.download-btn:hover {
  background: rgba(255, 255, 255, 0.28);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}
</style>
