<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { searchApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { SearchResponse, SearchResult, VectorStatus } from '@/types/api'

const route = useRoute()
const router = useRouter()
const query = ref('')
const loading = ref(false)
const error = ref('')
const response = ref<SearchResponse | null>(null)
const status = ref<VectorStatus | null>(null)

const suggestions = ['database index', 'asynchronous work', 'code review', 'REST API', 'subject verb agreement']

const modeLabel = computed(() => {
  const result = response.value
  if (!result) return ''
  if (result.mode === 'VECTOR') return result.sqliteVec ? '向量检索 · sqlite-vec' : '向量检索 · Java 余弦'
  if (result.mode === 'VECTOR_JAVA') return '向量检索 · Java 余弦'
  if (result.mode === 'KEYWORD') return '关键词回退'
  if (result.mode === 'EMPTY') return '索引为空'
  if (result.mode === 'DISABLED') return '向量检索未启用'
  return result.mode
})

const statusLabel = computed(() => {
  if (!status.value) return '检查索引中'
  if (!status.value.enabled) return '向量检索已关闭'
  if (status.value.index === 'SQLITE_VEC') return 'sqlite-vec 已启用'
  if (status.value.index === 'JAVA_COSINE') return 'Java 余弦回退'
  return status.value.index
})

const statusTone = computed(() => (status.value?.index === 'SQLITE_VEC' ? 'green' : 'amber'))

function isArticle(result: SearchResult) {
  return result.sourceType === 'ARTICLE'
}

function openResult(result: SearchResult) {
  if (isArticle(result)) {
    router.push(`/reading/${result.sourceId}`)
    return
  }
  router.push({ path: '/vocabulary', query: { q: result.title } })
}

function scoreLabel(score: number) {
  return `${Math.round(Math.max(0, Math.min(1, score)) * 100)}%`
}

async function runSearch(text?: string) {
  if (typeof text === 'string') {
    query.value = text
  }
  const value = query.value.trim()
  if (!value) {
    response.value = null
    return
  }
  loading.value = true
  error.value = ''
  try {
    response.value = await searchApi.query(value, 10)
  } catch (err) {
    error.value = errorMessage(err, '搜索失败')
  } finally {
    loading.value = false
  }
}

async function loadStatus() {
  try {
    status.value = await searchApi.status()
  } catch {
    status.value = null
  }
}

onMounted(() => {
  loadStatus()
  const initial = route.query.q
  if (typeof initial === 'string' && initial.trim()) {
    query.value = initial
    runSearch()
  }
})
</script>

<template>
  <div class="page search-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">VECTOR SEARCH</p>
        <h1 class="page-title">搜你已经学过的内容</h1>
        <p class="page-subtitle">
          在文章段落和词汇里做向量检索。默认使用本地 sqlite-vec 索引；扩展不可用时自动退回 Java 余弦计算。
        </p>
      </div>
      <div class="status-block">
        <span class="pill" :class="statusTone">{{ statusLabel }}</span>
        <span class="status-meta">
          {{ status?.documents ?? 0 }} vectors · {{ status?.dimension ?? 0 }} dim ·
          {{ status?.provider ?? 'hash' }}
        </span>
      </div>
    </header>

    <section class="search-panel card">
      <div class="search-row">
        <el-input
          v-model="query"
          size="large"
          placeholder="Try: database index, async work, code review"
          clearable
          @keyup.enter="runSearch()"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" :loading="loading" @click="runSearch()">
          搜索
        </el-button>
      </div>
      <div class="suggestions">
        <span>试试</span>
        <button
          v-for="item in suggestions"
          :key="item"
          type="button"
          class="suggestion"
          @click="runSearch(item)"
        >
          {{ item }}
        </button>
      </div>
    </section>

    <div v-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="runSearch()">重试</el-button>
    </div>

    <section v-else-if="response" class="results">
      <div class="results-head">
        <span>{{ response.results.length }} 条结果</span>
        <span class="mode-tag">{{ modeLabel }}</span>
      </div>

      <div v-if="response.results.length === 0" class="empty">
        <span class="empty-icon">Aa</span>
        <strong>没有匹配的内容</strong>
        <p>换一个更短的关键词，或先在阅读实验室导入一篇文章。</p>
      </div>

      <article
        v-for="result in response.results"
        :key="`${result.sourceType}-${result.sourceId}`"
        class="result-card card"
        role="link"
        tabindex="0"
        @click="openResult(result)"
        @keydown.enter="openResult(result)"
      >
        <div class="result-top">
          <span class="pill" :class="isArticle(result) ? 'green' : ''">
            {{ isArticle(result) ? '文章' : '词汇' }}
          </span>
          <span class="score">相关度 {{ scoreLabel(result.score) }}</span>
        </div>
        <h2>{{ result.title }}</h2>
        <p>{{ result.snippet }}</p>
        <span class="result-link">{{ isArticle(result) ? '打开文章 →' : '在词汇本中查看 →' }}</span>
      </article>
    </section>

    <section v-else class="search-hint card">
      <div class="hint-art">
        <span class="hint-ring" />
        <span class="hint-dot" />
      </div>
      <div>
        <h2>向量索引已经准备好了</h2>
        <p>
          搜索会先把查询转换成 {{ status?.dimension ?? 384 }} 维向量，再和文章段落、词汇释义比较距离。
          当前嵌入模型是 <strong>{{ status?.model ?? 'local-hash-384' }}</strong>；
          配置 <code>EMBEDDING_PROVIDER=openai</code> 和 <code>EMBEDDING_API_KEY</code> 可以换成真实语义向量。
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.status-block {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.status-meta {
  color: var(--ink-soft);
  font-size: 0.8rem;
  letter-spacing: 0.02em;
}

.search-panel {
  padding: 22px 24px 18px;
}

.search-row {
  display: flex;
  gap: 12px;
}

.search-row :deep(.el-input) {
  flex: 1;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  color: var(--ink-soft);
  font-size: 0.8rem;
}

.suggestion {
  padding: 5px 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: transparent;
  color: var(--ink);
  font: inherit;
  cursor: pointer;
  transition: 0.18s ease;
}

.suggestion:hover {
  border-color: var(--green);
  color: var(--green);
  background: var(--green-soft);
}

.results {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.results-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-soft);
  font-size: 0.84rem;
}

.mode-tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--green-soft);
  color: var(--green-deep);
  font-size: 0.76rem;
}

