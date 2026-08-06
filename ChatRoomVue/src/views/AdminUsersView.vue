<!-- src/views/AdminUsersView.vue — 管理员-用户管理 -->
<template>
  <div class="min-h-screen bg-gray-50">
    <AppHeader />
    <div class="max-w-6xl mx-auto p-6">
      <h1 class="text-xl font-bold text-gray-900 mb-6">用户管理</h1>

      <!-- 工具栏 -->
      <div class="flex items-center gap-4 mb-4">
        <input v-model="keyword" placeholder="搜索用户名/昵称"
               class="w-64 rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
        <label class="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
          <input v-model="includeDeleted" type="checkbox" @change="loadUsers" />
          包含已删除
        </label>
        <button @click="loadUsers" class="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600">
          搜索
        </button>
      </div>

      <!-- 用户表格 -->
      <div class="overflow-x-auto bg-white rounded-lg border border-gray-200">
        <table class="w-full">
          <thead>
            <tr class="bg-gray-50">
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">ID</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">用户名</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">昵称</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">角色</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">状态</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">最后活跃</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id" class="border-t border-gray-100 hover:bg-gray-50">
              <td class="px-4 py-3 text-sm text-gray-700">{{ user.id }}</td>
              <td class="px-4 py-3 text-sm text-gray-700">{{ user.username }}</td>
              <td class="px-4 py-3 text-sm text-gray-700">{{ user.name }}</td>
              <td class="px-4 py-3 text-sm">
                <select :value="user.role" @change="changeRole(user, $event)"
                        class="rounded border border-gray-200 px-2 py-1 text-xs">
                  <option value="ROLE_USER">用户</option>
                  <option value="ROLE_ADMIN">管理员</option>
                  <option value="ROLE_GUEST">游客</option>
                </select>
              </td>
              <td class="px-4 py-3 text-sm">
                <span :class="user.status === 1 ? 'text-emerald-500' : 'text-red-500'">
                  {{ user.status === 1 ? '正常' : '禁用' }}
                </span>
              </td>
              <td class="px-4 py-3 text-sm text-gray-500">{{ formatTime(user.lastActiveTime) }}</td>
              <td class="px-4 py-3 text-right space-x-2">
                <button class="text-xs text-gray-400 hover:text-gray-600"
                        @click="toggleStatus(user)">{{ user.status === 1 ? '禁用' : '启用' }}</button>
                <button class="text-xs text-red-500 hover:text-red-600" @click="deleteUser(user)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div class="flex items-center justify-between py-3 text-sm">
        <span class="text-gray-500">共 {{ total }} 条</span>
        <div class="flex gap-1">
          <button :disabled="page <= 1" @click="page--; loadUsers()"
                  class="px-2.5 py-1 rounded text-gray-600 hover:bg-gray-100 disabled:opacity-40">
            上一页
          </button>
          <span class="px-2.5 py-1 text-gray-600">{{ page }} / {{ totalPages }}</span>
          <button :disabled="page >= totalPages" @click="page++; loadUsers()"
                  class="px-2.5 py-1 rounded text-gray-600 hover:bg-gray-100 disabled:opacity-40">
            下一页
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import { formatTime } from '@/utils/format'
import AppHeader from '@/components/layout/AppHeader.vue'

const authStore = useAuthStore()
const users = ref([])
const keyword = ref('')
const includeDeleted = ref(false)
const page = ref(1)
const size = 10
const total = ref(0)
const totalPages = ref(1)

async function loadUsers() {
  try {
    const res = await adminApi.getUsers({
      page: page.value, size, keyword: keyword.value || undefined,
      includeDeleted: includeDeleted.value
    })
    if (res.data.code === 200) {
      users.value = res.data.data.items
      total.value = res.data.data.total
      totalPages.value = res.data.data.totalPages
    }
  } catch (e) { /* 忽略 */ }
}

async function changeRole(user, e) {
  const role = e.target.value
  if (role === user.role) return
  if (!confirm(`确定将 ${user.name} 的角色改为「${roleName(role)}」吗？`)) {
    e.target.value = user.role  // 还原
    return
  }
  try {
    await adminApi.updateRole(user.id, { role })
    user.role = role
  } catch (err) {
    alert(err.response?.data?.message || '修改失败')
  }
}

async function toggleStatus(user) {
  const newStatus = user.status === 1 ? 0 : 1
  if (!confirm(`确定${newStatus === 0 ? '禁用' : '启用'}用户 ${user.name} 吗？`)) return
  try {
    await adminApi.updateUser(user.id, { status: newStatus })
    user.status = newStatus
  } catch (e) {
    alert(e.response?.data?.message || '操作失败')
  }
}

async function deleteUser(user) {
  if (!confirm(`确定删除用户 ${user.name} 吗？此操作不可恢复！`)) return
  try {
    await adminApi.deleteUser(user.id)
    loadUsers()
  } catch (e) {
    alert(e.response?.data?.message || '删除失败')
  }
}

function roleName(role) {
  return { ROLE_USER: '用户', ROLE_ADMIN: '管理员', ROLE_GUEST: '游客' }[role] || role
}

onMounted(loadUsers)
</script>
