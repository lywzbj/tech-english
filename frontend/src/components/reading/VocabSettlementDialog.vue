<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { normWord, type GuessCard, type SettleSummary, type VocabLedger } from '@/composables/useVocabLedger'

/**
 * 读后生词结算页（对话框形态）：三段式清偿本文的词汇欠账——
 * 1 待验证（写猜测→AI 判定）、2 已查（复用缓存讲解）、3 漏网之鱼（AI 扫描）。
 * 勾选后批量入库。所有状态变更都通过回调交给父组件（账本与持久化在父组件）。
 */
const props = defineProps<{
  modelValue: boolean
  articleTitle: string
  ledger: VocabLedger
  pendingCount: number
  judgingWord: string
  scanning: boolean
  scanError: string
  judge: (word: string) => Promise<void>
  scan: () => Promise<void>
  settle: (words: string[]) => Promise<SettleSummary>
  setDraft: (word: string, text: string) => void
  remove: (words: string[]) => void
}>()

const emit = defineEmits<{ 'update:modelValue': [open: boolean] }>()

const view = ref<'list' | 'done'>('list')
const summary = ref<SettleSummary | null>(null)
const settleError = ref('')
const busy = ref(false)
const selected = ref<string[]>([])
/** Words the learner manually toggled; auto-recommendation no longer overrides them. */
let touched = new Set<string>()

const VERDICT_LABEL: Record<string, string> = {
  correct: '判对',
  partial: '大致对',
  wrong: '判错',
  degraded: '降级',
}

const selectedCount = computed(() => selected.value.length)
const totalCount = computed(
  () => props.ledger.guesses.length + props.ledger.looked.length + props.ledger.scanned.length,
)

function isSelected(word: string): boolean {
  const key = normWord(word)
  return selected.value.some((w) => normWord(w) === key)
}

function toggle(word: string, on: boolean) {
  touched.add(normWord(word))
  if (on) {
    if (!isSelected(word)) selected.value.push(word)
  } else {
    selected.value = selected.value.filter((w) => normWord(w) !== normWord(word))
  }
}

function guessAdvice(g: GuessCard): { recAdd: boolean; tag: string; why: string } | null {
  switch (g.verdict) {
    case 'correct':
      return { recAdd: false, tag: '不必入库', why: '语境义已能准确猜出，不占复习队列' }
    case 'partial':
      return { recAdd: true, tag: '推荐入库', why: '方向对了但不够精确，复习一次钉牢' }
    case 'wrong':
      return { recAdd: true, tag: '推荐入库', why: '按常用义理解会被带偏，最该收进生词本' }
    case 'degraded':
      return { recAdd: true, tag: '先入库', why: '本次未判定；入库后可在复习里继续验证' }
    default:
      return null
  }
}

function recommendedWords(): string[] {
  const out: string[] = []
  for (const g of props.ledger.guesses) {
    const advice = guessAdvice(g)
    if (advice?.recAdd) out.push(g.word)
  }
  for (const l of props.ledger.looked) out.push(l.word)
  for (const m of props.ledger.scanned) out.push(m.word)
  return out
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    view.value = 'list'
    summary.value = null
    settleError.value = ''
    touched = new Set()
    selected.value = recommendedWords()
  },
)

// 扫描结果在弹窗打开之后才出现，需按同样的推荐规则补勾选（用户手动动过的不覆盖）。
watch(
  () => props.ledger.scanned.map((m) => normWord(m.word)).join('|'),
  () => {
    for (const m of props.ledger.scanned) {
      if (!touched.has(normWord(m.word)) && !isSelected(m.word)) selected.value.push(m.word)
    }
  },
)

async function onJudge(word: string) {
  await props.judge(word)
  // 判定完成后按系统建议自动勾选（用户手动动过的卡片不再覆盖）。
  const g = props.ledger.guesses.find((x) => normWord(x.word) === normWord(word))
  if (g && !touched.has(normWord(g.word)) && guessAdvice(g)?.recAdd && !isSelected(g.word)) {
    selected.value.push(g.word)
  }
}

