<!-- src/views/LoginView.vue — 登录页（对齐参考站 traums.cn/login） -->
<template>
  <div class="min-h-screen flex items-center justify-center px-4 py-12" style="background: var(--color-bg)">
    <div class="w-full max-w-md space-y-8 rounded-lg p-8 shadow-md"
         style="background: var(--color-card)">
      <!-- 标题 -->
      <div>
        <h2 class="text-center text-3xl font-extrabold" style="color: var(--color-ink)">TraumSpace</h2>
        <p class="mt-2 text-center text-sm" style="color: var(--color-ink-soft)">欢迎回来，请登录您的账户</p>
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
          <div>
            <label for="username" class="block text-sm font-medium mb-1" style="color: var(--color-ink)">用户名</label>
            <n-input id="username" v-model:value="username" type="text" placeholder="请输入用户名"
                     autocomplete="username" @keyup.enter="handleLogin" />
          </div>
          <div>
            <label for="password" class="block text-sm font-medium mb-1" style="color: var(--color-ink)">密码</label>
            <n-input id="password" v-model:value="password" type="password" placeholder="请输入密码"
                     autocomplete="current-password" show-password-on="click" @keyup.enter="handleLogin" />
          </div>
        </div>

        <n-button type="primary" size="large" block class="mt-6" :loading="authStore.loading" attr-type="submit">
          登录
        </n-button>
      </n-form>

      <!-- 分割线 -->
      <div class="relative">
        <div class="absolute inset-0 flex items-center">
          <div class="w-full" style="border-top: 1px solid var(--color-border)"></div>
        </div>
        <div class="relative flex justify-center text-sm">
          <span class="px-2" style="background: var(--color-card); color: var(--color-ink-faint)">或者</span>
        </div>
      </div>

      <!-- 游客入口 + 注册链接 -->
      <div class="space-y-3">
        <n-button block size="large" class="guest-btn" :loading="guestLoading" @click="loginAsGuest">
          <template #icon><AppIcon name="radio" :size="16" /></template>
          以游客身份进入
        </n-button>

        <div class="text-center space-y-2">
          <RouterLink to="/register" class="block text-sm font-medium transition-opacity hover:opacity-80"
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
  color: #b45309 !important;
  border: 2px dashed #f59e0b !important;
  background: #fffbeb !important;
}
.guest-btn:hover {
  background: #fef3c7 !important;
}
</style>
