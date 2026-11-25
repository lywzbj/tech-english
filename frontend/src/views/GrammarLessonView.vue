<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, Close, Right } from '@element-plus/icons-vue'
import { grammarApi } from '@/api'
import { completeTaskByRef } from '@/composables/useTaskCompletion'
import { renderMarkdown } from '@/utils/markdown'
import { errorMessage } from '@/utils/error'
import type { AttemptsResponse, GrammarLesson } from '@/types/api'

const route = useRoute()
const router = useRouter()
const lesson = ref<GrammarLesson | null>(null)
const loading = ref(true)
const error = ref('')
const submitting = ref(false)
const result = ref<AttemptsResponse | null>(null)
const answers = reactive<Record<string, string>>({})

const lessonHtml = computed(() => renderMarkdown(lesson.value?.contentMd ?? ''))
const answeredCount = computed(() => {
  if (!lesson.value) return 0
  return lesson.value.exercises.filter((exercise) => Boolean(answers[exercise.id])).length
})
const allAnswered = computed(
  () => Boolean(lesson.value) && answeredCount.value === (lesson.value?.exercises.length ?? 0),
)
const resultMap = computed(() => {
  const map = new Map<string, AttemptsResponse['results'][number]>()
  result.value?.results.forEach((item) => map.set(item.exerciseId, item))
  return map
})

async function load(slug: string) {
  loading.value = true
  error.value = ''
  result.value = null
  Object.keys(answers).forEach((key) => delete answers[key])
  try {
    lesson.value = await grammarApi.lesson(slug)
  } catch (err) {
    error.value = errorMessage(err, '加载课程失败')
    lesson.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.slug as string,
  (slug) => {
    if (slug) load(slug)
  },
  { immediate: true },
)

async function submit() {
  if (!lesson.value) return
  if (!allAnswered.value) {
    ElMessage.warning('先把所有题都选上答案')
    return
  }
  submitting.value = true
  try {
    const response = await grammarApi.submit(
      lesson.value.topic.slug,
      lesson.value.exercises.map((exercise) => ({
        exerciseId: exercise.id,
        answer: answers[exercise.id],
      })),
    )
    result.value = response
    lesson.value.topic.mastery = response.mastery
    lesson.value.topic.status = response.status as 'LOCKED' | 'AVAILABLE' | 'IN_PROGRESS' | 'MASTERED'
    lesson.value.topic.completed = response.status === 'MASTERED'
    ElMessage.success(`本次得分 ${response.score}，掌握度 ${response.mastery}%`)
    await completeTaskByRef('GRAMMAR', lesson.value.topic.id)
    setTimeout(() => {
      document.getElementById('result-panel')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 50)
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交失败'))
  } finally {
    submitting.value = false
  }
}

function next() {
  if (result.value?.nextSlug) {
    router.push(`/grammar/${result.value.nextSlug}`)
  } else {
    router.push('/grammar')
  }
}

function optionState(exerciseId: string, option: string) {
  const item = resultMap.value.get(exerciseId)
  if (!item) return ''
  if (option === item.correctAnswer) return 'correct'
  if (answers[exerciseId] === option) return 'wrong'
  return ''
}
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
            <span class="pill amber">{{ lesson.exercises.length }} 道练习</span>
          </div>
          <h1>{{ lesson.topic.title }}</h1>
          <p>{{ lesson.topic.summary }}</p>
        </div>
        <div class="mastery-box">
          <span>当前掌握度</span>
          <strong>{{ Math.round(lesson.topic.mastery) }}<small>%</small></strong>
          <div class="mastery-rule">70% 掌握 + 80% Boss = 解锁下一关</div>
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

          <section class="card side-card tip-card">
            <p class="eyebrow">HOW TO PRACTICE</p>
            <p>
              先读一遍规则，再遮住中文看英文例句。做题时不要查答案；错题比全对更有信息量。
            </p>
          </section>
        </aside>
      </div>

      <section class="exercise-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">BOSS CHECK</p>
            <h2 class="section-title">把规则用起来</h2>
          </div>
          <span class="answered">{{ answeredCount }} / {{ lesson.exercises.length }} 已作答</span>
        </div>

        <div class="exercise-list">
          <article
            v-for="(exercise, index) in lesson.exercises"
            :key="exercise.id"
            class="card exercise"
            :class="{
              correct: resultMap.get(exercise.id)?.correct,
              wrong: resultMap.has(exercise.id) && !resultMap.get(exercise.id)?.correct,
            }"
          >
            <div class="exercise-head">
              <span class="exercise-number">{{ String(index + 1).padStart(2, '0') }}</span>
              <p>{{ exercise.prompt }}</p>
            </div>

            <el-radio-group v-model="answers[exercise.id]" class="option-list" :disabled="Boolean(result)">
              <el-radio
                v-for="option in exercise.options"
                :key="option"
                :value="option"
                class="option"
                :class="optionState(exercise.id, option)"
              >
                {{ option }}
              </el-radio>
            </el-radio-group>

            <div v-if="resultMap.get(exercise.id)" class="answer-feedback">
              <div class="feedback-line">
                <el-icon v-if="resultMap.get(exercise.id)?.correct" class="ok"><Check /></el-icon>
                <el-icon v-else class="no"><Close /></el-icon>
                <strong>{{ resultMap.get(exercise.id)?.correct ? '答对了' : '再记一下' }}</strong>
                <span>正确答案：{{ resultMap.get(exercise.id)?.correctAnswer }}</span>
              </div>
              <p>{{ resultMap.get(exercise.id)?.explanation }}</p>
            </div>
          </article>
        </div>

        <div v-if="result" id="result-panel" class="result-panel">
          <div class="result-score">
            <span>本次得分</span>
            <strong>{{ result.score }}</strong>
            <small>掌握度 {{ result.mastery }}%</small>
          </div>
          <div class="result-copy">
            <h3>{{ result.status === 'MASTERED' ? '节点已掌握，下一关已解锁。' : '先别急着翻页，错题更有价值。' }}</h3>
            <p>
              你答对了 {{ result.correct }} / {{ result.total }} 题。
              {{ result.status === 'MASTERED' ? '继续往前，或者过几天回来复习。' : '回到规则和例句，再提交一次也可以。' }}
            </p>
          </div>
          <div class="result-actions">
            <el-button @click="router.push('/grammar')">回到地图</el-button>
            <el-button v-if="result.status !== 'MASTERED'" type="primary" plain @click="load(lesson.topic.slug)">
              再做一次
            </el-button>
            <el-button v-if="result.nextSlug" type="primary" @click="next">
              下一关 <el-icon class="el-icon--right"><Right /></el-icon>
            </el-button>
          </div>
        </div>

        <div v-if="!result" class="submit-bar">
          <div>
            <strong>{{ allAnswered ? '可以提交了' : '还有题目没有作答' }}</strong>
            <span>{{ allAnswered ? '提交后会立即看到解析和掌握度变化。' : '先选一个你觉得最自然的答案。' }}</span>
          </div>
          <el-button type="primary" size="large" :loading="submitting" :disabled="!allAnswered" @click="submit">
            提交并查看结果
          </el-button>
        </div>
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

.answered {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
}

.exercise-list {
  display: grid;
  gap: 12px;
}

.exercise {
  padding: 22px;
  transition: 180ms ease;
}

.exercise.correct {
  border-color: var(--green-line);
}

.exercise.wrong {
  border-color: var(--rose-line);
}

.exercise-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.exercise-number {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--surface-3);
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.exercise-head p {
  padding-top: 2px;
  color: var(--ink);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
}

.option-list {
  display: grid;
  gap: 7px;
  margin: 14px 0 0 40px;
}

.option {
  width: 100%;
  min-height: 38px;
  margin: 0;
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-2);
  transition: 150ms ease;
}

