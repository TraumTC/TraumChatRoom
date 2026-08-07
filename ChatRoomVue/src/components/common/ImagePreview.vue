<!-- src/components/common/ImagePreview.vue — 图片预览模态框 -->
<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-[100] flex items-center justify-center"
         @click.self="$emit('close')">
      <!-- 遮罩 -->
      <div class="absolute inset-0 bg-black/80"></div>

      <!-- 内容 -->
      <div class="relative flex flex-col items-center">
        <!-- 关闭按钮（图片外右上角，纯线条叉，无背景） -->
        <button @click="$emit('close')" title="关闭" aria-label="关闭"
                class="absolute -top-12 right-0 flex items-center justify-center"
                style="color: rgba(255,255,255,0.85); cursor: pointer">
          <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <!-- 图片 -->
        <img :src="src" :alt="fileName"
             class="max-w-[90vw] max-h-[82vh] object-contain rounded-lg shadow-2xl" />

        <!-- 文件名 + 下载按钮（图片下方） -->
        <div class="mt-4 flex items-center gap-4">
          <span class="text-sm" style="color: rgba(255,255,255,0.9)">{{ fileName }}</span>
          <a :href="src + '?name=' + encodeURIComponent(fileName || '')" download
             class="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg transition-colors"
             style="background: rgba(255,255,255,0.15); color: #fff; cursor: pointer"
             @mouseenter="$event.target.style.background='rgba(255,255,255,0.25)'"
             @mouseleave="$event.target.style.background='rgba(255,255,255,0.15)'">
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
defineProps({
  visible: { type: Boolean, default: false },
  src: { type: String, default: '' },
  fileName: { type: String, default: '' }
})

defineEmits(['close'])
</script>
