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
    borderRadius: '10px',
    borderRadiusSmall: '6px',
    borderRadiusLarge: '14px',
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', sans-serif"
  },
  // 按钮优化：更精致的圆角、字重、阴影
  Button: {
    borderRadiusTiny: '6px',
    borderRadiusSmall: '8px',
    borderRadiusMedium: '10px',
    borderRadiusLarge: '12px',
    fontWeight: '500',
    fontWeightStrong: '600',
    paddingTiny: '0 10px',
    paddingSmall: '0 14px',
    paddingMedium: '0 18px',
    paddingLarge: '0 22px',
    heightTiny: '28px',
    heightSmall: '32px',
    heightMedium: '36px',
    heightLarge: '42px'
  },
  // 输入框优化：聚焦边框色、圆角、高度
  Input: {
    borderRadius: '10px',
    heightTiny: '28px',
    heightSmall: '34px',
    heightMedium: '38px',
    heightLarge: '44px',
    colorFocus: tokens.card,
    borderFocus: `1px solid ${tokens.signal}`,
    borderHover: `1px solid #C8CCD2`,
    boxShadowFocus: `0 0 0 3px rgba(59,130,246,0.12)`,
    caretColor: tokens.signal,
    groupHeaderColor: tokens.hover,
    placeholderColor: tokens.inkFaint
  },
  // 弹窗/卡片阴影和圆角
  Card: {
    borderRadius: '14px',
    borderColor: tokens.border,
    boxShadow: '0 4px 24px rgba(0,0,0,0.06), 0 1px 3px rgba(0,0,0,0.04)'
  },
  Modal: {
    borderRadius: '14px',
    boxShadow: '0 12px 40px rgba(0,0,0,0.12), 0 2px 8px rgba(0,0,0,0.06)'
  },
  // 数据表格优化
  DataTable: {
    borderRadius: '10px',
    thColor: tokens.hover,
    thTextColor: tokens.inkSoft,
    thFontWeight: '600',
    tdColorHover: 'rgba(59,130,246,0.04)',
    borderColor: tokens.border
  },
  // 标签页优化
  Tabs: {
    tabFontWeightActive: '600',
    tabTextColorActive: tokens.signal,
    tabTextColorHover: tokens.signal,
    barColor: tokens.signal,
    tabBorderRadius: '8px 8px 0 0'
  },
  // 分页器优化
  Pagination: {
    itemBorderRadius: '8px',
    itemColorActive: tokens.signal,
    itemColorHover: tokens.hover,
    buttonColor: tokens.card
  },
  // 选择器优化
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: '10px'
      },
      InternalSelectMenu: {
        borderRadius: '10px'
      }
    }
  },
  // 标记/徽章优化
  Badge: {
    color: tokens.alarm,
    borderRadius: '10px',
    fontSize: '11px'
  },
  // 警告提示优化
  Alert: {
    borderRadius: '10px',
    padding: '12px 16px'
  },
  // 下拉菜单优化
  Dropdown: {
    borderRadius: '10px',
    optionColorHover: tokens.hover,
    optionColorActive: tokens.signalGhost,
    boxShadow: '0 8px 30px rgba(0,0,0,0.1), 0 2px 6px rgba(0,0,0,0.05)'
  },
  // 复选框优化
  Checkbox: {
    borderRadius: '4px',
    colorChecked: tokens.signal,
    borderChecked: `1px solid ${tokens.signal}`
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
