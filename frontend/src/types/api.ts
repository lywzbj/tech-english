export interface User {
  id: number
  email: string
  displayName: string
}

export interface Profile {
  targetBand: number | null
  examDate: string | null
  dailyMinutes: number
  cefrLevel: string
  techStack: string[]
  interests: string[]
  explanationLanguage: string
  onboardingCompleted: boolean
}

export interface AuthResponse {
  accessToken: string
  expiresInSeconds: number
  user: User
  profile: Profile
}

export interface MeResponse {
  user: User
  profile: Profile
}

export interface SetupStatus {
  needsSetup: boolean
}

export interface ProfileUpdateRequest {
  targetBand?: number | null
  examDate?: string | null
  dailyMinutes?: number
  cefrLevel?: string
  techStack?: string[]
  interests?: string[]
  explanationLanguage?: string
  onboardingCompleted?: boolean
}

export interface GrammarTopic {
  id: number
  slug: string
  title: string
  cefrLevel: string
  summary: string
  status: 'LOCKED' | 'AVAILABLE' | 'IN_PROGRESS' | 'MASTERED'
  mastery: number
  prerequisites: string[]
  locked: boolean
  completed: boolean
  sortOrder: number
}

export interface GrammarWorld {
  name: string
  order: number
  topics: GrammarTopic[]
}

export interface GrammarPath {
  worlds: GrammarWorld[]
  overallMastery: number
}

export interface GrammarExample {
  sentence: string
  translation: string
  note: string
}

export interface GrammarExercise {
  id: string
  type: string
  prompt: string
  options: string[]
  skill?: 'recognize' | 'form' | 'use' | null
  level?: number | null
  source: 'SEED' | 'AI'
}

export interface GrammarPool {
  practiceCount: number
  bossCount: number
  bossSource: 'AI' | 'SEED'
  aiConfigured: boolean
  generationNeeded: boolean
}

export interface GrammarWrongAnswer {
  exerciseId: string
  prompt: string
  yourAnswer: string
  correctAnswer: string
}

export interface GrammarHistory {
  attempts: number
  correct: number
  lastPracticedAt?: string | null
  bossScore?: number | null
  recentWrong: GrammarWrongAnswer[]
}

export interface GrammarLesson {
  topic: GrammarTopic
  contentMd: string
  examples: GrammarExample[]
  practice: GrammarExercise[]
  boss: GrammarExercise[]
  pool: GrammarPool
  history: GrammarHistory
}

export interface AttemptResult {
  exerciseId: string
  prompt: string
  correct: boolean
  yourAnswer: string
  correctAnswer: string
  explanation: string
  optionNotes: Record<string, string>
}

export interface AttemptsResponse {
  mode: 'PRACTICE' | 'BOSS'
  total: number
  correct: number
  score: number
  mastery: number
  bossScore: number
  requiredScore: number
  passed: boolean
  status: string
  unlockedNext: boolean
  nextSlug: string | null
  results: AttemptResult[]
}

export interface GrammarGenerateResponse {
  provider: string
  model: string
  promptVersion: string
  practiceAdded: number
  bossAdded: number
  practiceReady: boolean
  bossReady: boolean
}

export interface VocabCard {
  userVocabId: number
  vocabId: number
  lemma: string
  pos: string
  phonetic: string
  definitionEn: string
  definitionZh: string
  exampleEn: string
  exampleZh: string
  contextSentence: string | null
  status: string
  intervalDays: number
  nextReviewAt: string
}

export interface DueVocabResponse {
  dueCount: number
  cards: VocabCard[]
}

export interface VocabularyListResponse {
  total: number
  dueCount: number
  words: VocabCard[]
}

export interface ReviewResponse {
  userVocabId: number
  nextReviewAt: string
  intervalDays: number
  status: string
  remainingDue: number
}

export interface VocabLookup {
  vocabId: number
  lemma: string
  pos: string
  phonetic: string
  definitionEn: string
  definitionZh: string
  exampleEn: string
  exampleZh: string
  category: string
  domain: string
  inDeck: boolean
}

export interface VocabularyAddRequest {
  lemma: string
  contextSentence?: string
  definitionZh?: string
  definitionEn?: string
  pos?: string
}

export interface ReadingTag {
  id: number
  name: string
  sortOrder: number
  enabled: boolean
  articleCount: number
}

export interface ReadingArticleSummary {
  id: number
  title: string
  subtitle: string | null
  sourceType: 'IELTS' | 'TECH' | 'CUSTOM'
  summary: string
  wordCount: number
  cefrLevel: string
  estimatedMinutes: number
  topics: string[]
  sourceUrl: string | null
  read: boolean
  /** Raw resume position 0-100; completion is reported by read, not by this value. */
  scrollPercent: number
  tagId: number | null
  tagName: string
}

export interface ReadingQuestion {
  id: number
  type: 'MCQ' | 'TRUE_FALSE' | 'HEADING' | 'SHORT_ANSWER'
  prompt: string
  options: string[]
}

export interface ReadingArticleDetail {
  article: ReadingArticleSummary
  contentMd: string
  questions: ReadingQuestion[]
  /** 0-100, last saved scroll position for resuming the article. */
  scrollPercent: number
}

