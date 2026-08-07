<!-- src/views/LoginView.vue — 登录页（深夜电台） -->
<template>
  <div class="min-h-screen flex items-center justify-center px-4"
       style="background: var(--color-night)">
    <!-- 背景氛围：一束琥珀光 -->
    <div class="fixed inset-0 pointer-events-none"
         style="background: radial-gradient(ellipse at 50% 120%, rgba(232,163,61,0.12), transparent 60%)"></div>

    <div class="relative w-full max-w-md space-y-6 rounded-xl p-8"
         style="background: var(--color-night-raise); border: 1px solid var(--color-night-line)">
      <!-- 品牌 -->
      <div class="text-center">
        <div class="flex items-center justify-center gap-2 mb-3">
          <span class="signal-dot signal-dot--amber"></span>
          <span class="text-2xl font-bold" style="color: var(--color-paper)">
            Traum<span style="color: var(--color-amber)">Chat</span>
          </span>
        </div>
        <p class="text-sm" style="color: var(--color-paper-soft)">深夜电台 · 让对话被点亮</p>
      </div>

      <n-alert v-if="error" type="error" :show-icon="false" closable @close="error = ''">
        {{ error }}
      </n-alert>

      <!-- 锁定倒计时 -->
      <n-alert v-if="lockRemain > 0" type="warning" :show-icon="false">
        登录失败次数过多，请在 {{ lockDisplay }} 后重试
      </n-alert>

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

      <div class="relative flex items-center gap-3 my-2">
        <div class="flex-1" style="border-top: 1px solid var(--color-night-line)"></div>
        <span class="text-xs" style="color: var(--color-paper-faint)">或</span>
        <div class="flex-1" style="border-top: 1px solid var(--color-night-line)"></div>
      </div>

      <div class="space-y-3">
        <n-button block size="large" ghost class="guest-btn" :loading="guestLoading" @click="loginAsGuest">
          <template #icon><AppIcon name="radio" :size="16" /></template>
          以游客身份进入
        </n-button>

        <div class="text-center space-y-2">
          <RouterLink to="/register" class="inline-block text-sm font-medium transition-opacity hover:opacity-80"
                      style="color: var(--color-amber)">
            没有账号？去注册 →
          </RouterLink>
          <p class="text-xs" style="color: var(--color-paper-faint)">游客无需注册，系统自动分配专属名称</p>
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
    // 解析后端 lockRemainSeconds（AuthServiceImpl 已返回精确剩余秒数）
    const lockSeconds = authStore.error?.match(/请在\s*([\d\s分秒]+)\s*后重试/)
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
  color: var(--color-amber) !important;
  border-color: var(--color-amber) !important;
}
.guest-btn:hover {
  background: var(--color-amber-ghost) !important;
}
</style>
