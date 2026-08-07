<!-- src/views/ProfileView.vue — 个人中心 -->
<template>
  <div class="min-h-screen bg-gray-50">
    <AppHeader />

    <div class="max-w-lg mx-auto p-8">
      <!-- 返回链接 -->
      <RouterLink to="/chat" class="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-900 mb-6 transition-colors">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
        </svg>
        返回聊天
      </RouterLink>

      <div class="bg-white rounded-lg shadow-sm">
        <!-- 头像区 -->
        <div class="flex flex-col items-center py-8 border-b border-gray-100">
          <div class="relative cursor-pointer group mb-3" title="点击查看头像"
               @click="showAvatarPreview = true">
            <UserAvatar :user="authStore.user" size="lg" />
            <div class="absolute inset-0 rounded-full bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
              <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
              </svg>
            </div>
          </div>
          <p class="text-xs text-gray-400">点击头像预览或更换</p>

          <!-- 头像预览模态框 -->
          <AvatarPreview :visible="showAvatarPreview"
                         :user="authStore.user"
                         @close="showAvatarPreview = false"
                         @change="handleAvatarChange"
                         @delete="handleAvatarDelete" />
        </div>

        <!-- 基本信息 -->
        <div class="p-6 space-y-4">
          <h2 class="text-sm font-medium text-gray-900">基本信息</h2>

          <div>
            <label class="block text-sm text-gray-600 mb-1">用户名</label>
            <input :value="authStore.user?.username" disabled
                   class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm bg-gray-50 text-gray-500" />
          </div>

          <div>
            <label class="block text-sm text-gray-600 mb-1">昵称</label>
            <input v-model="name" placeholder="输入新昵称" maxlength="20"
                   class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
          </div>

          <div>
            <label class="block text-sm text-gray-600 mb-1">角色</label>
            <input :value="roleName" disabled
                   class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm bg-gray-50 text-gray-500" />
          </div>

          <button @click="handleSaveProfile"
                  class="w-full bg-blue-500 text-white py-2 rounded-md hover:bg-blue-600 transition-colors">
            保存昵称
          </button>
        </div>

        <!-- 修改密码 -->
        <div class="p-6 border-t border-gray-100 space-y-4">
          <h2 class="text-sm font-medium text-gray-900">修改密码</h2>
          <div class="space-y-3">
            <input v-model="oldPassword" type="password" placeholder="当前密码"
                   class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
            <input v-model="newPassword" type="password" placeholder="新密码（6-20位，含字母和数字）"
                   class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
            <button @click="handleChangePassword"
                    class="w-full bg-white border border-gray-200 text-gray-700 py-2 rounded-md hover:bg-gray-50 transition-colors">
              修改密码
            </button>
          </div>
        </div>

        <!-- 错误提示 -->
        <div v-if="error" class="mx-6 mb-6 p-3 bg-red-50 border border-red-200 text-red-600 text-sm rounded-md">
          {{ error }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'
import AppHeader from '@/components/layout/AppHeader.vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AvatarPreview from '@/components/user/AvatarPreview.vue'

const router = useRouter()
const authStore = useAuthStore()

const name = ref('')
const oldPassword = ref('')
const newPassword = ref('')
const error = ref('')
const showAvatarPreview = ref(false)

const roleName = computed(() => {
  return { ROLE_ADMIN: '管理员', ROLE_GUEST: '游客', ROLE_USER: '普通用户' }[authStore.user?.role] || ''
})

// 保存昵称
async function handleSaveProfile() {
  error.value = ''
  if (!name.value.trim()) return

  try {
    const res = await userApi.updateProfile({ name: name.value.trim() })
    if (res.data.code === 200) {
      authStore.user.name = name.value
      authStore.user = { ...authStore.user }
    } else {
      error.value = res.data.message
    }
  } catch (e) {
    error.value = e.response?.data?.message || '保存失败'
  }
}

// 修改密码
async function handleChangePassword() {
  error.value = ''
  if (!oldPassword.value || !newPassword.value) {
    error.value = '请填写完整'
    return
  }
  if (newPassword.value.length < 6 || newPassword.value.length > 20) {
    error.value = '新密码长度需6-20位'
    return
  }
  if (!/[a-zA-Z]/.test(newPassword.value) || !/[0-9]/.test(newPassword.value)) {
    error.value = '新密码必须同时包含字母和数字'
    return
  }

  try {
    const res = await userApi.updatePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value
    })
    if (res.data.code === 200) {
      alert('密码修改成功，请重新登录')
      await authStore.logout()
      router.push('/')
    } else {
      error.value = res.data.message
    }
  } catch (e) {
    error.value = e.response?.data?.message || '修改失败'
  }
}

// 更换头像（从 AvatarPreview 模态框触发）
async function handleAvatarChange(file) {
  error.value = ''
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await userApi.uploadAvatar(formData)
    if (res.data.code === 200) {
      authStore.user.avatar = res.data.data.avatarUrl
      authStore.user = { ...authStore.user }
    } else {
      error.value = res.data.message
    }
  } catch (err) {
    error.value = err.response?.data?.message || '头像上传失败'
  }
}

// 删除头像
async function handleAvatarDelete() {
  try {
    const res = await userApi.deleteAvatar()
    if (res.data.code === 200) {
      authStore.user.avatar = null
      authStore.user = { ...authStore.user }
    }
  } catch (e) {
    error.value = e.response?.data?.message || '删除失败'
  }
}

onMounted(() => {
  name.value = authStore.user?.name || ''
})
</script>
