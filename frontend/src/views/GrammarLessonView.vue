<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Right } from '@element-plus/icons-vue'
import GrammarExerciseList from '@/components/grammar/GrammarExerciseList.vue'
import { grammarApi } from '@/api'
import { completeTaskByRef } from '@/composables/useTaskCompletion'
import { renderMarkdown } from '@/utils/markdown'
import { errorMessage } from '@/utils/error'
import type { AttemptResult, AttemptsResponse, GrammarLesson, GrammarTopic } from '@/types/api'

const PASS_SCORE = 80

const route = useRoute()
const router = useRouter()
const lesson = ref<GrammarLesson | null>(null)
const loading = ref(true)
const error = ref('')
const generating = ref(false)
const generationError = ref('')
const submittingPractice = ref(false)
const submittingBoss = ref(false)
const practiceResult = ref<AttemptsResponse | null>(null)
const bossResult = ref<AttemptsResponse | null>(null)
const practiceAnswers = reactive<Record<string, string>>({})
const bossAnswers = reactive<Record<string, string>>({})
const bossStarted = ref(false)
let generationAttemptedFor = ''

const lessonHtml = computed(() => renderMarkdown(lesson.value?.contentMd ?? ''))

const practiceAnswered = computed(() => {
  if (!lesson.value) return 0
  return lesson.value.practice.filter((exercise) => Boolean(practiceAnswers[exercise.id])).length
})
const allPracticeAnswered = computed(
  () => Boolean(lesson.value) && practiceAnswered.value === (lesson.value?.practice.length ?? 0),
)
const bossAnswered = computed(() => {
  if (!lesson.value) return 0
  return lesson.value.boss.filter((exercise) => Boolean(bossAnswers[exercise.id])).length
})
const allBossAnswered = computed(
  () => Boolean(lesson.value) && bossAnswered.value === (lesson.value?.boss.length ?? 0),
)

const practiceResults = computed(() => toResultMap(practiceResult.value))
const bossResults = computed(() => toResultMap(bossResult.value))
const bossRequiredCorrect = computed(() =>
  lesson.value ? Math.ceil((lesson.value.boss.length * PASS_SCORE) / 100) : 0,
)
const accuracy = computed(() => {
  const history = lesson.value?.history
  if (!history || history.attempts === 0) return 0
  return Math.round((history.correct / history.attempts) * 100)
})
const resultHeading = computed(() => {
  const result = bossResult.value
  if (!result) return ''
  if (result.passed) return 'Boss 通过，节点已掌握。'
  if (result.status === 'MASTERED') return '本次未达标，但节点此前已掌握。'
  return '还没过线，先把错题弄懂。'
})
const resultMessage = computed(() => {
  const result = bossResult.value
  if (!result) return ''
  if (result.passed) {
    return result.nextSlug
      ? '下一关已经解锁，也可以过几天回来复习。'
      : '这个节点已经掌握，可以回到地图继续或复习。'
  }
  if (result.status === 'MASTERED') return '已掌握的节点不会因为一次低分回退。'
  return `本关需要 ${PASS_SCORE} 分（答对 ${bossRequiredCorrect.value} / ${lesson.value?.boss.length ?? 0} 题）。`
})

function toResultMap(response: AttemptsResponse | null): Record<string, AttemptResult> {
  const map: Record<string, AttemptResult> = {}
  response?.results.forEach((item) => {
    map[item.exerciseId] = item
  })
  return map
}

function clearAnswers(record: Record<string, string>) {
  Object.keys(record).forEach((key) => delete record[key])
}

function resetAttempts() {
  practiceResult.value = null
  bossResult.value = null
  bossStarted.value = false
  clearAnswers(practiceAnswers)
  clearAnswers(bossAnswers)
}

