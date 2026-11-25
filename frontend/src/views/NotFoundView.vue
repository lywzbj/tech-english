<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
</script>

<template>
  <div class="not-found">
    <span class="code">404</span>
    <h1>这一页还没有被写出来。</h1>
    <p>回到今天的学习任务，或者打开语法地图继续。</p>
    <div class="btn-row">
      <el-button type="primary" @click="router.push(auth.isAuthenticated ? '/' : '/login')">
        回到首页
      </el-button>
      <el-button v-if="auth.isAuthenticated" @click="router.push('/grammar')">去语法地图</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: grid;
  place-items: center;
  align-content: center;
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at 50% 20%, var(--glow-b), transparent 34%),
    var(--bg);
  text-align: center;
}

.code {
  color: #c3d5c9;
  font-family: var(--font-reading);
  font-size: clamp(100px, 18vw, 190px);
  line-height: 0.8;
}

h1 {
  margin-top: 20px;
  font-family: var(--font-reading);
  font-size: clamp(28px, 4vw, 42px);
  font-weight: 500;
  letter-spacing: -0.04em;
}

p {
  max-width: 420px;
  margin-top: 9px;
  color: var(--muted);
  font-size: 12px;
}

.btn-row {
  justify-content: center;
  margin-top: 22px;
}
</style>