async function onSettle() {
  if (!selected.value.length) return
  busy.value = true
  settleError.value = ''
  try {
    summary.value = await props.settle([...selected.value])
    selected.value = []
    view.value = 'done'
  } catch (err) {
    settleError.value = err instanceof Error && err.message ? err.message : '入库失败，请重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <el-dialog
    class="settle-dialog"
    :model-value="modelValue"
    width="min(780px, 94vw)"
    top="5vh"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="emit('update:modelValue', Boolean($event))"
  >
    <template #header>
      <div class="settle-head">
        <div class="settle-head-main">
          <p class="settle-eyebrow">VOCABULARY SETTLEMENT</p>
          <h2>读后生词 · {{ articleTitle }}</h2>
        </div>
        <span class="settle-n">{{ pendingCount }} 待处理</span>
      </div>
    </template>

    <div v-if="view === 'done' && summary" class="settle-done">
      <p class="done-eyebrow">SETTLED</p>
      <h3>本篇词汇结算</h3>
      <div class="done-grid">
        <div class="done-cell">
          <strong>{{ summary.correctCount }}/{{ summary.judgedCount }}</strong>
          <span>猜测判对</span>
        </div>
        <div class="done-cell">
          <strong>{{ summary.added }}</strong>
          <span>新入库</span>
        </div>
        <div class="done-cell">
          <strong>{{ summary.already }}</strong>
          <span>已在库·自动合并</span>
        </div>
        <div class="done-cell">
          <strong>今天</strong>
          <span>进入复习队列</span>
        </div>
      </div>
      <p class="done-note">欠账已清偿，新词已带上原句进入 SRS 队列，今日任务里就能复习。</p>
      <el-button type="primary" @click="emit('update:modelValue', false)">完成</el-button>
    </div>

    <div v-else class="settle-body">
      <!-- 1 待验证：猜过的词 -->
      <section class="settle-sec">
        <h3><span class="sec-k">1</span>待验证 · 猜过的词</h3>
        <p v-if="!ledger.guesses.length" class="sec-empty">
          阅读时在生词旁点工具条的「猜」，词会标上虚线并汇总到这里，读完再一起判定。
        </p>
        <div v-for="g in ledger.guesses" :key="g.word" class="s-card">
          <div class="s-top">
            <strong class="s-word">{{ g.word }}</strong>
            <span v-if="g.verdict" class="verdict" :class="'v-' + g.verdict">
              {{ VERDICT_LABEL[g.verdict] }}
            </span>
            <span class="s-grow" />
            <el-checkbox
              v-if="guessAdvice(g)"
              :model-value="isSelected(g.word)"
              @update:model-value="toggle(g.word, Boolean($event))"
            >
              <span class="advice" :class="{ good: !guessAdvice(g)!.recAdd }">
                {{ guessAdvice(g)!.tag }}
              </span>
              <span class="advice-why">{{ guessAdvice(g)!.why }}</span>
            </el-checkbox>
          </div>
          <p v-for="(s, i) in g.sentences" :key="i" class="s-sent">“{{ s }}”</p>
          <template v-if="!g.verdict">
            <el-input
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              :model-value="g.guess"
              placeholder="写下你猜的意思（中文 / 英文都行），AI 会对照原句判定对错并讲解"
              @update:model-value="setDraft(g.word, String($event))"
            />
            <div class="s-foot">
              <el-button
                type="primary"
                size="small"
                :loading="judgingWord === g.word"
                :disabled="!g.guess.trim()"
                @click="onJudge(g.word)"
              >
                提交判定
              </el-button>
              <button type="button" class="s-drop" @click="remove([g.word])">忽略</button>
            </div>
          </template>
          <template v-else>
            <p v-if="g.meaning" class="s-meaning">本文语境义：{{ g.meaning }}</p>
            <p v-if="g.reason" class="s-reason">{{ g.reason }}</p>
            <button
              v-if="g.verdict === 'degraded'"
              type="button"
              class="s-drop"
              @click="onJudge(g.word)"
            >
              重试判定
            </button>
          </template>
        </div>
      </section>

      <!-- 2 已查：查过的词，讲解走本地缓存 -->
      <section class="settle-sec">
        <h3><span class="sec-k">2</span>已查 · 阅读中查过的词</h3>
        <p v-if="!ledger.looked.length" class="sec-empty">还没查过词。查过的词会自动带上讲解缓存在这里。</p>
        <div v-for="l in ledger.looked" :key="l.word" class="s-card">
          <div class="s-top">
            <strong class="s-word">{{ l.word }}</strong>
            <span v-if="l.lookup?.pos" class="pos-pill">{{ l.lookup.pos }}</span>
            <span class="s-grow" />
            <el-checkbox
              :model-value="isSelected(l.word)"
              @update:model-value="toggle(l.word, Boolean($event))"
            >
              <span class="advice">推荐入库</span>
              <span class="advice-why">阅读中特意查过，说明没把握</span>
            </el-checkbox>
          </div>
          <p class="s-meaning">
            {{ l.lookup?.definitionZh || l.ai?.translation || l.ai?.explanation || '（暂无释义，入库后可补充）' }}
          </p>
          <p v-for="(s, i) in l.sentences" :key="i" class="s-sent">“{{ s }}”</p>
        </div>
      </section>

      <!-- 3 漏网之鱼：AI 扫描未处理的高价值词 -->
      <section class="settle-sec">
        <h3>
          <span class="sec-k">3</span>漏网之鱼 · AI 找出的高价值词
          <el-button size="small" :loading="scanning" @click="scan()">
            <el-icon><Refresh /></el-icon> {{ ledger.scanned.length ? '重新扫描本文' : '扫描本文' }}
          </el-button>
        </h3>
        <p v-if="scanError" class="sec-degrade">
          <span class="badge-degrade">降级模式</span> {{ scanError }}
        </p>
        <p v-else-if="!ledger.scanned.length" class="sec-empty">
          点「扫描本文」，AI 会找出全文反复出现、挡住主旨、或熟词僻义却未被处理的词（≤8 个）。
        </p>
        <div v-for="m in ledger.scanned" :key="m.word" class="s-card">
          <div class="s-top">
            <strong class="s-word">{{ m.word }}</strong>
            <span class="s-grow" />
            <el-checkbox
              :model-value="isSelected(m.word)"
              @update:model-value="toggle(m.word, Boolean($event))"
            >
              <span class="advice">推荐入库</span>
            </el-checkbox>
          </div>
          <p class="s-meaning">{{ m.meaning }}</p>
          <p class="s-reason">{{ m.reason }}</p>
          <p v-if="m.sentence" class="s-sent">“{{ m.sentence }}”</p>
        </div>
      </section>
    </div>

    <template #footer>
      <div v-if="view === 'list'" class="settle-bar">
        <span class="bar-hint">
          已选 <b>{{ selectedCount }}</b> / {{ totalCount }}
          <em v-if="settleError" class="bar-err">{{ settleError }}</em>
        </span>
        <el-button @click="emit('update:modelValue', false)">稍后再说</el-button>
        <el-button type="primary" :loading="busy" :disabled="!selectedCount" @click="onSettle">
          入库（{{ selectedCount }}）
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.settle-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.settle-eyebrow {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.14em;
}

.settle-head h2 {
  margin-top: 3px;
  font-size: 17px;
  font-weight: 650;
}

.settle-n {
  flex: 0 0 auto;
  padding: 4px 10px;
  border: 1px solid var(--amber-line);
  border-radius: 999px;
  background: var(--amber-soft);
  color: var(--amber-text);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
}

.settle-body {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
}

.settle-sec {
  margin-bottom: 26px;
}

.settle-sec h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
}

.sec-k {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--green-3);
  color: var(--green);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 800;
}

