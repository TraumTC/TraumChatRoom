// src/theme.js — Naive UI 主题（亮色 · 白底蓝强调）
// 改造前原始 Token：暖白纸面 + 墨色文字 + 品牌蓝强调。
import { lightTheme } from 'naive-ui'

// 设计 Token（与 styles/main.css 的 CSS 变量保持一致）
export const tokens = {
  bg: '#F7F7F5',            // 页面底（暖白纸面）
  card: '#FFFFFF',          // 卡片/气泡/输入区
  hover: '#F3F4F6',         // 列表 hover / 他人气泡
  border: '#E5E7EB',        // 描边/分割线
  // 墨色文字
  ink: '#1F2328',           // 主文字
  inkSoft: '#6B7280',       // 次文字
  inkFaint: '#9CA3AF',      // 占位/禁用
  // 品牌蓝强调
  signal: '#3B82F6',        // 自己气泡/主按钮/AI/链接/激活
  signalDeep: '#2563EB',    // hover/pressed
  signalGhost: '#EFF6FF',   // AI 气泡底/选中行底/focus ring
  // 语义色
  live: '#10B981',          // 在线绿
  alarm: '#EF4444',         // 警示红
  warn: '#F59E0B'           // 警示黄
}

// Naive UI 主题覆盖
export const themeOverrides = {
  common: {
    primaryColor: tokens.signal,
    primaryColorHover: tokens.signalDeep,
    primaryColorPressed: tokens.signalDeep,
    primaryColorSuppl: tokens.signalGhost,
    bodyColor: tokens.bg,
    cardColor: tokens.card,
    modalColor: tokens.card,
    popoverColor: tokens.card,
    tableColor: tokens.card,
    inputColor: tokens.card,
    inputColorDisabled: tokens.hover,
    textColor1: tokens.ink,
    textColor2: tokens.inkSoft,
    textColor3: tokens.inkFaint,
    borderColor: tokens.border,
    dividerColor: tokens.border,
    hoverColor: tokens.hover,
    successColor: tokens.live,
    warningColor: tokens.warn,
    errorColor: tokens.alarm,
    borderRadius: '8px',
    borderRadiusSmall: '4px',
    borderRadiusLarge: '12px',
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', sans-serif"
  }
}

export function createAppTheme() {
  return {
    theme: lightTheme,
    themeOverrides,
    'data-theme': 'light-blue'
  }
}

export { lightTheme }
