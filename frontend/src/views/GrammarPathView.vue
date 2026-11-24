<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Right } from '@element-plus/icons-vue'
import { grammarApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { GrammarPath, GrammarTopic } from '@/types/api'

const router = useRouter()
const path = ref<GrammarPath | null>(null)
const loading = ref(true)
const error = ref('')

const allTopics = computed(() => path.value?.worlds.flatMap((world) => world.topics) ?? [])
const nextTopic = computed(
  () => allTopics.value.find((topic) => !topic.locked && !topic.completed) ?? null,
)
const masteredCount = computed(() => allTopics.value.filter((topic) => topic.completed).length)
const totalCount = computed(() => allTopics.value.length)
const percent = computed(() =>
  totalCount.value === 0 ? 0 : Math.round((masteredCount.value / totalCount.value) * 100),
)

function statusLabel(topic: GrammarTopic) {
  if (topic.status === 'MASTERED') return '已掌握'
  if (topic.status === 'IN_PROGRESS') return '学习中'
  if (topic.status === 'AVAILABLE') return '可开始'
  return '未解锁'
}

function openTopic(topic: GrammarTopic) {
  if (topic.locked) {
    ElMessage.info('先完成前置节点，掌握度达到 70 后解锁')
    return
  }
  router.push(`/grammar/${topic.slug}`)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    path.value = await grammarApi.path()
  } catch (err) {
    error.value = errorMessage(err, '加载语法路径失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page grammar-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">GRAMMAR PATH</p>
        <h1 class="page-title">把语法拆成可以通关的节点</h1>
        <p class="page-subtitle">
          每个节点只讲一个规则，配技术场景例句和一组练习。掌握度达到 70、Boss 达到 80，才会解锁下一关。
        </p>
      </div>
      <div v-if="path" class="path-progress">
        <span>{{ masteredCount }} / {{ totalCount }} 已掌握</span>
        <strong>{{ percent }}%</strong>
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

    <template v-else-if="path">
      <section class="path-hero">
        <div class="path-hero-copy">
          <span class="hero-label">NEXT NODE</span>
          <h2>{{ nextTopic ? nextTopic.title : '所有节点已完成' }}</h2>
          <p>
            {{ nextTopic ? nextTopic.summary : '可以去阅读或复习，保持输入和输出。' }}
          </p>
          <div v-if="nextTopic" class="hero-meta">
            <span>{{ nextTopic.cefrLevel }}</span>
            <span class="dot" />
            <span>掌握度 {{ Math.round(nextTopic.mastery) }}%</span>
            <span class="dot" />
            <span>{{ nextTopic.status === 'IN_PROGRESS' ? '继续上次进度' : '新节点' }}</span>
          </div>
          <el-button
            v-if="nextTopic"
            type="primary"
            size="large"
            round
            @click="openTopic(nextTopic)"
          >
            进入节点 <el-icon class="el-icon--right"><Right /></el-icon>
          </el-button>
        </div>
        <div class="path-hero-orbit">
          <div class="orbit-ring ring-1" />
          <div class="orbit-ring ring-2" />
          <div class="orbit-center">
            <span>{{ path.overallMastery }}%</span>
            <small>总掌握</small>
          </div>
        </div>
      </section>

      <section v-for="world in path.worlds" :key="world.name" class="world">
        <div class="world-head">
          <div class="world-index">W{{ String(world.order).padStart(2, '0') }}</div>
          <div>
            <h2>{{ world.name }}</h2>
            <p>{{ world.topics.filter((topic) => topic.completed).length }} / {{ world.topics.length }} 节点完成</p>
          </div>
          <div class="world-line" />
        </div>

        <div class="node-grid">
          <button
            v-for="topic in world.topics"
            :key="topic.id"
            type="button"
            class="node-card"
            :class="{
              locked: topic.locked,
              completed: topic.completed,
              current: nextTopic?.id === topic.id,
            }"
            @click="openTopic(topic)"
          >
            <div class="node-top">
              <span class="node-status" :class="topic.status.toLowerCase()">
                {{ statusLabel(topic) }}
              </span>
              <span class="node-cefr">{{ topic.cefrLevel }}</span>
            </div>
            <h3>{{ topic.title }}</h3>
            <p>{{ topic.summary }}</p>
            <div class="node-foot">
              <div class="mastery">
                <div class="mastery-track">
                  <span :style="{ width: `${topic.mastery}%` }" />
                </div>
                <small>{{ Math.round(topic.mastery) }}%</small>
              </div>
              <el-icon v-if="topic.locked" class="lock"><Lock /></el-icon>
              <el-icon v-else class="arrow"><Right /></el-icon>
            </div>
            <span v-if="nextTopic?.id === topic.id" class="current-mark">CURRENT</span>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.path-progress {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 13px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}

.path-progress strong {
  color: var(--green);
  font-family: var(--font-reading);
  font-size: 19px;
  font-weight: 600;
}

.path-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 250px;
  margin-bottom: 34px;
  padding: 34px 40px;
  overflow: hidden;
  border-radius: var(--radius-lg);
  color: #eef5ef;
  background:
    radial-gradient(circle at 82% 30%, rgb(135 181 152 / 32%), transparent 34%),
    linear-gradient(125deg, #1d3f2d, #163124 66%, #12261c);
  box-shadow: var(--shadow-md);
}

.path-hero-copy {
  position: relative;
  z-index: 2;
  max-width: 620px;
}

.hero-label {
  color: #a8c8b3;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.2em;
}

.path-hero h2 {
  margin-top: 12px;
  font-family: var(--font-reading);
  font-size: clamp(30px, 3.5vw, 43px);
  font-weight: 500;
  letter-spacing: -0.04em;
  line-height: 1.1;
}

.path-hero p {
  max-width: 530px;
  margin-top: 9px;
  color: rgb(238 245 239 / 62%);
  font-size: 12px;
  line-height: 1.7;
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 17px 0 22px;
  color: rgb(238 245 239 / 48%);
  font-family: var(--font-mono);
  font-size: 9px;
}

.hero-meta .dot {
  background: rgb(238 245 239 / 28%);
}

.path-hero-orbit {
  position: relative;
  flex: 0 0 220px;
  height: 200px;
}

.orbit-ring {
  position: absolute;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 50%;
}

.ring-1 {
  inset: 18px;
}

.ring-2 {
  inset: 44px;
  border-style: dashed;
}

.orbit-center {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  text-align: center;
}

.orbit-center span {
  font-family: var(--font-reading);
  font-size: 43px;
  font-weight: 500;
  letter-spacing: -0.04em;
}

.orbit-center small {
  color: rgb(238 245 239 / 45%);
  font-size: 9px;
  letter-spacing: 0.14em;
}

.world {
  margin-top: 34px;
}

.world-head {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-bottom: 14px;
}

.world-index {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: var(--surface);
  color: var(--green);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
}

.world-head h2 {
  font-family: var(--font-reading);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.world-head p {
  color: var(--faint);
  font-size: 10px;
}

.world-line {
  flex: 1;
  height: 1px;
  background: var(--line);
}

.node-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 11px;
}

.node-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 190px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: 180ms ease;
}

.node-card:hover {
  transform: translateY(-3px);
  border-color: #b9d1c1;
  box-shadow: var(--shadow-md);
}

.node-card.current {
  border-color: var(--green-2);
  box-shadow: 0 0 0 3px rgb(59 122 91 / 10%);
}

.node-card.completed {
  background: linear-gradient(170deg, #fffdf8, #f0f6f1);
}

.node-card.locked {
  opacity: 0.52;
  cursor: not-allowed;
  background: var(--surface-2);
}

.node-card.locked:hover {
  transform: none;
  border-color: var(--line);
  box-shadow: none;
}

.node-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 13px;
}

.node-status {
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--muted);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.node-status.available {
  background: #e9f1f6;
  color: var(--blue);
}

.node-status.in_progress {
  background: var(--amber-soft);
  color: #8d4d1d;
}

.node-status.mastered {
  background: var(--green-3);
  color: var(--green);
}

.node-status.locked {
  background: #eee9de;
  color: var(--faint);
}

.node-cefr {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.node-card h3 {
  font-family: var(--font-reading);
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.node-card > p {
  display: -webkit-box;
  margin-top: 7px;
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.node-foot {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: auto;
  padding-top: 16px;
}

.mastery {
  display: flex;
  align-items: center;
  gap: 7px;
  flex: 1;
}

.mastery-track {
  flex: 1;
  height: 4px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--surface-3);
}

.mastery-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--green-2);
}

.mastery small {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
}

.lock,
.arrow {
  color: var(--faint);
  font-size: 12px;
}

.node-card:not(.locked) .arrow {
  color: var(--green);
}

.current-mark {
  position: absolute;
  top: -1px;
  right: 14px;
  padding: 3px 6px;
  border-radius: 0 0 5px 5px;
  background: var(--green);
  color: #fff;
  font-size: 7px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

@media (max-width: 1200px) {
  .node-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .path-hero-orbit {
    display: none;
  }

  .path-hero {
    padding: 28px;
  }
}

@media (max-width: 640px) {
  .node-grid {
    grid-template-columns: 1fr 1fr;
  }

  .node-card {
    min-height: 175px;
    padding: 13px;
  }

  .node-card h3 {
    font-size: 17px;
  }

  .path-hero {
    padding: 24px 20px;
  }
}
</style>
