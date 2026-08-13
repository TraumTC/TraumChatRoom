<!-- src/views/AdminUsersView.vue — 管理员-用户管理（亮色） -->
<template>
  <div class="app-h-screen flex flex-col overflow-hidden" style="background: var(--color-bg)">
    <AppHeader />
    <div class="flex-1 min-h-0 max-w-6xl w-full mx-auto p-4 sm:p-6 flex flex-col">
      <AdminTabs />
      <h1 class="text-lg font-semibold mb-6" style="color: var(--color-ink)">用户管理</h1>

      <!-- 工具栏 -->
      <div class="flex items-center gap-3 mb-4 flex-wrap shrink-0">
        <n-input v-model:value="keyword" placeholder="搜索用户名/昵称" clearable class="w-full sm:w-60"
                 @keyup.enter="loadUsers">
          <template #prefix><AppIcon name="search" :size="14" /></template>
        </n-input>
        <n-checkbox v-model:checked="includeDeleted" @update:checked="loadUsers">包含已删除</n-checkbox>
        <n-button type="primary" @click="loadUsers">搜索</n-button>
        <n-button @click="handleReset">重置</n-button>
      </div>

      <!-- 表格 -->
      <div class="flex-1 min-h-0 overflow-y-auto">
        <n-data-table :columns="columns" :data="users" :loading="loading"
                      :bordered="true" size="small" :scroll-x="800" />
      </div>

      <!-- 分页 -->
      <div class="flex items-center justify-between gap-2 flex-wrap py-3 shrink-0">
        <span class="text-sm" style="color: var(--color-ink-soft)">共 {{ total }} 条</span>
        <n-pagination v-model:page="page" :page-size="size" :item-count="total"
                      @update:page="loadUsers" class="pagination-plain" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, h, onMounted } from 'vue'
import { NInput } from 'naive-ui'
import { adminApi } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import { formatTime } from '@/utils/format'
import AppHeader from '@/components/layout/AppHeader.vue'
import AdminTabs from '@/components/admin/AdminTabs.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const authStore = useAuthStore()
const users = ref([])
const keyword = ref('')
const includeDeleted = ref(false)
const page = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)

const roleOptions = [
  { label: '用户', value: 'ROLE_USER' },
  { label: '管理员', value: 'ROLE_ADMIN' }
]

function statusRender(row) {
  return h('span', { style: { color: row.status === 1 ? 'var(--color-live)' : 'var(--color-alarm)' } },
    row.status === 1 ? '正常' : '禁用')
}

function actionsRender(row) {
  return h('div', { style: 'display:flex;gap:12px;justify-content:flex-end' }, [
    h('a', { class: 'action-link', style: 'color:var(--color-signal)', onClick: () => resetPassword(row) }, '重置密码'),
    h('a', { class: 'action-link', style: 'color:var(--color-ink-soft)', onClick: () => toggleStatus(row) }, row.status === 1 ? '禁用' : '启用'),
    h('a', { class: 'action-link', style: 'color:var(--color-alarm)', onClick: () => deleteUser(row) }, '删除')
  ])
}

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '用户名', key: 'username', width: 120 },
  { title: '昵称', key: 'name', width: 140 },
  {
    title: '角色', key: 'role', width: 120,
    render(row) {
      return h('n-select', {
        value: row.role,
        options: roleOptions,
        size: 'small',
        style: 'width:110px',
        onUpdateValue: (v) => changeRole(row, v)
      }, null)
    }
  },
  { title: '状态', key: 'status', width: 80, render: statusRender },
  { title: '最后活跃', key: 'lastActiveTime', width: 160, render: (row) => formatTime(row.lastActiveTime) },
  { title: '操作', key: 'actions', align: 'right', render: actionsRender }
]

async function loadUsers() {
  loading.value = true
  try {
    const res = await adminApi.getUsers({
      page: page.value, size, keyword: keyword.value || undefined,
      includeDeleted: includeDeleted.value
    })
    if (res.data.code === 200) {
      users.value = res.data.data.items
      total.value = res.data.data.total
    }
  } catch (e) { /* 忽略 */ }
  finally { loading.value = false }
}

// 重置筛选条件
function handleReset() {
  keyword.value = ''
  includeDeleted.value = false
  page.value = 1
  loadUsers()
}

function roleName(role) {
  return { ROLE_USER: '用户', ROLE_ADMIN: '管理员', ROLE_GUEST: '游客' }[role] || role
}

async function changeRole(user, role) {
  if (role === user.role) return
  const dialog = window.$dialog
  if (dialog) {
    dialog.warning({
      title: '修改角色',
      content: `确定将 ${user.name} 的角色改为「${roleName(role)}」吗？`,
      positiveText: '确认',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await adminApi.updateRole(user.id, { role })
          user.role = role
          window.$message?.success('角色已修改')
        } catch (err) {
          window.$message?.error(err.response?.data?.message || '修改失败')
        }
      }
    })
  }
}

async function toggleStatus(user) {
  const newStatus = user.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  const dialog = window.$dialog
  if (dialog) {
    dialog.warning({
      title: `${action}用户`,
      content: `确定${action}用户 ${user.name} 吗？`,
      positiveText: '确认',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await adminApi.updateUser(user.id, { status: newStatus })
          user.status = newStatus
          window.$message?.success(`用户已${action}`)
        } catch (e) {
          window.$message?.error(e.response?.data?.message || '操作失败')
        }
      }
    })
  }
}

async function deleteUser(user) {
  const dialog = window.$dialog
  if (dialog) {
    dialog.warning({
      title: '删除用户',
      content: `确定删除用户 ${user.name} 吗？此操作不可恢复！`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await adminApi.deleteUser(user.id)
          window.$message?.success('用户已删除')
          loadUsers()
        } catch (e) {
          window.$message?.error(e.response?.data?.message || '删除失败')
        }
      }
    })
  }
}

function resetPassword(user) {
  const dialog = window.$dialog
  if (!dialog) return
  let newPassword = ''
  dialog.warning({
    title: '重置密码',
    content: () => h('form', { onSubmit: (e) => { e.preventDefault() } }, [
      h('p', { style: 'margin-bottom:8px;color:var(--color-ink-soft)' }, `为 ${user.name} 设置新密码（6-20位，含字母和数字）：`),
      h(NInput, {
        type: 'password',
        placeholder: '输入新密码',
        showPasswordOn: 'click',
        inputProps: { autocomplete: 'new-password' },
        onUpdateValue: (v) => { newPassword = v }
      })
    ]),
    positiveText: '重置',
    negativeText: '取消',
    // 返回 false / Promise<false> 时阻止弹窗关闭
    onPositiveClick: async () => {
      const pwd = (newPassword || '').trim()
      if (!pwd) {
        window.$message?.error('请输入新密码')
        return false
      }
      if (pwd.length < 6 || pwd.length > 20) {
        window.$message?.error('密码长度需6-20位')
        return false
      }
      if (!/[a-zA-Z]/.test(pwd) || !/[0-9]/.test(pwd)) {
        window.$message?.error('密码必须同时包含字母和数字')
        return false
      }
      try {
        await adminApi.updateUser(user.id, { password: pwd })
        window.$message?.success('密码已重置')
        return true
      } catch (e) {
        window.$message?.error(e.response?.data?.message || '重置失败')
        return false
      }
    }
  })
}

onMounted(loadUsers)
</script>
