// src/theme.js — Naive UI 主题（「深夜电台 · 琥珀信号」）
// 深蓝夜空 + 琥珀信号灯：对话是暗夜里被点亮的信号。
import { darkTheme } from 'naive-ui'

// 设计 Token（与 styles/main.css 的 CSS 变量保持一致）
export const tokens = {
  // 夜空底色
  night: '#0E1621',        // 页面底
  nightRaise: '#151E2B',   // 侧栏/输入区底
  nightHover: '#1D2735',   // hover / 他人气泡
  nightLine: '#26324A',    // 描边/分割线
  // 琥珀信号
  amber: '#E8A33D',        // 强调：自己消息/主按钮/在线点/AI
  amberDeep: '#C98A2E',    // hover/pressed
  amberGhost: 'rgba(232,163,61,0.14)', // 选中行底/focus ring
  // 纸面文字（暖白）
  paper: '#F5F1E8',        // 主文字
  paperSoft: '#9AA3B2',    // 次文字
  paperFaint: '#6B7484',   // 占位/禁用
  // 语义色
  live: '#4ADE80',         // 在场绿
  alarm: '#F87171',        // 警示红
  warn: '#F59E0B'
}

// Naive UI 主题覆盖
export const themeOverrides = {
  common: {
    primaryColor: tokens.amber,
    primaryColorHover: tokens.amberDeep,
    primaryColorPressed: tokens.amberDeep,
    primaryColorSuppl: tokens.amberGhost,
    bodyColor: tokens.night,
    cardColor: tokens.nightRaise,
    modalColor: tokens.nightRaise,
    popoverColor: tokens.nightRaise,
    tableColor: tokens.nightRaise,
    inputColor: tokens.nightRaise,
    inputColorDisabled: tokens.nightHover,
    textColor1: tokens.paper,
    textColor2: tokens.paperSoft,
    textColor3: tokens.paperFaint,
    borderColor: tokens.nightLine,
    dividerColor: tokens.nightLine,
    hoverColor: tokens.nightHover,
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
    theme: darkTheme,
    themeOverrides,
    // 与 main.css 的 CSS 变量同名，供自绘组件使用
    'data-theme': 'night-radio'
  }
}
