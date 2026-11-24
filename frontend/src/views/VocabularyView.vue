<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { vocabularyApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { VocabCard, VocabLookup } from '@/types/api'

const route = useRoute()
const router = useRouter()
const words = ref<VocabCard[]>([])
const dueCount = ref(0)
const loading = ref(true)
const error = ref('')
const keyword = ref('')
const statusFilter = ref('ALL')
const dialogOpen = ref(false)
const lookupWord = ref('')
const lookupLoading = ref(false)
const lookupResult = ref<VocabLookup | null>(null)
const lookupError = ref('')
const adding = ref(false)

const filters = [
  { value: 'ALL', label: '全部' },
  { value: 'NEW', label: '新词' },
  { value: 'LEARNING', label: '学习中' },
  { value: 'REVIEW', label: '复习中' },
  { value: 'MASTERED', label: '已掌握' },
]

const filtered = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return words.value.filter((word) => {
    const statusOk = statusFilter.value === 'ALL' || word.status === statusFilter.value
    const textOk =
      !text ||
      word.lemma.toLowerCase().includes(text) ||
      word.definitionZh.includes(text) ||
      word.definitionEn.toLowerCase().includes(text)
    return statusOk && textOk
  })
})

const counts = computed(() => ({
  all: words.value.length,
  new: words.value.filter((word) => word.status === 'NEW').length,
  learning: words.value.filter((word) => word.status === 'LEARNING').length,
  review: words.value.filter((word) => word.status === 'REVIEW').length,
  mastered: words.value.filter((word) => word.status === 'MASTERED').length,
}))

function statusLabel(status: string) {
  return (
    {
      NEW: '新词',
      LEARNING: '学习中',
      REVIEW: '复习中',
      MASTERED: '已掌握',
      SUSPENDED: '暂停',
    }[status] ?? status
  )
}

function nextReviewLabel(value: string) {
  if (!value) return '待安排'
  const date = dayjs(value)
  const diff = date.diff(dayjs(), 'day')
  if (diff <= 0) return '今天到期'
  if (diff === 1) return '明天复习'
  return `${date.format('M月D日')} 复习`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await vocabularyApi.list()
    words.value = response.words
    dueCount.value = response.dueCount
  } catch (err) {
    error.value = errorMessage(err, '加载词汇失败')
  } finally {
    loading.value = false
  }
}

async function lookup() {
  const word = lookupWord.value.trim()
  if (!word) return
  lookupLoading.value = true
  lookupError.value = ''
  lookupResult.value = null
  try {
    lookupResult.value = await vocabularyApi.lookup(word)
    if (lookupResult.value.inDeck) {
      lookupError.value = '这个词已经在你的词汇本里了'
    }
  } catch (err) {
    lookupError.value = errorMessage(err, '词库里暂时没有这个词')
  } finally {
    lookupLoading.value = false
  }
}

async function add() {
  if (!lookupResult.value) return
  adding.value = true
  try {
    await vocabularyApi.add(lookupResult.value.lemma)
    ElMessage.success('已加入词汇本')
    dialogOpen.value = false
    lookupWord.value = ''
    lookupResult.value = null
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err, '加入失败'))
  } finally {
    adding.value = false
  }
}

function openDialog() {
  lookupWord.value = ''
  lookupResult.value = null
  lookupError.value = ''
  dialogOpen.value = true
}

onMounted(() => {
  const initial = route.query.q
  if (typeof initial === 'string') {
    keyword.value = initial
  }
  load()
})
</script>

