<!-- src/components/ui/AppIcon.vue — 统一 Lucide 图标封装 -->
<template>
  <component :is="iconComponent" :size="size" :class="className" :stroke-width="1.8" />
</template>

<script setup>
import { computed } from 'vue'
import * as Icons from '@lucide/vue'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 16 },
  className: { type: String, default: '' }
})

// kebab-case → PascalCase：file-text → FileText、log-out → LogOut
function toPascalCase(name) {
  return name
    .split('-')
    .map(part => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')
}

const iconComponent = computed(() => {
  const key = toPascalCase(props.name)
  // 优先 PascalCase（Lucide 标准导出），兜底原始 kebab 键（部分库内联别名），最后回退 Circle
  return Icons[key] || Icons[props.name] || Icons.Circle
})
</script>
