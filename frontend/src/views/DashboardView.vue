<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { Collection, EditPen, Reading, Trophy } from '@element-plus/icons-vue'
import { use } from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'
import VChart from 'vue-echarts'
import { dashboardApi, grammarApi, planApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { Dashboard, Task } from '@/types/api'

use([RadarChart, CanvasRenderer])

const router = useRouter()
const dashboard = ref<Dashboard | null>(null)
const loading = ref(true)
const error = ref('')
const completing = ref<number | null>(null)

const skills = computed(() => dashboard.value?.skills ?? [])
const nextTask = computed(() => dashboard.value?.nextTask ?? null)
const stats = computed(() => dashboard.value?.stats)
const examLabel = computed(() => {
  if (!dashboard.value?.examDate) return '还没有设置考试日期'
  return `${dayjs(dashboard.value.examDate).format('YYYY年M月D日')} · 还有 ${dashboard.value.daysToExam} 天`
})
const progress = computed(() => {
  const s = stats.value
  if (!s || s.tasksTotal === 0) return 0
  return Math.round((s.tasksCompleted / s.tasksTotal) * 100)
})

const skillOption = computed<EChartsOption>(() => ({
  radar: {
    indicator: skills.value.map((skill) => ({ name: skill.label, max: 100 })),
    radius: '63%',
    splitNumber: 4,
    axisName: { color: '#6f7b71', fontSize: 11 },
    splitLine: { lineStyle: { color: '#e4ded0' } },
    splitArea: { show: false },
    axisLine: { lineStyle: { color: '#e4ded0' } },
  },
  series: [
    {
      type: 'radar',
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { color: '#245c43', width: 2 },
      itemStyle: { color: '#245c43' },
      areaStyle: { color: 'rgba(36, 92, 67, 0.16)' },
      data: [{ value: skills.value.map((skill) => skill.value), name: '能力' }],
    },
  ],
}))

function iconFor(task: Task) {
  if (task.type === 'REVIEW') return Collection
  if (task.type === 'GRAMMAR') return Trophy
  if (task.type === 'READING') return Reading
  return EditPen
}

function actionLabel(task: Task) {
  if (task.type === 'REVIEW') return '去复习'
  if (task.type === 'GRAMMAR') return '继续闯关'
  if (task.type === 'READING') return '去阅读'
  return '开始'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    dashboard.value = await dashboardApi.get()
  } catch (err) {
    error.value = errorMessage(err, '加载看板失败')
  } finally {
    loading.value = false
  }
}

async function openTask(task: Task) {
  if (task.type === 'REVIEW') {
    await router.push('/review')
    return
  }
  if (task.type === 'READING' && task.refId) {
    await router.push(`/reading/${task.refId}`)
    return
  }
  if (task.type === 'GRAMMAR' && task.refId) {
    try {
      const path = await grammarApi.path()
      const topic = path.worlds.flatMap((world) => world.topics).find((item) => item.id === task.refId)
      if (topic) {
        await router.push(`/grammar/${topic.slug}`)
        return
      }
    } catch {
      // Fall through to the grammar map.
    }
    await router.push('/grammar')
    return
  }
  await router.push('/grammar')
}

