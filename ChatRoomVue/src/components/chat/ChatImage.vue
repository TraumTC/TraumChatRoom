<!-- src/components/chat/ChatImage.vue — 聊天图片（固定占位框，消除虚拟列表滚动抖动） -->
<!--
  占位框的尺寸在图片下载完成之前就已确定，行高不再"先塌后撑"：
  - 尺寸已知（来自 imageSize 缓存）→ 直接按真实宽高比撑开，全程零抖动
  - 尺寸未知（第一次见到这张图）→ 先用 4:3 骨架占位，load 后取真实比例并记入缓存，
    之后每次回收重建、乃至刷新页面，都能一次性精确占位
  宽度规则沿用原来的 max-w-[60vw] sm:max-w-[240px]，并且不放大小图（取原始宽度与上限的较小值）。
-->
<template>
  <div class="chat-image" :class="{ 'is-placeholder': !dims }" :style="boxStyle">
    <!-- 骨架：加载完成前铺满占位框 -->
    <div v-if="!loaded && !failed" class="chat-image-skeleton"></div>

    <!-- 加载失败兜底：保持占位框尺寸，不引起行高变化 -->
    <div v-else-if="failed" class="chat-image-failed">
      <AppIcon name="image-off" :size="18" />
      <span>加载失败</span>
    </div>

    <img v-if="!failed" :src="resolveFileUrl(path)" :alt="fileName"
         loading="lazy" decoding="async"
         class="chat-image-img" :class="{ 'is-loaded': loaded }"
         @load="onLoad" @error="failed = true" @click="$emit('open')" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { resolveFileUrl } from '@/utils/url'
import { getImageSize, rememberImageSize } from '@/utils/imageSize'

const props = defineProps({
  path: { type: String, default: '' },      // 消息的 filePath，同时作为尺寸缓存的 key
  fileName: { type: String, default: '' }
})

defineEmits(['open'])

// 挂载时就尝试拿缓存尺寸：命中则首帧即为最终尺寸
const dims = ref(getImageSize(props.path))
const loaded = ref(false)
const failed = ref(false)

// 用 CSS 变量把尺寸交给样式表，宽度上限的响应式断点仍由媒体查询负责
const boxStyle = computed(() => ({
  '--img-w': dims.value ? `${dims.value.w}px` : '100vw',
  '--img-ar': dims.value ? `${dims.value.w} / ${dims.value.h}` : '4 / 3'
}))

function onLoad(e) {
  const img = e.target
  const w = img.naturalWidth
  const h = img.naturalHeight
  if (w > 0 && h > 0) {
    rememberImageSize(props.path, w, h)
    // 首次见到这张图：此刻才知道真实比例，落定一次。之后的重建都从缓存直接命中。
    if (!dims.value) dims.value = { w, h }
  }
  loaded.value = true
}
</script>

<style scoped>
.chat-image {
  position: relative;
  overflow: hidden;
  border-radius: 0.5rem;                  /* = rounded-lg */
  /* 不放大小图：取原始宽度与视口上限中的较小值 */
  width: min(var(--img-w), 60vw);
  aspect-ratio: var(--img-ar);
  background: var(--color-card);
  border: 1px solid var(--color-border);
}
@media (min-width: 640px) {               /* Tailwind sm 断点，对应原 sm:max-w-[240px] */
  .chat-image {
    width: min(var(--img-w), 240px);
  }
}

.chat-image-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
  opacity: 0;
  transition: opacity 200ms ease;
}
.chat-image-img.is-loaded {
  opacity: 1;
}
.chat-image-img.is-loaded:hover {
  opacity: 0.9;
}

.chat-image-skeleton,
.chat-image-failed {
  position: absolute;
  inset: 0;
}

.chat-image-skeleton {
  background: var(--color-hover);
  animation: chat-image-pulse 1.5s ease-in-out infinite;
}
@keyframes chat-image-pulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.5; }
}

.chat-image-failed {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-ink-faint);
  background: var(--color-ghost);
}
</style>
