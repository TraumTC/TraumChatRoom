// src/router/index.js — 路由配置 + 导航守卫
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: { guest: true }  // 已登录（非游客）用户自动跳聊天室
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guest: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guest: true }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { requiresAuth: true }  // 需要登录（含游客）
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { requiresAuth: true }  // 游客禁止访问
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('@/views/AdminUsersView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/sensitive-words',
    name: 'AdminSensitiveWords',
    component: () => import('@/views/AdminSensitiveWordsView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/logs',
    name: 'AdminLogs',
    component: () => import('@/views/AdminLogsView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导航守卫：路由跳转前检查权限
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  // 有 Token 但没有用户信息（页面刷新）→ 先获取用户信息
  if (authStore.isAuthenticated && !authStore.user) {
    await authStore.fetchUser()
  }

  // 需要登录但未登录 → 跳转登录页
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/')
    return
  }

  // 需要管理员但不是管理员 → 返回首页
  if (to.meta.requiresAdmin && authStore.user?.role !== 'ROLE_ADMIN') {
    next('/')
    return
  }

  // 游客禁止访问个人中心
  if (to.path === '/profile' && authStore.isGuest) {
    next('/chat')
    return
  }

  // 已登录用户不能访问登录/注册页（游客可以，用于"回到首页"）
  if (to.meta.guest && authStore.isAuthenticated && !authStore.isGuest) {
    next('/chat')
    return
  }

  next()
})

export default router
