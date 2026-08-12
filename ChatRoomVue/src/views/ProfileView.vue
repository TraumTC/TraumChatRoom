<!-- src/views/ProfileView.vue — 个人中心（亮色） -->
<template>
  <div class="min-h-screen flex flex-col" style="background: var(--color-bg)">
    <AppHeader />

    <div class="max-w-lg w-full mx-auto p-4 sm:p-8">
      <RouterLink to="/chat" class="inline-flex items-center gap-1 text-sm mb-6 transition-colors hover:opacity-80"
                  style="color: var(--color-ink-soft)">
        <AppIcon name="chevron-left" :size="16" />返回聊天
      </RouterLink>

      <div class="rounded-xl overflow-hidden" style="background: var(--color-card); border: 1px solid var(--color-border)">
        <!-- 头像区 -->
        <div class="flex flex-col items-center py-8" style="border-bottom: 1px solid var(--color-border)">
          <div class="relative cursor-pointer group mb-3" title="点击查看头像" @click="showAvatarPreview = true">
            <UserAvatar :user="authStore.user" size="lg" />
            <div class="absolute inset-0 rounded-full bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
              <AppIcon name="eye" :size="20" class="text-white" />
            </div>
          </div>
          <p class="text-xs" style="color: var(--color-ink-faint)">点击头像预览或更换</p>
          <AvatarPreview ref="avatarPreviewRef" :visible="showAvatarPreview" :user="authStore.user"
                         @close="showAvatarPreview = false"
                         @change="handleAvatarChange" @delete="handleAvatarDelete" />
        </div>

        <!-- 基本信息 -->
        <div class="p-6 space-y-4">
          <h2 class="text-sm font-medium" style="color: var(--color-ink)">基本信息</h2>

          <div>
            <label class="block text-xs mb-1" style="color: var(--color-ink-soft)">用户名</label>
            <n-input :value="authStore.user?.username" disabled />
          </div>

          <div>
            <label class="block text-xs mb-1" style="color: var(--color-ink-soft)">昵称</label>
            <n-input v-model:value="name" placeholder="输入新昵称" maxlength="20" @keyup.enter="handleSaveProfile" />
          </div>

          <div>
            <label class="block text-xs mb-1" style="color: var(--color-ink-soft)">角色</label>
            <n-input :value="roleName" disabled />
          </div>

          <n-button type="primary" block @click="handleSaveProfile">保存昵称</n-button>
        </div>

        <!-- 修改密码 -->
        <div class="p-6 space-y-4" style="border-top: 1px solid var(--color-border)">
          <h2 class="text-sm font-medium" style="color: var(--color-ink)">修改密码</h2>
          <div class="space-y-3">
            <n-input v-model:value="oldPassword" type="password" placeholder="当前密码" show-password-on="click" />
            <n-input v-model:value="newPassword" type="password" placeholder="新密码（6-20位，含字母和数字）" show-password-on="click"
                     @keyup.enter="handleChangePassword" />
            <n-button block @click="handleChangePassword">修改密码</n-button>
          </div>
        </div>

        <n-alert v-if="error" type="error" :show-icon="false" class="mx-6 mb-6" closable @close="error = ''">
          {{ error }}
        </n-alert>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { userApi } from '@/api/user'
import AppHeader from '@/components/layout/AppHeader.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AvatarPreview from '@/components/user/AvatarPreview.vue'

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()

const name = ref('')
const oldPassword = ref('')
const newPassword = ref('')
const error = ref('')
const showAvatarPreview = ref(false)
const avatarPreviewRef = ref(null)

const roleName = computed(() => {
  return { ROLE_ADMIN: '管理员', ROLE_GUEST: '游客', ROLE_USER: '普通用户' }[authStore.user?.role] || ''
})

async function handleSaveProfile() {
  error.value = ''
  if (!name.value.trim()) return
  try {
    const res = await userApi.updateProfile({ name: name.value.trim() })
    if (res.data.code === 200) {
      authStore.user.name = name.value.trim()
      authStore.user = { ...authStore.user }
      // 清除消息缓存，强制重拉历史以显示新昵称
      chatStore.clearMessages()
      window.$message?.success('昵称已更新')
    } else {
      error.value = res.data.message
    }
  } catch (e) {
    error.value = e.response?.data?.message || '保存失败'
  }
}

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
    const res = await userApi.updatePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value })
    if (res.data.code === 200) {
      window.$message?.success('密码修改成功，请重新登录')
      await authStore.logout()
      router.push('/')
    } else {
      error.value = res.data.message
    }
  } catch (e) {
    error.value = e.response?.data?.message || '修改失败'
  }
}

async function handleAvatarChange(blob) {
  error.value = ''
  const formData = new FormData()
  formData.append('file', blob, 'avatar.jpg')
  try {
    const res = await userApi.uploadAvatar(formData)
    if (res.data.code === 200) {
      // 缓存破坏：追加时间戳参数，强制浏览器重新加载
      authStore.user.avatar = res.data.data.avatarUrl + '?t=' + Date.now()
      authStore.user = { ...authStore.user }
      avatarPreviewRef.value?.uploadDone()
      window.$message?.success('头像更换成功')
    } else {
      avatarPreviewRef.value?.uploadFailed(res.data.message)
    }
  } catch (err) {
    const msg = err.response?.data?.message || '头像上传失败'
    avatarPreviewRef.value?.uploadFailed(msg)
  }
}

async function handleAvatarDelete() {
  try {
    const res = await userApi.deleteAvatar()
    if (res.data.code === 200) {
      authStore.user.avatar = null
      authStore.user = { ...authStore.user }
      window.$message?.success('已恢复默认头像')
    }
  } catch (e) {
    error.value = e.response?.data?.message || '删除失败'
  }
}

onMounted(() => {
  name.value = authStore.user?.name || ''
})
</script>