async function load(slug: string) {
  loading.value = true
  error.value = ''
  generationError.value = ''
  resetAttempts()
  try {
    lesson.value = await grammarApi.lesson(slug)
  } catch (err) {
    error.value = errorMessage(err, '加载课程失败')
    lesson.value = null
  } finally {
    loading.value = false
  }
  if (lesson.value) {
    void maybeGenerate(slug)
  }
}

async function maybeGenerate(slug: string) {
  const current = lesson.value
  if (!current || !current.pool.aiConfigured || !current.pool.generationNeeded) return
  if (generating.value || generationAttemptedFor === slug) return
  await generatePool(slug)
}

async function generatePool(slug: string) {
  if (generating.value) return
  generationAttemptedFor = slug
  generating.value = true
  generationError.value = ''
  try {
    await grammarApi.generate(slug)
    lesson.value = await grammarApi.lesson(slug)
    if (lesson.value.pool.generationNeeded) {
      generationError.value = 'AI 只生成了部分题目，可以重试补齐。'
    }
  } catch (err) {
    generationError.value = errorMessage(err, 'AI 出题失败')
  } finally {
    generating.value = false
  }
}

function selectPractice(exerciseId: string, option: string) {
  practiceAnswers[exerciseId] = option
}

function selectBoss(exerciseId: string, option: string) {
  bossAnswers[exerciseId] = option
}

function applyTopic(response: AttemptsResponse) {
  const current = lesson.value
  if (!current) return
  current.topic.mastery = response.mastery
  current.topic.status = response.status as GrammarTopic['status']
  current.topic.completed = response.status === 'MASTERED'
  current.history.attempts += response.total
  current.history.correct += response.correct
  current.history.lastPracticedAt = new Date().toISOString()
  const freshWrong = response.results
    .filter((item) => !item.correct)
    .map((item) => ({
      exerciseId: item.exerciseId,
      prompt: item.prompt,
      yourAnswer: item.yourAnswer,
      correctAnswer: item.correctAnswer,
    }))
  if (freshWrong.length) {
    const freshIds = new Set(freshWrong.map((item) => item.exerciseId))
    current.history.recentWrong = [
      ...freshWrong,
      ...current.history.recentWrong.filter((item) => !freshIds.has(item.exerciseId)),
    ].slice(0, 5)
  }
  if (response.mode === 'BOSS') {
    current.history.bossScore = response.bossScore
  }
}

async function submitPractice() {
  if (!lesson.value) return
  if (!allPracticeAnswered.value) {
    ElMessage.warning('先把练习全部选完')
    return
  }
  submittingPractice.value = true
  try {
    const response = await grammarApi.submit(
      lesson.value.topic.slug,
      'PRACTICE',
      lesson.value.practice.map((exercise) => ({
        exerciseId: exercise.id,
        answer: practiceAnswers[exercise.id],
      })),
    )
    practiceResult.value = response
    applyTopic(response)
    ElMessage.success(`练习完成：答对 ${response.correct} / ${response.total} 题`)
    await completeTaskByRef('GRAMMAR', lesson.value.topic.id)
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交失败'))
  } finally {
    submittingPractice.value = false
  }
}

