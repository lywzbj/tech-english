<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Calendar,
  Collection,
  DataAnalysis,
  Notebook,
  Reading,
  Search,
  Setting,
  SwitchButton,
  Trophy,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits<{ navigate: [] }>()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const primary = [
  { name: 'dashboard', label: '今日学习', icon: DataAnalysis, to: '/' },
  { name: 'plan', label: '学习计划', icon: Calendar, to: '/plan' },
  { name: 'grammar', label: '语法闯关', icon: Trophy, to: '/grammar' },
  { name: 'review', label: '到期复习', icon: Collection, to: '/review' },
  { name: 'reading', label: '阅读实验室', icon: Reading, to: '/reading' },
  { name: 'vocabulary', label: '我的词汇', icon: Notebook, to: '/vocabulary' },
  { name: 'search', label: '知识搜索', icon: Search, to: '/search' },
]

const initials = computed(() => auth.displayName.slice(0, 1).toUpperCase())
const target = computed(() => auth.profile?.targetBand?.toFixed(1) ?? '—')

function active(name: string) {
  if (name === 'grammar') return route.name === 'grammar' || route.name === 'grammar-lesson'
  if (name === 'reading') return route.name === 'reading' || route.name === 'reading-detail'
  return route.name === name
}

function go(to: string) {
  emit('navigate')
  if (route.path !== to) router.push(to)
}

async function logout() {
  await auth.logout()
  emit('navigate')
  router.replace('/login')
}
</script>

<template>
  <div class="nav-shell">
    <button class="brand" type="button" @click="go('/')">
      <span class="brand-mark">TE</span>
      <span class="brand-copy">
        <strong>Tech English</strong>
        <small>AI learning workspace</small>
      </span>
    </button>

    <div class="nav-scroll">
      <p class="nav-label">LEARN</p>
      <nav class="nav-list">
        <button
          v-for="item in primary"
          :key="item.name"
          type="button"
          class="nav-item"
          :class="{ active: active(item.name) }"
          @click="go(item.to)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <span v-if="item.name === 'grammar'" class="nav-mark">PATH</span>
        </button>
      </nav>

      <p class="nav-label second">ACCOUNT</p>
      <nav class="nav-list">
        <button
          type="button"
          class="nav-item"
          :class="{ active: route.name === 'settings' }"
          @click="go('/settings')"
        >
          <el-icon><Setting /></el-icon>
          <span>设置与目标</span>
        </button>
      </nav>
    </div>

    <div class="account">
      <div class="account-main">
        <span class="avatar">{{ initials }}</span>
        <div class="account-copy">
          <strong>{{ auth.displayName }}</strong>
          <small>目标 IELTS {{ target }}</small>
        </div>
      </div>
      <button type="button" class="logout" title="退出登录" @click="logout">
        <el-icon><SwitchButton /></el-icon>
      </button>
    </div>
  </div>
</template>

<style scoped>
.nav-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 22px 16px 18px;
  color: #eef5ef;
  background:
    radial-gradient(circle at 20% 0%, rgb(89 137 108 / 35%), transparent 42%),
    linear-gradient(168deg, #1b3528 0%, #162a20 60%, #12231b 100%);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 0 8px 24px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 12px;
  background: rgb(255 255 255 / 9%);
  font-family: var(--font-reading);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.brand-copy {
  display: grid;
  gap: 1px;
}

.brand-copy strong {
  font-family: var(--font-reading);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.brand-copy small {
  color: rgb(238 245 239 / 52%);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.nav-scroll {
  flex: 1;
  overflow-y: auto;
}

.nav-label {
  margin: 10px 10px 8px;
  color: rgb(238 245 239 / 38%);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.nav-label.second {
  margin-top: 28px;
}

.nav-list {
  display: grid;
  gap: 3px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 42px;
  padding: 8px 11px;
  border: 1px solid transparent;
  border-radius: 11px;
  background: transparent;
  color: rgb(238 245 239 / 68%);
  font-size: 13px;
  cursor: pointer;
  transition: 160ms ease;
  text-align: left;
}

.nav-item .el-icon {
  font-size: 16px;
}

.nav-item:hover {
  background: rgb(255 255 255 / 6%);
  color: #fff;
}

.nav-item.active {
  border-color: rgb(255 255 255 / 10%);
  background: rgb(255 255 255 / 11%);
  color: #fff;
  box-shadow: inset 2px 0 0 #b8d8c3;
}

.nav-mark {
  margin-left: auto;
  padding: 2px 5px;
  border-radius: 4px;
  background: rgb(184 216 195 / 13%);
  color: #b8d8c3;
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.account {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  padding: 12px 8px 0;
  border-top: 1px solid rgb(255 255 255 / 9%);
}

.account-main {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  flex: 1;
}

.avatar {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: #d5e7db;
  color: #1e4c38;
  font-size: 13px;
  font-weight: 800;
}

.account-copy {
  display: grid;
  min-width: 0;
  gap: 1px;
}

.account-copy strong {
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-copy small {
  color: rgb(238 245 239 / 45%);
  font-size: 10px;
}

.logout {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 8px;
  background: transparent;
  color: rgb(238 245 239 / 55%);
  cursor: pointer;
}

.logout:hover {
  background: rgb(255 255 255 / 8%);
  color: #fff;
}
</style>
