<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { ElMessage } from 'element-plus'
import { Check, Collection, Reading, Trophy } from '@element-plus/icons-vue'
import { planApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { Task, WeekPlan } from '@/types/api'

dayjs.locale('zh-cn')

const router = useRouter()
const week = ref<WeekPlan | null>(null)
const loading = ref(true)
const error = ref('')
const completing = ref<number | null>(null)

const percent = computed(() => {
  if (!week.value || week.value.totalMinutes === 0) return 0
  return Math.round((week.value.completedMinutes / week.value.totalMinutes) * 100)
})
const today = computed(() => week.value?.days.find((day) => day.today) ?? null)

function iconFor(task: Task) {
  if (task.type === 'REVIEW') return Collection
  if (task.type === 'GRAMMAR') return Trophy
  if (task.type === 'READING') return Reading
  return Check
}

function dayLabel(date: string) {
  const value = dayjs(date)
  return {
    weekday: value.format('ddd'),
    day: value.format('DD'),
    month: value.format('MMM'),
  }
}

function openTask(task: Task) {
  if (task.type === 'REVIEW') router.push('/review')
  else if (task.type === 'READING' && task.refId) router.push(`/reading/${task.refId}`)
  else router.push('/grammar')
}

async function completeTask(task: Task) {
  if (task.status === 'DONE') return
  completing.value = task.id
  try {
    await planApi.completeTask(task.id)
    week.value = await planApi.week()
    ElMessage.success('任务已完成')
  } catch (err) {
    ElMessage.error(errorMessage(err, '标记失败'))
  } finally {
    completing.value = null
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    week.value = await planApi.week()
  } catch (err) {
    error.value = errorMessage(err, '加载计划失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page plan-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">WEEKLY RHYTHM</p>
        <h1 class="page-title">计划不是一个清单，是节奏</h1>
        <p class="page-subtitle">
          今天先完成到期复习，再推进一个语法节点和一段阅读。错过的任务不会无限堆积。
        </p>
      </div>
      <div class="week-summary card">
        <span>本周完成</span>
        <strong>{{ percent }}%</strong>
        <div class="summary-bar"><span :style="{ width: `${percent}%` }" /></div>
        <small>{{ week?.completedMinutes ?? 0 }} / {{ week?.totalMinutes ?? 0 }} 分钟</small>
      </div>
    </header>

    <div v-if="loading" class="card card-pad">
      <el-skeleton animated :rows="8" />
    </div>
    <div v-else-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="load">重试</el-button>
    </div>

    <template v-else-if="week">
      <section class="week-strip">
        <button
          v-for="day in week.days"
          :key="day.date"
          type="button"
          class="day-chip"
          :class="{ today: day.today, complete: day.tasks.length > 0 && day.tasks.every((task) => task.status === 'DONE') }"
        >
          <span>{{ dayLabel(day.date).weekday }}</span>
          <strong>{{ dayLabel(day.date).day }}</strong>
          <small>{{ day.tasks.length }} 任务</small>
          <i v-if="day.today" />
        </button>
      </section>

      <div class="plan-grid">
        <section class="card today-plan">
          <div class="section-head">
            <div>
              <p class="eyebrow">TODAY · {{ today ? dayjs(today.date).format('MMM D').toUpperCase() : '' }}</p>
              <h2 class="section-title">今天的 2-3 件事</h2>
            </div>
            <span class="today-total">{{ today?.totalMinutes ?? 0 }} min</span>
          </div>

          <div v-if="!today || today.tasks.length === 0" class="empty">
            <span class="empty-icon">✓</span>
            <strong>今天还没有生成任务</strong>
            <p class="small">完成 onboarding 后会自动生成。</p>
          </div>

          <div v-else class="plan-task-list">
            <article
              v-for="task in today.tasks"
              :key="task.id"
              class="plan-task"
              :class="{ done: task.status === 'DONE' }"
            >
              <div class="plan-task-icon" :class="task.type.toLowerCase()">
                <el-icon><component :is="iconFor(task)" /></el-icon>
              </div>
              <div class="plan-task-main">
                <div class="plan-task-head">
                  <h3>{{ task.title }}</h3>
                  <span>{{ task.estimatedMinutes }} min</span>
                </div>
                <p>{{ task.description }}</p>
                <div class="plan-task-actions">
                  <el-button
                    v-if="task.status !== 'DONE'"
                    size="small"
                    plain
                    :loading="completing === task.id"
                    @click="completeTask(task)"
                  >
                    标记完成
                  </el-button>
                  <el-button v-if="task.status !== 'DONE'" size="small" type="primary" @click="openTask(task)">
                    开始
                  </el-button>
                  <span v-else class="done-text">已完成</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <aside class="plan-side">
          <section class="card principle">
            <p class="eyebrow">THE RULE</p>
            <h2>先复习，再学习</h2>
            <p>
              到期词汇优先于新词，旧语法优先于新节点。系统会自动把复习任务排到前面，避免“学了很多、忘了更多”。
            </p>
          </section>

          <section class="card week-list">
            <div class="section-head">
              <div>
                <p class="eyebrow">UPCOMING</p>
                <h2 class="section-title small-title">本周安排</h2>
              </div>
            </div>
            <div class="upcoming-list">
              <div v-for="day in week.days.filter((item) => !item.today)" :key="day.date" class="upcoming-row">
                <span>{{ dayjs(day.date).format('ddd DD') }}</span>
                <div class="upcoming-track">
                  <span v-for="task in day.tasks" :key="task.id" :class="task.type.toLowerCase()" />
                </div>
                <small>{{ day.tasks.length ? `${day.tasks.length} 项` : '待生成' }}</small>
              </div>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.week-summary {
  flex: 0 0 200px;
  padding: 14px 16px;
}

.week-summary > span {
  color: var(--muted);
  font-size: 9px;
}

.week-summary strong {
  display: block;
  margin-top: 2px;
  font-family: var(--font-reading);
  font-size: 28px;
  font-weight: 600;
  line-height: 1.1;
}

.summary-bar {
  height: 4px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--surface-3);
}

.summary-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--green-2);
}

.week-summary small {
  display: block;
  margin-top: 5px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
}

.week-strip {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
  margin-bottom: 20px;
}

.day-chip {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 1px;
  padding: 11px 8px 9px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgb(255 253 248 / 60%);
  color: var(--muted);
  cursor: default;
}

.day-chip span {
  font-size: 9px;
  text-transform: uppercase;
}

.day-chip strong {
  font-family: var(--font-reading);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.1;
}

.day-chip small {
  color: var(--faint);
  font-size: 8px;
}

.day-chip.today {
  border-color: var(--green-2);
  background: var(--green);
  color: #fff;
  box-shadow: 0 8px 20px rgb(36 92 67 / 16%);
}

.day-chip.today span,
.day-chip.today small {
  color: rgb(255 255 255 / 58%);
}

.day-chip.complete:not(.today) {
  background: var(--green-3);
  border-color: #c9dfd1;
}

.plan-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(300px, 0.7fr);
  gap: 20px;
  align-items: start;
}