async function submitBoss() {
  if (!lesson.value) return
  if (!allBossAnswered.value) {
    ElMessage.warning('先把 Boss 题目全部选完')
    return
  }
  submittingBoss.value = true
  try {
    const response = await grammarApi.submit(
      lesson.value.topic.slug,
      'BOSS',
      lesson.value.boss.map((exercise) => ({
        exerciseId: exercise.id,
        answer: bossAnswers[exercise.id],
      })),
    )
    bossResult.value = response
    applyTopic(response)
    ElMessage({
      type: response.passed ? 'success' : 'warning',
      message: response.passed
        ? `Boss 通过：${response.correct} / ${response.total} 题`
        : `Boss 未通过：${response.correct} / ${response.total} 题，需 ${PASS_SCORE} 分`,
    })
    setTimeout(() => {
      document.getElementById('boss-result')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 60)
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交失败'))
  } finally {
    submittingBoss.value = false
  }
}

function startBoss() {
  bossStarted.value = true
  setTimeout(() => {
    document.getElementById('boss-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 40)
}

function retryBoss() {
  bossResult.value = null
  clearAnswers(bossAnswers)
  bossStarted.value = true
  setTimeout(() => {
    document.getElementById('boss-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 40)
}

function retryPractice() {
  if (lesson.value) {
    void load(lesson.value.topic.slug)
  }
}

function next() {
  const slug = bossResult.value?.nextSlug
  if (slug) {
    router.push(`/grammar/${slug}`)
  } else {
    router.push('/grammar')
  }
}

watch(
  () => route.params.slug as string,
  (slug) => {
    if (slug) {
      generationAttemptedFor = ''
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
            <span class="pill">{{ lesson.topic.status === 'MASTERED' ? '已掌握' : '学习节点' }}</span>
            <span class="pill amber">{{ lesson.practice.length }} 题练习 · {{ lesson.boss.length }} 题 Boss</span>
          </div>
          <h1>{{ lesson.topic.title }}</h1>
          <p>{{ lesson.topic.summary }}</p>
        </div>
        <div class="mastery-box">
          <span>当前掌握度</span>
          <strong>{{ Math.round(lesson.topic.mastery) }}<small>%</small></strong>
          <div class="mastery-rule">Boss {{ PASS_SCORE }} 分通过即掌握，并解锁下一关</div>
        </div>
      </header>

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

          <section v-if="lesson.history.attempts > 0" class="card side-card history-card">
            <p class="eyebrow">YOUR RECORD</p>
            <h2>练习记录</h2>
            <div class="history-stats">
              <span><strong>{{ lesson.history.attempts }}</strong> 题</span>
              <span>正确率 <strong>{{ accuracy }}%</strong></span>
              <span v-if="lesson.history.bossScore != null">
                Boss 最高 <strong>{{ lesson.history.bossScore }}</strong>
              </span>
            </div>
            <div v-if="lesson.history.recentWrong.length" class="wrong-list">
              <p class="wrong-title">最近错题</p>
              <div v-for="wrong in lesson.history.recentWrong" :key="wrong.exerciseId" class="wrong-item">
                <p>{{ wrong.prompt }}</p>
                <span>你选：{{ wrong.yourAnswer }} · 正确：{{ wrong.correctAnswer }}</span>
              </div>
            </div>
          </section>

          <section class="card side-card tip-card">
            <p class="eyebrow">HOW TO PRACTICE</p>
            <p>
              先读规则和例句，再做练习：练习会给即时解析，Boss 提交后才判分。
              错题比全对更有信息量。
            </p>
          </section>
        </aside>
      </div>

      <section class="exercise-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">PRACTICE SET</p>
            <h2 class="section-title">先练一组，再进 Boss</h2>
            <p class="section-sub">
              识别 → 形式 → 使用。提交后逐题给出正确答案和每个选项的解析。
              <span v-if="!lesson.pool.aiConfigured">当前为内置题库，配置 AI 后会自动扩充。</span>
            </p>
          </div>
          <span class="answered">{{ practiceAnswered }} / {{ lesson.practice.length }} 已作答</span>
        </div>

        <div v-if="generating" class="card ai-state">
          <div>
            <strong>AI 正在为这个节点补充题目</strong>
            <p>会生成练习和 Boss 题各 5 道，通常需要 20–40 秒。上面的规则和例句可以先读。</p>
          </div>
          <el-skeleton animated :rows="4" />
        </div>

        <template v-else>
          <div v-if="generationError" class="card ai-error">
            <div>
              <strong>AI 题目暂未补齐</strong>
              <p>{{ generationError }} 先用现有题目练习；也可以刷新页面后自动重试。</p>
            </div>
            <el-button
              v-if="practiceAnswered === 0 && !practiceResult"
              size="small"
              @click="lesson && generatePool(lesson.topic.slug)"
            >
              重试生成
            </el-button>
          </div>

          <GrammarExerciseList
            :exercises="lesson.practice"
            :answers="practiceAnswers"
            :results="practiceResults"
            :disabled="Boolean(practiceResult)"
            @select="selectPractice"
          />

          <div v-if="!practiceResult" class="submit-bar">
            <div>
              <strong>{{ allPracticeAnswered ? '可以提交了' : '还有题目没有作答' }}</strong>
              <span>
                {{ allPracticeAnswered ? '提交后会立即看到逐题解析。' : '先选一个你觉得最自然的答案。' }}
              </span>
            </div>
            <el-button
              type="primary"
              size="large"
              :loading="submittingPractice"
              :disabled="!allPracticeAnswered"
              @click="submitPractice"
            >
              提交练习
            </el-button>
          </div>

          <div v-else class="practice-done card">
            <div>
              <strong>练习完成：{{ practiceResult.correct }} / {{ practiceResult.total }} 题</strong>
              <span>正确率 {{ Math.round(practiceResult.score) }}%。先看错题解析，再进 Boss。</span>
            </div>
            <div class="practice-done-actions">
              <el-button @click="retryPractice">再练一组</el-button>
              <el-button type="primary" @click="startBoss" :disabled="bossStarted">
                进入 Boss
              </el-button>
            </div>
          </div>
        </template>
      </section>

      <section v-if="practiceResult" id="boss-section" class="exercise-section boss-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">BOSS CHECK</p>
            <h2 class="section-title">独立题池 · 混合挑战</h2>
            <p class="section-sub">
              {{ lesson.boss.length }} 道题，答对 {{ bossRequiredCorrect }} 道通过（{{ PASS_SCORE }} 分）。
              <span v-if="lesson.pool.bossSource === 'SEED'">
                当前使用 3 道内置题，需全部答对；生成更多题目后会扩为 5 题。
              </span>
            </p>
          </div>
          <span class="answered">{{ bossAnswered }} / {{ lesson.boss.length }} 已作答</span>
        </div>

        <div v-if="!bossStarted" class="card boss-intro">
          <div>
            <strong>准备好了再开始</strong>
            <p>Boss 没有即时提示，提交后统一判分。没通过可以重做，也可以先回去再练一组。</p>
          </div>
          <el-button type="primary" @click="startBoss">开始 Boss</el-button>
        </div>

        <template v-else>
          <GrammarExerciseList
            :exercises="lesson.boss"
            :answers="bossAnswers"
            :results="bossResults"
            :disabled="Boolean(bossResult)"
            @select="selectBoss"
          />

          <div v-if="!bossResult" class="submit-bar">
            <div>
              <strong>{{ allBossAnswered ? '可以提交 Boss 了' : '还有 Boss 题目没有作答' }}</strong>
              <span>{{ allBossAnswered ? '提交后统一判分。' : 'Boss 没有提示，按自己的判断选。' }}</span>
            </div>
            <el-button
              type="primary"
              size="large"
              :loading="submittingBoss"
              :disabled="!allBossAnswered"
              @click="submitBoss"
            >
              提交 Boss
            </el-button>
          </div>

          <div v-else id="boss-result" class="result-panel" :class="{ passed: bossResult.passed }">
            <div class="result-score">
              <span>{{ bossResult.passed ? 'BOSS PASSED' : 'BOSS CHECK' }}</span>
              <strong>{{ bossResult.score }}</strong>
              <small>掌握度 {{ bossResult.mastery }}%</small>
            </div>
            <div class="result-copy">
              <h3>{{ resultHeading }}</h3>
              <p>
                你答对了 {{ bossResult.correct }} / {{ bossResult.total }} 题。
                {{ resultMessage }}
              </p>
            </div>
            <div class="result-actions">
              <el-button @click="router.push('/grammar')">回到地图</el-button>
              <el-button v-if="!bossResult.passed" plain @click="retryPractice">再练一组</el-button>
              <el-button v-if="!bossResult.passed" @click="retryBoss">再挑战一次</el-button>
              <el-button v-if="bossResult.nextSlug" type="primary" @click="next">
                下一关 <el-icon class="el-icon--right"><Right /></el-icon>
              </el-button>
            </div>
          </div>
        </template>
      </section>
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

.mastery-box {
  flex: 0 0 190px;
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

.history-card {
  background: var(--surface);
}

.history-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 11px;
}

.history-stats strong {
  color: var(--ink);
  font-family: var(--font-mono);
  font-weight: 600;
}

.wrong-list {
  margin-top: 16px;
  padding-top: 13px;
  border-top: 1px solid var(--line);
}

.wrong-title {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

.wrong-item {
  margin-top: 10px;
}

.wrong-item p {
  display: -webkit-box;
  overflow: hidden;
  color: var(--ink);
  font-size: 11px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.wrong-item span {
  display: block;
  margin-top: 3px;
  color: var(--faint);
  font-size: 9.5px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.tip-card {
  background: var(--surface-2);
}

.tip-card > p:last-child {
  margin-top: 8px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.75;
}

.exercise-section {
  margin-top: 34px;
}

.boss-section {
  padding-top: 22px;
  border-top: 1px dashed var(--line);
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
}

.section-title {
  margin-top: 4px;
  font-family: var(--font-reading);
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.025em;
}

.section-sub {
  max-width: 680px;
  margin-top: 5px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}

.section-sub span {
  color: var(--faint);
}

.answered {
  flex: 0 0 auto;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
}

.ai-state {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.ai-state strong {
  font-size: 12px;
}

.ai-state p {
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
}

.ai-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 14px 18px;
  border-color: var(--amber-line);
  background: var(--amber-soft);
}

.ai-error strong {
  color: var(--amber-text);
  font-size: 12px;
}

.ai-error p {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.practice-done {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 18px;
  padding: 18px 22px;
}

.practice-done strong {
  display: block;
  font-size: 12px;
}

.practice-done span {
  display: block;
  margin-top: 3px;
  color: var(--faint);
  font-size: 10px;
}

.practice-done-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 7px;
}

.boss-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 22px;
}

.boss-intro strong {
  font-size: 12px;
}

.boss-intro p {
  max-width: 620px;
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
}

.submit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 20px;
  padding: 18px 22px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
}

.submit-bar strong {
  display: block;
  font-size: 12px;
}

.submit-bar span {
  display: block;
  margin-top: 2px;
  color: var(--faint);
  font-size: 10px;
}

.result-panel {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) auto;
  gap: 22px;
  align-items: center;
  margin-top: 22px;
  padding: 22px 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--wash-a), var(--wash-b));
}

.result-panel.passed {
  border-color: var(--green-line);
}

.result-score {
  display: grid;
  justify-items: center;
  padding-right: 20px;
  border-right: 1px solid var(--line);
}

.result-score span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;
}

.result-score strong {
  font-family: var(--font-reading);
  font-size: 40px;
  font-weight: 600;
  line-height: 1.1;
}

.result-score small {
  color: var(--green);
  font-size: 9px;
}

.result-copy h3 {
  font-family: var(--font-reading);
  font-size: 21px;
  font-weight: 600;
}

.result-copy p {
  max-width: 560px;
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 7px;
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

  .mastery-box {
    width: 100%;
    text-align: left;
  }

  .lesson-side {
    grid-template-columns: 1fr;
  }

  .section-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .result-panel {
    grid-template-columns: 1fr;
  }

  .result-score {
    justify-items: start;
    padding: 0 0 12px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .result-actions {
    justify-content: flex-start;
  }

  .submit-bar,
  .practice-done,
  .boss-intro,
  .ai-error {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