.option:hover {
  border-color: var(--green-2);
}

.option.correct {
  border-color: var(--green-line);
  background: var(--green-soft);
}

.option.wrong {
  border-color: var(--rose-line);
  background: var(--rose-soft);
}

.answer-feedback {
  margin: 13px 0 0 40px;
  padding: 12px 14px;
  border-radius: 9px;
  background: var(--surface-2);
}

.feedback-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  font-size: 11px;
}

.feedback-line strong {
  color: var(--ink);
}

.feedback-line span {
  color: var(--muted);
}

.feedback-line .ok {
  color: var(--green);
}

.feedback-line .no {
  color: var(--rose);
}

.answer-feedback > p {
  margin-top: 6px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
}

.result-panel {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) auto;
  gap: 22px;
  align-items: center;
  margin-top: 22px;
  padding: 22px 24px;
  border: 1px solid var(--green-line);
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--wash-a), var(--wash-b));
}

.result-score {
  display: grid;
  justify-items: center;
  padding-right: 20px;
  border-right: 1px solid var(--green-line);
}

.result-score span {
  color: var(--muted);
  font-size: 9px;
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

  .result-panel {
    grid-template-columns: 1fr;
  }

  .result-score {
    justify-items: start;
    padding: 0 0 12px;
    border-right: 0;
    border-bottom: 1px solid var(--green-line);
  }

  .result-actions {
    justify-content: flex-start;
  }

  .submit-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .option-list,
  .answer-feedback {
    margin-left: 0;
  }
}
</style>
