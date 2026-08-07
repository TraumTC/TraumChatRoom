<!-- src/views/LoginView.vue — 登录页（亮色） -->
<template>
  <div class="min-h-screen flex items-center justify-center px-4 py-12" style="background: var(--color-bg)">
    <div class="w-full max-w-md space-y-8 rounded-xl p-8 shadow-md"
         style="background: var(--color-card); border: 1px solid var(--color-border)">
      <!-- 标题 -->
      <div class="text-center">
        <div class="flex items-center justify-center gap-2 mb-2">
          <span class="signal-dot signal-dot--blue"></span>
          <h2 class="text-2xl font-bold" style="color: var(--color-ink)">
            Traum<span style="color: var(--color-signal)">Chat</span>
          </h2>
        </div>
        <p class="text-sm" style="color: var(--color-ink-soft)">欢迎回来，请登录您的账户</p>
      </div>

      <n-alert v-if="error" type="error" :show-icon="false" closable @close="error = ''">
        {{ error }}
      </n-alert>

      <!-- 锁定倒计时 -->
      <n-alert v-if="lockRemain > 0" type="warning" :show-icon="false">
        登录失败次数过多，请在 {{ lockDisplay }} 后重试
      </n-alert>

      <!-- 登录表单 -->
      <n-form @submit.prevent="handleLogin">
        <div class="space-y-4">
          <n-input v-model:value="username" type="text" placeholder="用户名" size="large"
                   autocomplete="username" @keyup.enter="handleLogin">
            <template #prefix><AppIcon name="user" :size="16" /></template>
          </n-input>
          <n-input v-model:value="password" type="password" placeholder="密码" size="large"
                   autocomplete="current-password" show-password-on="click" @keyup.enter="handleLogin">
            <template #prefix><AppIcon name="lock" :size="16" /></template>
          </n-input>
        </div>

        <n-button type="primary" size="large" block class="mt-5" :loading="authStore.loading" attr-type="submit">
          登录
        </n-button>
      </n-form>

      <!-- 分割线 -->
      <div class="relative flex items-center gap-3">
        <div class="flex-1" style="border-top: 1px solid var(--color-border)"></div>
        <span class="text-xs" style="color: var(--color-ink-faint)">或者</span>
        <div class="flex-1" style="border-top: 1px solid var(--color-border)"></div>
      </div>

      <!-- 游客入口 + 注册链接 -->
      <div class="space-y-3">
        <n-button block size="large" class="guest-btn" :loading="guestLoading" @click="loginAsGuest">
          <template #icon><AppIcon name="radio" :size="16" /></template>
          以游客身份进入
        </n-button>

        <div class="text-center space-y-1">
          <RouterLink to="/register" class="inline-block text-sm font-medium transition-opacity hover:opacity-80"
                      style="color: var(--color-signal)">
            没有账号？去注册 →
          </RouterLink>
          <p class="text-xs" style="color: var(--color-ink-faint)">游客无需注册，系统会自动为您分配一个专属名称</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()
const authStore = useAuthStore()
const username = ref('')
const password = ref('')
const error = ref('')
const lockRemain = ref(0)
const guestLoading = ref(false)
let countdownTimer = null

const lockDisplay = computed(() => {
  const m = Math.floor(lockRemain.value / 60)
  const s = lockRemain.value % 60
  return m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`
})

function startCountdown(seconds) {
  lockRemain.value = seconds
  countdownTimer = setInterval(() => {
    lockRemain.value--
    if (lockRemain.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function handleLogin() {
  error.value = ''
  const success = await authStore.login(username.value, password.value)
  if (success) {
    router.push('/chat')
  } else {
    const raw = authStore.error
    const secondsMatch = raw?.match(/(\d+)\s*分\s*(\d+)\s*秒/)
    if (secondsMatch) {
      startCountdown(parseInt(secondsMatch[1]) * 60 + parseInt(secondsMatch[2]))
    } else if (raw?.includes('锁定') || raw?.includes('失败次数')) {
      const secMatch = raw?.match(/(\d+)\s*秒/)
      startCountdown(secMatch ? parseInt(secMatch[1]) : 60)
    }
    error.value = authStore.error
  }
}

async function loginAsGuest() {
  guestLoading.value = true
  error.value = ''
  try {
    const success = await authStore.loginAsGuest()
    if (success) router.push('/chat')
    else error.value = authStore.error || '游客进入失败'
  } finally {
    guestLoading.value = false
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})
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
