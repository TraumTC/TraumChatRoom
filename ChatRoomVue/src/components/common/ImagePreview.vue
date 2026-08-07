<!-- src/components/common/ImagePreview.vue — 图片预览模态框 -->
<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-[100] flex items-center justify-center"
         @click.self="$emit('close')">
      <!-- 遮罩 -->
      <div class="absolute inset-0 bg-black/80"></div>

      <!-- 内容 -->
      <div class="relative max-w-[90vw] max-h-[90vh] flex flex-col items-center">
        <!-- 关闭按钮（右上角） -->
        <button @click="$emit('close')"
                class="absolute -top-10 right-0 w-8 h-8 flex items-center justify-center text-white/80 hover:text-white transition-colors"
                title="关闭">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <!-- 图片 -->
        <img :src="src" :alt="fileName"
             class="max-w-[90vw] max-h-[85vh] object-contain rounded-lg shadow-2xl" />

        <!-- 文件名 + 下载按钮 -->
        <div v-if="fileName" class="mt-3 flex items-center gap-3">
          <span class="text-sm text-white/70">{{ fileName }}</span>
          <a :href="src + '?name=' + encodeURIComponent(fileName)"
             download
             class="flex items-center gap-1 px-3 py-1 bg-white/20 hover:bg-white/30 text-white text-xs rounded-lg transition-colors backdrop-blur-sm">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
