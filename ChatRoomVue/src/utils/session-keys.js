// src/utils/session-keys.js — 会话级 localStorage key 的唯一定义处
//
// 这些 key 都承载「上一个登录者是谁、和谁聊过」的信息，登出时必须全部清掉：
// 同一浏览器换人登录时若有残留，新用户会看到上一个人的私聊会话列表（联系人 username + 昵称）。
//
// 单独抽成无依赖的叶子模块，是为了让 chat store（写入方）与 session-cleanup（清理方）
// 共用同一份 key 名，避免两处各写一遍字符串而漏掉某一个。
export const SESSION_KEYS = {
  privateTabs: 'chat:private_tabs:v2',
  currentChat: 'chat:current_chat:v2'
}

/**
 * 旧版本遗留 key，一律丢弃：
 * - chat:private_tabs / chat:current_chat —— 结构已变更（v2 重建）
 * - myId / myName —— 曾与 authStore.user 重复存储同一份身份信息，构成第二个数据源。
 *   两者不一致时消息归属判断会错（详见 chat store 的 isMyMessage 注释），
 *   现已统一为只认 authStore.user，这两个 key 不再写入，仅需清掉存量残留。
 */
export const LEGACY_SESSION_KEYS = [
  'chat:private_tabs',
  'chat:current_chat',
  'myId',
  'myName'
]

/**
 * 清空全部会话级 localStorage（含旧版遗留 key）。
 *
 * 不依赖任何 store 实例：用户在 /profile 刷新后直接改密码登出时，
 * chat store 可能在本次页面加载中从未被实例化，靠 store 内部清理会漏掉。
 */
export function clearSessionKeys() {
  try {
    Object.values(SESSION_KEYS).forEach(k => localStorage.removeItem(k))
    LEGACY_SESSION_KEYS.forEach(k => localStorage.removeItem(k))
  } catch (e) { /* 隐私模式下 localStorage 可能不可用，忽略 */ }
}
