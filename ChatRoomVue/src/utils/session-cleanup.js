// src/utils/session-cleanup.js — 会话清理注册表
//
// 登出（或 401 被踢）时要拆掉的不只是 token：还有 WebSocket 连接和 chat store 里的会话状态。
// 但 stores/auth.js 不能直接 import stores/chat.js 或 composables/useWebSocket.js ——
// 那会形成 auth → chat → api → router → auth 的循环依赖。
//
// 所以反过来：由持有状态的一方在加载时注册自己的清理函数，
// authStore.logout() 与 api 的 401 拦截器只负责触发。
//
// 这里替代了原先只管 WebSocket 的 ws-cleanup.js —— 之前 401 路径只断了连接、
// 没清 chat 状态，属于同一个 bug 的第三个实例。

const handlers = new Set()

/**
 * 注册一个会话清理函数（持有会话状态的模块在加载/初始化时调用）
 * @returns 反注册函数
 */
export function registerSessionCleanup(fn) {
  handlers.add(fn)
  return () => handlers.delete(fn)
}

/**
 * 拆掉当前会话的全部客户端状态：先跑注册的内存清理，再兜底清 localStorage。
 *
 * 单个 handler 抛错不影响其余 handler —— 登出路径必须尽最大努力清干净，
 * 不能因为某一处报错就把残留数据留给下一个登录者。
 */
export function runSessionCleanup() {
  handlers.forEach(fn => {
    try {
      fn()
    } catch (e) {
      console.warn('会话清理失败', e)
    }
  })
}
