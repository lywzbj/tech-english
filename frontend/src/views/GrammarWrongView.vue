<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { grammarApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { GrammarLesson } from '@/types/api'

const route = useRoute()
const router = useRouter()
const lesson = ref<GrammarLesson | null>(null)
const loading = ref(true)
const error = ref('')

async function load(slug: string) {
  loading.value = true
  error.value = ''
  try {
    lesson.value = await grammarApi.lesson(slug)
  } catch (err) {
    error.value = errorMessage(err, '加载错题失败')
    lesson.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.slug as string,
  (slug) => {
    if (slug) {
      load(slug)
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="page wrong-page">
    <div class="wrong-topbar">
      <button type="button" class="back-link" @click="router.push(`/grammar/${route.params.slug}`)">
        <el-icon><ArrowLeft /></el-icon> 返回讲义
      </button>
      <button type="button" class="link-btn" @click="router.push('/grammar')">语法地图</button>
    </div>

    <div v-if="loading" class="card card-pad">
      <el-skeleton animated :rows="6" />
    </div>
    <div v-else-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="router.push('/grammar')">回到地图</el-button>
    </div>

    <template v-else-if="lesson">
      <header class="wrong-head">
        <div class="lesson-tags">
          <span class="pill green">{{ lesson.topic.cefrLevel }}</span>
          <span class="pill">{{ lesson.topic.title }}</span>
        </div>
        <h1>最近错题</h1>
        <p v-if="lesson.history.lastPracticedAt">
          最近练习：{{ new Date(lesson.history.lastPracticedAt).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }) }}
          · 正确率 {{ lesson.history.attempts ? Math.round((lesson.history.correct / lesson.history.attempts) * 100) : 0 }}%
        </p>
      </header>

      <div v-if="!lesson.history.recentWrong.length" class="card empty">
        <strong>还没有错题记录</strong>
        <p>做过的练习里答错的题会保留在这里，方便回看。</p>
        <el-button @click="router.push(`/grammar/${lesson.topic.slug}/practice`)">去做题</el-button>
      </div>

      <div v-else class="wrong-list">
        <article v-for="(wrong, index) in lesson.history.recentWrong" :key="wrong.exerciseId" class="card wrong-item">
          <span class="wrong-number">{{ String(index + 1).padStart(2, '0') }}</span>
          <div class="wrong-body">
            <p class="wrong-prompt">{{ wrong.prompt }}</p>
            <div class="wrong-answer">
              <span class="your">你的答案：{{ wrong.yourAnswer }}</span>
              <span class="right">参考答案：{{ wrong.correctAnswer }}</span>
            </div>
          </div>
        </article>
      </div>
    </template>
  </div>
</template>

<style scoped>
.wrong-page {
  max-width: 860px;
  margin: 0 auto;
}

.wrong-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.back-link,
.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
}

.back-link:hover,
.link-btn:hover {
  color: var(--green);
}

.wrong-head {
  margin-bottom: 22px;
}

.lesson-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: 12px;
}

.wrong-head h1 {
  font-family: var(--font-reading);
  font-size: clamp(28px, 3.6vw, 40px);
  font-weight: 500;
  letter-spacing: -0.04em;
  line-height: 1.1;
}

.wrong-head > p {
  margin-top: 8px;
  color: var(--muted);
  font-size: 11px;
}

.wrong-list {
  display: grid;
  gap: 12px;
}

.wrong-item {
  display: flex;
  gap: 14px;
  padding: 20px 22px;
  border-color: var(--rose-line);
}

.wrong-number {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--rose-soft);
  color: var(--rose);
  font-family: var(--font-mono);
  font-size: 9px;
}

.wrong-body {
  min-width: 0;
}

.wrong-prompt {
  color: var(--ink);
  font-size: 13.5px;
  font-weight: 500;
  line-height: 1.6;
}

.wrong-answer {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin-top: 8px;
  font-size: 11px;
}

.wrong-answer .your {
  color: var(--rose);
}

.wrong-answer .right {
  color: var(--green);
}

@media (max-width: 760px) {
  .wrong-item {
    padding: 16px;
  }
}
</style>
