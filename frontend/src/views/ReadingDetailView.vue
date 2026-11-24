<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ChatDotRound,
  Check,
  Close,
  Plus,
  Search,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import { aiApi, readingApi, vocabularyApi } from '@/api'
import { completeTaskByRef } from '@/composables/useTaskCompletion'
import { extractToc, renderMarkdown } from '@/utils/markdown'
import { errorMessage } from '@/utils/error'
import type {
  AiExplainResponse,
  ReadingArticleDetail,
  ReadingCompleteResponse,
  VocabLookup,
} from '@/types/api'

const route = useRoute()
const router = useRouter()
const isMobile = useMediaQuery('(max-width: 900px)')

const article = ref<ReadingArticleDetail | null>(null)
const loading = ref(true)
const error = ref('')
const articleBody = ref<HTMLElement | null>(null)
const articleHtml = computed(() => {
  const source = article.value?.contentMd ?? ''
  return renderMarkdown(source.replace(/^\s*#\s+.+\r?\n+/, ''))
})
const toc = computed(() => extractToc(article.value?.contentMd ?? ''))
const fontScale = ref(1)

const selectedText = ref('')
const selectedContext = ref('')
const selectionRect = ref<DOMRect | null>(null)
const toolbarVisible = computed(() => Boolean(selectedText.value && selectionRect.value))
const isWordSelection = computed(() => /^[A-Za-z][A-Za-z'-]*$/.test(selectedText.value.trim()))

const activePanel = ref<'none' | 'word' | 'ai'>('none')
const wordResult = ref<VocabLookup | null>(null)
const wordLoading = ref(false)
const wordError = ref('')
const aiResult = ref<AiExplainResponse | null>(null)
const aiLoading = ref(false)
const aiError = ref('')

const answers = reactive<Record<number, string>>({})
const submitting = ref(false)
const completeResult = ref<ReadingCompleteResponse | null>(null)
const startedAt = ref(Date.now())
const elapsedMinutes = ref(0)
let timer: number | undefined

const questionCount = computed(() => article.value?.questions.length ?? 0)
const answeredCount = computed(
  () => article.value?.questions.filter((question) => Boolean(answers[question.id]?.trim())).length ?? 0,
)
const allAnswered = computed(() => questionCount.value === 0 || answeredCount.value === questionCount.value)
const readingTime = computed(() => article.value?.article.estimatedMinutes ?? 0)

const toolbarStyle = computed(() => {
  if (!selectionRect.value) return {}
  const width = 330
  const left = Math.min(
    Math.max(selectionRect.value.left + selectionRect.value.width / 2, width / 2 + 12),
    window.innerWidth - width / 2 - 12,
  )
  const above = selectionRect.value.top > 70
  const top = above ? selectionRect.value.top - 52 : selectionRect.value.bottom + 12
  return {
    left: `${left}px`,
    top: `${top}px`,
    transform: 'translateX(-50%)',
  }
})

function typeLabel(type: string) {
  return { IELTS: 'IELTS Academic', TECH: 'Tech Doc', CUSTOM: 'Imported' }[type] ?? type
}

function sourceClass(type: string) {
  return type.toLowerCase()
}

async function load(id: string) {
  loading.value = true
  error.value = ''
  completeResult.value = null
  activePanel.value = 'none'
  aiResult.value = null
  wordResult.value = null
  Object.keys(answers).forEach((key) => delete answers[Number(key)])
  try {
    article.value = await readingApi.detail(Number(id))
    startedAt.value = Date.now()
    elapsedMinutes.value = 0
  } catch (err) {
    error.value = errorMessage(err, '加载文章失败')
    article.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.id as string,
  (id) => {
    if (id) load(id)
  },
  { immediate: true },
)

function scrollTo(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function handleSelection() {
  const selection = window.getSelection()
  const text = selection?.toString().trim() ?? ''
  if (!selection || selection.rangeCount === 0 || !text) {
    selectedText.value = ''
    selectionRect.value = null
    return
  }
  const range = selection.getRangeAt(0)
  if (!articleBody.value?.contains(range.commonAncestorContainer)) {
    return
  }
  const anchorElement =
    range.commonAncestorContainer instanceof Element
      ? range.commonAncestorContainer
      : range.commonAncestorContainer.parentElement
  selectedText.value = text.slice(0, 800)
  selectedContext.value = (anchorElement?.closest('p,li,h2,h3,pre,blockquote')?.textContent ?? '').slice(0, 1200)
  selectionRect.value = range.getBoundingClientRect()
}

function hideToolbar() {
  selectedText.value = ''
  selectionRect.value = null
}

function scrollToAssistant() {
  if (!isMobile.value) return
  nextTick(() => {
    document.getElementById('assistant-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

async function lookupSelected() {
  const word = selectedText.value.trim()
  if (!word) return
  wordLoading.value = true
  wordError.value = ''
  wordResult.value = null
  activePanel.value = 'word'
  try {
    wordResult.value = await vocabularyApi.lookup(word)
    scrollToAssistant()
  } catch (err) {
    wordError.value = errorMessage(err, '词库里暂时没有这个词')
    await explainSelected('word')
  } finally {
    wordLoading.value = false
  }
}

async function explainSelected(mode?: 'word' | 'sentence') {
  const text = selectedText.value.trim()
  if (!text) return
  aiLoading.value = true
  aiError.value = ''
  aiResult.value = null
  activePanel.value = 'ai'
  try {
    aiResult.value = await aiApi.explain({
      text,
      context: selectedContext.value,
      mode: mode ?? (isWordSelection.value ? 'word' : 'sentence'),
    })
    scrollToAssistant()
  } catch (err) {
    aiError.value = errorMessage(err, 'AI 暂时不可用')
  } finally {
    aiLoading.value = false
  }
}

async function addSelectedWord() {
  const word = (wordResult.value?.lemma ?? selectedText.value).trim()
  if (!word) return
  try {
    await vocabularyApi.add(word, selectedContext.value)
    if (wordResult.value) wordResult.value.inDeck = true
    ElMessage.success('已加入词汇本')
  } catch (err) {
    ElMessage.error(errorMessage(err, '加入失败'))
  }
}

function increaseFont() {
  fontScale.value = Math.min(1.3, Number((fontScale.value + 0.1).toFixed(1)))
}

function decreaseFont() {
  fontScale.value = Math.max(0.85, Number((fontScale.value - 0.1).toFixed(1)))
}

async function completeReading() {
  if (!article.value) return
  if (!allAnswered.value) {
    ElMessage.warning('先回答完所有题目')
    return
  }
  submitting.value = true
  try {
    const minutes = Math.max(1, Math.min(240, Math.round((Date.now() - startedAt.value) / 60000) || 1))
    completeResult.value = await readingApi.complete(
      article.value.article.id,
      {
        minutes,
        answers: article.value.questions.map((question) => ({
          questionId: question.id,
          answer: answers[question.id] ?? '',
        })),
      },
    )
    article.value.article.read = true
    await completeTaskByRef('READING', article.value.article.id)
    ElMessage.success(completeResult.value.message)
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交阅读失败'))
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  timer = window.setInterval(() => {
    elapsedMinutes.value = Math.floor((Date.now() - startedAt.value) / 60000)
  }, 30_000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="page reader-page">
    <button type="button" class="back-link" @click="router.push('/reading')">
      <el-icon><ArrowLeft /></el-icon> 阅读实验室
    </button>

    <div v-if="loading" class="card card-pad">
      <el-skeleton animated :rows="12" />
    </div>
    <div v-else-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="router.push('/reading')">回到文章库</el-button>
    </div>

    <template v-else-if="article">
      <header class="reader-head">
        <div class="reader-head-main">
          <div class="reader-tags">
            <span class="source-tag" :class="sourceClass(article.article.sourceType)">
              {{ typeLabel(article.article.sourceType) }}
            </span>
            <span class="pill">{{ article.article.cefrLevel }}</span>
            <span class="pill">{{ article.article.wordCount }} words</span>
            <span class="pill">约 {{ readingTime }} min</span>
            <span v-if="article.article.read" class="pill green">已读</span>
          </div>
          <h1>{{ article.article.title }}</h1>
          <p v-if="article.article.subtitle" class="reader-sub">{{ article.article.subtitle }}</p>
          <p class="reader-summary">{{ article.article.summary }}</p>
        </div>
        <div class="reader-controls">
          <span class="elapsed">{{ elapsedMinutes }} min</span>
          <el-button circle size="small" title="缩小字体" @click="decreaseFont">
            <el-icon><ZoomOut /></el-icon>
          </el-button>
          <span class="font-label">{{ Math.round(fontScale * 100) }}%</span>
          <el-button circle size="small" title="放大字体" @click="increaseFont">
            <el-icon><ZoomIn /></el-icon>
          </el-button>
        </div>
      </header>

      <div class="reader-layout">
        <article class="reader-main card">
          <div
            ref="articleBody"
            class="reading-prose"
            :style="{ fontSize: `${19 * fontScale}px` }"
            @mouseup="handleSelection"
            v-html="articleHtml"
          />

          <div class="article-end">
            <span>END OF ARTICLE</span>
            <div class="article-end-actions">
              <el-button @click="router.push('/reading')">返回文章库</el-button>
              <el-button
                type="primary"
                :loading="submitting"
                :disabled="Boolean(completeResult)"
                @click="completeReading"
              >
                <el-icon><Check /></el-icon> {{ completeResult ? '阅读已完成' : '完成阅读' }}
              </el-button>
            </div>
          </div>
        </article>

        <aside class="reader-side">
          <section class="card side-card toc-card">
            <p class="eyebrow">ON THIS PAGE</p>
            <h2>文章目录</h2>
            <div v-if="toc.length" class="toc-list">
              <button
                v-for="item in toc"
                :key="item.id"
                type="button"
                class="toc-item"
                :class="{ sub: item.level === 3 }"
                @click="scrollTo(item.id)"
              >
                {{ item.text }}
              </button>
            </div>
            <p v-else class="side-empty">这篇文章没有分节标题。</p>
          </section>

          <section id="assistant-panel" class="card side-card assistant-card">
            <div class="assistant-head">
              <div>
                <p class="eyebrow">AI READING ASSISTANT</p>
                <h2>选中一句话试试</h2>
              </div>
              <span class="ai-provider">{{ aiResult?.provider === 'deepseek' ? 'DeepSeek' : 'Demo AI' }}</span>
            </div>

            <div v-if="aiLoading" class="assistant-loading">
              <el-skeleton animated :rows="4" />
            </div>

            <div v-else-if="activePanel === 'word' && wordResult" class="word-panel">
              <div class="word-panel-head">
                <h3>{{ wordResult.lemma }}</h3>
                <span class="pill">{{ wordResult.pos }}</span>
              </div>
              <p class="word-phonetic">{{ wordResult.phonetic }}</p>
              <p class="word-zh">{{ wordResult.definitionZh }}</p>
              <p class="word-en">{{ wordResult.definitionEn }}</p>
              <p v-if="wordResult.exampleEn" class="word-example">{{ wordResult.exampleEn }}</p>
              <div class="word-actions">
                <span class="pill green">{{ wordResult.domain }}</span>
                <el-button
                  size="small"
                  type="primary"
                  plain
                  :disabled="wordResult.inDeck"
                  @click="addSelectedWord"
                >
                  {{ wordResult.inDeck ? '已在词汇本' : '加入生词本' }}
                </el-button>
              </div>
            </div>

            <div v-else-if="activePanel === 'word' && wordError && !aiResult" class="assistant-error">
              <p>{{ wordError }}</p>
              <el-button size="small" @click="explainSelected('word')">让 AI 解释</el-button>
            </div>

            <div v-else-if="activePanel === 'ai' && aiResult" class="ai-panel">
              <div v-if="aiResult.translation" class="ai-block">
                <span class="ai-label">TRANSLATION</span>
                <p class="ai-translation">{{ aiResult.translation }}</p>
              </div>
              <div class="ai-block">
                <span class="ai-label">EXPLANATION</span>
                <p>{{ aiResult.explanation }}</p>
              </div>
              <div v-if="aiResult.grammarPoints.length" class="ai-block">
                <span class="ai-label">GRAMMAR</span>
                <div v-for="point in aiResult.grammarPoints" :key="point.point" class="grammar-point">
                  <strong>{{ point.point }}</strong>
                  <p>{{ point.detail }}</p>
                </div>
              </div>
              <div v-if="aiResult.vocabulary.length" class="ai-block">
                <span class="ai-label">WORDS</span>
                <div v-for="word in aiResult.vocabulary" :key="word.word" class="vocab-hint">
                  <strong>{{ word.word }}</strong>
                  <span>{{ word.meaning }}</span>
                  <p>{{ word.note }}</p>
                </div>
              </div>
            </div>

            <div v-else-if="aiError" class="assistant-error">
              <p>{{ aiError }}</p>
              <el-button size="small" @click="explainSelected()">重试</el-button>
            </div>

            <div v-else class="assistant-hint">
              <div class="hint-icon"><el-icon><ChatDotRound /></el-icon></div>
              <p>用鼠标选中一个词或一句话，然后选择“查词”或“AI 解释”。</p>
            </div>
          </section>

          <section class="card side-card question-card">
            <div class="assistant-head">
              <div>
                <p class="eyebrow">COMPREHENSION</p>
                <h2>阅读理解</h2>
              </div>
              <span class="question-count">{{ answeredCount }} / {{ questionCount }}</span>
            </div>

            <div v-if="questionCount === 0" class="no-questions">
              <p>这是导入文章，暂时没有题目。完成阅读后仍会记录阅读时长。</p>
            </div>

            <div v-else class="question-list">
              <article v-for="(question, index) in article.questions" :key="question.id" class="question">
                <div class="question-title">
                  <span>{{ String(index + 1).padStart(2, '0') }}</span>
                  <p>{{ question.prompt }}</p>
                </div>
                <el-radio-group
                  v-if="question.options.length"
                  v-model="answers[question.id]"
                  class="question-options"
                  :disabled="Boolean(completeResult)"
                >
                  <el-radio v-for="option in question.options" :key="option" :value="option">
                    {{ option }}
                  </el-radio>
                </el-radio-group>
                <el-input
                  v-else
                  v-model="answers[question.id]"
                  placeholder="输入你的答案"
                  :disabled="Boolean(completeResult)"
                />
              </article>
            </div>

            <div v-if="completeResult" class="reading-result">
              <div class="result-line">
                <strong>{{ completeResult.correct }} / {{ completeResult.total }}</strong>
                <span>答对</span>
                <span class="score">{{ Math.round(completeResult.score) }} 分</span>
              </div>
              <p>{{ completeResult.message }}</p>
            </div>

            <el-button
              v-else
              type="primary"
              class="complete-button"
              :loading="submitting"
              :disabled="!allAnswered"
              @click="completeReading"
            >
              提交阅读记录
            </el-button>
            <p class="question-hint">提交后会记录阅读时长，并完成今日阅读任务。</p>
          </section>
        </aside>
      </div>

      <div v-if="toolbarVisible" class="selection-toolbar" :style="toolbarStyle" @mousedown.prevent>
        <button v-if="isWordSelection" type="button" @click="lookupSelected">
          <el-icon><Search /></el-icon> 查词
        </button>
        <button type="button" @click="explainSelected()">
          <el-icon><ChatDotRound /></el-icon> AI 解释
        </button>
        <button v-if="isWordSelection" type="button" @click="addSelectedWord">
          <el-icon><Plus /></el-icon> 加生词
        </button>
        <button type="button" class="close" title="关闭" @click="hideToolbar">
          <el-icon><Close /></el-icon>
        </button>
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

.reader-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  max-width: 1020px;
  margin-bottom: 26px;
}

.reader-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.source-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--muted);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.source-tag.ielts {
  background: #e9f1f6;
  color: var(--blue);
}

.source-tag.tech {
  background: var(--green-3);
  color: var(--green);
}

.source-tag.custom {
  background: var(--amber-soft);
  color: #8d4d1d;
}

.reader-head h1 {
  max-width: 900px;
  font-family: var(--font-reading);
  font-size: clamp(34px, 4.4vw, 56px);
  font-weight: 500;
  letter-spacing: -0.045em;
  line-height: 1.04;
}

.reader-sub {
  margin-top: 8px;
  color: var(--muted);
  font-family: var(--font-reading);
  font-size: 17px;
  font-style: italic;
}

.reader-summary {
  max-width: 800px;
  margin-top: 12px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.reader-controls {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  padding: 7px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
}

.elapsed {
  margin-right: 4px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.font-label {
  min-width: 34px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8px;
  text-align: center;
}

.reader-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(300px, 0.62fr);
  gap: 20px;
  align-items: start;
}

.reader-main {
  padding: clamp(24px, 4vw, 54px);
}

.reader-main :deep(.reading-prose) {
  max-width: 760px;
  margin: 0 auto;
}

.reader-main :deep(.reading-prose h2) {
  scroll-margin-top: 100px;
}

.reader-main :deep(.reading-prose h3) {
  scroll-margin-top: 100px;
}

.article-end {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  max-width: 760px;
  margin: 50px auto 0;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.article-end > span {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.15em;
}

.article-end-actions {
  display: flex;
  gap: 7px;
}

.reader-side {
  display: grid;
  gap: 14px;
  position: sticky;
  top: 100px;
}

.side-card {
  padding: 20px;
}

.side-card h2 {
  margin-top: 3px;
  font-family: var(--font-reading);
  font-size: 21px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.toc-list {
  display: grid;
  gap: 1px;
  margin-top: 12px;
}

.toc-item {
  padding: 6px 8px;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 0 5px 5px 0;
  background: transparent;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.5;
  cursor: pointer;
  text-align: left;
}

.toc-item:hover {
  border-left-color: var(--green-2);
  background: var(--surface-2);
  color: var(--ink);
}

.toc-item.sub {
  padding-left: 18px;
  font-size: 9px;
}

.side-empty {
  margin-top: 10px;
  color: var(--faint);
  font-size: 10px;
}

.assistant-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.ai-provider {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 7px;
}

.assistant-loading {
  margin-top: 16px;
}

.assistant-hint {
  display: grid;
  justify-items: center;
  gap: 9px;
  margin-top: 18px;
  padding: 22px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: 11px;
  text-align: center;
}

.hint-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: var(--green-3);
  color: var(--green);
  font-size: 17px;
}

.assistant-hint p {
  max-width: 230px;
  color: var(--faint);
  font-size: 10px;
  line-height: 1.65;
}

.ai-block {
  margin-top: 15px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.ai-block:first-child {
  border-top: 0;
  padding-top: 0;
}

.ai-label {
  color: var(--faint);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.ai-translation {
  margin-top: 4px;
  color: var(--ink);
  font-family: var(--font-reading);
  font-size: 17px;
  line-height: 1.5;
}

.ai-block > p {
  margin-top: 4px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}

.grammar-point {
  margin-top: 9px;
  padding: 9px 10px;
  border-radius: 8px;
  background: var(--surface-2);
}

.grammar-point strong {
  font-size: 10px;
}

.grammar-point p {
  margin-top: 2px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.6;
}

.vocab-hint {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 3px 7px;
  margin-top: 9px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--line);
}

.vocab-hint strong {
  font-family: var(--font-reading);
  font-size: 14px;
}

.vocab-hint span {
  color: var(--muted);
  font-size: 10px;
}

.vocab-hint p {
  grid-column: 1 / -1;
  color: var(--faint);
  font-size: 9px;
  line-height: 1.5;
}

.word-panel-head {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin-top: 14px;
}

.word-panel-head h3 {
  font-family: var(--font-reading);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.word-phonetic {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.word-zh {
  margin-top: 9px;
  color: var(--ink);
  font-size: 12px;
}

.word-en {
  margin-top: 3px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.6;
}

.word-example {
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
  color: var(--ink-soft);
  font-family: var(--font-reading);
  font-size: 14px;
  line-height: 1.5;
}

.word-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 14px;
}

.assistant-error {
  margin-top: 14px;
  padding: 11px 12px;
  border-radius: 8px;
  background: #f9edeb;
  color: var(--rose);
  font-size: 10px;
  line-height: 1.6;
}

.assistant-error .el-button {
  margin-top: 8px;
}

.question-count {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.question-list {
  display: grid;
  gap: 18px;
  margin-top: 17px;
}

.question {
  padding-top: 13px;
  border-top: 1px solid var(--line);
}

.question:first-child {
  padding-top: 0;
  border-top: 0;
}

.question-title {
  display: flex;
  gap: 8px;
}

.question-title > span {
  padding-top: 1px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
}

.question-title p {
  color: var(--ink);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.6;
}

.question-options {
  display: grid;
  gap: 3px;
  margin: 8px 0 0 18px;
}

.question-options :deep(.el-radio) {
  height: auto;
  margin-right: 0;
  padding: 3px 0;
  align-items: flex-start;
}

.question-options :deep(.el-radio__label) {
  font-size: 10px;
  line-height: 1.45;
  white-space: normal;
}

.no-questions {
  margin-top: 13px;
  padding: 13px;
  border-radius: 9px;
  background: var(--surface-2);
  color: var(--muted);
  font-size: 10px;
  line-height: 1.65;
}

.reading-result {
  margin-top: 16px;
  padding: 13px;
  border-radius: 9px;
  background: var(--green-3);
}

.result-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.result-line strong {
  color: var(--green);
  font-family: var(--font-reading);
  font-size: 24px;
  font-weight: 600;
}

.result-line span {
  color: var(--muted);
  font-size: 9px;
}

.result-line .score {
  margin-left: auto;
  color: var(--green);
  font-weight: 700;
}

.reading-result p {
  margin-top: 3px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.6;
}

.complete-button {
  width: 100%;
  margin-top: 16px;
}

.question-hint {
  margin-top: 8px;
  color: var(--faint);
  font-size: 8px;
  line-height: 1.5;
  text-align: center;
}

.selection-toolbar {
  position: fixed;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 1px;
  width: max-content;
  max-width: calc(100vw - 24px);
  padding: 4px;
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 9px;
  background: #1b2e23;
  box-shadow: 0 12px 30px rgb(20 36 27 / 28%);
  animation: rise-in 0.16s ease both;
}

.selection-toolbar button {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #e9f2ec;
  font-size: 10px;
  cursor: pointer;
  white-space: nowrap;
}

.selection-toolbar button:hover {
  background: rgb(255 255 255 / 10%);
}

.selection-toolbar button.close {
  padding-inline: 6px;
  color: rgb(233 242 236 / 50%);
}

@media (max-width: 1100px) {
  .reader-layout {
    grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.65fr);
  }
}

@media (max-width: 900px) {
  .reader-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .reader-controls {
    align-self: flex-end;
  }

  .reader-layout {
    grid-template-columns: 1fr;
  }

  .reader-side {
    position: static;
  }

  .selection-toolbar {
    bottom: 18px;
    top: auto !important;
    left: 50% !important;
    transform: translateX(-50%) !important;
  }
}

@media (max-width: 600px) {
  .reader-main {
    padding: 22px 18px;
  }

  .article-end {
    align-items: flex-start;
    flex-direction: column;
  }

  .reader-head h1 {
    font-size: 35px;
  }
}
</style>
