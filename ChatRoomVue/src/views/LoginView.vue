<!-- src/views/LoginView.vue — 登录页 -->
<template>
  <div class="min-h-screen bg-white flex items-center justify-center">
    <div class="w-full max-w-sm px-8">
      <h1 class="text-2xl font-bold text-gray-900 mb-6 text-center">登录</h1>

      <div v-if="error" class="mb-4 p-3 bg-red-50 text-red-600 text-sm rounded">
        {{ error }}
      </div>

      <div v-if="lockRemain > 0" class="mb-4 p-3 bg-amber-50 text-amber-600 text-sm rounded">
        登录失败次数过多，请在 {{ lockRemain }} 秒后重试
      </div>

      <form @submit.prevent="handleLogin" class="space-y-4">
        <div>
          <label class="block text-sm text-gray-600 mb-1">用户名</label>
          <input v-model="username" type="text" required
                 class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                 placeholder="请输入用户名" />
        </div>

        <div>
          <label class="block text-sm text-gray-600 mb-1">密码</label>
          <input v-model="password" type="password" required
                 class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                 placeholder="请输入密码" />
        </div>

        <button type="submit" :disabled="authStore.loading"
                class="w-full bg-blue-500 text-white py-2 rounded hover:bg-blue-600 disabled:opacity-50">
          {{ authStore.loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="mt-6 text-center text-sm space-y-2">
        <div>
          <span class="text-gray-400">没有账号？</span>
          <RouterLink to="/register" class="text-blue-500 hover:text-blue-600">去注册</RouterLink>
        </div>
        <div>
          <button @click="loginAsGuest" class="text-gray-400 hover:text-gray-600">以游客身份进入</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const username = ref('')
const password = ref('')
const error = ref('')
const lockRemain = ref(0)

async function handleLogin() {
  error.value = ''
  const success = await authStore.login(username.value, password.value)
  if (success) {
    router.push('/chat')
  } else {
    // 显示锁定倒计时
    if (authStore.error?.includes('锁定') || authStore.error?.includes('失败次数')) {
      lockRemain.value = 750
    }
    error.value = authStore.error
  }
}

async function loginAsGuest() {
  const success = await authStore.loginAsGuest()
  if (success) router.push('/chat')
}
</script>
