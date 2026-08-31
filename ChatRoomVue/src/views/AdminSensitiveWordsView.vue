<!-- src/views/AdminSensitiveWordsView.vue — 管理员-敏感词管理（亮色） -->
<template>
  <div class="app-h-screen flex flex-col overflow-hidden" style="background: var(--color-bg)">
    <AppHeader />
    <div class="flex-1 min-h-0 max-w-6xl w-full mx-auto p-4 sm:p-6 flex flex-col">
      <AdminTabs />
      <div class="flex items-center justify-between gap-2 flex-wrap mb-6 shrink-0">
        <h1 class="text-lg font-semibold" style="color: var(--color-ink)">敏感词管理</h1>
        <n-button type="primary" @click="openAdd">
          <template #icon><AppIcon name="plus" :size="15" /></template>
          添加敏感词
        </n-button>
      </div>

      <!-- 过滤（桌面端横向条） -->
      <div v-if="!isMobile" class="flex items-center gap-3 mb-4 flex-wrap shrink-0">
        <n-select v-model:value="levelFilter" :options="levelOptions" placeholder="全部级别" clearable class="w-full sm:w-[120px]"
                  @update:value="loadWords" />
        <n-select v-model:value="categoryFilter" :options="categoryOptions" placeholder="全部分类" clearable class="w-full sm:w-[120px]"
                  @update:value="loadWords" />
        <n-button @click="refreshWords">刷新词库</n-button>
        <n-button @click="handleReset">重置</n-button>
      </div>

      <!-- 过滤（平板/移动端：抽屉） -->
      <div v-else class="flex items-center gap-2 mb-4 shrink-0">
        <n-button type="primary" size="small" @click="showFilterDrawer = true">
          <template #icon><AppIcon name="filter" :size="14" /></template>
          筛选
        </n-button>
        <n-button size="small" quaternary @click="refreshWords">刷新词库</n-button>
        <n-button size="small" quaternary @click="handleReset">重置</n-button>
      </div>

      <n-drawer v-model:show="showFilterDrawer" :width="300" placement="right">
        <n-drawer-content title="筛选条件" closable>
          <div class="flex flex-col gap-3">
            <n-select v-model:value="levelFilter" :options="levelOptions" placeholder="全部级别" clearable
                      @update:value="loadWords" />
            <n-select v-model:value="categoryFilter" :options="categoryOptions" placeholder="全部分类" clearable
                      @update:value="loadWords" />
          </div>
        </n-drawer-content>
      </n-drawer>

      <!-- 列表（桌面端表格） -->
      <div v-if="!isMobile" class="flex-1 min-h-0 overflow-y-auto">
        <n-data-table :columns="columns" :data="words" :bordered="true" size="small" :scroll-x="600" />
      </div>

      <!-- 列表（移动端卡片） -->
      <div v-else class="flex-1 min-h-0 overflow-y-auto space-y-2">
        <div v-for="row in words" :key="row.id"
             class="rounded-lg px-3 py-3 space-y-2" style="background: var(--color-card); border: 1px solid var(--color-border)">
          <div class="flex items-center gap-2">
            <span class="text-xs" style="color: var(--color-ink-faint)">#{{ row.id }}</span>
            <span class="text-sm font-medium truncate" style="color: var(--color-ink)">{{ row.word }}</span>
            <span class="ml-auto shrink-0 text-xs" :style="{ color: row.level === 1 ? 'var(--color-warn)' : 'var(--color-alarm)' }">
              {{ levelName(row.level) }}
            </span>
          </div>
          <div class="flex items-center gap-4 text-sm">
            <span class="text-xs" style="color: var(--color-ink-soft)">分类：{{ categoryName(row.category) }}</span>
            <span class="ml-auto action-link" style="color:var(--color-signal)" @click="openEdit(row)">修改</span>
            <span class="action-link" style="color:var(--color-alarm)" @click="deleteWord(row)">删除</span>
          </div>
        </div>
      </div>

      <!-- 添加/修改弹窗 -->
      <n-modal v-model:show="showModal" preset="card" :title="editingId ? '修改敏感词' : '添加敏感词'"
               :style="{ width: '90%', maxWidth: '22rem' }">
        <div class="space-y-3">
          <n-input v-model:value="newWord" placeholder="敏感词" maxlength="50" @keyup.enter="saveWord" />
          <n-select v-model:value="newLevel" :options="levelOptions" />
          <n-select v-model:value="newCategory" :options="categoryOptions" />
          <n-button type="primary" block @click="saveWord">{{ editingId ? '保存' : '添加' }}</n-button>
        </div>
      </n-modal>
    </div>
  </div>
