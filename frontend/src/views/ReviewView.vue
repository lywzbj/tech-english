<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Refresh, View } from '@element-plus/icons-vue'
import { vocabularyApi } from '@/api'
import { completeTaskByRef } from '@/composables/useTaskCompletion'
import { errorMessage } from '@/utils/error'
import type { VocabCard } from '@/types/api'

const router = useRouter()
const cards = ref<VocabCard[]>([])
const index = ref(0)
const revealed = ref(false)
const loading = ref(true)
const reviewing = ref(false)
const finished = ref(false)
const completed = ref(0)
const initialDue = ref(0)
const error = ref('')

const current = computed(() => cards.value[index.value] ?? null)
const progress = computed(() => {
  if (cards.value.length === 0) return 0
  return Math.round((index.value / cards.value.length) * 100)
})
const ratingData = [
  { rating: 0, label: '忘记', hint: '1', className: 'again' },
  { rating: 1, label: '模糊', hint: '2', className: 'hard' },
  { rating: 2, label: '认识', hint: '3', className: 'good' },
  { rating: 3, label: '简单', hint: '4', className: 'easy' },
]

async function load() {
  loading.value = true
  error.value = ''
  finished.value = false
  index.value = 0
  completed.value = 0
  revealed.value = false
  try {
    const response = await vocabularyApi.due(20)
    cards.value = response.cards
    initialDue.value = response.dueCount
    if (cards.value.length === 0) finished.value = true
  } catch (err) {
    error.value = errorMessage(err, '加载复习队列失败')
  } finally {
    loading.value = false
  }
}

function reveal() {
  if (current.value) revealed.value = true
}

