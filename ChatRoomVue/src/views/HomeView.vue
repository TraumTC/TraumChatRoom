<!-- src/views/HomeView.vue — 首页 -->
<template>
  <div class="min-h-screen bg-white flex items-center justify-center">
    <div class="text-center">
      <h1 class="text-4xl font-bold text-gray-900 mb-4">TraumChatRoom</h1>
      <p class="text-gray-500 mb-8">实时聊天室 · 好友 · AI 助手</p>
      <div class="flex gap-4 justify-center">
        <RouterLink to="/login" class="px-6 py-2 bg-blue-500 text-white rounded hover:bg-blue-600">登录</RouterLink>
        <RouterLink to="/register" class="px-6 py-2 bg-white border border-gray-200 text-gray-700 rounded hover:bg-gray-50">注册</RouterLink>
        <RouterLink to="/chat" class="px-6 py-2 bg-white border border-gray-200 text-gray-700 rounded hover:bg-gray-50">游客进入</RouterLink>
      </div>
    </div>
  </div>
</template>
