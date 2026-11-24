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
}

export interface GrammarLesson {
  topic: GrammarTopic
  contentMd: string
  examples: GrammarExample[]
  exercises: GrammarExercise[]
}

export interface AttemptResult {
  exerciseId: string
  correct: boolean
  correctAnswer: string
  explanation: string
}

export interface AttemptsResponse {
  total: number
  correct: number
  score: number
  mastery: number
  status: string
  unlockedNext: boolean
  nextSlug: string | null
  results: AttemptResult[]
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
