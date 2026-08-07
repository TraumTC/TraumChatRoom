<!-- src/views/RegisterView.vue — 注册页 -->
<template>
  <div class="min-h-screen bg-gray-50 flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
    <div class="max-w-md w-full space-y-8 bg-white p-8 rounded-lg shadow-md">
      <!-- 标题 -->
      <div>
        <h2 class="text-center text-3xl font-extrabold text-gray-900">创建账户</h2>
        <p class="mt-2 text-center text-sm text-gray-600">加入我们，开始聊天</p>
      </div>

      <!-- 错误提示 -->
      <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md text-sm">
        {{ error }}
      </div>

      <!-- 注册表单 -->
      <form @submit.prevent="handleRegister" class="mt-8 space-y-6">
        <div class="space-y-4">
          <!-- 用户名 -->
          <div>
            <label for="username" class="block text-sm font-medium text-gray-700 mb-1">用户名 *</label>
            <input id="username" v-model="username" type="text" required autofocus maxlength="20"
                   class="appearance-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                   placeholder="请设置用户名（字母数字下划线）" />
            <p class="mt-1 text-xs text-gray-500">
              用户名长度：<span :class="username.length >= 20 ? 'text-red-600 font-semibold' : 'text-gray-500'">{{ username.length }}</span>/20 个字符
            </p>
            <p v-if="usernameError" class="mt-1 text-xs text-red-500">{{ usernameError }}</p>
          </div>

          <!-- 昵称 -->
          <div>
            <label for="displayName" class="block text-sm font-medium text-gray-700 mb-1">昵称 *</label>
            <input id="displayName" v-model="name" type="text" required maxlength="20"
                   class="appearance-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                   placeholder="请设置您的昵称" />
            <p class="mt-1 text-xs text-gray-500">
              昵称长度：<span :class="name.length >= 20 ? 'text-red-600 font-semibold' : 'text-gray-500'">{{ name.length }}</span>/20 个字符
            </p>
          </div>

          <!-- 密码 -->
          <div>
            <label for="password" class="block text-sm font-medium text-gray-700 mb-1">密码 *</label>
            <input id="password" v-model="password" type="password" required
                   class="appearance-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 sm:text-sm"
                   placeholder="请设置密码（6-20位，含字母和数字）" />
            <p v-if="passwordError" class="mt-1 text-xs text-red-500">{{ passwordError }}</p>
          </div>
        </div>

        <button type="submit" :disabled="authStore.loading"
                class="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors disabled:opacity-50">
          {{ authStore.loading ? '注册中...' : '注册' }}
        </button>
      </form>

      <!-- 登录链接 -->
      <div class="text-center">
        <RouterLink to="/" class="font-medium text-blue-600 hover:text-blue-500 transition-colors">
          已有帐号？立即登录 →
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
      router.push('/')
    } else {
      error.value = res.data.message
    }
  } catch (e) {
    error.value = e.response?.data?.message || '注册失败，请稍后再试'
  }
}
</script>
