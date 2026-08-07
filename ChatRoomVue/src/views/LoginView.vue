<!-- src/views/LoginView.vue — 登录页（新首页） -->
<template>
  <div class="min-h-screen bg-gray-50 flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full space-y-8 bg-white p-8 rounded-lg shadow-md">
      <!-- 标题 -->
      <div>
        <h2 class="text-center text-3xl font-extrabold text-gray-900">TraumChatRoom</h2>
        <p class="mt-2 text-center text-sm text-gray-600">欢迎回来，请登录您的账户</p>
      </div>

      <!-- 错误提示 -->
      <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md text-sm">
        {{ error }}
      </div>

      <!-- 锁定倒计时 -->
      <div v-if="lockRemain > 0" class="bg-amber-50 border border-amber-200 text-amber-700 px-4 py-3 rounded-md text-sm">
        登录失败次数过多，请在 {{ lockRemain }} 秒后重试
      </div>

      <!-- 登录表单 -->
      <form @submit.prevent="handleLogin" class="mt-8 space-y-6">
        <div class="space-y-4">
          <div>
            <label for="username" class="block text-sm font-medium text-gray-700 mb-1">用户名</label>
            <input id="username" v-model="username" type="text" required autocomplete="username"
                   class="appearance-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                   placeholder="请输入用户名" />
          </div>

          <div>
            <label for="password" class="block text-sm font-medium text-gray-700 mb-1">密码</label>
            <input id="password" v-model="password" type="password" required autocomplete="current-password"
                   class="appearance-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                   placeholder="请输入密码" />
          </div>
        </div>

        <button type="submit" :disabled="authStore.loading"
                class="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors disabled:opacity-50">
          {{ authStore.loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <!-- 分割线 -->
      <div class="relative">
        <div class="absolute inset-0 flex items-center">
          <div class="w-full border-t border-gray-300"></div>
        </div>
        <div class="relative flex justify-center text-sm">
          <span class="px-2 bg-white text-gray-500">或者</span>
        </div>
      </div>

      <!-- 游客入口 + 注册链接 -->
      <div class="space-y-3">
        <button @click="loginAsGuest" :disabled="guestLoading"
                class="group relative w-full flex justify-center py-2 px-4 border-2 border-dashed border-yellow-400 text-sm font-medium rounded-md text-yellow-700 bg-yellow-50 hover:bg-yellow-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-yellow-500 transition-colors disabled:opacity-50">
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
          </svg>
          {{ guestLoading ? '进入中...' : '以游客身份进入' }}
        </button>

        <div class="text-center space-y-2">
          <RouterLink to="/register" class="font-medium text-blue-600 hover:text-blue-500 transition-colors block">
            没有账号？去注册 →
          </RouterLink>
          <p class="text-xs text-gray-500">游客无需注册，系统会自动为您分配一个专属名称</p>
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
const guestLoading = ref(false)

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
</script>
