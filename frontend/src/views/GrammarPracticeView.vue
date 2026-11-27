<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, List, Right } from '@element-plus/icons-vue'
import GrammarAttemptDrawer from '@/components/grammar/GrammarAttemptDrawer.vue'
import GrammarExerciseList from '@/components/grammar/GrammarExerciseList.vue'
import { grammarApi } from '@/api'
import { completeTaskByRef } from '@/composables/useTaskCompletion'
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
// Proficient learners see the practice set hidden by default; this is the escape hatch.
const showPracticeAnyway = ref(false)
const attemptDrawerOpen = ref(false)
// Question chosen in the attempt map: the main area switches to its single-question view.
const focusedExerciseId = ref('')
let generationAttemptedFor = ''

const practiceAnswered = computed(() => {
  if (!lesson.value) return 0
  return lesson.value.practice.filter((exercise) => Boolean(practiceAnswers[exercise.id]?.trim())).length
})
const allPracticeAnswered = computed(
  () => Boolean(lesson.value) && practiceAnswered.value === (lesson.value?.practice.length ?? 0),
)
const bossAnswered = computed(() => {
  if (!lesson.value) return 0
  return lesson.value.boss.filter((exercise) => Boolean(bossAnswers[exercise.id]?.trim())).length
})
const allBossAnswered = computed(
  () => Boolean(lesson.value) && bossAnswered.value === (lesson.value?.boss.length ?? 0),
)

const practiceResults = computed(() => toResultMap(practiceResult.value))
const bossResults = computed(() => toResultMap(bossResult.value))
const masteredNode = computed(() => lesson.value?.topic.status === 'MASTERED')
// Server-side proficiency signal: mastered, or every practice-pool question has
// been answered correctly at least once. Proficient learners skip straight to boss.
const recommendBoss = computed(() => Boolean(lesson.value?.recommendBoss))
const bossWrongCount = computed(
  () => lesson.value?.boss.filter((exercise) => exercise.wrongReview).length ?? 0,
)
// With a well-drilled pool the fixed practice set only re-reveals known answers,
// so it stays off the page until the learner explicitly asks for it.
const practiceListShown = computed(() => {
  const current = lesson.value
  if (!current || !current.practice.length) return false
  return !recommendBoss.value || showPracticeAnyway.value
})
const drawerSections = computed(() => {
  const current = lesson.value
  if (!current) return []
  const sections: {
    key: string
    label: string
    exercises: GrammarLesson['practice']
    answers: Record<string, string>
    results: Record<string, AttemptResult>
  }[] = []
  if (practiceListShown.value || practiceResult.value) {
    sections.push({
      key: 'practice',
      label: '练习组',
      exercises: current.practice,
      answers: practiceAnswers,
      results: practiceResults.value,
    })
  }
  if (bossStarted.value) {
    sections.push({
      key: 'boss',
      label: 'Boss 卷',
      exercises: current.boss,
      answers: bossAnswers,
      results: bossResults.value,
    })
  }
  return sections
})
const hasAttemptLog = computed(() => drawerSections.value.length > 0)
// 侧边入口角标：优先提示错题数，判分前提示已作答数。
const attemptTabBadge = computed(() => {
  let wrong = 0
  let answered = 0
  for (const section of drawerSections.value) {
    for (const exercise of section.exercises) {
      const result = section.results[exercise.id]
      if (result) {
        if (!result.correct) wrong += 1
      } else if (section.answers[exercise.id]?.trim()) {
        answered += 1
      }
    }
  }
  if (wrong > 0) return { count: wrong, wrong: true }
  return answered > 0 ? { count: answered, wrong: false } : null
})
// Mastered nodes open the boss section immediately: a review pass should not
// force the practice set first (which would only re-reveal answers).
const bossUnlocked = computed(() => Boolean(practiceResult.value) || masteredNode.value || recommendBoss.value)
const bossRequiredCorrect = computed(() =>
  lesson.value ? Math.ceil((lesson.value.boss.length * PASS_SCORE) / 100) : 0,
)
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
  showPracticeAnyway.value = false
  attemptDrawerOpen.value = false
  focusedExerciseId.value = ''
  clearAnswers(practiceAnswers)
  clearAnswers(bossAnswers)
}

function firstWrongExerciseId() {
  for (const section of drawerSections.value) {
    const wrong = section.exercises.find((exercise) => {
      const result = section.results[exercise.id]
      return result && !result.correct
    })
    if (wrong) return wrong.id
  }
  return ''
}