async function completeTask(task: Task) {
  if (task.status === 'DONE') return
  completing.value = task.id
  try {
    await planApi.completeTask(task.id)
    ElMessage.success('任务完成')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '标记完成失败'))
  } finally {
    completing.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page dashboard">
    <div v-if="loading" class="loading-grid">
      <el-skeleton animated :rows="8" />
    </div>

    <div v-else-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="load">重试</el-button>
    </div>

    <template v-else-if="dashboard">
      <header class="hero">
        <div>
          <p class="eyebrow">TODAY · {{ dayjs().format('MMM D').toUpperCase() }}</p>
          <h1 class="hero-title">{{ dashboard.greeting }}</h1>
          <p class="hero-sub">
            今天先从最重要的一件事开始。目标 IELTS {{ dashboard.targetBand?.toFixed(1) ?? '—' }}，
            {{ examLabel }}。
          </p>
        </div>
        <div class="hero-side">
          <div class="day-progress">
            <div class="day-progress-head">
              <span>今日任务</span>
              <strong>{{ stats?.tasksCompleted }}/{{ stats?.tasksTotal }}</strong>
            </div>
            <el-progress :percentage="progress" :show-text="false" :stroke-width="7" />
            <span class="day-progress-note">
              {{ progress === 100 ? '今天的闭环已经完成。' : '完成 2-3 个短任务，比一次学很久更稳。' }}
            </span>
          </div>
        </div>
      </header>

      <section class="stats-grid">
        <article class="stat-card">
          <span class="stat-label">连续学习</span>
          <strong>{{ stats?.streakDays }}<small>天</small></strong>
          <p>每天 10 分钟也算连续</p>
          <span class="stat-icon">◷</span>
        </article>
        <article class="stat-card">
          <span class="stat-label">本周投入</span>
          <strong>{{ stats?.weekMinutes }}<small>分钟</small></strong>
          <p>按已完成任务估算</p>
          <span class="stat-icon">↗</span>
        </article>
        <article class="stat-card">
          <span class="stat-label">到期词汇</span>
          <strong>{{ stats?.dueVocab }}<small>个</small></strong>
          <p>先清到期队列</p>
          <span class="stat-icon">Aa</span>
        </article>
        <article class="stat-card">
          <span class="stat-label">语法掌握</span>
          <strong>{{ stats?.grammarMastery }}<small>%</small></strong>
          <p>来自最近练习与复习</p>
          <span class="stat-icon">✦</span>
        </article>
      </section>

      <div class="dashboard-grid">
        <section class="card today-card">
          <div class="section-head">
            <div>
              <p class="eyebrow">DAILY LOOP</p>
              <h2 class="section-title">今天学什么</h2>
            </div>
            <router-link to="/plan" class="text-link">查看周计划 →</router-link>
          </div>

          <div v-if="dashboard.todayTasks.length === 0" class="empty">
            <span class="empty-icon">✓</span>
            <strong>今天的计划还在生成</strong>
            <p class="small">完成 onboarding 或稍后刷新页面。</p>
          </div>

          <div v-else class="task-list">
            <article
              v-for="(task, index) in dashboard.todayTasks"
              :key="task.id"
              class="task-row"
              :class="{ done: task.status === 'DONE' }"
            >
              <div class="task-index">{{ String(index + 1).padStart(2, '0') }}</div>
              <div class="task-icon" :class="task.type.toLowerCase()">
                <el-icon><component :is="iconFor(task)" /></el-icon>
              </div>
              <div class="task-main">
                <div class="task-title-line">
                  <h3>{{ task.title }}</h3>
                  <span class="task-minutes">{{ task.estimatedMinutes }} min</span>
                </div>
                <p>{{ task.description }}</p>
              </div>
              <div class="task-actions">
                <span v-if="task.status === 'DONE'" class="done-label">已完成</span>
                <template v-else>
                  <el-button size="small" plain @click="completeTask(task)" :loading="completing === task.id">
                    完成
                  </el-button>
                  <el-button size="small" type="primary" @click="openTask(task)">
                    {{ actionLabel(task) }}
                  </el-button>
                </template>
              </div>
            </article>
          </div>
        </section>

        <aside class="side-column">
          <section class="focus-card">
            <p class="focus-kicker">NEXT BEST ACTION</p>
            <template v-if="nextTask">
              <span class="focus-type">{{ nextTask.type }}</span>
              <h2>{{ nextTask.title }}</h2>
              <p>{{ nextTask.description }}</p>
              <div class="focus-meta">
                <span>{{ nextTask.estimatedMinutes }} 分钟</span>
                <span class="dot" />
                <span>{{ dashboard.cefrLevel }} 起点</span>
              </div>
              <el-button type="primary" size="large" round @click="openTask(nextTask)">
                {{ actionLabel(nextTask) }}
              </el-button>
            </template>
            <template v-else>
              <span class="focus-type">ALL CLEAR</span>
              <h2>今天的闭环完成了。</h2>
              <p>可以去阅读实验室挑一篇技术文章，或者提前复习明天的词。</p>
              <el-button type="primary" size="large" round @click="router.push('/reading')">
                去阅读
              </el-button>
            </template>
          </section>

          <section class="card radar-card">
            <div class="section-head compact">
              <div>
                <p class="eyebrow">SKILL SNAPSHOT</p>
                <h2 class="section-title small-title">能力分布</h2>
              </div>
            </div>
            <VChart :option="skillOption" autoresize class="radar-chart" />
            <div class="skill-list">
              <div v-for="skill in skills" :key="skill.key" class="skill-row">
                <div class="skill-row-head">
                  <span>{{ skill.label }}</span>
                  <strong>{{ Math.round(skill.value) }}</strong>
                </div>
                <div class="skill-track"><span :style="{ width: `${skill.value}%` }" /></div>
                <p>{{ skill.note }}</p>
              </div>
            </div>
          </section>
        </aside>
      </div>

      <section class="bottom-strip">
        <div class="bottom-copy">
          <p class="eyebrow">A SMALL RULE</p>
          <h2>不要等到“准备好”再开口。先完成一个 15 分钟的节点。</h2>
        </div>
        <div class="bottom-links">
          <router-link to="/grammar" class="quick-link">语法地图 <span>→</span></router-link>
          <router-link to="/reading" class="quick-link">阅读实验室 <span>→</span></router-link>
          <router-link to="/review" class="quick-link">复习 {{ stats?.dueVocab }} 个词 <span>→</span></router-link>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.loading-grid {
  padding: 20px;
}

.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  margin-bottom: 28px;
}

.hero-title {
  font-family: var(--font-reading);
  font-size: clamp(36px, 4.5vw, 58px);
  font-weight: 500;
  letter-spacing: -0.05em;
  line-height: 1.03;
}

