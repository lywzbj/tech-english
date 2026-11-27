<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Right } from '@element-plus/icons-vue'
import { grammarApi } from '@/api'
import { renderMarkdown } from '@/utils/markdown'
import { errorMessage } from '@/utils/error'
import type { GrammarLesson } from '@/types/api'

const PASS_SCORE = 80

const route = useRoute()
const router = useRouter()
const lesson = ref<GrammarLesson | null>(null)
const loading = ref(true)
const error = ref('')

const lessonHtml = computed(() => renderMarkdown(lesson.value?.contentMd ?? ''))
const masteredNode = computed(() => lesson.value?.topic.status === 'MASTERED')
// Proficiency signal from the server: practice pool consumed — the practice page
// will skip straight to the boss paper (wrong answers first).
const recommendBoss = computed(() => Boolean(lesson.value?.recommendBoss))

const accuracy = computed(() => {
  const history = lesson.value?.history
  if (!history || history.attempts === 0) return 0
  return Math.round((history.correct / history.attempts) * 100)
})

const lastPracticedText = computed(() => {
  const raw = lesson.value?.history.lastPracticedAt
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })
})

const nextReviewText = computed(() => {
  const raw = lesson.value?.topic.nextReviewAt
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })
})

async function load(slug: string) {
  loading.value = true
  error.value = ''
  try {
    lesson.value = await grammarApi.lesson(slug)
  } catch (err) {
    error.value = errorMessage(err, '加载课程失败')
    lesson.value = null
  } finally {
    loading.value = false
  }
}

function goPractice() {
  if (lesson.value) {
    router.push(`/grammar/${lesson.value.topic.slug}/practice`)
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
  <div class="page lesson-page">
    <button type="button" class="back-link" @click="router.push('/grammar')">
      <el-icon><ArrowLeft /></el-icon> 返回语法地图
    </button>

    <div v-if="loading" class="card card-pad">
      <el-skeleton animated :rows="10" />
    </div>
    <div v-else-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="router.push('/grammar')">回到地图</el-button>
    </div>

    <template v-else-if="lesson">
      <header class="lesson-head">
        <div>
          <div class="lesson-tags">
            <span class="pill green">{{ lesson.topic.cefrLevel }}</span>
            <span class="pill">{{ masteredNode ? '已掌握' : '学习节点' }}</span>
            <span v-if="lesson.topic.source && lesson.topic.source !== 'SEED'" class="pill amber">
              自定义
            </span>
          </div>
          <h1>{{ lesson.topic.title }}</h1>
          <p>{{ lesson.topic.summary }}</p>
        </div>
        <div class="head-side">
          <div class="mastery-box">
            <span>当前掌握度</span>
            <strong>{{ Math.round(lesson.topic.mastery) }}<small>%</small></strong>
            <div class="mastery-rule">
              {{
                masteredNode
                  ? '复习模式：成绩只升不降'
                  : recommendBoss
                    ? '练习池已刷完：进页后直接挑战 Boss，错题优先'
                    : `Boss ${PASS_SCORE} 分通过即掌握，并解锁下一关`
              }}
            </div>
          </div>
          <el-button type="primary" size="large" class="cta-button" @click="goPractice">
            开始做题
            <span class="cta-count">{{ lesson.practice.length }} 练习 · {{ lesson.boss.length }} Boss</span>
            <el-icon class="el-icon--right"><Right /></el-icon>
          </el-button>
          <button
            v-if="lesson.history.recentWrong.length"
            type="button"
            class="wrong-entry"
            @click="router.push(`/grammar/${lesson.topic.slug}/wrong`)"
          >
            最近错题 {{ lesson.history.recentWrong.length }} 道 →
          </button>
        </div>
      </header>

      <section class="status-strip" :class="{ 'status-strip--empty': lesson.history.attempts === 0 }">
        <template v-if="lesson.history.attempts > 0">
          <div class="status-tile">
            <span>累计做题</span>
            <strong>{{ lesson.history.attempts }}</strong>
          </div>
          <div class="status-tile">
            <span>正确率</span>
            <strong>{{ accuracy }}%</strong>
          </div>
          <div class="status-tile" v-if="lesson.history.bossScore != null">
            <span>Boss 最高</span>
            <strong>{{ lesson.history.bossScore }}</strong>
          </div>
          <div class="status-tile" v-if="lastPracticedText">
            <span>最近练习</span>
            <strong>{{ lastPracticedText }}</strong>
          </div>
          <div class="status-tile" v-if="nextReviewText">
            <span>下次复习</span>
            <strong>{{ nextReviewText }}</strong>
          </div>
        </template>
        <p v-else class="status-empty">还没有做题记录，读完讲义后点右上角「开始做题」。</p>
      </section>

      <div class="lesson-layout">
        <article class="card lesson-content">
          <div class="prose" v-html="lessonHtml" />
        </article>

        <aside class="lesson-side">
          <section class="card side-card">
            <p class="eyebrow">IN SOFTWARE CONTEXT</p>
            <h2>技术场景例句</h2>
            <div class="example-list">
              <div v-for="(example, index) in lesson.examples" :key="index" class="example">
                <span class="example-index">{{ String(index + 1).padStart(2, '0') }}</span>
                <p class="example-en">{{ example.sentence }}</p>
                <p class="example-zh">{{ example.translation }}</p>
                <p class="example-note">{{ example.note }}</p>
              </div>
            </div>
          </section>

        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 20px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
}

.back-link:hover {
  color: var(--green);
}

.lesson-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  margin-bottom: 26px;
}

