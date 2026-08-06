<!-- src/views/ProfileView.vue — 个人中心 -->
<template>
  <div class="min-h-screen bg-gray-50">
    <AppHeader />

    <div class="max-w-md mx-auto p-8">
      <h1 class="text-xl font-bold text-gray-900 mb-6">个人中心</h1>

      <div class="bg-white rounded-lg shadow-sm p-6">
        <!-- 头像区 -->
        <div class="flex flex-col items-center mb-6">
          <div class="relative mb-3">
            <UserAvatar :user="authStore.user" size="lg" />
            <!-- 上传头像按钮 -->
            <label class="absolute bottom-0 right-0 w-8 h-8 rounded-full bg-blue-500 text-white flex items-center justify-center cursor-pointer hover:bg-blue-600"
                   aria-label="更换头像">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              <input type="file" class="hidden" accept="image/*" @change="handleAvatarUpload" />
            </label>
          </div>
          <button v-if="authStore.user?.avatar" @click="handleAvatarDelete"
                  class="text-xs text-gray-400 hover:text-red-500">
            删除头像
          </button>
        </div>

        <!-- 信息 -->
        <div class="space-y-4">
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

          <!-- 修改昵称 -->
          <button @click="handleSaveProfile"
                  class="w-full bg-blue-500 text-white py-2 rounded hover:bg-blue-600">
            保存昵称
          </button>
        </div>

        <!-- 修改密码 -->
        <div class="mt-8 pt-6 border-t border-gray-100">
          <h2 class="text-sm font-medium text-gray-900 mb-4">修改密码</h2>
          <div class="space-y-3">
            <input v-model="oldPassword" type="password" placeholder="当前密码"
                   class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
            <input v-model="newPassword" type="password" placeholder="新密码（6-20位，含字母和数字）"
                   class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
            <button @click="handleChangePassword"
                    class="w-full bg-white border border-gray-200 text-gray-700 py-2 rounded hover:bg-gray-50">
              修改密码
            </button>
          </div>
        </div>

        <!-- 错误提示 -->
        <div v-if="error" class="mt-4 p-3 bg-red-50 text-red-600 text-sm rounded">
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

const router = useRouter()
const authStore = useAuthStore()

const name = ref('')
const oldPassword = ref('')
const newPassword = ref('')
const error = ref('')

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
      // 更新本地用户信息
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
      router.push('/login')
    } else {
      error.value = res.data.message
    }
  } catch (e) {
    error.value = e.response?.data?.message || '修改失败'
  }
}

// 上传头像
async function handleAvatarUpload(e) {
  const file = e.target.files[0]
  if (!file) return
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
  e.target.value = ''
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