// 做题区域逐题展示，不再整卷滚动：显式选中的题优先，判分前默认聚焦本组第一题。
function resolveFocus(
  exercises: GrammarLesson['practice'],
  fallbackToFirst: boolean,
): { exercise: GrammarLesson['practice'][number]; index: number } | null {
  if (!exercises.length) return null
  const explicit = exercises.findIndex((exercise) => exercise.id === focusedExerciseId.value)
  if (explicit >= 0) return { exercise: exercises[explicit], index: explicit }
  return fallbackToFirst ? { exercise: exercises[0], index: 0 } : null
}

const focusPractice = computed(() => {
  const current = lesson.value
  if (!current || (!practiceListShown.value && !practiceResult.value)) return null
  return resolveFocus(current.practice, practiceListShown.value && !practiceResult.value)
})
const focusBoss = computed(() => {
  const current = lesson.value
  if (!current || !bossStarted.value) return null
  return resolveFocus(current.boss, !bossResult.value)
})
// 地图高亮当前左侧正在展示的那道题（默认聚焦时 focusedExerciseId 为空）。
const activeExerciseId = computed(
  () =>
    focusedExerciseId.value ||
    focusPractice.value?.exercise.id ||
    focusBoss.value?.exercise.id ||
    '',
)

function focusExercise(exerciseId: string) {
  focusedExerciseId.value = exerciseId
  void nextTick(() => {
    setTimeout(() => {
      const inPractice = lesson.value?.practice.some((exercise) => exercise.id === exerciseId)
      document
        .getElementById(inPractice ? 'question-focus-practice' : 'question-focus-boss')
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 40)
  })
}

function stepFocus(exercises: GrammarLesson['practice'], index: number, delta: number) {
  const next = exercises[index + delta]
  if (next) focusExercise(next.id)
}

function openAttemptDrawer(focusFirstWrong = false) {
  attemptDrawerOpen.value = true
  if (focusFirstWrong) {
    const id = firstWrongExerciseId()
    if (id) focusExercise(id)
  }
}

async function load(slug: string, enterBossDirectly = true) {
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
    // Proficient learners should not sit through the fixed practice set again:
    // open the boss immediately (its paper now leads with their wrong answers).
    // An explicit "再练一组" opts out of this for the reload.
    if (enterBossDirectly && lesson.value.recommendBoss) {
      startBoss()
    }
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
    const refreshed = await grammarApi.lesson(slug)
    const current = lesson.value
    // Never yank the paper out from under typed answers: refresh pool metadata only
    // and keep the questions on screen (the old ids stay valid in the grading universe).
    if (current && (Object.keys(practiceAnswers).length > 0 || Object.keys(bossAnswers).length > 0)) {
      current.pool = refreshed.pool
    } else {
      lesson.value = refreshed
    }
    if ((lesson.value ?? refreshed).pool.generationNeeded) {
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
        answer: (practiceAnswers[exercise.id] ?? '').trim(),
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
        answer: (bossAnswers[exercise.id] ?? '').trim(),
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

async function retryBoss() {
  if (!lesson.value) return
  bossResult.value = null
  clearAnswers(bossAnswers)
  try {
    // Server rotates the paper away from the just-revealed set; a retake must
    // not be the same questions the user just saw answers for.
    const draw = await grammarApi.bossDraw(lesson.value.topic.slug)
    lesson.value.boss = draw.exercises
    lesson.value.pool.bossSource = draw.bossSource
  } catch (err) {
    ElMessage.error(errorMessage(err, '换题失败，请刷新页面重试'))
    return
  }
  bossStarted.value = true
  setTimeout(() => {
    document.getElementById('boss-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 40)
}

function retryPractice() {
  if (lesson.value) {
    void load(lesson.value.topic.slug, false)
  }
}

function goLesson() {
  if (lesson.value) {
    router.push(`/grammar/${lesson.value.topic.slug}`)
  } else {
    router.push('/grammar')
  }
}

function next() {
  const slug = bossResult.value?.nextSlug
  if (slug) {
    router.push(`/grammar/${slug}/practice`)
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
  <div class="page practice-page">
    <div class="practice-topbar">
      <button type="button" class="back-link" @click="goLesson">
        <el-icon><ArrowLeft /></el-icon> 返回讲义
      </button>
      <button v-if="lesson" type="button" class="link-btn" @click="router.push('/grammar')">
        语法地图
      </button>
    </div>

    <div v-if="loading" class="card card-pad">
      <el-skeleton animated :rows="10" />
    </div>
    <div v-else-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="goLesson">回到讲义</el-button>
    </div>

    <template v-else-if="lesson">
      <header class="practice-head">
        <div>
          <div class="practice-tags">
            <span class="pill green">{{ lesson.topic.cefrLevel }}</span>
            <span class="pill">{{ lesson.topic.status === 'MASTERED' ? '复习模式' : '闯关练习' }}</span>
            <span class="pill amber">{{ lesson.practice.length }} 题练习 · {{ lesson.boss.length }} 题 Boss</span>
          </div>
          <h1>{{ lesson.topic.title }} · 做题</h1>
          <p v-if="masteredNode">
            该节点已掌握：可以直接重打 Boss 复习，成绩只升不降。
          </p>
          <p v-else-if="recommendBoss">
            练习池的题目你都已经答对过了：已为你直接开放 Boss，本卷优先带上之前的错题。
          </p>
        </div>
        <div class="mastery-box">
          <span>当前掌握度</span>
          <strong>{{ Math.round(lesson.topic.mastery) }}<small>%</small></strong>
          <div class="mastery-rule">
            {{ masteredNode ? '重打 Boss 不会回退掌握度' : `练习即时解析，Boss ${PASS_SCORE} 分通过即掌握` }}
          </div>
        </div>
      </header>

      <section class="exercise-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">PRACTICE SET</p>
            <h2 class="section-title">
              {{
                recommendBoss && !showPracticeAnyway
                  ? '基础练习已按熟练度收起'
                  : recommendBoss
                    ? '查缺补漏（可直接跳过）'
                    : '先练一组，再进 Boss'
              }}
            </h2>
            <p class="section-sub">
              识别 → 形式 → 使用。选择题逐题给出答案和选项解析；改写 / 翻译 / 造句是输出题，提交后由 AI 批改并给出反馈。
              <span v-if="!lesson.pool.aiConfigured">当前为内置题库（只有选择题），配置 AI 后会自动扩充。</span>
            </p>
          </div>
          <span v-if="practiceListShown && !practiceResult" class="answered">
            {{ practiceAnswered }} / {{ lesson.practice.length }} 已作答
          </span>
        </div>

        <div v-if="generating" class="card ai-state">
          <div>
            <strong>AI 正在为这个节点补充题目</strong>
            <p>会生成练习和 Boss 题各 5 道，通常需要 20–40 秒。可以先回讲义页复习规则和例句。</p>
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

          <div
            v-if="!lesson.practice.length && !lesson.boss.length"
            class="card empty-pool"
          >
            <div>
              <strong>这个节点还没有题目</strong>
              <p>
                新添加的知识点需要先由 AI 生成题库。配置好 AI 后回到本页会自动生成；
                也可以回讲义页补充讲义内容。
              </p>
            </div>
            <el-button v-if="lesson.pool.aiConfigured" @click="lesson && generatePool(lesson.topic.slug)">
              立即生成题目
            </el-button>
          </div>

          <template v-else>
            <div
              v-if="recommendBoss && !showPracticeAnyway && !practiceResult"
              class="card boss-suggest"
            >
              <div>
                <strong>熟练度提示：这一组不用再练，已为你收起</strong>
                <p>
                  练习池的题目你都已经答对过，答案你也见过了，重复练价值不大。
                  Boss 已经开放，本卷优先带上你之前的错题。
                </p>
              </div>
              <div class="boss-suggest-actions">
                <el-button plain size="small" @click="showPracticeAnyway = true">
                  仍然练这一组
                </el-button>
                <el-button type="primary" plain @click="startBoss">去做 Boss</el-button>
              </div>
            </div>

            <div v-if="focusPractice" id="question-focus-practice" class="focus-wrap">
              <div class="focus-bar card">
                <div class="focus-bar-side">
                  <el-button
                    v-if="practiceResult && focusedExerciseId"
                    plain
                    size="small"
                    @click="focusedExerciseId = ''"
                  >
                    返回小结
                  </el-button>
                </div>
                <div class="focus-bar-nav">
                  <el-button
                    plain
                    size="small"
                    :disabled="focusPractice.index === 0"
                    @click="stepFocus(lesson.practice, focusPractice.index, -1)"
                  >
                    上一题
                  </el-button>
                  <span>第 {{ focusPractice.index + 1 }} 题 · 共 {{ lesson.practice.length }} 题</span>
                  <el-button
                    plain
                    size="small"
                    :disabled="focusPractice.index >= lesson.practice.length - 1"
                    @click="stepFocus(lesson.practice, focusPractice.index, 1)"
                  >
                    下一题
                  </el-button>
                </div>
              </div>
              <GrammarExerciseList
                :exercises="[focusPractice.exercise]"
                :answers="practiceAnswers"
                :results="practiceResults"
                :disabled="Boolean(practiceResult)"
                @select="selectPractice"
              />
            </div>

            <div v-if="practiceListShown && !practiceResult" class="submit-bar">
              <div>
                <strong>{{ allPracticeAnswered ? '可以提交了' : '还有题目没有作答' }}</strong>
                <span>
                  {{
                    allPracticeAnswered
                      ? '提交后会立即看到逐题解析；主观题由 AI 批改，可能需要多等几秒。'
                      : '选择题选答案，改写 / 翻译 / 造句题把句子写进输入框。'
                  }}
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

            <div v-else-if="practiceResult && !focusPractice" class="practice-done card">
              <div>
                <strong>练习完成：{{ practiceResult.correct }} / {{ practiceResult.total }} 题</strong>
                <span>
                  正确率 {{ Math.round(practiceResult.score) }}%。打开右侧「做题情况」地图可逐题回看解析。
                </span>
              </div>
              <div class="practice-done-actions">
                <el-button plain @click="openAttemptDrawer(true)">
                  {{ firstWrongExerciseId() ? '看错题解析' : '逐题解析' }}
                </el-button>
                <el-button @click="retryPractice">再练一组</el-button>
                <el-button type="primary" @click="startBoss" :disabled="bossStarted">
                  进入 Boss
                </el-button>
              </div>
            </div>
          </template>
        </template>
      </section>

      <section v-if="bossUnlocked" id="boss-section" class="exercise-section boss-section">
        <div class="section-head">
          <div>
            <p class="eyebrow">BOSS CHECK</p>
            <h2 class="section-title">独立题池 · 混合挑战</h2>
            <p class="section-sub">
              {{ lesson.boss.length }} 道题，答对 {{ bossRequiredCorrect }} 道通过（{{ PASS_SCORE }} 分）。
              <span v-if="lesson.pool.bossSource === 'SEED'">
                当前使用 3 道内置题，需全部答对；AI 补齐更多题目后会扩为 5 题。
              </span>
              <span v-else>换题再战优先出新题，上一轮公布过答案的题不会再出现；做错过的题会被带回重练。</span>
              <span v-if="bossWrongCount">本卷已优先带上 {{ bossWrongCount }} 道错题重练。</span>
            </p>
          </div>
          <span v-if="bossStarted && !bossResult" class="answered">
            {{ bossAnswered }} / {{ lesson.boss.length }} 已作答
          </span>
        </div>

        <div v-if="!bossStarted" class="card boss-intro">
          <div>
            <strong>准备好了再开始</strong>
            <p>
              Boss 没有即时提示，提交后统一判分。没通过会换一批新题再战，
              也可以先回去再练一组。
            </p>
          </div>
          <el-button type="primary" @click="startBoss">开始 Boss</el-button>
        </div>

        <template v-else>
          <div v-if="focusBoss" id="question-focus-boss" class="focus-wrap">
            <div class="focus-bar card">
              <div class="focus-bar-side">
                <el-button
                  v-if="bossResult && focusedExerciseId"
                  plain
                  size="small"
                  @click="focusedExerciseId = ''"
                >
                  返回结算
                </el-button>
              </div>
              <div class="focus-bar-nav">
                <el-button
                  plain
                  size="small"
                  :disabled="focusBoss.index === 0"
                  @click="stepFocus(lesson.boss, focusBoss.index, -1)"
                >
                  上一题
                </el-button>
                <span>第 {{ focusBoss.index + 1 }} 题 · 共 {{ lesson.boss.length }} 题</span>
                <el-button
                  plain
                  size="small"
                  :disabled="focusBoss.index >= lesson.boss.length - 1"
                  @click="stepFocus(lesson.boss, focusBoss.index, 1)"
                >
                  下一题
                </el-button>
              </div>
            </div>
            <GrammarExerciseList
              :exercises="[focusBoss.exercise]"
              :answers="bossAnswers"
              :results="bossResults"
              :disabled="Boolean(bossResult)"
              @select="selectBoss"
            />
          </div>

          <div v-if="!bossResult" class="submit-bar">
            <div>
              <strong>{{ allBossAnswered ? '可以提交 Boss 了' : '还有 Boss 题目没有作答' }}</strong>
              <span>{{ allBossAnswered ? '提交后统一判分，主观题由 AI 批改。' : 'Boss 没有提示，选择题凭判断选，主观题直接写句子。' }}</span>
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

          <div v-else-if="!focusBoss" id="boss-result" class="result-panel" :class="{ passed: bossResult.passed }">
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
              <el-button plain @click="openAttemptDrawer(true)">
                {{ firstWrongExerciseId() ? '看错题解析' : '逐题解析' }}
              </el-button>
              <el-button @click="router.push('/grammar')">回到地图</el-button>
              <el-button plain @click="goLesson">回讲义</el-button>
              <el-button v-if="!bossResult.passed" plain @click="retryPractice">再练一组</el-button>
              <el-button @click="retryBoss">换题再战</el-button>
              <el-button v-if="bossResult.nextSlug" type="primary" @click="next">
                下一关 <el-icon class="el-icon--right"><Right /></el-icon>
              </el-button>
            </div>
          </div>
        </template>
      </section>

      <!-- 页面入场动画的 transform 会把 fixed 的包含块从视口改成祖先，侧边入口必须 Teleport 到 body。 -->
      <Teleport to="body">
        <div v-if="hasAttemptLog && !attemptDrawerOpen" class="side-tabs">
          <button
            type="button"
            class="side-tab"
            title="查看做题情况"
            @click="openAttemptDrawer()"
          >
            <el-icon><List /></el-icon>
            <span>做题</span>
            <span v-if="attemptTabBadge" class="tab-count" :class="{ wrong: attemptTabBadge.wrong }">
              {{ attemptTabBadge.count }}
            </span>
          </button>
        </div>
      </Teleport>

      <GrammarAttemptDrawer
        v-model="attemptDrawerOpen"
        :sections="drawerSections"
        :active-exercise-id="activeExerciseId"
        @select="focusExercise"
      />
    </template>
  </div>
</template>

<style scoped>
.practice-page {
  max-width: 920px;
  margin: 0 auto;
}

.practice-topbar {
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

/* 侧边入口：贴视口右缘，跟随做题情况抽屉的开合（与阅读详情页的目录按钮一致）。 */
.side-tabs {
  position: fixed;
  top: 50%;
  right: 0;
  z-index: 80;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transform: translateY(-50%);
}

.side-tab {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 10px;
  border: 1px solid var(--line);
  border-right: 0;
  border-radius: 10px 0 0 10px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.side-tab:hover,
.side-tab.active {
  border-color: var(--green-line);
  color: var(--green);
}

.tab-count {
  position: absolute;
  top: -7px;
  right: -7px;
  display: grid;
  place-items: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--green);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
}

.tab-count.wrong {
  background: var(--rose);
}

.boss-suggest-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

/* 单题聚焦视图：抽屉地图里点中的题目在左侧展示。 */
.focus-wrap {
  display: grid;
  gap: 12px;
}

.focus-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 16px;
}

.focus-bar span {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
}

/* 左侧空槽保证「返回小结」出现/消失时导航不跳动。 */
.focus-bar-side {
  display: flex;
  align-items: center;
  min-width: 88px;
}

.focus-bar-nav {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.practice-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  margin-bottom: 26px;
}

.practice-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: 12px;
}

.practice-head h1 {
  font-family: var(--font-reading);
  font-size: clamp(26px, 3.4vw, 38px);
  font-weight: 500;
  letter-spacing: -0.04em;
  line-height: 1.1;
}

.practice-head > div > p {
  max-width: 620px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 12px;
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

.exercise-section {
  margin-top: 10px;
}

.boss-section {
  margin-top: 34px;
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

.boss-suggest {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 14px 18px;
  border-color: var(--green-line);
  background: var(--green-3);
}

.boss-suggest strong {
  color: var(--green);
  font-size: 12px;
}

.boss-suggest p {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.empty-pool {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 22px;
}

.empty-pool strong {
  font-size: 12px;
}

.empty-pool p {
  max-width: 560px;
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
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

@media (max-width: 760px) {
  .practice-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .mastery-box {
    width: 100%;
    text-align: left;
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
  .ai-error,
  .boss-suggest,
  .empty-pool {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
