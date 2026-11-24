import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/setup',
      name: 'setup',
      component: () => import('@/views/SetupView.vue'),
      meta: { public: true, title: '创建账号' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/onboarding',
      name: 'onboarding',
      component: () => import('@/views/OnboardingView.vue'),
      meta: { title: '设定目标' },
    },
    {
      path: '/',
      component: () => import('@/layouts/AppShell.vue'),
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '今日学习' },
        },
        {
          path: 'plan',
          name: 'plan',
          component: () => import('@/views/PlanView.vue'),
          meta: { title: '学习计划' },
        },
        {
          path: 'grammar',
          name: 'grammar',
          component: () => import('@/views/GrammarPathView.vue'),
          meta: { title: '语法闯关' },
        },
        {
          path: 'grammar/:slug',
          name: 'grammar-lesson',
          component: () => import('@/views/GrammarLessonView.vue'),
          meta: { title: '语法课' },
        },
        {
          path: 'review',
          name: 'review',
          component: () => import('@/views/ReviewView.vue'),
          meta: { title: '到期复习' },
        },
        {
          path: 'vocabulary',
          name: 'vocabulary',
          component: () => import('@/views/VocabularyView.vue'),
          meta: { title: '词汇本' },
        },
        {
          path: 'reading',
          name: 'reading',
          component: () => import('@/views/ReadingListView.vue'),
          meta: { title: '阅读实验室' },
        },
        {
          path: 'reading/:id',
          name: 'reading-detail',
          component: () => import('@/views/ReadingDetailView.vue'),
          meta: { title: '阅读' },
        },
        {
          path: 'search',
          name: 'search',
          component: () => import('@/views/SearchView.vue'),
          meta: { title: '知识搜索' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/SettingsView.vue'),
          meta: { title: '设置' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.bootstrapped) {
    await auth.bootstrap()
  }

  if (to.meta.public) {
    if (auth.setupRequired && to.name !== 'setup') {
      return { name: 'setup' }
    }
    if (!auth.setupRequired && to.name === 'setup') {
      return auth.isAuthenticated
        ? { name: auth.isOnboarded ? 'dashboard' : 'onboarding' }
        : { name: 'login' }
    }
    if (auth.isAuthenticated && (to.name === 'login' || to.name === 'setup')) {
      return { name: auth.isOnboarded ? 'dashboard' : 'onboarding' }
    }
    return true
  }

  if (auth.setupRequired) {
    return { name: 'setup' }
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!auth.isOnboarded && to.name !== 'onboarding') {
    return { name: 'onboarding' }
  }
  if (auth.isOnboarded && to.name === 'onboarding') {
    return { name: 'dashboard' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · Tech English` : 'Tech English'
})

export default router
