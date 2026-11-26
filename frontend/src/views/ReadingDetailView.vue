<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Aim,
  ArrowLeft,
  ArrowUp,
  ChatDotRound,
  Check,
  Close,
  EditPen,
  List,
  MagicStick,
  Plus,
  PriceTag,
  Search,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import ContentDrawer from '@/components/common/ContentDrawer.vue'
import ReadingAssistantPanel from '@/components/reading/ReadingAssistantPanel.vue'
import ReadingComprehensionPanel from '@/components/reading/ReadingComprehensionPanel.vue'
import ReadingQuizPanel from '@/components/reading/ReadingQuizPanel.vue'
import ReadingToc from '@/components/reading/ReadingToc.vue'
import VocabSettlementDialog from '@/components/reading/VocabSettlementDialog.vue'
import { normWord, useVocabLedger, type SettleSummary } from '@/composables/useVocabLedger'
import { aiApi, readingApi, vocabularyApi } from '@/api'
import { completeTaskByRef } from '@/composables/useTaskCompletion'
import { extractSections, extractToc, renderMarkdown } from '@/utils/markdown'
import { errorMessage } from '@/utils/error'
import type {
  AiExplainResponse,
  ReadingArticleDetail,
  ReadingCompleteResponse,
  VocabLookup,
  VocabularyBatchItem,
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

// --- AI 自测的「当前小节」：滚动到哪一节，出题就针对哪一节 ---------------------
// toc 与 sections 来自同一份标题扫描，下标一一对应。
const sections = computed(() => extractSections(article.value?.contentMd ?? ''))
const activeHeadingIndex = ref(-1)

function updateActiveHeading() {
  let idx = -1
  for (let i = 0; i < toc.value.length; i++) {
    const el = document.getElementById(toc.value[i].id)
    if (!el) continue
    if (el.getBoundingClientRect().top <= 140) idx = i
    else break
  }
  activeHeadingIndex.value = idx
}

const MAX_SECTION_CHARS = 18000

/** null 时后端退回整篇文章出题（无标题的短文）。 */
const currentQuizSection = computed(() => {
  const section = sections.value[activeHeadingIndex.value]
  if (!section || !article.value) return null
  const text = article.value.contentMd.split('\n').slice(section.startLine, section.endLine).join('\n')
  return { title: section.text, text: text.slice(0, MAX_SECTION_CHARS) }
})

// 文章目录做成右侧折叠抽屉：展开时悬浮在正文右侧的空白区上，不挤压阅读区宽度。
const TOC_OPEN_KEY = 'reading.tocOpen'
const tocOpen = ref(localStorage.getItem(TOC_OPEN_KEY) === '1')
watch(tocOpen, (open) => localStorage.setItem(TOC_OPEN_KEY, open ? '1' : '0'))

// 读后生词账本：猜词/查词按文章记录在 localStorage，结算页从中读取。
const ledgerDocId = computed(() => {
  const id = Number(route.params.id)
  return Number.isFinite(id) && id > 0 ? id : null
})
const {
  ledger,
  recordGuess,
  recordLooked,
  setGuessDraft,
  setGuessVerdict,
  setScanned,
  removeWords,
  processedWords,
  pendingCount,
} = useVocabLedger(ledgerDocId)
const settlementOpen = ref(false)
const judgingWord = ref('')
const scanning = ref(false)
const scanError = ref('')

const selectedText = ref('')
const selectedContext = ref('')
const selectionRect = ref<DOMRect | null>(null)
const toolbarVisible = computed(() => Boolean(selectedText.value && selectionRect.value))
const isWordSelection = computed(() => /^[A-Za-z][A-Za-z'-]*$/.test(selectedText.value.trim()))

// 左侧抽屉同一时刻只承载一个面板：AI 助手或阅读理解，互斥切换。预习是独立页面。
const activeDrawer = ref<'none' | 'assistant' | 'questions'>('none')
const panelDrawerTitle = computed(() =>
  activeDrawer.value === 'assistant' ? 'AI 助手' : '阅读理解',
)
const panelDrawerEyebrow = computed(() =>
  activeDrawer.value === 'assistant' ? 'AI READING ASSISTANT' : 'COMPREHENSION',
)

function toggleQuestions() {
  activeDrawer.value = activeDrawer.value === 'questions' ? 'none' : 'questions'
  if (activeDrawer.value === 'questions') void nextTick(updateActiveHeading)
}

function onPanelDrawerToggle(open: boolean) {
  if (!open) activeDrawer.value = 'none'
}

const activePanel = ref<'none' | 'word' | 'ai'>('none')
const wordResult = ref<VocabLookup | null>(null)
const wordLoading = ref(false)
const wordError = ref('')
const aiResult = ref<AiExplainResponse | null>(null)
const aiLoading = ref(false)
const aiError = ref('')

// 预习在独立页面完成（reading-preview 路由）；本页只负责进文章前的询问与侧签角标。
const previewAskOpen = ref(false)
const hasSavedPreview = ref(false)

function goPreview() {
  previewAskOpen.value = false
  router.push({ name: 'reading-preview', params: { id: Number(route.params.id) } })
}

async function loadPreviewState(id: number) {
  previewAskOpen.value = false
  hasSavedPreview.value = false
  try {
    const state = await readingApi.previewState(id)
    if (state.exists) {
      hasSavedPreview.value = true
    } else if (article.value && !article.value.article.read) {
      // 进入文章前的询问：只在该用户还没存过本篇预习、且文章未读完时弹出。
      previewAskOpen.value = true
    }
  } catch {
    // 预习是可选增强，状态查询失败不打扰阅读。
  }
}

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
  activeDrawer.value = 'none'
  aiResult.value = null
  wordResult.value = null
  showBackTop.value = false
  savedPercent.value = 0
  pendingPercent = 0
  settlementOpen.value = false
  scanError.value = ''
  judgingWord.value = ''
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
    applyWordMarks()
    void loadPreviewState(Number(id))
  }
}

async function restoreReadingPosition() {
  const percent = savedPercent.value
  if (percent < 2 || percent > 97) return
  await nextTick()
  const max = document.documentElement.scrollHeight - window.innerHeight
  if (max < 400) return
  window.scrollTo({ top: Math.round((percent / 100) * max), behavior: 'auto' })
  updateActiveHeading()
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
  if (activeDrawer.value === 'questions') updateActiveHeading()
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

async function lookupSelected() {
  const word = selectedText.value.trim()
  if (!word) return
  wordLoading.value = true
  wordError.value = ''
  wordResult.value = null
  activePanel.value = 'word'
  activeDrawer.value = 'assistant'
  try {
    wordResult.value = await vocabularyApi.lookup(word)
    recordLooked(word, sentenceWithWord(selectedContext.value, word), wordResult.value)
  } catch (err) {
    wordError.value = errorMessage(err, '词库里暂时没有这个词')
    await explainSelected('word', { text: word })
    // Record even when the AI fallback failed: the word was still looked up,
    // and the settlement page renders a raw word when definitions are absent.
    recordLooked(word, sentenceWithWord(selectedContext.value, word), null, {
      translation: aiResult.value?.translation ?? '',
      explanation: aiResult.value?.explanation ?? '',
    })
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
  activeDrawer.value = 'assistant'
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

async function addSelectedWord() {
  const word = (wordResult.value?.lemma ?? selectedText.value).trim()
  if (!word) return
  const context = selectedContext.value
  const pending = ElMessage({ message: '正在加入生词本…', duration: 0 })
  try {
    // The curated dictionary gives pos/phonetic/definitions for free. A word met
    // while reading is often outside it; those are still collectable.
    let lookup: VocabLookup | null = null
    try {
      lookup = await vocabularyApi.lookup(word)
    } catch {
      // Not in the seed dictionary.
    }
    if (lookup?.inDeck) {
      ElMessage.info('这个词已经在词汇本里了')
      return
    }
    const details = lookup
      ? { definitionZh: lookup.definitionZh, definitionEn: lookup.definitionEn, pos: lookup.pos }
      : await aiDefinitionFor(word, context)
    await vocabularyApi.add({
      lemma: lookup?.lemma ?? word,
      contextSentence: context,
      ...details,
    })
    if (wordResult.value) wordResult.value.inDeck = true
    // 即时入库也算处理过，从读后账本里清掉，避免结算页重复。
    removeWords([word, lookup?.lemma ?? ''])
    ElMessage.success('已加入词汇本')
  } catch (err) {
    ElMessage.error(errorMessage(err, '加入失败'))
  } finally {
    pending.close()
  }
}

/**
 * Words outside the seed dictionary are saved with an empty definition when AI is
 * unavailable, so collection never blocks on the network. The demo model returns a
 * fixed unrelated payload, so it is not used as a definition source.
 */
async function aiDefinitionFor(word: string, context: string) {
  const empty = { definitionZh: '', definitionEn: '', pos: '' }
  try {
    const status = await aiApi.status()
    if (status.provider === 'mock' || !status.configured) return empty
    const explanation = await aiApi.explain({ text: word, context, mode: 'word' })
    const definitionZh =
      explanation.translation?.trim() || explanation.explanation?.split('。')[0]?.trim() || ''
    return { ...empty, definitionZh }
  } catch {
    return empty
  }
}

/* ---------- 读后生词结算（猜 → 验证 → 入库） ---------- */

function escapeRegExp(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 从选中所在的块文本里抠出包含该词的原句（取不到就退化为整块文本）。 */
function sentenceWithWord(blockText: string, word: string): string {
  const sentences = blockText.split(/(?<=[.!?])\s+/)
  const hit = sentences.find((s) => s.toLowerCase().includes(word.toLowerCase()))
  return (hit ?? blockText).trim().slice(0, 300)
}

/** 「猜」标记：不打断阅读，只记进账本并在正文加虚线（US1）。 */
function markGuessFromSelection() {
  const word = selectedText.value.trim()
  if (!/^[A-Za-z][A-Za-z'-]*$/.test(word)) return
  recordGuess(word, sentenceWithWord(selectedContext.value, word))
  ElMessage({ message: `已标记「${word}」，读完后在「读后生词」结算`, duration: 1600 })
  hideToolbar()
}

/**
 * 账本里未处理的词在正文加下划线：猜=虚线、查=实线。
 * 入库或忽略后 removeWords 触发账本 watch，标注随之消失。
 */
function applyWordMarks() {
  const root = articleBody.value
  if (!root) return
  root.querySelectorAll('span.word-mark').forEach((mark) => {
    const parent = mark.parentNode
    if (!parent) return
    parent.replaceChild(document.createTextNode(mark.textContent ?? ''), mark)
    parent.normalize()
  })
  const guessWords = ledger.value.guesses.filter((g) => !g.verdict).map((g) => g.word)
  const lookedWords = ledger.value.looked.map((l) => l.word)
  const all = [...new Set([...guessWords, ...lookedWords].map((w) => w.trim().toLowerCase()))].filter(Boolean)
  if (!all.length) return
  const regex = new RegExp(`\\b(${all.map(escapeRegExp).join('|')})\\b`, 'gi')
  const guessSet = new Set(guessWords.map((w) => w.toLowerCase()))
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      const value = node.nodeValue
      if (!value || !value.trim()) return NodeFilter.FILTER_REJECT
      if (node.parentElement?.closest('a, code, pre')) return NodeFilter.FILTER_REJECT
      regex.lastIndex = 0
      return regex.test(value) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT
    },
  })
  const targets: Text[] = []
  while (walker.nextNode()) targets.push(walker.currentNode as Text)
  for (const text of targets) {
    const value = text.nodeValue ?? ''
    const frag = document.createDocumentFragment()
    let cursor = 0
    regex.lastIndex = 0
    let match: RegExpExecArray | null
    while ((match = regex.exec(value))) {
      if (match.index > cursor) {
        frag.appendChild(document.createTextNode(value.slice(cursor, match.index)))
      }
      const span = document.createElement('span')
      span.className = `word-mark ${guessSet.has(match[0].toLowerCase()) ? 'mark-guess' : 'mark-looked'}`
      span.textContent = match[0]
      frag.appendChild(span)
      cursor = match.index + match[0].length
    }
    if (cursor < value.length) frag.appendChild(document.createTextNode(value.slice(cursor)))
    text.parentNode?.replaceChild(frag, text)
  }
}

watch(ledger, () => nextTick(applyWordMarks), { deep: true })
watch(articleHtml, () => nextTick(applyWordMarks))

async function runJudgeGuess(word: string) {
  if (judgingWord.value) return
  const entry = ledger.value.guesses.find((g) => normWord(g.word) === normWord(word))
  if (!entry || entry.verdict || !entry.guess.trim()) return
  judgingWord.value = entry.word
  try {
    const res = await aiApi.judgeGuess({
      word: entry.word,
      guess: entry.guess,
      context: entry.sentences[0],
    })
    setGuessVerdict(entry.word, res.verdict, res.meaning, res.reason)
  } catch (err) {
    // 降级模式：AI 网关不可达时结算流程不断，词仍然可以入库（spec 分支）。
    setGuessVerdict(entry.word, 'degraded', '', `${errorMessage(err, 'AI 暂不可用')}；可先入库，复习时再验证`)
  } finally {
    judgingWord.value = ''
  }
}

async function runScanMissed() {
  if (!article.value || scanning.value) return
  scanning.value = true
  scanError.value = ''
  try {
    const res = await readingApi.vocabScan(article.value.article.id, processedWords())
    const known = new Set(processedWords().map(normWord))
    setScanned(res.words.filter((w) => !known.has(normWord(w.word))))
  } catch (err) {
    scanError.value = errorMessage(err, 'AI 扫描暂不可用')
  } finally {
    scanning.value = false
  }
}

async function runSettle(words: string[]): Promise<SettleSummary> {
  if (!article.value) throw new Error('文章还没加载好，请稍后重试')
  const items: VocabularyBatchItem[] = []
  for (const word of words) {
    const key = normWord(word)
    const guess = ledger.value.guesses.find((g) => normWord(g.word) === key)
    const looked = ledger.value.looked.find((l) => normWord(l.word) === key)
    const missed = ledger.value.scanned.find((m) => normWord(m.word) === key)
    const contextSentence = guess?.sentences[0] ?? looked?.sentences[0] ?? missed?.sentence ?? ''
    let definitionZh = guess && guess.verdict !== 'degraded' ? guess.meaning : ''
    let definitionEn = ''
    let pos = ''
    if (looked?.lookup) {
      definitionZh = looked.lookup.definitionZh
      definitionEn = looked.lookup.definitionEn
      pos = looked.lookup.pos
    } else if (looked?.ai) {
      definitionZh = looked.ai.translation || looked.ai.explanation
    } else if (missed) {
      definitionZh = missed.meaning
    }
    items.push({
      lemma: word,
      contextSentence: contextSentence.slice(0, 1000) || undefined,
      definitionZh: definitionZh.slice(0, 500) || undefined,
      definitionEn: definitionEn.slice(0, 500) || undefined,
      pos: pos.slice(0, 20) || undefined,
      origin: 'post-review',
      sourceDoc: `${article.value.article.title} #${article.value.article.id}`.slice(0, 120),
    })
  }
  const judged = ledger.value.guesses.filter((g) => g.verdict)
  const res = await vocabularyApi.batchAdd(items)
  removeWords(words)
  return {
    added: res.added,
    already: res.alreadyInDeck,
    correctCount: judged.filter((g) => g.verdict === 'correct').length,
    judgedCount: judged.length,
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

onMounted(() => {
  timer = window.setInterval(() => {
    elapsedMinutes.value = Math.floor((Date.now() - startedAt.value) / 60000)
  }, 30_000)
  window.addEventListener('scroll', onWindowScroll, { passive: true })
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  window.removeEventListener('scroll', onWindowScroll)
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
              {{ article.article.tagName }}
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
          <button
            type="button"
            class="settle-entry"
            title="处理本文的词汇欠账：验证猜过的词、回看查过的词、AI 扫描漏网词"
            @click="settlementOpen = true"
          >
            <el-icon><PriceTag /></el-icon>
            读后生词
            <span v-if="pendingCount" class="settle-badge">{{ pendingCount }}</span>
          </button>
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

        <!-- 外层页面容器的入场 transform 会把 fixed 包含块从视口改成祖先，入口按钮必须 Teleport 到 body。 -->
        <Teleport to="body">
          <div class="side-tabs side-tabs-left">
            <button
              type="button"
              class="side-tab"
              title="本篇预习（独立预习页）"
              @click="goPreview"
            >
              <el-icon><MagicStick /></el-icon>
              <span>预习</span>
              <span v-if="hasSavedPreview" class="tab-count"><el-icon><Check /></el-icon></span>
            </button>
            <button
              type="button"
              class="side-tab"
              :class="{ active: activeDrawer === 'questions' }"
              title="阅读理解"
              @click="toggleQuestions"
            >
              <el-icon><EditPen /></el-icon>
              <span>题目</span>
              <span v-if="questionCount" class="tab-count">{{ questionCount }}</span>
            </button>
          </div>
          <div class="side-tabs side-tabs-right">
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
          </div>
        </Teleport>

        <ContentDrawer
          v-model="tocOpen"
          title="文章目录"
          eyebrow="ON THIS PAGE"
          direction="rtl"
          size="min(300px, 86vw)"
        >
          <ReadingToc :items="toc" @select="scrollTo" />
        </ContentDrawer>

        <ContentDrawer
          class="reader-panel-drawer"
          :model-value="activeDrawer !== 'none'"
          :title="panelDrawerTitle"
          :eyebrow="panelDrawerEyebrow"
          direction="ltr"
          size="min(400px, 92vw)"
          offset="var(--sidebar-width)"
          @update:model-value="onPanelDrawerToggle"
        >
          <template #header-extra>
            <span v-if="activeDrawer === 'assistant' && aiResult?.provider" class="drawer-badge">
              {{ aiResult.provider === 'deepseek' ? 'DeepSeek' : 'Demo AI' }}
            </span>
            <span v-else-if="activeDrawer === 'questions'" class="drawer-badge">
              {{ answeredCount }} / {{ questionCount }}
            </span>
          </template>

          <ReadingAssistantPanel
            v-if="activeDrawer === 'assistant'"
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
          <template v-else-if="activeDrawer === 'questions'">
            <ReadingQuizPanel :article-id="article.article.id" :section="currentQuizSection" />
            <div v-if="questionCount" class="drawer-divider">
              <span>本篇理解题 · {{ questionCount }} 道</span>
            </div>
            <ReadingComprehensionPanel
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
          </template>
        </ContentDrawer>
      </div>

      <!-- 读后生词结算页：账本在父组件（localStorage），这里只传状态与动作回调。 -->
      <VocabSettlementDialog
        v-model="settlementOpen"
        :article-title="article.article.title"
        :ledger="ledger"
        :pending-count="pendingCount"
        :judging-word="judgingWord"
        :scanning="scanning"
        :scan-error="scanError"
        :judge="runJudgeGuess"
        :scan="runScanMissed"
        :settle="runSettle"
        :set-draft="setGuessDraft"
        :remove="removeWords"
      />

      <!-- 进入文章前的预习询问：草稿确认后才按 (用户, 文章) 落库。 -->
      <el-dialog v-model="previewAskOpen" width="min(430px, 92vw)" :close-on-click-modal="false">
        <template #header>
          <strong class="preview-ask-title">要不要先预习一下这篇？</strong>
        </template>
        <p class="preview-ask-intro">让 AI 通读全文，先给你一张路标：</p>
        <ul class="preview-ask-list">
          <li><strong>预习词表</strong>≤15 个核心词，给“它在本文中的意思”</li>
          <li><strong>结构预告</strong>≤3 条高频句型，标出最可能栽跟头的句子</li>
        </ul>
        <p class="preview-ask-note">提取结果先在预习页给你过目，勾选确认后才会保存，下次进入自动带上。</p>
        <template #footer>
          <el-button @click="previewAskOpen = false">直接阅读</el-button>
          <el-button type="primary" @click="goPreview">
            <el-icon><MagicStick /></el-icon> 开始预习
          </el-button>
        </template>
      </el-dialog>

      <div v-if="toolbarVisible" class="selection-toolbar" :style="toolbarStyle" @mousedown.prevent>
        <button v-if="isWordSelection" type="button" @click="lookupSelected()">
          <el-icon><Search /></el-icon> 查词
        </button>
        <button
          v-if="isWordSelection"
          type="button"
          title="标记「我猜是这意思」，不打断阅读，读完再结算"
          @click="markGuessFromSelection"
        >
          <el-icon><Aim /></el-icon> 猜
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

/* 读后生词入口：顶栏胶囊按钮，琥珀色徽章计数，处理完自动归零。 */
.settle-entry {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-right: 4px;
  padding: 5px 11px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--muted);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.settle-entry:hover {
  border-color: var(--amber-line);
  color: var(--amber-text);
}

.settle-badge {
  display: grid;
  place-items: center;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--amber);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
}

/* 正文内生词标注：猜=琥珀虚线，查=绿色实线；结算/忽略后随账本 watch 移除。 */
.reader-main :deep(span.word-mark) {
  border-bottom: 2px dashed var(--amber);
  padding-bottom: 1px;
  cursor: help;
}

.reader-main :deep(span.word-mark.mark-looked) {
  border-bottom-style: solid;
  border-bottom-color: var(--green-2);
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

/* 侧边入口：左侧「题目」打开左抽屉，右侧「目录」打开右抽屉。 */
.side-tabs {
  position: fixed;
  top: 50%;
  z-index: 80;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transform: translateY(-50%);
}

.side-tabs-left {
  left: var(--sidebar-width);
}

/* 左抽屉锚定在侧边栏右缘：动画期间裁掉侧边栏区域，让它从菜单边缘滑出。 */
:global(.is-drawer.el-modal-drawer:has(> .reader-panel-drawer)) {
  clip-path: inset(0 0 0 var(--sidebar-width));
}

.side-tabs-right {
  right: 0;
}

.side-tab {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 10px;
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.side-tabs-left .side-tab {
  border-left: 0;
  border-radius: 0 10px 10px 0;
}

.side-tabs-right .side-tab {
  border-right: 0;
  border-radius: 10px 0 0 10px;
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

.side-tabs-left .tab-count {
  right: -7px;
  left: auto;
}

/* AI 自测区与静态题区之间的分隔条。 */
.drawer-divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 22px 0 14px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  white-space: nowrap;
}

.drawer-divider::before,
.drawer-divider::after {
  content: "";
  height: 1px;
  background: var(--line);
  flex: 1;
}

/* 抽屉头部的小徽标：AI provider 或答题进度。 */
.drawer-badge {
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
  white-space: nowrap;
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
  animation: rise-in 0.16s ease backwards;
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

  .side-tab {
    padding: 12px 8px;
    font-size: 11px;
  }
}

.preview-ask-title {
  font-size: 16px;
}

.preview-ask-intro {
  color: var(--muted);
  font-size: 13px;
}

.preview-ask-list {
  display: grid;
  gap: 8px;
  margin: 12px 0;
  padding: 0;
  list-style: none;
}

.preview-ask-list li {
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-2);
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.6;
}

.preview-ask-list li strong {
  margin-right: 7px;
  color: var(--ink);
}

.preview-ask-note {
  color: var(--faint);
  font-size: 12px;
  line-height: 1.6;
}
</style>
