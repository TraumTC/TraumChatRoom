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

// 权限不足时的统一提示。
// 导航守卫可能在全局消息实例（GlobalHooks 挂载的 window.$message）就绪前触发，
// 例如首屏直接深链到 /admin，因此对未就绪的情况做一次延迟兜底。
function notifyDenied(msg) {
  if (typeof window === 'undefined') return
  if (window.$message) {
    window.$message.warning(msg)
  } else {
    setTimeout(() => window.$message?.warning(msg), 300)
  }
}

// 导航守卫：路由跳转前检查权限
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  // 页面刷新后 accessToken 只在内存中，使用 HttpOnly Cookie 静默恢复会话
  await authStore.initialize()

  // 有 Token 但没有用户信息（例如直接刷新 /admin）→ 先获取用户信息
  if (authStore.isAuthenticated && !authStore.user) {
    await authStore.fetchUser()
  }

  // 需要登录但未登录 → 提示并跳转登录页
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    notifyDenied('请先登录后再访问该页面')
    next('/login')
    return
  }

  // 需要管理员权限但非 ROLE_ADMIN → 提示并回到聊天室
  // （能走到这里说明已登录，故重定向到 /chat 而非首页）
  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    notifyDenied('该页面仅管理员可访问，您暂无权限')
    next('/chat')
    return
  }

  // 游客禁止访问个人中心 → 提示并回到聊天室
  if (to.path === '/profile' && authStore.isGuest) {
    notifyDenied('游客无法访问个人中心，请登录后使用')
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
