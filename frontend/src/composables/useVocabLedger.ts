import { computed, ref, watch, type Ref } from 'vue'
import type { MissedWord, VocabLookup, VocabVerdict } from '@/types/api'

/**
 * Per-article vocabulary ledger for the post-reading settlement ("读后生词").
 * Guess drafts, lookups and scan results live in localStorage keyed by docId,
 * so leaving mid-settlement never loses input (US6). Nothing here touches the
 * network: the reading view performs the API calls and writes results back.
 */

/** A word the reader marked "我猜是这意思". Merged per word, first 2 sentences kept. */
export interface GuessCard {
  word: string
  sentences: string[]
  guess: string
  /** '' = awaiting judgement; 'degraded' = AI unreachable, verdict unknown. */
  verdict: VocabVerdict | 'degraded' | ''
  meaning: string
  reason: string
}

/** A word looked up while reading; the cached explanation is never re-requested. */
export interface LookedCard {
  word: string
  sentences: string[]
  lookup: VocabLookup | null
  /** Fallback AI explanation for words outside the seed dictionary. */
  ai: { translation: string; explanation: string } | null
}

export interface VocabLedger {
  guesses: GuessCard[]
  looked: LookedCard[]
  scanned: MissedWord[]
}

export interface SettleSummary {
  added: number
  already: number
  correctCount: number
  judgedCount: number
}

const KEY_PREFIX = 'reading.vocabLedger.'
const MAX_SENTENCES = 2

export function emptyLedger(): VocabLedger {
  return { guesses: [], looked: [], scanned: [] }
}

export function normWord(word: string): string {
  return word.trim().toLowerCase()
}

export function loadLedger(docId: number): VocabLedger {
  try {
    const raw = localStorage.getItem(KEY_PREFIX + docId)
    if (!raw) return emptyLedger()
    const parsed = JSON.parse(raw) as Partial<VocabLedger>
    return {
      guesses: Array.isArray(parsed.guesses) ? parsed.guesses : [],
      looked: Array.isArray(parsed.looked) ? parsed.looked : [],
      scanned: Array.isArray(parsed.scanned) ? parsed.scanned : [],
    }
  } catch {
    return emptyLedger()
  }
}

export function saveLedger(docId: number, ledger: VocabLedger) {
  try {
    localStorage.setItem(KEY_PREFIX + docId, JSON.stringify(ledger))
  } catch {
    // Quota failures only cost draft persistence; reading must continue.
  }
}

function pushSentence(sentences: string[], sentence: string) {
  const s = sentence.trim().slice(0, 900)
  if (
    s &&
    sentences.length < MAX_SENTENCES &&
    !sentences.some((existing) => existing.toLowerCase() === s.toLowerCase())
  ) {
    sentences.push(s)
  }
}

export function useVocabLedger(docIdRef: Ref<number | null>) {
  const ledger = ref<VocabLedger>(emptyLedger())

  watch(docIdRef, (id) => {
    ledger.value = id == null ? emptyLedger() : loadLedger(id)
  }, { immediate: true })

  watch(ledger, (l) => {
    const id = docIdRef.value
    if (id != null) saveLedger(id, l)
  }, { deep: true })

  function findGuess(word: string): GuessCard | undefined {
    const key = normWord(word)
    return ledger.value.guesses.find((g) => normWord(g.word) === key)
  }

  function recordGuess(word: string, sentence: string) {
    const w = word.trim()
    if (!w) return
    let card = findGuess(w)
    if (!card) {
      card = { word: w, sentences: [], guess: '', verdict: '', meaning: '', reason: '' }
      ledger.value.guesses.unshift(card)
    }
    pushSentence(card.sentences, sentence)
  }

  function recordLooked(
    word: string,
    sentence: string,
    lookup: VocabLookup | null,
    ai?: LookedCard['ai'],
  ) {
    const w = word.trim()
    if (!w) return
    const key = normWord(w)
    let card = ledger.value.looked.find((l) => normWord(l.word) === key)
    if (!card) {
      card = { word: lookup?.lemma || w, sentences: [], lookup: null, ai: null }
      ledger.value.looked.unshift(card)
    }
    pushSentence(card.sentences, sentence)
    if (lookup) card.lookup = lookup
    if (ai) card.ai = ai
  }

  function setGuessDraft(word: string, draft: string) {
    const card = findGuess(word)
    if (card) card.guess = draft
  }

  function setGuessVerdict(word: string, verdict: GuessCard['verdict'], meaning: string, reason: string) {
    const card = findGuess(word)
    if (!card) return
    card.verdict = verdict
    card.meaning = meaning
    card.reason = reason
  }

  function setScanned(words: MissedWord[]) {
    ledger.value.scanned = words
  }

  /** Drop a word from all three groups (忽略 / settled). */
  function removeWords(words: string[]) {
    const done = new Set(words.map(normWord))
    ledger.value.guesses = ledger.value.guesses.filter((g) => !done.has(normWord(g.word)))
    ledger.value.looked = ledger.value.looked.filter((l) => !done.has(normWord(l.word)))
    ledger.value.scanned = ledger.value.scanned.filter((m) => !done.has(normWord(m.word)))
  }

  /** Words already handled in this article — the input for the missed-word scan. */
  function processedWords(): string[] {
    return [
      ...ledger.value.guesses.map((g) => g.word),
      ...ledger.value.looked.map((l) => l.word),
      ...ledger.value.scanned.map((m) => m.word),
    ]
  }

  /** 待验证猜测 + 已查未处理 + 漏网推荐 = the 读后生词 (N) badge. */
  const pendingCount = computed(
    () =>
      ledger.value.guesses.filter((g) => !g.verdict).length +
      ledger.value.looked.length +
      ledger.value.scanned.length,
  )

  return {
    ledger,
    recordGuess,
    recordLooked,
    setGuessDraft,
    setGuessVerdict,
    setScanned,
    removeWords,
    processedWords,
    pendingCount,
  }
}
