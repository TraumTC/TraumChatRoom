<!-- src/components/common/GlobalHooks.vue — 在 Provider 上下文内挂载全局消息/弹窗实例 -->
<template>
  <span class="hidden"></span>
</template>

<script setup>
import { useMessage, useDialog, useNotification } from 'naive-ui'

// 把全局实例挂到 window，供非组件上下文（axios 拦截器、WebSocket、工具函数）调用
const message = useMessage()
const dialog = useDialog()
const notification = useNotification()

window.$message = message
window.$dialog = dialog
window.$notification = notification
</script>
