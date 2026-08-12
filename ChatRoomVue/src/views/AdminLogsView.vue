<!-- src/views/AdminLogsView.vue — 管理员-操作日志 -->
<template>
  <div class="app-h-screen flex flex-col overflow-hidden" style="background: var(--color-bg)">
    <AppHeader />
    <div class="flex-1 min-h-0 max-w-6xl w-full mx-auto p-4 sm:p-6 flex flex-col">
      <AdminTabs />
      <div class="flex items-center justify-between gap-2 flex-wrap mb-6">
        <h1 class="text-lg font-semibold" style="color: var(--color-ink)">操作日志</h1>
        <div class="flex items-center gap-2">
          <n-button size="small" quaternary @click="handleRefresh" :loading="loading">
            <template #icon><AppIcon name="refresh-cw" :size="14" /></template>
            刷新
          </n-button>
        </div>
      </div>

      <!-- 筛选 -->
      <div class="flex items-center gap-3 mb-4 flex-wrap">
        <n-select v-model:value="filters.action" :options="actionOptions" placeholder="全部操作"
                  clearable class="w-full sm:w-[140px]" />
        <n-select v-model:value="filters.targetType" :options="targetTypeOptions" placeholder="全部类型"
                  clearable class="w-full sm:w-[130px]" />
        <n-select v-model:value="successFilter" :options="successOptions" placeholder="全部结果"
                  clearable class="w-full sm:w-[130px]" />
        <n-input v-model:value="filters.username" placeholder="操作者用户名" clearable
                 @keydown.enter="handleQuery" class="w-full sm:w-[180px]">
          <template #prefix><AppIcon name="search" :size="14" /></template>
        </n-input>
        <n-date-picker v-model:value="filters.startDate" type="date" placeholder="开始日期"
                       class="w-full sm:w-[150px]" />
        <span style="color: var(--color-ink-faint)">至</span>
        <n-date-picker v-model:value="filters.endDate" type="date" placeholder="结束日期"
                       class="w-full sm:w-[150px]" />
        <n-button type="primary" @click="handleQuery">查询</n-button>
        <n-button @click="handleReset">重置</n-button>
      </div>

      <!-- 表格 -->
      <div class="flex-1 min-h-0 overflow-y-auto">
        <n-data-table :columns="columns" :data="logs" :loading="loading"
                      :bordered="true" size="small" :scroll-x="1050"
                      :row-class-name="rowClassName" />
        <div v-if="logs.length === 0 && !loading" class="py-10 text-center text-sm" style="color: var(--color-ink-faint)">
          暂无日志
        </div>
      </div>

      <!-- 分页 -->
      <div class="flex items-center justify-between gap-2 flex-wrap py-3 shrink-0">
        <span class="text-sm" style="color: var(--color-ink-soft)">共 {{ total }} 条</span>
        <n-pagination v-model:page="page" :page-size="size" :item-count="total" @update:page="onPageChange"
                      class="pagination-plain" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted, onBeforeUnmount, watch } from 'vue'
import { adminApi } from '@/api/admin'
import { formatTime } from '@/utils/format'
import AppHeader from '@/components/layout/AppHeader.vue'
import AdminTabs from '@/components/admin/AdminTabs.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

// ========== 状态 ==========
const logs = ref([])
const page = ref(1)
const size = 20
const total = ref(0)
const loading = ref(false)
const highlightedIds = ref(new Set())
const highlightedTimer = ref(null)

// filters：统一管理筛选条件（不包含 success，因为 success 有三态：true/false/null）
const filters = reactive({
  action: null,
  targetType: null,
  username: '',
  startDate: null,
  endDate: null
})
const successFilter = ref(null) // 'success' | 'fail' | null

// ========== 选项配置 ==========
const actionOptions = [
  { value: 'LOGIN', label: '登录' },
  { value: 'LOGOUT', label: '登出' },
  { value: 'GUEST_LOGIN', label: '游客登录' },
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
  { value: 'DELETE_SENSITIVE_WORD', label: '删除敏感词' },
  { value: 'UPDATE_SENSITIVE_WORD', label: '修改敏感词' }
]

const targetTypeOptions = [
  { value: 'user', label: '用户' },
  { value: 'message', label: '消息' },
  { value: 'file', label: '文件' },
  { value: 'friend', label: '好友' },
  { value: 'sensitive_word', label: '敏感词' }
]

const successOptions = [
  { value: 'success', label: '成功' },
  { value: 'fail', label: '失败' }
]

const targetTypeMap = {
  user: '用户', message: '消息', file: '文件', friend: '好友', sensitive_word: '敏感词'
}

function actionName(action) {
  return actionOptions.find(a => a.value === action)?.label || action
}

// ========== detail JSON 解析缓存（避免每列重复 parse） ==========
const detailCache = new Map()

function getParsedDetail(row) {
  if (!row.detail) return null
  if (detailCache.has(row.id)) return detailCache.get(row.id)
  try {
    const parsed = JSON.parse(row.detail)
    detailCache.set(row.id, parsed)
    return parsed
  } catch {
    detailCache.set(row.id, null)
    return null
  }
}