<template>
  <div class="page vocabulary-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">YOUR WORD BANK</p>
        <h1 class="page-title">词汇不是列表，是可调用的记忆</h1>
        <p class="page-subtitle">
          从技术文档和阅读中收词。每个词都有上下文、释义和下一次复习时间。
        </p>
      </div>
      <div class="header-actions">
        <el-button @click="router.push('/review')">
          去复习 <span class="due-inline">{{ dueCount }}</span>
        </el-button>
        <el-button type="primary" @click="openDialog">
          <el-icon><Plus /></el-icon> 添加单词
        </el-button>
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

    <template v-else>
      <section class="word-stats">
        <div class="word-stat">
          <span>总词数</span>
          <strong>{{ counts.all }}</strong>
          <small>当前词汇本</small>
        </div>
        <div class="word-stat accent">
          <span>今天到期</span>
          <strong>{{ dueCount }}</strong>
          <small>建议先清队列</small>
        </div>
        <div class="word-stat">
          <span>已掌握</span>
          <strong>{{ counts.mastered }}</strong>
          <small>间隔 ≥ 30 天</small>
        </div>
        <div class="word-stat">
          <span>学习中</span>
          <strong>{{ counts.learning + counts.review }}</strong>
          <small>正在形成长期记忆</small>
        </div>
      </section>

      <div class="toolbar card">
        <el-input v-model="keyword" placeholder="搜索单词或中文释义" clearable class="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="filter-row">
          <button
            v-for="filter in filters"
            :key="filter.value"
            type="button"
            class="filter"
            :class="{ active: statusFilter === filter.value }"
            @click="statusFilter = filter.value"
          >
            {{ filter.label }}
            <span v-if="filter.value === 'ALL'">{{ counts.all }}</span>
          </button>
        </div>
      </div>

      <div v-if="filtered.length === 0" class="empty">
        <span class="empty-icon">Aa</span>
        <strong>没有匹配的单词</strong>
        <p class="small">试试别的关键词，或者从阅读中收集新词。</p>
        <el-button type="primary" plain @click="router.push('/reading')">去阅读</el-button>
      </div>

      <div v-else class="word-grid">
        <article v-for="word in filtered" :key="word.userVocabId" class="word-card card">
          <div class="word-card-head">
            <div>
              <h3>{{ word.lemma }}</h3>
              <span class="word-pos">{{ word.pos }}</span>
              <span v-if="word.phonetic" class="word-phonetic">{{ word.phonetic }}</span>
            </div>
            <span class="status-tag" :class="word.status.toLowerCase()">{{ statusLabel(word.status) }}</span>
          </div>
          <p class="word-zh">{{ word.definitionZh }}</p>
          <p class="word-en">{{ word.definitionEn }}</p>
          <p v-if="word.exampleEn" class="word-example">{{ word.exampleEn }}</p>
          <div class="word-foot">
            <span>{{ nextReviewLabel(word.nextReviewAt) }}</span>
            <span v-if="word.intervalDays > 0">间隔 {{ word.intervalDays }} 天</span>
            <span v-else>新加入</span>
          </div>
        </article>
      </div>
    </template>

    <el-dialog v-model="dialogOpen" title="添加一个词" width="min(520px, 92vw)">
      <div class="lookup-box">
        <el-input
          v-model="lookupWord"
          size="large"
          placeholder="输入英文单词，例如 deployment"
          @keyup.enter="lookup"
        >
          <template #append>
            <el-button :loading="lookupLoading" @click="lookup">查询</el-button>
          </template>
        </el-input>
        <p class="lookup-hint">目前从内置技术词库中查询。阅读器里选中单词也可以直接加入。</p>
      </div>

      <div v-if="lookupError" class="lookup-error">{{ lookupError }}</div>

      <div v-if="lookupResult" class="lookup-result">
        <div class="lookup-head">
          <h3>{{ lookupResult.lemma }}</h3>
          <span class="pill">{{ lookupResult.pos }}</span>
          <span class="pill amber">{{ lookupResult.domain }}</span>
        </div>
        <p class="lookup-phonetic">{{ lookupResult.phonetic }}</p>
        <p class="lookup-zh">{{ lookupResult.definitionZh }}</p>
        <p class="lookup-en">{{ lookupResult.definitionEn }}</p>
        <p class="lookup-example">{{ lookupResult.exampleEn }}</p>
        <p class="lookup-example-zh">{{ lookupResult.exampleZh }}</p>
      </div>

      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!lookupResult || lookupResult.inDeck" :loading="adding" @click="add">
          加入词汇本
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.due-inline {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  margin-left: 4px;
  padding: 0 4px;
  border-radius: 5px;
  background: var(--green);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 8px;
}

