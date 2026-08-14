// src/utils/ws-cleanup.js — 轻量 WS 清理回调注册（避免 api 层与 composable 循环依赖）
let cleanupHandler = null

// 注册清理处理器（useWebSocket.js 在模块加载时注册）
export function registerWsCleanup(fn) {
  cleanupHandler = fn
}

// 供 api 拦截器等非组件上下文调用：断开残留的 WebSocket 连接
export function wsCleanup() {
  if (cleanupHandler) cleanupHandler()
}