// ========== 列渲染 ==========
function targetRender(row) {
  const detailObj = getParsedDetail(row)
  const params = detailObj?.params || []
  const targetType = targetTypeMap[row.targetType] || row.targetType || ''

  if (row.targetType === 'sensitive_word') {
    const wordParam = params.find(p => p && typeof p === 'object' && p.word)
    return wordParam ? `敏感词「${wordParam.word}」` : `${targetType}${row.targetId ? ' #' + row.targetId : ''}`
  }
  if (row.targetType === 'user') {
    const nameParam = params.find(p => p && typeof p === 'object' && (p.name || p.username))
    const label = nameParam?.name || nameParam?.username
    if (label) return `${targetType}「${label}」`
    return `${targetType}${row.targetId ? ' #' + row.targetId : ''}`
  }
  if (row.targetType === 'message') {
    return `消息 #${row.targetId || '-'}`
  }
  return `${targetType}${row.targetId ? ' #' + row.targetId : ''}`
}

function detailRender(row) {
  const d = getParsedDetail(row)
  if (!d) {
    return row.detail
      ? h('span', { style: 'color: var(--color-ink-soft)' }, row.detail.substring(0, 40))
      : '-'
  }
  if (d.error) {
    const full = d.error
    const shown = full.length > 60 ? full.substring(0, 60) + '…' : full
    return h('n-tooltip', { placement: 'top', trigger: 'hover' },
      () => h('n-tag', { type: 'error', size: 'small', round: true }, shown),
      {
        trigger: () => h('div', { class: 'max-w-[320px] break-all text-sm py-1 px-2',
          style: 'color: var(--color-ink)' }, full)
      }
    )
  }
  if (d.success) {
    return h('n-tag', { type: 'success', size: 'small', round: true }, '成功')
  }
  return h('n-tag', { type: 'warning', size: 'small', round: true }, '失败')
}

const columns = [
  { title: '时间', key: 'createdAt', width: 170, render: (row) => formatTime(row.createdAt) },
  { title: '操作者', key: 'username', width: 130 },
  { title: '操作', key: 'action', width: 120, render: (row) => actionName(row.action) },
  { title: '类型', key: 'targetType', width: 90, render: (row) => targetTypeMap[row.targetType] || row.targetType || '-' },
  { title: '目标', key: 'target', width: 200, render: (row) => targetRender(row) },
  { title: '结果', key: 'detail', width: 240, render: (row) => detailRender(row) },
  { title: 'IP', key: 'ip', width: 150 }
]

function rowClassName(row) {
  return highlightedIds.value.has(row.id) ? 'log-row-new' : ''
}

// ========== 日期格式 ==========
function formatDate(ts) {
  if (!ts) return undefined
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// ========== 加载数据 ==========
async function loadLogs() {
  loading.value = true
  try {
    const successParam =
      successFilter.value === 'success' ? true :
      successFilter.value === 'fail' ? false :
      undefined

    const res = await adminApi.getLogs({
      page: page.value,
      size,
      action: filters.action || undefined,
      targetType: filters.targetType || undefined,
      username: filters.username?.trim() || undefined,
      success: successParam,
      startDate: formatDate(filters.startDate),
      endDate: formatDate(filters.endDate)
    })

    if (res.data.code === 200) {
      const newItems = res.data.data.items
      // 检测新日志并高亮（仅 page=1 时）
      if (logs.value.length > 0 && page.value === 1) {
        const oldIds = new Set(logs.value.map(l => l.id))
        const newIds = new Set()
        for (const item of newItems) {
          if (!oldIds.has(item.id)) newIds.add(item.id)
        }
        if (newIds.size > 0) {
          if (highlightedTimer.value) clearTimeout(highlightedTimer.value)
          highlightedIds.value = newIds
          highlightedTimer.value = setTimeout(() => {
            highlightedIds.value = new Set()
          }, 3000)
        }
      }
      logs.value = newItems
      total.value = res.data.data.total
      detailCache.clear() // 换数据时清理缓存
    }
  } catch (e) { /* 忽略 */ }
  finally { loading.value = false }
}

// ========== 防抖 ==========
let debounceTimer = null
function scheduleLoad(immediate = false) {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (immediate) {
    loadLogs()
  } else {
    debounceTimer = setTimeout(() => loadLogs(), 300)
  }
}

// ========== 事件处理 ==========
function handleRefresh() {
  scheduleLoad(true)
}
function handleQuery() {
  page.value = 1
  scheduleLoad(true)
}
function handleReset() {
  filters.action = null
  filters.targetType = null
  filters.username = ''
  filters.startDate = null
  filters.endDate = null
  successFilter.value = null
  page.value = 1
  scheduleLoad(true)
}
function onPageChange() {
  scheduleLoad(true)
}

// 监听筛选条件变化 → 防抖自动请求（用户还可点查询立即请求）
watch(
  () => [filters.action, filters.targetType, filters.username, filters.startDate, filters.endDate, successFilter.value],
  () => {
    page.value = 1
    scheduleLoad(false)
  }
)

onMounted(() => {
  loadLogs()
})
onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (highlightedTimer.value) clearTimeout(highlightedTimer.value)
})
</script>

<style scoped>
/* 新日志高亮动画 */
:deep(.log-row-new td) {
  background: rgba(59, 130, 246, 0.06) !important;
  transition: background 0.3s ease;
}
</style>