.word-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 11px;
  margin-bottom: 18px;
}

.word-stat {
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
}

.word-stat.accent {
  background: linear-gradient(145deg, #eaf3ed, #f9fcf9);
  border-color: #c9dfd1;
}

.word-stat > span {
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.word-stat strong {
  display: block;
  margin-top: 3px;
  font-family: var(--font-reading);
  font-size: 30px;
  font-weight: 600;
  line-height: 1.1;
}

.word-stat.accent strong {
  color: var(--green);
}

.word-stat small {
  display: block;
  margin-top: 4px;
  color: var(--faint);
  font-size: 9px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  padding: 12px 14px;
}

.search {
  width: 280px;
  flex: 0 0 auto;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  overflow-x: auto;
}

.filter {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--muted);
  font-size: 10px;
  cursor: pointer;
  white-space: nowrap;
}

.filter span {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
}

.filter:hover {
  color: var(--ink);
}

.filter.active {
  background: var(--green-3);
  color: var(--green);
  font-weight: 700;
}

.word-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 11px;
}

.word-card {
  display: flex;
  flex-direction: column;
  padding: 18px;
  transition: 170ms ease;
}

.word-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.word-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.word-card h3 {
  display: inline;
  font-family: var(--font-reading);
  font-size: 23px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.word-pos {
  margin-left: 6px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.word-phonetic {
  display: block;
  margin-top: 2px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.status-tag {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--muted);
  font-size: 8px;
  font-weight: 700;
}

.status-tag.new {
  background: #e9f1f6;
  color: var(--blue);
}

.status-tag.learning {
  background: var(--amber-soft);
  color: #8d4d1d;
}

.status-tag.review {
  background: var(--green-3);
  color: var(--green);
}

.status-tag.mastered {
  background: #dcebe2;
  color: #1d5b3e;
}

.word-zh {
  margin-top: 12px;
  color: var(--ink);
  font-size: 12px;
}

.word-en {
  margin-top: 3px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.55;
}

.word-example {
  display: -webkit-box;
  margin-top: 12px;
  padding-top: 10px;
  overflow: hidden;
  border-top: 1px dashed var(--line);
  color: var(--muted);
  font-family: var(--font-reading);
  font-size: 14px;
  font-style: italic;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.word-foot {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  padding-top: 14px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
}

.lookup-box {
  margin-bottom: 14px;
}

.lookup-hint {
  margin-top: 7px;
  color: var(--faint);
  font-size: 10px;
}

.lookup-error {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f9edeb;
  color: var(--rose);
  font-size: 11px;
}

.lookup-result {
  margin-top: 14px;
  padding: 17px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-2);
}

.lookup-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px;
}

.lookup-head h3 {
  font-family: var(--font-reading);
  font-size: 28px;
  font-weight: 600;
}

.lookup-phonetic {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.lookup-zh {
  margin-top: 10px;
  color: var(--ink);
  font-size: 13px;
}

.lookup-en {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.lookup-example {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
  color: var(--ink-soft);
  font-family: var(--font-reading);
  font-size: 15px;
}

.lookup-example-zh {
  color: var(--muted);
  font-size: 10px;
}

@media (max-width: 1100px) {
  .word-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 800px) {
  .word-stats {
    grid-template-columns: 1fr 1fr;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }

  .search {
    width: 100%;
  }
}

@media (max-width: 600px) {
  .word-grid {
    grid-template-columns: 1fr;
  }
}
</style>
