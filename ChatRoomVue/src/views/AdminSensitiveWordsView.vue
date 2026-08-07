<!-- src/views/AdminSensitiveWordsView.vue — 管理员-敏感词管理（亮色） -->
<template>
  <div class="min-h-screen flex flex-col" style="background: var(--color-bg)">
    <AppHeader />
    <div class="max-w-4xl w-full mx-auto p-6">
      <AdminTabs />
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-lg font-semibold" style="color: var(--color-ink)">敏感词管理</h1>
        <n-button type="primary" @click="openAdd">
          <template #icon><AppIcon name="plus" :size="15" /></template>
          添加敏感词
        </n-button>
      </div>

      <!-- 过滤 -->
      <div class="flex items-center gap-3 mb-4 flex-wrap">
        <n-select v-model:value="levelFilter" :options="levelOptions" placeholder="全部级别" clearable style="width: 120px"
                  @update:value="loadWords" />
        <n-select v-model:value="categoryFilter" :options="categoryOptions" placeholder="全部分类" clearable style="width: 120px"
                  @update:value="loadWords" />
        <n-button @click="refreshWords">刷新词库</n-button>
      </div>

      <!-- 列表 -->
      <n-data-table :columns="columns" :data="words" :bordered="true" size="small" :scroll-x="600" />
      <div v-if="words.length === 0" class="py-10 text-center text-sm" style="color: var(--color-ink-faint)">
        暂无敏感词
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
import { ref, h, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { useChatStore } from '@/stores/chat'
import AppHeader from '@/components/layout/AppHeader.vue'
import AdminTabs from '@/components/admin/AdminTabs.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const chatStore = useChatStore()
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
  { label: '拦截（拒绝发送）', value: 2 },
  { label: '警告（仅记录）', value: 3 }
]
const categoryOptions = [
  { label: '辱骂', value: 'insult' },
  { label: '广告', value: 'ad' },
  { label: '垃圾', value: 'spam' }
]

function levelName(level) {
  return { 1: '替换', 2: '拦截', 3: '警告' }[level] || '未知'
}
function categoryName(cat) {
  return { insult: '辱骂', ad: '广告', spam: '垃圾' }[cat] || '其他'
}

function levelRender(row) {
  const color = { 1: 'var(--color-warn)', 2: 'var(--color-alarm)', 3: 'var(--color-ink-soft)' }[row.level] || 'var(--color-ink-soft)'
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
      h('a', { style: 'color:var(--color-signal);font-size:12px;cursor:pointer', onClick: () => openEdit(row) }, '修改'),
      h('a', { style: 'color:var(--color-alarm);font-size:12px;cursor:pointer', onClick: () => deleteWord(row) }, '删除')
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

onMounted(loadWords)
</script>
