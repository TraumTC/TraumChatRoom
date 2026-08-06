<!-- src/views/AdminLogsView.vue — 管理员-操作日志 -->
<template>
  <div class="min-h-screen bg-gray-50">
    <AppHeader />
    <div class="max-w-6xl mx-auto p-6">
      <h1 class="text-xl font-bold text-gray-900 mb-6">操作日志</h1>

      <!-- 筛选 -->
      <div class="flex items-center gap-4 mb-4 flex-wrap">
        <select v-model="actionFilter" @change="loadLogs"
                class="rounded-lg border border-gray-200 px-3 py-2 text-sm">
          <option value="">全部操作</option>
          <option v-for="a in actionOptions" :key="a.value" :value="a.value">{{ a.label }}</option>
        </select>
        <input v-model="startDate" type="date"
               class="rounded-lg border border-gray-200 px-3 py-2 text-sm" />
        <span class="text-gray-400">至</span>
        <input v-model="endDate" type="date"
               class="rounded-lg border border-gray-200 px-3 py-2 text-sm" />
        <button @click="loadLogs" class="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600">
          查询
        </button>
      </div>

      <!-- 日志表格 -->
      <div class="overflow-x-auto bg-white rounded-lg border border-gray-200">
        <table class="w-full">
          <thead>
            <tr class="bg-gray-50">
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">时间</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">操作者</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">操作</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">目标</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">IP</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id" class="border-t border-gray-100 hover:bg-gray-50">
              <td class="px-4 py-3 text-sm text-gray-500 whitespace-nowrap">{{ formatTime(log.createdAt) }}</td>
              <td class="px-4 py-3 text-sm text-gray-900">{{ log.username }}</td>
              <td class="px-4 py-3 text-sm text-gray-700">{{ actionName(log.action) }}</td>
              <td class="px-4 py-3 text-sm text-gray-500">
                {{ log.targetType || '-' }}{{ log.targetId ? ' #' + log.targetId : '' }}
              </td>
              <td class="px-4 py-3 text-sm text-gray-500">{{ log.ip }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="logs.length === 0" class="py-10 text-center text-sm text-gray-400">
          暂无日志
        </div>
      </div>

      <!-- 分页 -->
      <div class="flex items-center justify-between py-3 text-sm">
        <span class="text-gray-500">共 {{ total }} 条</span>
        <div class="flex gap-1">
          <button :disabled="page <= 1" @click="page--; loadLogs()"
                  class="px-2.5 py-1 rounded text-gray-600 hover:bg-gray-100 disabled:opacity-40">上一页</button>
          <span class="px-2.5 py-1 text-gray-600">{{ page }} / {{ totalPages }}</span>
          <button :disabled="page >= totalPages" @click="page++; loadLogs()"
                  class="px-2.5 py-1 rounded text-gray-600 hover:bg-gray-100 disabled:opacity-40">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { formatTime } from '@/utils/format'
import AppHeader from '@/components/layout/AppHeader.vue'

const logs = ref([])
const actionFilter = ref('')
const startDate = ref('')
const endDate = ref('')
const page = ref(1)
const size = 20
const total = ref(0)
const totalPages = ref(1)

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

async function loadLogs() {
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
      totalPages.value = res.data.data.totalPages
    }
  } catch (e) { /* 忽略 */ }
}

function actionName(action) {
  return actionOptions.find(a => a.value === action)?.label || action
}

onMounted(loadLogs)
</script>
