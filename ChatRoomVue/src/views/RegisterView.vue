<!-- src/views/RegisterView.vue — 注册页 -->
<template>
  <div class="min-h-screen bg-white flex items-center justify-center">
    <div class="w-full max-w-sm px-8">
      <h1 class="text-2xl font-bold text-gray-900 mb-6 text-center">注册</h1>

      <div v-if="error" class="mb-4 p-3 bg-red-50 text-red-600 text-sm rounded">
        {{ error }}
      </div>

      <form @submit.prevent="handleRegister" class="space-y-4">
        <div>
          <label class="block text-sm text-gray-600 mb-1">用户名（2-20位，字母数字下划线）</label>
          <input v-model="username" type="text" required
                 class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                 placeholder="请输入用户名" />
          <p v-if="usernameError" class="mt-1 text-xs text-red-500">{{ usernameError }}</p>
        </div>

        <div>
          <label class="block text-sm text-gray-600 mb-1">昵称（1-20位）</label>
          <input v-model="name" type="text" required
                 class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                 placeholder="请输入昵称" />
        </div>

        <div>
          <label class="block text-sm text-gray-600 mb-1">密码（6-20位，须含字母和数字）</label>
          <input v-model="password" type="password" required
                 class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                 placeholder="请输入密码" />
          <p v-if="passwordError" class="mt-1 text-xs text-red-500">{{ passwordError }}</p>
        </div>

        <button type="submit" :disabled="authStore.loading"
                class="w-full bg-blue-500 text-white py-2 rounded hover:bg-blue-600 disabled:opacity-50">
          {{ authStore.loading ? '注册中...' : '注册' }}
        </button>
      </form>

      <div class="mt-6 text-center text-sm">
        <span class="text-gray-400">已有账号？</span>
        <RouterLink to="/login" class="text-blue-500 hover:text-blue-600">去登录</RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const username = ref('')
const name = ref('')
const password = ref('')
const error = ref('')
const usernameError = ref('')
const passwordError = ref('')

async function handleRegister() {
  // 前端校验（与后端 @Pattern 一致）
  usernameError.value = ''
  passwordError.value = ''
  error.value = ''

  if (!/^[a-zA-Z0-9_]{2,20}$/.test(username.value)) {
    usernameError.value = '用户名仅支持字母、数字、下划线，长度2-20位'
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
      name: name.value,
      password: password.value
    })
    if (res.data.code === 200) {
      router.push('/login')
    } else {
      error.value = res.data.message
    }
  } catch (e) {
    error.value = e.response?.data?.message || '注册失败，请稍后再试'
  }
}
</script>