</template>

<script setup>
import { NButton, NDataTable, NDrawer, NDrawerContent, NInput, NModal, NSelect } from 'naive-ui'
import { ref, h, onMounted, onUnmounted } from 'vue'
import { adminApi } from '@/api/admin'
import AppHeader from '@/components/layout/AppHeader.vue'
import AdminTabs from '@/components/admin/AdminTabs.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

// ========== 响应式（平板/移动端卡片，桌面表格） ==========
const isMobile = ref(window.innerWidth < 1024)
function handleResize() {
  isMobile.value = window.innerWidth < 1024
}

const showFilterDrawer = ref(false)

const words = ref([])
const levelFilter = ref(null)
const categoryFilter = ref(null)
const showModal = ref(false)
const editingId = ref(null)
const newWord = ref('')
const newLevel = ref(1)
const newCategory = ref('insult')

const levelOptions = [
  { label: '替换（***）', value: 1 },
  { label: '拦截（拒绝发送）', value: 2 }
]
const categoryOptions = [
  { label: '辱骂', value: 'insult' },
  { label: '广告', value: 'ad' },
  { label: '垃圾', value: 'spam' }
]

function levelName(level) {
  return { 1: '替换', 2: '拦截' }[level] || '未知'
}
function categoryName(cat) {
  return { insult: '辱骂', ad: '广告', spam: '垃圾' }[cat] || '其他'
}

function levelRender(row) {
  const color = { 1: 'var(--color-warn)', 2: 'var(--color-alarm)' }[row.level] || 'var(--color-ink-soft)'
  return h('span', { style: `color:${color}` }, levelName(row.level))
}

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '敏感词', key: 'word', width: 200 },
  { title: '级别', key: 'level', width: 100, render: levelRender },
  { title: '分类', key: 'category', width: 100, render: (r) => categoryName(r.category) },
  {
    title: '操作', key: 'actions', align: 'right',
    render: (row) => h('div', { style: 'display:flex;gap:12px;justify-content:flex-end' }, [
      h('a', { class: 'action-link', style: 'color:var(--color-signal)', onClick: () => openEdit(row) }, '修改'),
      h('a', { class: 'action-link', style: 'color:var(--color-alarm)', onClick: () => deleteWord(row) }, '删除')
    ])
  }
]

async function loadWords() {
  try {
    const res = await adminApi.getSensitiveWords({
      page: 1, size: 100,
      level: levelFilter.value ?? undefined,
      category: categoryFilter.value ?? undefined
    })
    if (res.data.code === 200) {
      words.value = res.data.data.items
    }
  } catch (e) { /* 忽略 */ }
}

// 重置筛选条件
function handleReset() {
  levelFilter.value = null
  categoryFilter.value = null
  loadWords()
}

function openAdd() {
  editingId.value = null
  newWord.value = ''
  newLevel.value = 1
  newCategory.value = 'insult'
  showModal.value = true
}

function openEdit(row) {
  editingId.value = row.id
  newWord.value = row.word
  newLevel.value = row.level
  newCategory.value = row.category || 'insult'
  showModal.value = true
}

async function saveWord() {
  if (!newWord.value.trim()) return
  try {
    const payload = {
      word: newWord.value.trim(),
      level: newLevel.value,
      category: newCategory.value
    }
    let res
    if (editingId.value) {
      res = await adminApi.updateSensitiveWord(editingId.value, payload)
    } else {
      res = await adminApi.addSensitiveWord(payload)
    }
    if (res.data.code === 200) {
      window.$message?.success(editingId.value ? '敏感词已修改' : '敏感词已添加')
      showModal.value = false
      loadWords()
    } else {
      window.$message?.error(res.data.message)
    }
  } catch (e) {
    window.$message?.error(e.response?.data?.message || (editingId.value ? '修改失败' : '添加失败'))
  }
}

async function deleteWord(word) {
  const dialog = window.$dialog
  if (dialog) {
    dialog.warning({
      title: '删除敏感词',
      content: `确定删除敏感词「${word.word}」吗？`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await adminApi.deleteSensitiveWord(word.id)
          loadWords()
        } catch (e) {
          window.$message?.error(e.response?.data?.message || '删除失败')
        }
      }
    })
  }
}

async function refreshWords() {
  try {
    const res = await adminApi.refreshSensitiveWords()
    if (res.data.code === 200) {
      window.$message?.success(`词库已刷新，共 ${res.data.data.count} 个敏感词`)
    }
  } catch (e) {
    window.$message?.error('刷新失败')
  }
}

onMounted(() => {
  loadWords()
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>
