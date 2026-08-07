<!-- src/views/HomeView.vue — 首页：项目介绍 + 入口（亮色） -->
<template>
  <div class="min-h-screen flex flex-col" style="background: var(--color-bg)">
    <!-- 顶部品牌条 -->
    <header class="flex items-center justify-between px-6 h-14 shrink-0"
            style="background: var(--color-card); border-bottom: 1px solid var(--color-border)">
      <div class="flex items-center gap-2 text-base font-semibold" style="color: var(--color-ink)">
        <span class="signal-dot signal-dot--blue"></span>
        <span>Traum<span style="color: var(--color-signal)">Chat</span></span>
      </div>
      <div class="flex items-center gap-2">
        <n-button text style="color: var(--color-ink-soft)" @click="router.push('/login')">登录</n-button>
        <n-button type="primary" size="small" @click="router.push('/register')">注册</n-button>
      </div>
    </header>

    <!-- 主区 -->
    <div class="flex-1 flex items-center justify-center px-4 py-12">
      <div class="max-w-3xl w-full">
        <!-- 品牌区 -->
        <div class="text-center mb-10">
          <div class="flex items-center justify-center gap-3 mb-4">
            <span class="signal-dot signal-dot--blue"></span>
            <h1 class="text-3xl font-bold" style="color: var(--color-ink)">
              Traum<span style="color: var(--color-signal)">Chat</span>
            </h1>
          </div>
          <p class="text-base" style="color: var(--color-ink-soft)">一个在线聊天室：群聊 · 私聊 · AI 助手 · 好友</p>
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

        <!-- 入口 -->
        <div class="space-y-3 max-w-sm mx-auto">
          <n-button type="primary" size="large" block @click="router.push('/login')">
            登录
          </n-button>
          <n-button size="large" block @click="router.push('/register')">
            注册新账号
          </n-button>
          <n-button size="large" block class="guest-btn" @click="loginAsGuest">
            <template #icon><AppIcon name="radio" :size="16" /></template>
            以游客身份进入
          </n-button>
          <p class="text-center text-xs pt-1" style="color: var(--color-ink-faint)">
            游客无需注册，系统自动分配专属名称 · 2 小时内有效
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()
const authStore = useAuthStore()

const features = [
  { icon: 'messages-square', title: '群聊', desc: '实时文本、图片、文件消息' },
  { icon: 'bot', title: 'AI 助手', desc: '@小爱 随时提问，智能回复' },
  { icon: 'users', title: '好友', desc: '搜索、申请、私聊，双向好友' }
]

async function loginAsGuest() {
  const success = await authStore.loginAsGuest()
  if (success) router.push('/chat')
  else window.$message?.error('游客进入失败，请重试')
}
</script>

<style scoped>
.guest-btn {
  color: var(--color-signal) !important;
  border-color: var(--color-signal) !important;
}
.guest-btn:hover {
  background: var(--color-signal-ghost) !important;
}
</style>
