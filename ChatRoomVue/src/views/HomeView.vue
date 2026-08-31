<!-- src/views/HomeView.vue — 首页：项目介绍 + 进入入口 + 友情链接（亮色） -->
<template>
  <div class="min-h-screen flex flex-col relative overflow-hidden" style="background: var(--color-bg)">
    <!-- 背景装饰光晕 -->
    <div class="hero-glow" aria-hidden="true"></div>

    <!-- 顶部品牌条 -->
    <header class="relative z-10 flex items-center justify-between px-6 h-14 shrink-0 header-glass"
            style="border-bottom: 1px solid var(--color-border)">
      <div class="text-base font-semibold" style="color: var(--color-ink)">
        Traum<span style="color: var(--color-signal)">Space</span>
      </div>
      <div class="flex items-center gap-2">
        <n-button type="primary" size="small" @click="goLogin">
          <template #icon><AppIcon name="log-in" :size="15" /></template>
          {{ authStore.isAuthenticated ? '进入' : '登录' }}
        </n-button>
      </div>
    </header>

    <!-- 主区 -->
    <main class="relative z-10 flex-1 flex items-center justify-center px-4 py-12">
      <div class="max-w-3xl w-full">
        <!-- 品牌区 -->
        <div class="text-center mb-12">
          <div class="inline-flex items-center gap-2 capsule-tag mb-6">
            <span class="signal-dot signal-dot--blue"></span>
            实时在线 · 群聊 · 私聊 · AI
          </div>
          <h1 class="text-4xl sm:text-5xl font-bold tracking-tight" style="color: var(--color-ink)">
            Traum<span style="color: var(--color-signal)">Space</span>
          </h1>
          <p class="text-base sm:text-lg mt-4 max-w-xl mx-auto leading-relaxed" style="color: var(--color-ink-soft)">
            一个轻量、实时的在线聊天室 —— 群聊、私聊、AI 助手与好友，一站畅聊。
          </p>
          <p class="mt-3 text-xs" style="color: var(--color-ink-faint)">特此声明，本网站内容仅供个人学习交流使用</p>
        </div>

        <!-- 功能亮点 -->
        <div class="grid gap-4 mb-12"
             style="display:grid; grid-template-columns: repeat(auto-fit,minmax(180px,1fr))">
          <div v-for="f in features" :key="f.title" class="feature-card rounded-xl p-5"
               style="background: var(--color-card)">
            <div class="feature-icon mb-3">
              <AppIcon :name="f.icon" :size="20" style="color: var(--color-signal)" />
            </div>
            <div class="text-sm font-semibold mb-1" style="color: var(--color-ink)">{{ f.title }}</div>
            <div class="text-xs leading-relaxed" style="color: var(--color-ink-soft)">{{ f.desc }}</div>
          </div>
        </div>

        <!-- 入口按钮 -->
        <div class="max-w-sm mx-auto flex flex-col gap-3">
          <n-button type="primary" size="large" block @click="goLogin">
            <template #icon><AppIcon name="log-in" :size="16" /></template>
            {{ authStore.isAuthenticated ? '进入聊天室' : '登录' }}
          </n-button>
          <n-button v-if="!authStore.isAuthenticated" size="large" block class="guest-btn"
                    :loading="guestLoading" @click="enterAsGuest">
            <template #icon><AppIcon name="radio" :size="16" /></template>
            以游客身份体验
          </n-button>
        </div>
      </div>
    </main>

    <!-- 页脚：友情链接 + 版权 -->
    <footer class="relative z-10 shrink-0 px-6 py-5 text-center"
            style="border-top: 1px solid var(--color-border)">
      <div class="flex items-center justify-center gap-2 text-xs flex-wrap" style="color: var(--color-ink-faint)">
        <span>友情链接</span>
        <span style="color: var(--color-border)">·</span>
        <a href="https://lilicould.cn" target="_blank" rel="noopener noreferrer"
           class="link-underline font-medium" style="color: var(--color-signal)">立里博客</a>
      </div>
      <p class="mt-2 text-xs" style="color: var(--color-ink-faint)">© {{ year }} TraumSpace · 仅供学习交流</p>
    </footer>
  </div>
</template>

<script setup>
import { NButton } from 'naive-ui'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const guestLoading = ref(false)
const year = new Date().getFullYear()

// 已登录直接进聊天室，未登录进登录页
function goLogin() {
  if (authStore.isAuthenticated) {
    router.push('/chat')
  } else {
    router.push('/login')
  }
}

// 游客体验：复用 auth store 的游客登录逻辑
async function enterAsGuest() {
  guestLoading.value = true
  try {
    const ok = await authStore.loginAsGuest()
    if (ok) router.push('/chat')
    else window.$message?.error(authStore.error || '游客进入失败，请稍后再试')
  } finally {
    guestLoading.value = false
  }
}

const features = [
  { icon: 'messages-square', title: '群聊', desc: '实时文本、图片、文件消息，多人同频畅聊' },
  { icon: 'message-square', title: '私聊', desc: '与好友一对一私密对话，消息即时送达' },
  { icon: 'bot', title: 'AI 助手', desc: '@小汤 随时提问，智能友善的即时回复' },
  { icon: 'users', title: '好友', desc: '搜索、申请、双向好友，社交更简单' }
]
</script>

<style scoped>
/* 背景装饰光晕：品牌蓝径向渐变，营造层次感 */
.hero-glow {
  position: absolute;
  top: -12%;
  left: 50%;
  transform: translateX(-50%);
  width: 720px;
  height: 720px;
  max-width: 130vw;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.10) 0%, rgba(59, 130, 246, 0) 62%);
  pointer-events: none;
  z-index: 0;
}

/* 功能卡图标底衬 */
.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--color-signal-ghost);
}

/* 游客入口按钮（与登录页保持一致的琥珀虚线风格） */
.guest-btn {
  color: #b45309 !important;
  border: 2px dashed #f59e0b !important;
  background: #fffbeb !important;
  transition: all 0.2s ease !important;
}
.guest-btn:hover {
  background: #fef3c7 !important;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.15);
}
.guest-btn:active {
  transform: translateY(0);
}
</style>