.result-card {
  padding: 18px 22px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: box-shadow 0.18s ease, border-color 0.18s ease;
}

.result-card:hover,
.result-card:focus-visible {
  border-color: var(--green);
  box-shadow: 0 14px 34px rgb(31 46 37 / 9%);
  outline: none;
}

.result-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.score {
  color: var(--ink-soft);
  font-size: 0.76rem;
  letter-spacing: 0.04em;
}

.result-card h2 {
  margin: 0 0 6px;
  font-family: var(--font-serif);
  font-size: 1.25rem;
  color: var(--ink-strong);
}

.result-card p {
  margin: 0 0 10px;
  color: var(--ink-soft);
  line-height: 1.7;
  font-size: 0.9rem;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-link {
  color: var(--green);
  font-size: 0.82rem;
  font-weight: 600;
}

.search-hint {
  display: flex;
  gap: 24px;
  align-items: center;
  padding: 26px 28px;
}

.search-hint h2 {
  margin: 0 0 8px;
  font-family: var(--font-serif);
  font-size: 1.3rem;
}

.search-hint p {
  margin: 0;
  max-width: 640px;
  color: var(--ink-soft);
  line-height: 1.8;
  font-size: 0.9rem;
}

.search-hint code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--paper-deep);
  font-size: 0.82rem;
}

.hint-art {
  position: relative;
  flex: 0 0 84px;
  height: 84px;
}

.hint-ring {
  position: absolute;
  inset: 4px;
  border: 1.5px solid var(--green);
  border-radius: 50%;
  opacity: 0.55;
}

.hint-ring::after {
  content: '';
  position: absolute;
  inset: 14px;
  border: 1px solid var(--green);
  border-radius: 50%;
  opacity: 0.5;
}

.hint-dot {
  position: absolute;
  width: 16px;
  height: 16px;
  top: 26px;
  left: 48px;
  border-radius: 50%;
  background: var(--green);
}

.empty {
  padding: 48px 24px;
}

.empty p {
  margin: 6px 0 0;
  color: var(--ink-soft);
  font-size: 0.86rem;
}

@media (max-width: 720px) {
  .search-row {
    flex-direction: column;
  }

  .status-block {
    align-items: flex-start;
  }

  .search-hint {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
