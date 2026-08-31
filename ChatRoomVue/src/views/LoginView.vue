<!-- src/views/LoginView.vue — 登录页（含前端表单校验） -->
<template>
  <div class="min-h-screen flex items-center justify-center px-4 py-12" style="background: var(--color-bg)">
    <div class="w-full max-w-md space-y-8 rounded-xl p-8 auth-card"
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

      <!-- 登录表单（含前端校验） -->
      <n-form ref="formRef" :model="loginForm" :rules="rules" @submit.prevent="handleLogin"
              show-label require-mark-placement="right-hanging" :label-width="64">
        <n-form-item path="username" label="用户名">
          <n-input v-model:value="loginForm.username" type="text" placeholder="请输入用户名"
                   :input-props="{ autocomplete: 'username' }" @keyup.enter="handleLogin" />
        </n-form-item>
        <n-form-item path="password" label="密码">
          <n-input v-model:value="loginForm.password" type="password" placeholder="请输入密码"
                   :input-props="{ autocomplete: 'current-password' }" show-password-on="click" @keyup.enter="handleLogin" />
        </n-form-item>

        <n-button type="primary" size="large" block class="mt-4" :loading="authStore.loading" attr-type="submit">
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
import { NAlert, NButton, NForm, NFormItem, NInput } from 'naive-ui'
import { ref, reactive, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()
const authStore = useAuthStore()

// 表单数据
const loginForm = reactive({
  username: '',
  password: ''
})

// 表单引用 + 校验规则
const formRef = ref(null)
const rules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: ['blur', 'change'] },
    { min: 2, max: 20, message: '用户名长度需 2-20 字符', trigger: ['blur', 'change'] }
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: ['blur', 'change'] }
  ]
}

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

  // 前端校验：只取首个错误
  try {
    await formRef.value?.validate()
  } catch (e) {
    // Naive UI validate 失败时抛出 { errors: [{ message }] } 或数组
    const errs = Array.isArray(e) ? e : e?.errors
    if (errs && errs.length) {
      error.value = errs[0].message || errs[0]?.[0]?.message || '表单校验失败'
    }
    return
  }

  const success = await authStore.login(loginForm.username, loginForm.password)
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
