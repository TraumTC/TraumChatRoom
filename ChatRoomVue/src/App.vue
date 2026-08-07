<!-- src/App.vue — 根组件：Naive Provider + 全局实例桥 -->
<template>
  <NConfigProvider :theme="lightTheme" :theme-overrides="themeOverrides" :locale="zhHans" :date-locale="dateZhCN">
    <NMessageProvider>
      <NDialogProvider>
        <NNotificationProvider>
          <GlobalHooks />
          <RouterView />
        </NNotificationProvider>
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>

<script setup>
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider } from 'naive-ui'
import zhHans from 'naive-ui/es/locales/common/zhCN.mjs'
import dateZhCN from 'naive-ui/es/locales/date/zhCN.mjs'
import { lightTheme, themeOverrides } from './theme'
import GlobalHooks from '@/components/common/GlobalHooks.vue'
</script>
