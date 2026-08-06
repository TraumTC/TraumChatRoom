<!-- src/views/AdminSensitiveWordsView.vue — 管理员-敏感词管理 -->
<template>
  <div class="min-h-screen bg-gray-50">
    <AppHeader />
    <div class="max-w-4xl mx-auto p-6">
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-xl font-bold text-gray-900">敏感词管理</h1>
        <button @click="showAdd = true" class="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600">
          添加敏感词
        </button>
      </div>

      <!-- 过滤 -->
      <div class="flex items-center gap-4 mb-4">
        <select v-model="levelFilter" @change="loadWords"
                class="rounded-lg border border-gray-200 px-3 py-2 text-sm">
          <option value="">全部级别</option>
          <option value="1">替换</option>
          <option value="2">拦截</option>
          <option value="3">警告</option>
        </select>
        <select v-model="categoryFilter" @change="loadWords"
                class="rounded-lg border border-gray-200 px-3 py-2 text-sm">
          <option value="">全部分类</option>
          <option value="insult">辱骂</option>
          <option value="ad">广告</option>
          <option value="spam">垃圾</option>
        </select>
        <button @click="refreshWords" class="px-4 py-2 text-sm bg-white border border-gray-200 text-gray-700 rounded hover:bg-gray-50">
          刷新词库
        </button>
      </div>

      <!-- 列表 -->
      <div class="overflow-x-auto bg-white rounded-lg border border-gray-200">
        <table class="w-full">
          <thead>
            <tr class="bg-gray-50">
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">ID</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">敏感词</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">级别</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-left">分类</th>
              <th class="px-4 py-3 text-xs text-gray-500 uppercase text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="word in words" :key="word.id" class="border-t border-gray-100 hover:bg-gray-50">
              <td class="px-4 py-3 text-sm text-gray-700">{{ word.id }}</td>
              <td class="px-4 py-3 text-sm text-gray-900 font-medium">{{ word.word }}</td>
              <td class="px-4 py-3 text-sm">
                <span :class="{ 'text-amber-500': word.level === 1, 'text-red-500': word.level === 2, 'text-gray-500': word.level === 3 }">
                  {{ levelName(word.level) }}
                </span>
              </td>
              <td class="px-4 py-3 text-sm text-gray-500">{{ categoryName(word.category) }}</td>
              <td class="px-4 py-3 text-right">
                <button class="text-xs text-red-500 hover:text-red-600" @click="deleteWord(word)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="words.length === 0" class="py-10 text-center text-sm text-gray-400">
          暂无敏感词
        </div>
      </div>

      <!-- 添加弹窗 -->
      <div v-if="showAdd" class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/40 z-40" @click="showAdd = false"></div>
        <div class="bg-white rounded-lg shadow-lg w-full max-w-sm relative z-50">
          <div class="px-5 pt-5 text-lg font-semibold text-gray-900">添加敏感词</div>
          <div class="px-5 py-4 space-y-3">
            <input v-model="newWord" placeholder="敏感词" maxlength="50"
                   class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
            <select v-model="newLevel" class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm">
              <option :value="1">替换（***）</option>
              <option :value="2">拦截（拒绝发送）</option>
              <option :value="3">警告（仅记录）</option>
            </select>
            <select v-model="newCategory" class="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm">
              <option value="insult">辱骂</option>
              <option value="ad">广告</option>
              <option value="spam">垃圾</option>
            </select>
          </div>
          <div class="px-5 pb-5 pt-2 flex justify-end gap-2">
            <button class="px-4 py-2 text-sm text-gray-600 hover:bg-gray-50 rounded" @click="showAdd = false">取消</button>
            <button class="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600" @click="addWord">添加</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { useChatStore } from '@/stores/chat'
import AppHeader from '@/components/layout/AppHeader.vue'

const chatStore = useChatStore()
const words = ref([])
const levelFilter = ref('')
const categoryFilter = ref('')
const showAdd = ref(false)
const newWord = ref('')
const newLevel = ref(1)
const newCategory = ref('insult')

async function loadWords() {
  try {
    const res = await adminApi.getSensitiveWords({
      page: 1, size: 100,
      level: levelFilter.value || undefined,
      category: categoryFilter.value || undefined
    })
    if (res.data.code === 200) {
      words.value = res.data.data.items
    }
  } catch (e) { /* 忽略 */ }
}

async function addWord() {
  if (!newWord.value.trim()) return
  try {
    const res = await adminApi.addSensitiveWord({
      word: newWord.value.trim(),
      level: newLevel.value,
      category: newCategory.value
    })
    if (res.data.code === 200) {
      chatStore.addNotification({ type: 'success', message: '敏感词已添加' })
      showAdd.value = false
      newWord.value = ''
      loadWords()
    } else {
      chatStore.addNotification({ type: 'error', message: res.data.message })
    }
  } catch (e) {
    chatStore.addNotification({ type: 'error', message: e.response?.data?.message || '添加失败' })
  }
}

async function deleteWord(word) {
  if (!confirm(`确定删除敏感词「${word.word}」吗？`)) return
  try {
    await adminApi.deleteSensitiveWord(word.id)
    loadWords()
  } catch (e) {
    alert(e.response?.data?.message || '删除失败')
  }
}

async function refreshWords() {
  try {
    const res = await adminApi.refreshSensitiveWords()
    if (res.data.code === 200) {
      chatStore.addNotification({ type: 'success', message: `词库已刷新，共 ${res.data.data.count} 个敏感词` })
    }
  } catch (e) {
    chatStore.addNotification({ type: 'error', message: '刷新失败' })
  }
}

function levelName(level) {
  return { 1: '替换', 2: '拦截', 3: '警告' }[level] || '未知'
}
function categoryName(cat) {
  return { insult: '辱骂', ad: '广告', spam: '垃圾' }[cat] || '其他'
}

onMounted(loadWords)
</script>