.lesson-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: 12px;
}

.lesson-head h1 {
  font-family: var(--font-reading);
  font-size: clamp(34px, 4vw, 50px);
  font-weight: 500;
  letter-spacing: -0.045em;
  line-height: 1.06;
}

.lesson-head > div > p {
  max-width: 620px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 13px;
}

.head-side {
  display: grid;
  flex: 0 0 auto;
  justify-items: end;
  gap: 12px;
}

.mastery-box {
  width: 190px;
  padding: 15px 17px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  text-align: right;
}

.mastery-box > span {
  color: var(--muted);
  font-size: 10px;
}

.mastery-box strong {
  display: block;
  margin-top: 2px;
  font-family: var(--font-reading);
  font-size: 34px;
  font-weight: 600;
  letter-spacing: -0.03em;
  line-height: 1;
}

.mastery-box small {
  color: var(--muted);
  font-family: var(--font-ui);
  font-size: 12px;
}

.mastery-rule {
  margin-top: 7px;
  color: var(--faint);
  font-size: 8px;
  line-height: 1.5;
}

.cta-button {
  display: inline-flex;
  align-items: center;
}

.cta-count {
  margin-left: 8px;
  opacity: 0.75;
  font-size: 11px;
  font-weight: 400;
}

.lesson-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 0.75fr);
  gap: 20px;
  align-items: start;
}

.lesson-content {
  padding: clamp(24px, 3vw, 40px);
}

.lesson-content :deep(.prose) {
  font-size: 14px;
  line-height: 1.85;
}

.lesson-content :deep(.prose h2) {
  margin-top: 30px;
  font-size: 24px;
}

.lesson-content :deep(.prose h2:first-child) {
  margin-top: 0;
}

.lesson-side {
  display: grid;
  gap: 16px;
  position: sticky;
  top: 100px;
}

.side-card {
  padding: 22px;
}

.side-card h2 {
  margin-top: 4px;
  font-family: var(--font-reading);
  font-size: 22px;
  font-weight: 600;
}

.example-list {
  display: grid;
  gap: 18px;
  margin-top: 18px;
}

.example {
  padding-top: 13px;
  border-top: 1px solid var(--line);
}

.example:first-child {
  padding-top: 0;
  border-top: 0;
}

.example-index {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
}

.example-en {
  margin-top: 4px;
  color: var(--ink);
  font-family: var(--font-reading);
  font-size: 17px;
  line-height: 1.4;
}

.example-zh {
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
}

.example-note {
  margin-top: 5px;
  color: var(--faint);
  font-size: 10px;
  line-height: 1.55;
}

.status-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.status-strip--empty {
  grid-template-columns: 1fr;
}

.status-tile {
  display: grid;
  gap: 4px;
  padding: 12px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
}

.status-tile > span {
  color: var(--muted);
  font-size: 10px;
}

.status-tile > strong {
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 600;
}

.status-empty {
  margin: 0;
  padding: 12px 16px;
  border: 1px dashed var(--line);
  border-radius: 12px;
  color: var(--muted);
  font-size: 11px;
}

.wrong-entry {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
}

.wrong-entry:hover {
  color: var(--rose);
}

@media (min-width: 1001px) {
  .lesson-page {
    display: flex;
    flex-direction: column;
    /* 固定为视口剩余高度：topbar 为 sticky，页面内容 = 100vh − topbar − content 上下内边距 */
    height: calc(100vh - var(--topbar-height, 80px) - 92px);
    min-height: 460px;
  }

  .lesson-head,
  .status-strip,
  .back-link {
    flex: 0 0 auto;
  }

  .lesson-layout {
    flex: 1;
    min-height: 0;
    align-items: stretch;
  }

  .lesson-content {
    min-height: 0;
    overflow-y: auto;
  }

  .lesson-side {
    position: static;
    min-height: 0;
    overflow-y: auto;
    align-content: start;
  }
}

@media (max-width: 1000px) {
  .lesson-layout {
    grid-template-columns: 1fr;
  }

  .lesson-side {
    position: static;
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 760px) {
  .lesson-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .head-side {
    width: 100%;
    justify-items: stretch;
  }

  .mastery-box {
    width: 100%;
    text-align: left;
  }

  .cta-button {
    justify-content: center;
  }

  .lesson-side {
    grid-template-columns: 1fr;
  }
}
</style>
