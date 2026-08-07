<!-- src/views/HomeView.vue — 首页：项目介绍 + 单个进入入口（亮色） -->
<template>
  <div class="min-h-screen flex flex-col" style="background: var(--color-bg)">
    <!-- 顶部品牌条 -->
    <header class="flex items-center justify-between px-6 h-14 shrink-0"
            style="background: var(--color-card); border-bottom: 1px solid var(--color-border)">
      <div class="text-base font-semibold" style="color: var(--color-ink)">
        Traum<span style="color: var(--color-signal)">Chat</span>
      </div>
      <div class="flex items-center gap-2">
        <n-button type="primary" size="small" @click="router.push('/login')">
          <template #icon><AppIcon name="log-in" :size="15" /></template>
          登录
        </n-button>
      </div>
    </header>

    <!-- 主区 -->
    <div class="flex-1 flex items-center justify-center px-4 py-12">
      <div class="max-w-3xl w-full">
        <!-- 品牌区 -->
        <div class="text-center mb-10">
          <h1 class="text-3xl font-bold" style="color: var(--color-ink)">
            Traum<span style="color: var(--color-signal)">Chat</span>
          </h1>
          <p class="text-base mt-3" style="color: var(--color-ink-soft)">一个在线聊天室：群聊 · 私聊 · AI 助手 · 好友</p>
        </div>

        <!-- 功能亮点 -->
        <div class="grid gap-3 mb-10" style="display:grid; grid-template-columns: repeat(auto-fit,minmax(180px,1fr))">
          <div v-for="f in features" :key="f.title" class="rounded-xl p-4"
               style="background: var(--color-card); border: 1px solid var(--color-border)">
            <AppIcon :name="f.icon" :size="20" class="mb-2" style="color: var(--color-signal)" />
            <div class="text-sm font-semibold mb-1" style="color: var(--color-ink)">{{ f.title }}</div>
            <div class="text-xs" style="color: var(--color-ink-soft)">{{ f.desc }}</div>
          </div>
        </div>

        <!-- 单个入口 -->
        <div class="max-w-sm mx-auto">
          <n-button type="primary" size="large" block @click="router.push('/login')">
            <template #icon><AppIcon name="log-in" :size="16" /></template>
            登录
          </n-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()

const features = [
  { icon: 'messages-square', title: '群聊', desc: '实时文本、图片、文件消息' },
  { icon: 'bot', title: 'AI 助手', desc: '@小爱 随时提问，智能回复' },
  { icon: 'users', title: '好友', desc: '搜索、申请、私聊，双向好友' }
]
</script>
