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

const iconComponent = computed(() => {
  const key = props.name.charAt(0).toUpperCase() + props.name.slice(1)
  return Icons[key] || Icons.Circle
})
</script>