.sec-empty {
  margin: 2px 0 0 26px;
  color: var(--faint);
  font-size: 12px;
  line-height: 1.7;
}

.sec-degrade {
  margin: 2px 0 8px 26px;
  color: var(--amber-text);
  font-size: 12px;
}

.badge-degrade {
  display: inline-block;
  margin-right: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--amber-soft);
  color: var(--amber-text);
  font-size: 11px;
  font-weight: 700;
}

.s-card {
  margin: 0 0 10px 26px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.s-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.s-word {
  font-size: 15px;
  font-weight: 700;
}

.pos-pill {
  padding: 2px 7px;
  border-radius: 5px;
  background: var(--surface-2);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.s-grow {
  flex: 1 1 auto;
}

.verdict {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.verdict.v-correct {
  background: var(--green-3);
  color: var(--green);
}

.verdict.v-partial {
  background: var(--amber-soft);
  color: var(--amber-text);
}

.verdict.v-wrong {
  background: var(--rose-soft);
  color: var(--rose);
}

.verdict.v-degraded {
  background: var(--surface-2);
  color: var(--muted);
}

.s-sent {
  margin-top: 6px;
  color: var(--muted);
  font-family: var(--font-reading, inherit);
  font-size: 12.5px;
  line-height: 1.65;
}

.s-meaning {
  margin-top: 6px;
  color: var(--ink);
  font-size: 13px;
  line-height: 1.6;
}

.s-reason {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.s-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.s-drop {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--faint);
  font-size: 12px;
  cursor: pointer;
}

.s-drop:hover {
  color: var(--rose);
}

.advice {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--green-3);
  color: var(--green);
  font-size: 11px;
  font-weight: 700;
}

.advice.good {
  background: var(--surface-2);
  color: var(--muted);
}

.advice-why {
  margin-left: 6px;
  color: var(--faint);
  font-size: 11.5px;
}

.settle-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bar-hint {
  flex: 1 1 auto;
  color: var(--muted);
  font-size: 12.5px;
}

.bar-hint b {
  color: var(--green);
  font-family: var(--font-mono);
}

.bar-err {
  margin-left: 8px;
  color: var(--rose);
  font-style: normal;
}

.settle-done {
  padding: 10px 4px 6px;
  text-align: center;
}

.done-eyebrow {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
}

.settle-done h3 {
  margin: 4px 0 18px;
  font-size: 18px;
  font-weight: 700;
}

.done-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.done-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 6px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-2);
}

.done-cell strong {
  color: var(--green);
  font-family: var(--font-mono);
  font-size: 20px;
}

.done-cell span {
  color: var(--muted);
  font-size: 11.5px;
}

.done-note {
  margin-bottom: 18px;
  color: var(--muted);
  font-size: 12.5px;
}
</style>