export interface ReadingListResponse {
  total: number
  readCount: number
  articles: ReadingArticleSummary[]
}

export interface ReadingCompleteResponse {
  total: number
  correct: number
  score: number
  message: string
}

// ---- 阅读理解 AI 自测（抽屉内按小节生成单选题） ----

export type QuizDifficulty = 'EASY' | 'NORMAL' | 'HARD'

export interface QuizQuestion {
  id: number
  prompt: string
  options: string[]
}

/** 一套生成记录；未判分时带题目（恢复作答），已判分只回成绩摘要。 */
export interface QuizSet {
  id: number
  difficulty: QuizDifficulty
  sectionTitle: string | null
  totalCount: number
  submitted: boolean
  correctCount: number | null
  createdAt: string
  submittedAt: string | null
  questions: QuizQuestion[]
}

export interface QuizGenerateResponse {
  provider: string
  model: string
  promptVersion: string
  set: QuizSet
}

export interface QuizListResponse {
  sets: QuizSet[]
}

export interface QuizResultItem {
  questionId: number
  prompt: string
  options: string[]
  yourAnswer: string
  correctAnswer: string
  correct: boolean
  explanation: string
}

export interface QuizSubmitResponse {
  total: number
  correct: number
  score: number
  message: string
  items: QuizResultItem[]
}

export interface Task {
  id: number
  date: string
  type: 'REVIEW' | 'GRAMMAR' | 'READING' | 'WRITING'
  title: string
  description: string | null
  refType: string | null
  refId: number | null
  estimatedMinutes: number
  status: 'PENDING' | 'DONE' | 'SKIPPED'
  sortOrder: number
}

export interface TodayPlan {
  date: string
  totalMinutes: number
  completedMinutes: number
  tasks: Task[]
}

export interface DayPlan {
  date: string
  today: boolean
  totalMinutes: number
  tasks: Task[]
}

export interface WeekPlan {
  days: DayPlan[]
  totalMinutes: number
  completedMinutes: number
}

export interface DashboardStats {
  streakDays: number
  weekMinutes: number
  grammarMastery: number
  dueVocab: number
  totalVocab: number
  masteredVocab: number
  articlesRead: number
  totalArticles: number
  tasksCompleted: number
  tasksTotal: number
}

export interface Skill {
  key: string
  label: string
  value: number
  note: string
}

export interface Dashboard {
  greeting: string
  displayName: string
  targetBand: number | null
  examDate: string | null
  daysToExam: number
  cefrLevel: string
  stats: DashboardStats
  todayTasks: Task[]
  nextTask: Task | null
  skills: Skill[]
}

export interface SearchResult {
  sourceType: string
  sourceId: number
  title: string
  snippet: string
  score: number
}

export interface SearchResponse {
  query: string
  mode: string
  provider: string
  model: string
  dimension: number
  sqliteVec: boolean
  results: SearchResult[]
}

export interface VectorStatus {
  enabled: boolean
  provider: string
  model: string
  dimension: number
  documents: number
  index: string
  configured: boolean
}

export interface AiStatus {
  provider: string
  configured: boolean
  model: string
}

export interface AiGrammarPoint {
  point: string
  detail: string
}

export interface AiVocabHint {
  word: string
  meaning: string
  note: string
}

export interface AiExplainResponse {
  provider: string
  model: string
  translation: string
  explanation: string
  grammarPoints: AiGrammarPoint[]
  vocabulary: AiVocabHint[]
}

export interface PreviewWord {
  word: string
  meaning: string
  note?: string
  where?: string
}

export interface PreviewGrammar {
  structure: string
  where?: string
  risk?: string
  example?: string
}

export interface PreviewContent {
  words: PreviewWord[]
  grammar: PreviewGrammar[]
  promptVersion?: string
  generatedAt?: string
}

export interface PreviewGenerateResponse {
  provider: string
  model: string
  promptVersion: string
  words: PreviewWord[]
  grammar: PreviewGrammar[]
}

export interface PreviewStateResponse {
  exists: boolean
  preview?: PreviewContent
}

/* ---------- 读后生词结算 ---------- */

export type VocabVerdict = 'correct' | 'partial' | 'wrong'

export interface VocabJudgeResponse {
  provider: string
  model: string
  verdict: VocabVerdict
  meaning: string
  reason: string
}

export interface MissedWord {
  word: string
  meaning: string
  reason: string
  sentence: string
}

export interface VocabScanResponse {
  provider: string
  model: string
  words: MissedWord[]
}

export interface VocabularyBatchItem {
  lemma: string
  contextSentence?: string
  definitionZh?: string
  definitionEn?: string
  pos?: string
  origin?: string
  sourceDoc?: string
}

export interface VocabularyBatchItemResult {
  lemma: string
  userVocabId: number
  vocabId: number
  alreadyInDeck: boolean
  status: string
  intervalDays: number
}

export interface VocabularyBatchResponse {
  total: number
  added: number
  alreadyInDeck: number
  results: VocabularyBatchItemResult[]
}
