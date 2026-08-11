<!-- src/views/RegisterView.vue — 注册页（亮色） -->
<template>
  <div class="min-h-screen flex items-center justify-center px-4"
       style="background: var(--color-bg)">
    <div class="fixed inset-0 pointer-events-none"
         style="background: radial-gradient(ellipse at 50% 120%, rgba(59,130,246,0.08), transparent 60%)"></div>

    <div class="relative w-full max-w-md space-y-6 rounded-xl p-8 auth-card"
         style="background: var(--color-card)">
      <div class="text-center">
        <h2 class="text-xl font-semibold" style="color: var(--color-ink)">创建账户</h2>
        <p class="mt-1 text-sm" style="color: var(--color-ink-soft)">加入我们，开始你的对话</p>
      </div>

      <n-alert v-if="error" type="error" :show-icon="false" closable @close="error = ''">
        {{ error }}
      </n-alert>

      <n-form @submit.prevent="handleRegister">
        <div class="space-y-4">
          <div>
            <n-input v-model:value="username" type="text" placeholder="用户名（字母数字下划线，2-20位）"
                     maxlength="20" autofocus :status="usernameError ? 'error' : undefined" @keyup.enter="handleRegister">
              <template #prefix><AppIcon name="user" :size="16" /></template>
            </n-input>
            <div class="flex justify-between items-center mt-1">
              <div v-if="usernameError" class="text-xs" style="color: var(--color-alarm)">{{ usernameError }}</div>
              <div class="ml-auto text-xs" :style="countStyle(username, 20)">{{ username.length }} / 20</div>
            </div>
          </div>

          <div>
            <n-input v-model:value="name" type="text" placeholder="昵称（1-20位）"
                     maxlength="20" @keyup.enter="handleRegister">
              <template #prefix><AppIcon name="badge-check" :size="16" /></template>
            </n-input>
            <div class="flex justify-end mt-1">
              <div class="text-xs" :style="countStyle(name, 20)">{{ name.length }} / 20</div>
            </div>
          </div>

          <div>
            <n-input v-model:value="password" type="password" placeholder="密码（6-20位，含字母和数字）"
                     maxlength="20" show-password-on="click" :status="passwordError ? 'error' : undefined" @keyup.enter="handleRegister">
              <template #prefix><AppIcon name="lock" :size="16" /></template>
            </n-input>
            <div class="flex justify-between items-center mt-1">
              <div v-if="passwordError" class="text-xs" style="color: var(--color-alarm)">{{ passwordError }}</div>
              <div class="ml-auto text-xs" :style="countStyle(password, 20)">{{ password.length }} / 20</div>
            </div>
          </div>
        </div>

        <n-button type="primary" size="large" block class="mt-5" :loading="authStore.loading" attr-type="submit">
          注册
        </n-button>
      </n-form>

      <div class="text-center">
        <RouterLink to="/login" class="text-sm font-medium transition-opacity hover:opacity-80" style="color: var(--color-signal)">
          已有账号？立即登录 →
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import AppIcon from '@/components/ui/AppIcon.vue'

const router = useRouter()
const authStore = useAuthStore()
const username = ref('')
const name = ref('')
const password = ref('')
const error = ref('')
const usernameError = ref('')
const passwordError = ref('')

// 字符计数颜色：接近上限变红
function countStyle(value, max) {
  return {
    color: value.length >= max ? 'var(--color-alarm)' : 'var(--color-ink-faint)'
  }
}

async function handleRegister() {
  usernameError.value = ''
  passwordError.value = ''
  error.value = ''

  if (!/^[a-zA-Z0-9_]{2,20}$/.test(username.value)) {
    usernameError.value = '用户名仅支持字母、数字、下划线，长度2-20位'
    return
  }
  if (!name.value.trim()) {
    error.value = '昵称不能为空'
    return
  }
  if (password.value.length < 6 || password.value.length > 20) {
    passwordError.value = '密码长度需6-20位'
    return
  }
  if (!/[a-zA-Z]/.test(password.value) || !/[0-9]/.test(password.value)) {
    passwordError.value = '密码必须同时包含字母和数字'
    return
  }

  try {
    const res = await authApi.register({
      username: username.value,
      name: name.value.trim(),
      password: password.value
    })
    if (res.data.code === 200) {
      window.$message?.success('注册成功，请登录')
      router.push('/')
    } else {
      error.value = res.data.message
    }
  } catch (e) {
    error.value = e.response?.data?.message || '注册失败，请稍后再试'
  }
}
</script>
