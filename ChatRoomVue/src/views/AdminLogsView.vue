<!-- src/views/AdminLogsView.vue — 管理员-操作日志（亮色） -->
<template>
  <div class="min-h-screen flex flex-col" style="background: var(--color-bg)">
    <AppHeader />
    <div class="max-w-6xl w-full mx-auto p-6">
      <h1 class="text-lg font-semibold mb-6" style="color: var(--color-ink)">操作日志</h1>

      <!-- 筛选 -->
      <div class="flex items-center gap-3 mb-4 flex-wrap">
        <n-select v-model:value="actionFilter" :options="actionOptions" placeholder="全部操作" clearable style="width: 140px"
                  @update:value="page = 1; loadLogs()" />
        <n-input v-model:value="startDate" type="date" placeholder="开始日期" style="width: 150px" />
        <span style="color: var(--color-ink-faint)">至</span>
        <n-input v-model:value="endDate" type="date" placeholder="结束日期" style="width: 150px" />
        <n-button type="primary" @click="page = 1; loadLogs()">查询</n-button>
      </div>

      <!-- 表格 -->
      <n-data-table :columns="columns" :data="logs" :loading="loading"
                    :bordered="true" size="small" :scroll-x="900" />
      <div v-if="logs.length === 0 && !loading" class="py-10 text-center text-sm" style="color: var(--color-ink-faint)">
        暂无日志
      </div>

      <!-- 分页 -->
      <div class="flex items-center justify-between py-3">
        <span class="text-sm" style="color: var(--color-ink-soft)">共 {{ total }} 条</span>
        <n-pagination v-model:page="page" :page-size="size" :item-count="total" @update:page="loadLogs" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, h, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { formatTime } from '@/utils/format'
import AppHeader from '@/components/layout/AppHeader.vue'

const logs = ref([])
const actionFilter = ref(null)
const startDate = ref('')
const endDate = ref('')
const page = ref(1)
const size = 20
const total = ref(0)
const loading = ref(false)

const actionOptions = [
  { value: 'LOGIN', label: '登录' },
  { value: 'LOGIN_FAIL', label: '登录失败' },
  { value: 'LOGOUT', label: '登出' },
  { value: 'REGISTER', label: '注册' },
  { value: 'CHANGE_PASSWORD', label: '修改密码' },
  { value: 'CHANGE_PROFILE', label: '修改资料' },
  { value: 'CHANGE_ROLE', label: '修改角色' },
  { value: 'DELETE_USER', label: '删除用户' },
  { value: 'RECALL_MESSAGE', label: '撤回消息' },
  { value: 'UPLOAD_FILE', label: '上传文件' },
  { value: 'ADD_FRIEND', label: '添加好友' },
  { value: 'DELETE_FRIEND', label: '删除好友' },
  { value: 'ADD_SENSITIVE_WORD', label: '添加敏感词' },
  { value: 'DELETE_SENSITIVE_WORD', label: '删除敏感词' }
]

function actionName(action) {
  return actionOptions.find(a => a.value === action)?.label || action
}

const columns = [
  { title: '时间', key: 'createdAt', width: 170, render: (row) => formatTime(row.createdAt) },
  { title: '操作者', key: 'username', width: 130 },
  { title: '操作', key: 'action', width: 120, render: (row) => actionName(row.action) },
  { title: '目标', key: 'target', width: 150, render: (row) => `${row.targetType || '-'}${row.targetId ? ' #' + row.targetId : ''}` },
  { title: 'IP', key: 'ip', width: 150 }
]

async function loadLogs() {
  loading.value = true
  try {
    const res = await adminApi.getLogs({
      page: page.value, size,
      action: actionFilter.value || undefined,
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined
    })
    if (res.data.code === 200) {
      logs.value = res.data.data.items
      total.value = res.data.data.total
    }
  } catch (e) { /* 忽略 */ }
  finally { loading.value = false }
}

onMounted(loadLogs)
</script>
