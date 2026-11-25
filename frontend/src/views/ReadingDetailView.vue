<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ArrowUp,
  ChatDotRound,
  Check,
  Close,
  EditPen,
  List,
  Loading,
  Plus,
  Search,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import ReadingAssistantPanel from '@/components/ReadingAssistantPanel.vue'
import ReadingComprehensionPanel from '@/components/ReadingComprehensionPanel.vue'
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

// 文章目录做成右侧折叠抽屉：展开时悬浮在正文右侧的空白区上，不挤压阅读区宽度。
const TOC_OPEN_KEY = 'reading.tocOpen'
const tocOpen = ref(localStorage.getItem(TOC_OPEN_KEY) === '1')
watch(tocOpen, (open) => localStorage.setItem(TOC_OPEN_KEY, open ? '1' : '0'))

const selectedText = ref('')
const selectedContext = ref('')
const selectionRect = ref<DOMRect | null>(null)
const toolbarVisible = computed(() => Boolean(selectedText.value && selectionRect.value))
const isWordSelection = computed(() => /^[A-Za-z][A-Za-z'-]*$/.test(selectedText.value.trim()))

// Right-click context menu on the article body: translate / explain the selection.
const ctxMenuVisible = ref(false)
const ctxMenuEl = ref<HTMLElement | null>(null)
const ctxMode = ref<'menu' | 'result'>('menu')
const ctxX = ref(0)
const ctxY = ref(0)
const ctxLoading = ref(false)
const ctxMoreLoading = ref(false)
const ctxTranslation = ref('')
const ctxError = ref('')
const ctxSource = ref('')
const ctxContext = ref('')
const ctxIsWord = ref(false)

const ctxMenuStyle = computed(() => {
  const width = ctxMode.value === 'result' ? 250 : 190
  const height = ctxMode.value === 'result' ? 190 : 140
  const left = Math.max(8, Math.min(ctxX.value, window.innerWidth - width - 8))
  const top = Math.max(8, Math.min(ctxY.value, window.innerHeight - height - 8))
  return { left: `${left}px`, top: `${top}px` }
})

// 助手与阅读理解都改为右下角浮层，不再常驻侧栏占用阅读区宽度。
const assistantOpen = ref(false)
const questionsOpen = ref(false)

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

// Resume-reading: scroll progress (0-100) synced to the backend, plus the back-to-top control.
const showBackTop = ref(false)
const savedPercent = ref(0)
let pendingPercent = 0
let progressTimer: number | undefined

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
  flushProgress()
  window.scrollTo({ top: 0, behavior: 'auto' })
  loading.value = true
  error.value = ''
  completeResult.value = null
  activePanel.value = 'none'
  assistantOpen.value = false
  questionsOpen.value = false
  aiResult.value = null
  wordResult.value = null
  showBackTop.value = false
  savedPercent.value = 0
  pendingPercent = 0
  Object.keys(answers).forEach((key) => delete answers[Number(key)])
  try {
    article.value = await readingApi.detail(Number(id))
    savedPercent.value = article.value.scrollPercent ?? 0
    pendingPercent = savedPercent.value
    startedAt.value = Date.now()
    elapsedMinutes.value = 0
  } catch (err) {
    error.value = errorMessage(err, '加载文章失败')
    article.value = null
  } finally {
    loading.value = false
  }
  if (article.value) {
    await restoreReadingPosition()
  }
}

async function restoreReadingPosition() {
  const percent = savedPercent.value
  if (percent < 2 || percent > 97) return
  await nextTick()
  const max = document.documentElement.scrollHeight - window.innerHeight
  if (max < 400) return
  window.scrollTo({ top: Math.round((percent / 100) * max), behavior: 'auto' })
  ElMessage.info(`已跳转到上次阅读位置（${percent}%）`)
}

function pageScrollPercent() {
  const max = document.documentElement.scrollHeight - window.innerHeight
  if (max <= 0) return 0
  return Math.min(100, Math.max(0, Math.round((window.scrollY / max) * 100)))
}

function flushProgress() {
  if (progressTimer !== undefined) {
    window.clearTimeout(progressTimer)
    progressTimer = undefined
  }
  const id = article.value?.article.id
  if (!id || completeResult.value) return
  if (Math.abs(pendingPercent - savedPercent.value) < 1) return
  savedPercent.value = pendingPercent
  readingApi.saveProgress(id, pendingPercent).catch(() => {})
}

function onWindowScroll() {
  showBackTop.value = window.scrollY > 480
  if (ctxMenuVisible.value) closeCtxMenu()
  if (!article.value || completeResult.value) return
  pendingPercent = pageScrollPercent()
  if (progressTimer === undefined) {
    progressTimer = window.setTimeout(flushProgress, 2000)
  }
}

function backToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
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

function isWordChar(ch: string | undefined) {
  return Boolean(ch && /[A-Za-z'-]/.test(ch))
}

// Select the word sitting under the caret at (x, y) so right-clicking a bare word works.
function selectWordAtPoint(x: number, y: number) {
  const hit =
    typeof document.caretRangeFromPoint === 'function'
      ? document.caretRangeFromPoint(x, y)
      : null
  let node: Node | null = hit?.startContainer ?? null
  let offset = hit?.startOffset ?? 0
  if (!node && typeof (document as any).caretPositionFromPoint === 'function') {
    const pos = (document as any).caretPositionFromPoint(x, y)
    node = pos?.offsetNode ?? null
    offset = pos?.offset ?? 0
  }
  if (!node || node.nodeType !== Node.TEXT_NODE || !articleBody.value?.contains(node)) return false
  const text = node.textContent ?? ''
  let start = offset
  if (!isWordChar(text[start]) && isWordChar(text[start - 1])) start -= 1
  if (!isWordChar(text[start])) return false
  let left = start
  while (left > 0 && isWordChar(text[left - 1])) left -= 1
  let right = start
  while (right < text.length - 1 && isWordChar(text[right + 1])) right += 1
  const range = document.createRange()
  range.setStart(node, left)
  range.setEnd(node, right + 1)
  const selection = window.getSelection()
  selection?.removeAllRanges()
  selection?.addRange(range)
  return true
}

function onArticleContextMenu(event: MouseEvent) {
  if (!articleBody.value?.contains(event.target as Node)) return
  const selection = window.getSelection()
  if (!selection?.toString().trim() && !selectWordAtPoint(event.clientX, event.clientY)) return
  handleSelection()
  if (!selectedText.value) return
  event.preventDefault()
  selectionRect.value = null // suppress the floating toolbar while the menu is open
  // Snapshot the selection: the shared selectedText/selectedContext can be
  // cleared by mouseup etc. while the menu is open, and every action below
  // must operate on exactly what the user right-clicked.
  ctxSource.value = selectedText.value
  ctxContext.value = selectedContext.value
  ctxIsWord.value = isWordSelection.value
  ctxMode.value = 'menu'
  ctxTranslation.value = ''
  ctxError.value = ''
  ctxX.value = event.clientX
  ctxY.value = event.clientY
  ctxMenuVisible.value = true
}

function closeCtxMenu() {
  ctxMenuVisible.value = false
  ctxLoading.value = false
}

function onCtxDocPointerDown(event: PointerEvent) {
  if (ctxMenuEl.value?.contains(event.target as Node)) return
  closeCtxMenu()
}

function onCtxKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeCtxMenu()
}

async function ctxTranslate() {
  ctxMode.value = 'result'
  ctxLoading.value = true
  ctxError.value = ''
  ctxTranslation.value = ''
  try {
    const result = await aiApi.explain({
      text: ctxSource.value.trim(),
      context: selectedContext.value,
      mode: ctxIsWord.value ? 'word' : 'sentence',
    })
    ctxTranslation.value = result.translation || result.explanation || '暂无翻译结果'
  } catch (err) {
    ctxError.value = errorMessage(err, '翻译失败')
  } finally {
    ctxLoading.value = false
  }
}

async function ctxExplain() {
  // Always act on the snapshot captured when the menu opened, never on the
  // shared selection state which can be cleared before this click lands.
  const text = ctxSource.value.trim()
  if (!text) {
    closeCtxMenu()
    return
  }
  ctxMoreLoading.value = true
  try {
    await explainSelected(ctxIsWord.value ? 'word' : 'sentence', { text, context: ctxContext.value })
    closeCtxMenu()
  } finally {
    ctxMoreLoading.value = false
  }
}

function ctxLookup() {
  closeCtxMenu()
  void lookupSelected(ctxSource.value)
}

function ctxAddWord() {
  closeCtxMenu()
  void addSelectedWord(ctxSource.value)
}

async function lookupSelected(override?: string) {
  const word = (override ?? selectedText.value).trim()
  if (!word) return
  wordLoading.value = true
  wordError.value = ''
  wordResult.value = null
  activePanel.value = 'word'
  assistantOpen.value = true
  try {
    wordResult.value = await vocabularyApi.lookup(word)
  } catch (err) {
    wordError.value = errorMessage(err, '词库里暂时没有这个词')
    await explainSelected('word', { text: word })
  } finally {
    wordLoading.value = false
  }
}

async function explainSelected(mode?: 'word' | 'sentence', override?: { text?: string; context?: string }) {
  const text = (override?.text ?? selectedText.value).trim()
  if (!text) return
  aiLoading.value = true
  aiError.value = ''
  aiResult.value = null
  activePanel.value = 'ai'
  assistantOpen.value = true
  try {
    aiResult.value = await aiApi.explain({
      text,
      context: override?.context ?? selectedContext.value,
      mode: mode ?? (isWordSelection.value ? 'word' : 'sentence'),
    })
  } catch (err) {
    aiError.value = errorMessage(err, 'AI 暂时不可用')
  } finally {
    aiLoading.value = false
  }
}

async function addSelectedWord(override?: string) {
  // An explicit override (context-menu path) wins over the last lookup's lemma,
  // which may be stale from a previously different word.
  const word = (override ?? wordResult.value?.lemma ?? selectedText.value).trim()
  if (!word) return
  try {
    await vocabularyApi.add(word, override ? ctxContext.value : selectedContext.value)
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

function setAnswer(questionId: number, value: string) {
  answers[questionId] = value
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
    savedPercent.value = 0
    pendingPercent = 0
    await completeTaskByRef('READING', article.value.article.id)
    ElMessage.success(completeResult.value.message)
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交阅读失败'))
  } finally {
    submitting.value = false
  }
}

watch(ctxMenuVisible, (visible) => {
  if (visible) {
    document.addEventListener('pointerdown', onCtxDocPointerDown, true)
    document.addEventListener('keydown', onCtxKeyDown)
  } else {
    document.removeEventListener('pointerdown', onCtxDocPointerDown, true)
    document.removeEventListener('keydown', onCtxKeyDown)
  }
})

onMounted(() => {
  timer = window.setInterval(() => {
    elapsedMinutes.value = Math.floor((Date.now() - startedAt.value) / 60000)
  }, 30_000)
  window.addEventListener('scroll', onWindowScroll, { passive: true })
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  window.removeEventListener('scroll', onWindowScroll)
  document.removeEventListener('pointerdown', onCtxDocPointerDown, true)
  document.removeEventListener('keydown', onCtxKeyDown)
  flushProgress()
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
            :style="{ fontSize: `${20 * fontScale}px` }"
            @mouseup="handleSelection"
            @contextmenu="onArticleContextMenu"
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

        <!-- 外层页面容器的入场 transform 会把 fixed 包含块从视口改成祖先，抽屉和入口必须 Teleport 到 body。 -->
        <Teleport to="body">
        <aside class="toc-drawer card" :class="{ open: tocOpen }" aria-label="文章目录">
          <div class="toc-drawer-head">
            <div>
              <p class="eyebrow">ON THIS PAGE</p>
              <h2>文章目录</h2>
            </div>
            <el-button circle size="small" title="收起目录" @click="tocOpen = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <nav v-if="toc.length" class="toc-list">
            <button
              v-for="item in toc"
              :key="item.id"
              type="button"
              class="toc-item"
              :class="{ sub: item.level === 3, deep: item.level >= 4 }"
              @click="scrollTo(item.id)"
            >
              {{ item.text }}
            </button>
          </nav>
          <p v-else class="side-empty">这篇文章没有分节标题。</p>
        </aside>

        <div class="side-tabs">
          <button
            v-if="!tocOpen"
            type="button"
            class="side-tab"
            title="展开文章目录"
            @click="tocOpen = true"
          >
            <el-icon><List /></el-icon>
            <span>目录</span>
          </button>
          <button
            type="button"
            class="side-tab"
            :class="{ active: questionsOpen }"
            title="阅读理解"
            @click="questionsOpen = !questionsOpen"
          >
            <el-icon><EditPen /></el-icon>
            <span>题目</span>
            <span v-if="questionCount" class="tab-count">{{ questionCount }}</span>
          </button>
        </div>

        <div class="reader-dock">
          <ReadingAssistantPanel
            v-model:open="assistantOpen"
            :panel="activePanel"
            :word-loading="wordLoading"
            :word-result="wordResult"
            :word-error="wordError"
            :ai-loading="aiLoading"
            :ai-result="aiResult"
            :ai-error="aiError"
            @explain="explainSelected"
            @add-word="addSelectedWord()"
          />
          <ReadingComprehensionPanel
            v-model:open="questionsOpen"
            :questions="article.questions"
            :answers="answers"
            :submitting="submitting"
            :complete-result="completeResult"
            :answered-count="answeredCount"
            :question-count="questionCount"
            :all-answered="allAnswered"
            @answer="setAnswer"
            @submit="completeReading"
          />
        </div>
        </Teleport>
      </div>

      <div v-if="toolbarVisible" class="selection-toolbar" :style="toolbarStyle" @mousedown.prevent>
        <button v-if="isWordSelection" type="button" @click="lookupSelected()">
          <el-icon><Search /></el-icon> 查词
        </button>
        <button type="button" @click="explainSelected()">
          <el-icon><ChatDotRound /></el-icon> AI 解释
        </button>
        <button v-if="isWordSelection" type="button" @click="addSelectedWord()">
          <el-icon><Plus /></el-icon> 加生词
        </button>
        <button type="button" class="close" title="关闭" @click="hideToolbar">
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <Teleport to="body">
        <div
          v-if="ctxMenuVisible"
          ref="ctxMenuEl"
          class="ctx-menu"
          :style="ctxMenuStyle"
          @mousedown.prevent
          @contextmenu.prevent
        >
          <template v-if="ctxMode === 'menu'">
            <p class="ctx-quote">{{ ctxSource.length > 32 ? ctxSource.slice(0, 32) + '…' : ctxSource }}</p>
            <button type="button" class="ctx-item" @click="ctxTranslate">
              <span class="ctx-glyph">文A</span> 翻译选中内容
            </button>
            <button
              type="button"
              class="ctx-item"
              :disabled="ctxMoreLoading"
              @click="ctxExplain"
            >
              <el-icon v-if="ctxMoreLoading" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><ChatDotRound /></el-icon>
              {{ ctxMoreLoading ? '正在解析…' : 'AI 详解（右下助手）' }}
            </button>
            <button v-if="ctxIsWord" type="button" class="ctx-item" @click="ctxLookup">
              <el-icon><Search /></el-icon> 查词库
            </button>
            <button v-if="ctxIsWord" type="button" class="ctx-item" @click="ctxAddWord">
              <el-icon><Plus /></el-icon> 加入生词本
            </button>
          </template>
          <div v-else class="ctx-result">
            <div class="ctx-result-head">
              <span>译文</span>
              <button type="button" class="ctx-close" title="关闭" @click="closeCtxMenu">
                <el-icon><Close /></el-icon>
              </button>
            </div>
            <p v-if="ctxLoading" class="ctx-loading">
              <el-icon class="is-loading"><Loading /></el-icon> 正在翻译…
            </p>
            <p v-else-if="ctxError" class="ctx-err">{{ ctxError }}</p>
            <p v-else class="ctx-translation">{{ ctxTranslation }}</p>
            <button
              v-if="!ctxLoading && !ctxError"
              type="button"
              class="ctx-more"
              :disabled="ctxMoreLoading"
              @click="ctxExplain"
            >
              <el-icon v-if="ctxMoreLoading" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><ChatDotRound /></el-icon>
              {{ ctxMoreLoading ? '正在解析…' : '查看完整解析' }}
            </button>
          </div>
        </div>
      </Teleport>

      <button v-show="showBackTop" type="button" class="back-top" title="回到顶部" @click="backToTop">
        <el-icon><ArrowUp /></el-icon>
        <span>顶部</span>
      </button>
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
  font-size: 13px;
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
  padding: 5px 10px;
  border-radius: 6px;
  background: var(--surface-3);
  color: var(--muted);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.source-tag.ielts {
  background: var(--blue-soft);
  color: var(--blue);
}

.source-tag.tech {
  background: var(--green-3);
  color: var(--green);
}

.source-tag.custom {
  background: var(--amber-soft);
  color: var(--amber-text);
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
  font-size: 14px;
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
  font-size: 11px;
}

.font-label {
  min-width: 38px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
  text-align: center;
}

.reader-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
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
  font-size: 10px;
  letter-spacing: 0.15em;
}

.article-end-actions {
  display: flex;
  gap: 7px;
}

.toc-list {
  display: grid;
  gap: 1px;
  margin-top: 12px;
}

.toc-item {
  padding: 7px 9px;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 0 5px 5px 0;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.55;
  cursor: pointer;
  text-align: left;
}

.toc-item:hover {
  border-left-color: var(--green-2);
  background: var(--surface-2);
  color: var(--ink);
}

.toc-item.sub {
  padding-left: 22px;
  font-size: 12px;
}

.toc-item.deep {
  padding-left: 34px;
  font-size: 12px;
  color: var(--muted);
}

/* 抽屉本体：固定在视口右缘，自身独立滚动，目录再长也不会顶出页面。 */
.toc-drawer {
  position: fixed;
  top: 96px;
  right: 0;
  bottom: 24px;
  z-index: 80;
  width: 288px;
  padding: 20px 12px 20px 20px;
  border-right: 0;
  border-radius: var(--radius) 0 0 var(--radius);
  overflow-y: auto;
  overscroll-behavior: contain;
  transform: translateX(105%);
  transition: transform 0.28s ease;
}

.toc-drawer.open {
  transform: none;
}

.toc-drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-right: 8px;
}

.toc-drawer-head h2 {
  margin-top: 3px;
  font-family: var(--font-reading);
  font-size: 21px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

/* 右缘悬浮入口按钮列：目录收起时的「目录」入口与「题目」开关。 */
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
  left: -7px;
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

/* 右下角浮层停靠区：AI 助手与阅读理解弹窗共用，浮在返回顶部按钮上方。 */
.reader-dock {
  position: fixed;
  right: 24px;
  bottom: 88px;
  z-index: 95;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  pointer-events: none;
}

.reader-dock > * {
  pointer-events: auto;
}

.side-empty {
  margin-top: 10px;
  color: var(--faint);
  font-size: 12px;
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
  font-size: 12px;
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

/* Right-click translation menu — same dark floating surface as the selection toolbar. */
.ctx-menu {
  position: fixed;
  z-index: 110;
  min-width: 178px;
  max-width: min(300px, calc(100vw - 20px));
  padding: 5px;
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 10px;
  background: #1b2e23;
  box-shadow: 0 14px 34px rgb(10 20 14 / 34%);
  animation: rise-in 0.14s ease both;
}

.ctx-quote {
  margin: 2px 6px 5px;
  padding-bottom: 5px;
  border-bottom: 1px solid rgb(255 255 255 / 10%);
  overflow: hidden;
  color: rgb(233 242 236 / 55%);
  font-family: var(--font-reading);
  font-size: 11.5px;
  font-style: italic;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ctx-item {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 7px 9px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #e9f2ec;
  font-size: 12.5px;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}

.ctx-item:hover {
  background: rgb(255 255 255 / 10%);
}

.ctx-item:disabled,
.ctx-more:disabled {
  color: rgb(233 242 236 / 55%);
  cursor: default;
}

.ctx-glyph {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  color: rgb(233 242 236 / 80%);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 800;
}

.ctx-result {
  padding: 8px 10px 7px;
}

.ctx-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: rgb(233 242 236 / 55%);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.ctx-close {
  display: grid;
  place-items: center;
  padding: 2px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: rgb(233 242 236 / 50%);
  cursor: pointer;
}

.ctx-close:hover {
  background: rgb(255 255 255 / 10%);
  color: #e9f2ec;
}

.ctx-translation {
  margin-top: 5px;
  max-height: 42vh;
  overflow-y: auto;
  color: #e9f2ec;
  font-family: var(--font-reading);
  font-size: 14px;
  line-height: 1.65;
}

.ctx-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 7px;
  color: rgb(233 242 236 / 65%);
  font-size: 12px;
}

.ctx-err {
  margin-top: 6px;
  color: #f0a8a8;
  font-size: 12px;
  line-height: 1.6;
}

.ctx-more {
  display: flex;
  align-items: center;
  gap: 5px;
  width: 100%;
  margin-top: 7px;
  padding: 6px 8px;
  border: 0;
  border-top: 1px solid rgb(255 255 255 / 10%);
  border-radius: 0 0 6px 6px;
  background: transparent;
  color: rgb(233 242 236 / 75%);
  font-size: 12px;
  cursor: pointer;
}

.ctx-more:hover {
  background: rgb(255 255 255 / 8%);
  color: #e9f2ec;
}

.back-top {
  position: fixed;
  right: 28px;
  bottom: 32px;
  z-index: 90;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  box-shadow: var(--shadow-md);
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease;
}

.back-top:hover {
  border-color: var(--green-line);
  color: var(--green);
}

@media (max-width: 900px) {
  .reader-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .reader-controls {
    align-self: flex-end;
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

  .back-top {
    right: 16px;
    bottom: 20px;
  }

  .reader-dock {
    right: 16px;
    bottom: 76px;
  }
}
</style>