.hero-sub {
  max-width: 640px;
  margin-top: 12px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.hero-side {
  flex: 0 0 250px;
}

.day-progress {
  padding: 15px 17px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: rgb(255 253 248 / 68%);
}

.day-progress-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 9px;
  color: var(--muted);
  font-size: 11px;
}

.day-progress-head strong {
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 11px;
}

.day-progress-note {
  display: block;
  margin-top: 8px;
  color: var(--faint);
  font-size: 10px;
  line-height: 1.5;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 22px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  padding: 17px 18px 15px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.stat-label {
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.stat-card strong {
  display: block;
  margin-top: 5px;
  font-family: var(--font-reading);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.stat-card strong small {
  margin-left: 3px;
  color: var(--muted);
  font-family: var(--font-ui);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0;
}

.stat-card p {
  margin-top: 5px;
  color: var(--faint);
  font-size: 10px;
}

.stat-icon {
  position: absolute;
  top: 14px;
  right: 15px;
  color: #b5c9bc;
  font-family: var(--font-reading);
  font-size: 19px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 0.85fr);
  gap: 20px;
}

.today-card {
  padding: 24px;
}

.text-link {
  color: var(--green);
  font-size: 11px;
  font-weight: 700;
}

.task-list {
  display: grid;
}

.task-row {
  display: grid;
  grid-template-columns: 26px 40px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  padding: 17px 0;
  border-top: 1px solid var(--line);
}

.task-row:first-child {
  border-top: 0;
}

.task-row.done {
  opacity: 0.55;
}

.task-index {
  padding-top: 12px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.task-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  margin-top: 1px;
  border-radius: 11px;
  background: var(--surface-3);
  color: var(--green);
  font-size: 17px;
}

.task-icon.review {
  background: #e9f1f6;
  color: var(--blue);
}

.task-icon.grammar {
  background: #f7e7d7;
  color: var(--amber);
}

.task-icon.reading {
  background: #e7f0e9;
  color: var(--green);
}

.task-title-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.task-title-line h3 {
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.task-minutes {
  flex: 0 0 auto;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.task-main p {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.task-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 4px;
}

.done-label {
  color: var(--green-2);
  font-size: 10px;
  font-weight: 700;
}

.side-column {
  display: grid;
  gap: 20px;
  align-content: start;
}

.focus-card {
  padding: 26px;
  border-radius: var(--radius);
  background:
    radial-gradient(circle at 90% 10%, rgb(137 181 151 / 28%), transparent 38%),
    linear-gradient(150deg, #1e4431, #173627);
  color: #eef5ef;
  box-shadow: var(--shadow-md);
}

.focus-kicker {
  color: #a8c8b3;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.focus-type {
  display: inline-block;
  margin-top: 26px;
  padding: 3px 8px;
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 999px;
  color: #c6ddcd;
  font-size: 9px;
  letter-spacing: 0.08em;
}

.focus-card h2 {
  margin-top: 10px;
  font-family: var(--font-reading);
  font-size: 27px;
  font-weight: 500;
  letter-spacing: -0.03em;
  line-height: 1.15;
}

.focus-card p {
  margin-top: 9px;
  color: rgb(238 245 239 / 62%);
  font-size: 11px;
  line-height: 1.7;
}

.focus-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 15px 0 20px;
  color: rgb(238 245 239 / 45%);
  font-size: 10px;
}

.focus-meta .dot {
  background: rgb(238 245 239 / 30%);
}

.radar-card {
  padding: 22px;
}

.compact {
  margin-bottom: 0;
}

.small-title {
  font-size: 19px;
}

.radar-chart {
  height: 210px;
  margin: 0 -8px;
}

.skill-list {
  display: grid;
  gap: 10px;
  margin-top: 4px;
}

.skill-row-head {
  display: flex;
  justify-content: space-between;
  color: var(--ink-soft);
  font-size: 11px;
}

.skill-row-head strong {
  color: var(--green);
  font-family: var(--font-mono);
  font-size: 10px;
}

.skill-track {
  height: 4px;
  margin-top: 4px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--surface-3);
}

.skill-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--green-2), #92b9a0);
}

.skill-row p {
  margin-top: 3px;
  color: var(--faint);
  font-size: 9px;
}

.bottom-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 30px;
  margin-top: 22px;
  padding: 22px 26px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: rgb(255 253 248 / 55%);
}

.bottom-copy h2 {
  max-width: 720px;
  margin-top: 4px;
  font-family: var(--font-reading);
  font-size: 21px;
  font-weight: 500;
  letter-spacing: -0.02em;
}

.bottom-links {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
}

.quick-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--ink-soft);
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.quick-link span {
  color: var(--green);
}

.quick-link:hover {
  border-color: var(--green-2);
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .side-column {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 900px) {
  .hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-side {
    width: 100%;
  }

  .stats-grid {
    grid-template-columns: 1fr 1fr;
  }

  .bottom-strip {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .side-column {
    grid-template-columns: 1fr;
  }

  .task-row {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .task-index {
    display: none;
  }

  .task-actions {
    grid-column: 2;
    padding-top: 0;
  }

  .today-card {
    padding: 18px;
  }
}
</style>