async function rate(rating: number) {
  if (!current.value || !revealed.value || reviewing.value) return
  reviewing.value = true
  try {
    await vocabularyApi.review(current.value.userVocabId, rating)
    completed.value++
    index.value++
    revealed.value = false
    if (index.value >= cards.value.length) {
      finished.value = true
      if (initialDue.value - completed.value <= 0) {
        await completeTaskByRef('REVIEW')
      }
    }
  } catch (err) {
    ElMessage.error(errorMessage(err, '保存复习结果失败'))
  } finally {
    reviewing.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if (finished.value) return
  if (event.code === 'Space') {
    event.preventDefault()
    if (!revealed.value) reveal()
    return
  }
  if (!revealed.value) return
  const map: Record<string, number> = { Digit1: 0, Digit2: 1, Digit3: 2, Digit4: 3 }
  if (event.code in map) {
    event.preventDefault()
    rate(map[event.code])
  }
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="page review-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">SPACED REPETITION</p>
        <h1 class="page-title">让词汇按时回来</h1>
        <p class="page-subtitle">
          先回忆，再看答案。评分不是考试，而是告诉系统下一次什么时候再出现。
        </p>
      </div>
      <div class="due-badge">
        <span>今日到期</span>
        <strong>{{ initialDue }}</strong>
        <small>个词</small>
      </div>
    </header>

    <div v-if="loading" class="card card-pad">
      <el-skeleton animated :rows="6" />
    </div>
    <div v-else-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="load">重试</el-button>
    </div>

    <template v-else>
      <div class="review-progress card">
        <div class="progress-copy">
          <span>{{ finished ? '本次完成' : `第 ${index + 1} 张` }}</span>
          <strong>{{ completed }} / {{ cards.length }}</strong>
        </div>
        <el-progress :percentage="progress" :show-text="false" :stroke-width="7" />
        <span class="keyboard-hint">Space 显示答案 · 1-4 评分</span>
      </div>

      <div v-if="finished" class="finish card">
        <div class="finish-mark">✓</div>
        <p class="eyebrow">QUEUE CLEAR</p>
        <h2>{{ cards.length ? '这一组复习完成了。' : '今天没有到期卡片。' }}</h2>
        <p>
          {{
            cards.length
              ? `你完成了 ${completed} 张卡片。下一批到期时间已经按你的评分重新排好。`
              : '可以去阅读实验室积累新词，或者继续语法闯关。'
          }}
        </p>
        <div class="btn-row finish-actions">
          <el-button type="primary" @click="load">
            <el-icon><Refresh /></el-icon> 再来一组
          </el-button>
          <el-button @click="router.push('/reading')">去阅读</el-button>
          <el-button @click="router.push('/grammar')">去语法</el-button>
        </div>
      </div>

      <div v-else-if="current" class="flash-stage">
        <div class="flash-card" :class="{ revealed }">
          <div class="flash-top">
            <span class="pill">{{ current.status }}</span>
            <span class="flash-count">{{ current.intervalDays === 0 ? '新词' : `间隔 ${current.intervalDays} 天` }}</span>
          </div>

          <div class="flash-word">
            <h2>{{ current.lemma }}</h2>
            <p v-if="current.phonetic" class="phonetic">{{ current.phonetic }}</p>
            <span class="pos">{{ current.pos }}</span>
          </div>

          <div v-if="current.contextSentence" class="context">
            <span class="context-label">FROM YOUR READING</span>
            <p>{{ current.contextSentence }}</p>
          </div>

          <div v-if="!revealed" class="reveal-area">
            <p>先回忆它的意思，再翻开。</p>
            <el-button type="primary" size="large" round @click="reveal">
              <el-icon><View /></el-icon> 显示答案
            </el-button>
          </div>

          <div v-else class="answer-area">
            <div class="answer-main">
              <span class="answer-label">MEANING</span>
              <h3>{{ current.definitionZh || '暂无释义' }}</h3>
              <p v-if="current.definitionEn" class="definition-en">{{ current.definitionEn }}</p>
            </div>
            <div v-if="current.exampleEn" class="example-block">
              <span class="answer-label">EXAMPLE</span>
              <p class="example-en">{{ current.exampleEn }}</p>
              <p class="example-zh">{{ current.exampleZh }}</p>
            </div>
            <div v-if="current.contextSentence" class="example-block">
              <span class="answer-label">CONTEXT</span>
              <p class="example-en">{{ current.contextSentence }}</p>
            </div>
          </div>
        </div>

        <div v-if="revealed" class="rating-panel">
          <p>这张卡片对你来说有多熟？</p>
          <div class="rating-buttons">
            <button
              v-for="item in ratingData"
              :key="item.rating"
              type="button"
              class="rating-button"
              :class="item.className"
              :disabled="reviewing"
              @click="rate(item.rating)"
            >
              <span>{{ item.hint }}</span>
              <strong>{{ item.label }}</strong>
            </button>
          </div>
        </div>
        <div v-else class="rating-placeholder">
          <span>评分快捷键</span>
          <div class="ghost-ratings">
            <span>1 忘记</span><span>2 模糊</span><span>3 认识</span><span>4 简单</span>
          </div>
        </div>
      </div>

      <div v-if="finished && cards.length" class="next-note">
        <span>下一组还有 {{ Math.max(0, initialDue - completed) }} 个到期词。</span>
        <button type="button" @click="load">
          继续 <el-icon><ArrowRight /></el-icon>
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.due-badge {
  display: flex;
  align-items: baseline;
  gap: 5px;
  padding: 8px 14px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--muted);
  font-size: 10px;
  white-space: nowrap;
}

.due-badge strong {
  color: var(--green);
  font-family: var(--font-reading);
  font-size: 22px;
  font-weight: 600;
}

.review-progress {
  display: grid;
  grid-template-columns: 120px 1fr auto;
  gap: 20px;
  align-items: center;
  margin-bottom: 20px;
  padding: 15px 18px;
}

.progress-copy {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: var(--muted);
  font-size: 10px;
}

.progress-copy strong {
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 11px;
}

.keyboard-hint {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
  white-space: nowrap;
}

.flash-stage {
  max-width: 760px;
  margin: 0 auto;
}

.flash-card {
  position: relative;
  min-height: 410px;
  padding: 30px clamp(24px, 4vw, 48px);
  border: 1px solid var(--line);
  border-radius: 24px;
  background:
    radial-gradient(circle at 96% 0%, var(--glow-b), transparent 28%),
    var(--surface);
  box-shadow: var(--shadow-md);
  transition: 220ms ease;
}

.flash-card.revealed {
  min-height: 500px;
}

.flash-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.flash-count {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.flash-word {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 54px;
}

.flash-word h2 {
  font-family: var(--font-reading);
  font-size: clamp(46px, 7vw, 78px);
  font-weight: 500;
  letter-spacing: -0.05em;
  line-height: 1;
}

.phonetic {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 13px;
}

.pos {
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
}

.context {
  margin-top: 34px;
  padding: 13px 16px;
  border-left: 2px solid var(--green-2);
  border-radius: 0 9px 9px 0;
  background: var(--surface-2);
}

.context-label,
.answer-label {
  display: block;
  color: var(--faint);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.context p {
  margin-top: 4px;
  color: var(--ink-soft);
  font-family: var(--font-reading);
  font-size: 17px;
  line-height: 1.55;
}

.reveal-area {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 44px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.reveal-area p {
  color: var(--faint);
  font-size: 11px;
}

.answer-area {
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
  animation: rise-in 0.35s ease backwards;
}

.answer-main h3 {
  margin-top: 4px;
  font-family: var(--font-reading);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.definition-en {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.65;
}

.example-block {
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
}

.example-en {
  margin-top: 4px;
  color: var(--ink);
  font-family: var(--font-reading);
  font-size: 18px;
  line-height: 1.5;
}

.example-zh {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
}

.rating-panel {
  margin-top: 18px;
  text-align: center;
}

.rating-panel > p {
  color: var(--muted);
  font-size: 11px;
}

.rating-buttons {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-top: 11px;
}

.rating-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 11px 8px;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: var(--surface);
  color: var(--ink-soft);
  cursor: pointer;
  transition: 150ms ease;
}

.rating-button:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.rating-button span {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
}

.rating-button strong {
  font-size: 11px;
}

.rating-button.again:hover {
  border-color: var(--rose-line);
  color: var(--rose);
}

.rating-button.hard:hover {
  border-color: var(--amber-line);
  color: var(--amber-text);
}

.rating-button.good:hover {
  border-color: var(--green-line);
  color: var(--green);
}

.rating-button.easy:hover {
  border-color: var(--blue-line);
  color: var(--blue);
}

.rating-button:disabled {
  opacity: 0.5;
  cursor: wait;
}

.rating-placeholder {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  color: var(--faint);
  font-size: 9px;
}

.ghost-ratings {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-family: var(--font-mono);
  font-size: 8px;
}

.finish {
  max-width: 680px;
  margin: 0 auto;
  padding: 50px 34px;
  text-align: center;
}

.finish-mark {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin: 0 auto 20px;
  border-radius: 18px;
  background: var(--green-3);
  color: var(--green);
  font-size: 24px;
   font-weight: 700;
}

.finish h2 {
  margin-top: 6px;
  font-family: var(--font-reading);
  font-size: 34px;
  font-weight: 500;
  letter-spacing: -0.03em;
}

.finish > p:not(.eyebrow) {
  max-width: 470px;
  margin: 10px auto 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.75;
}

.finish-actions {
  justify-content: center;
  margin-top: 24px;
}

.next-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 760px;
  margin: 18px auto 0;
  color: var(--faint);
  font-size: 10px;
}

.next-note button {
  display: flex;
  align-items: center;
  gap: 3px;
  border: 0;
  background: transparent;
  color: var(--green);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 640px) {
  .review-progress {
    grid-template-columns: 1fr;
    gap: 9px;
  }

  .keyboard-hint {
    display: none;
  }

  .flash-word {
    margin-top: 34px;
  }

  .rating-buttons {
    grid-template-columns: 1fr 1fr;
  }

  .reveal-area,
  .rating-placeholder {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
