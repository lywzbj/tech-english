<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { Menu } from '@element-plus/icons-vue'
import SidebarNav from '@/components/common/SidebarNav.vue'
import ThemeSwitcher from '@/components/common/ThemeSwitcher.vue'
import { useAuthStore } from '@/stores/auth'

dayjs.locale('zh-cn')

const route = useRoute()
const auth = useAuthStore()
const mobileOpen = ref(false)
const topbarEl = ref<HTMLElement | null>(null)

// 固定抽屉（ContentDrawer）锚定在顶栏下缘。顶栏真实高度会随内容/断点变化，
// min-height 只是下限，所以挂载与窗口尺寸变化时把测量值写进 --topbar-height。
// ResizeObserver 为主，window resize 兜底：部分嵌入式 webview 中 RO 不随视口缩放触发。
function syncTopbarHeight() {
  const el = topbarEl.value
  if (el) document.documentElement.style.setProperty('--topbar-height', `${el.getBoundingClientRect().height}px`)
}
let topbarObserver: ResizeObserver | null = null
onMounted(() => {
  syncTopbarHeight()
  topbarObserver = new ResizeObserver(syncTopbarHeight)
  const el = topbarEl.value
  if (el) topbarObserver.observe(el)
  window.addEventListener('resize', syncTopbarHeight)
})
onBeforeUnmount(() => {
  topbarObserver?.disconnect()
  window.removeEventListener('resize', syncTopbarHeight)
})

const pageTitle = computed(() => (route.meta.title as string | undefined) ?? '今日学习')
const today = computed(() => dayjs().format('MM月DD日 dddd'))
const target = computed(() =>
  auth.profile?.targetBand != null ? `IELTS ${auth.profile.targetBand.toFixed(1)}` : '设定目标',
)
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <SidebarNav />
    </aside>

    <el-drawer
      v-model="mobileOpen"
      direction="ltr"
      size="280px"
      :with-header="false"
      class="mobile-drawer"
    >
      <SidebarNav @navigate="mobileOpen = false" />
    </el-drawer>

    <div class="main">
      <header ref="topbarEl" class="topbar">
        <div class="topbar-left">
          <button class="menu-button" type="button" @click="mobileOpen = true">
            <el-icon><Menu /></el-icon>
          </button>
          <div>
            <p class="topbar-kicker">TECH ENGLISH</p>
            <h1 class="topbar-title">{{ pageTitle }}</h1>
          </div>
        </div>
        <div class="topbar-right">
          <ThemeSwitcher />
          <span class="date">{{ today }}</span>
          <span class="target">{{ target }}</span>
          <span class="avatar">{{ auth.displayName.slice(0, 1).toUpperCase() }}</span>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  position: sticky;
  top: 0;
  flex: 0 0 var(--sidebar-width);
  width: var(--sidebar-width);
  height: 100vh;
}

.main {
  min-width: 0;
  flex: 1;
}

.topbar {
  position: sticky;
  z-index: 20;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 80px;
  padding: 14px 34px;
  border-bottom: 1px solid var(--header-line);
  background: var(--header-bg);
  backdrop-filter: blur(18px);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar-kicker {
  color: var(--faint);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.2em;
}

.topbar-title {
  margin-top: 1px;
  font-family: var(--font-reading);
  font-size: 23px;
  font-weight: 600;
  letter-spacing: -0.025em;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.date {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}

.target {
  padding: 5px 10px;
  border: 1px solid var(--green-line);
  border-radius: 999px;
  background: var(--green-soft);
  color: var(--green);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.avatar {
  display: none;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--green-solid);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.menu-button {
  display: none;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  color: var(--ink);
  cursor: pointer;
}

.content {
  width: 100%;
  max-width: 1320px;
  margin: 0 auto;
  padding: 32px 34px 60px;
}

.mobile-drawer :deep(.el-drawer__body) {
  padding: 0;
}

@media (max-width: 1100px) {
  .content {
    padding-inline: 24px;
  }
}

@media (max-width: 900px) {
  .sidebar {
    display: none;
  }

  .menu-button,
  .avatar {
    display: grid;
  }

  .topbar {
    min-height: 70px;
    padding: 12px 18px;
  }

  .topbar-right .date,
  .topbar-right .target {
    display: none;
  }

  .content {
    padding: 22px 18px 50px;
  }
}
</style>