.today-plan {
  padding: 24px;
}

.today-total {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.plan-task-list {
  display: grid;
}

.plan-task {
  display: flex;
  gap: 13px;
  padding: 17px 0;
  border-top: 1px solid var(--line);
}

.plan-task:first-child {
  border-top: 0;
}

.plan-task.done {
  opacity: 0.55;
}

.plan-task-icon {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: var(--surface-3);
  color: var(--green);
  font-size: 16px;
}

.plan-task-icon.review {
  background: #e9f1f6;
  color: var(--blue);
}

.plan-task-icon.grammar {
  background: var(--amber-soft);
  color: #8d4d1d;
}

.plan-task-icon.reading {
  background: var(--green-3);
  color: var(--green);
}

.plan-task-main {
  flex: 1;
  min-width: 0;
}

.plan-task-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.plan-task-head h3 {
  font-size: 13px;
  font-weight: 600;
}

.plan-task-head span {
  flex: 0 0 auto;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
}

.plan-task-main > p {
  margin-top: 3px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.6;
}

.plan-task-actions {
  display: flex;
  gap: 6px;
  margin-top: 9px;
}

.done-text {
  color: var(--green-2);
  font-size: 9px;
  font-weight: 700;
}

.plan-side {
  display: grid;
  gap: 14px;
}

.principle {
  padding: 22px;
  background: linear-gradient(145deg, #1f4431, #173426);
  color: #eef5ef;
}

.principle .eyebrow {
  color: #a8c8b3;
}

.principle h2 {
  margin-top: 5px;
  font-family: var(--font-reading);
  font-size: 25px;
  font-weight: 500;
  letter-spacing: -0.03em;
}

.principle p {
  margin-top: 9px;
  color: rgb(238 245 239 / 60%);
  font-size: 10px;
  line-height: 1.75;
}

.week-list {
  padding: 22px;
}

.small-title {
  font-size: 19px;
}

.upcoming-list {
  display: grid;
  gap: 7px;
}

.upcoming-row {
  display: flex;
  align-items: center;
  gap: 9px;
}

.upcoming-row > span {
  flex: 0 0 48px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8px;
}

.upcoming-track {
  display: flex;
  flex: 1;
  gap: 3px;
  height: 4px;
}

.upcoming-track span {
  flex: 1;
  border-radius: 99px;
  background: var(--surface-3);
}

.upcoming-track span.review {
  background: #9ebbd0;
}

.upcoming-track span.grammar {
  background: #dfb28b;
}

.upcoming-track span.reading {
  background: #8fb99e;
}

.upcoming-row small {
  flex: 0 0 30px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 7px;
  text-align: right;
}

@media (max-width: 1000px) {
  .plan-grid {
    grid-template-columns: 1fr;
  }

  .plan-side {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 700px) {
  .week-strip {
    overflow-x: auto;
    grid-template-columns: repeat(7, 82px);
    padding-bottom: 4px;
  }

  .plan-side {
    grid-template-columns: 1fr;
  }

  .week-summary {
    width: 100%;
  }
}
</style>
