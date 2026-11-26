import { http } from './http'
import type {
  AiExplainResponse,
  AiStatus,
  AttemptsResponse,
  AuthResponse,
  Dashboard,
  DueVocabResponse,
  GrammarLesson,
  GrammarPath,
  GrammarGenerateResponse,
  MeResponse,
  PreviewContent,
  PreviewGenerateResponse,
  PreviewGrammar,
  PreviewStateResponse,
  PreviewWord,
  ProfileUpdateRequest,
  QuizGenerateResponse,
  QuizListResponse,
  QuizSubmitResponse,
  ReadingArticleDetail,
  ReadingCompleteResponse,
  ReadingListResponse,
  ReadingTag,
  ReviewResponse,
  SearchResponse,
  SetupStatus,
  Task,
  TodayPlan,
  VectorStatus,
  VocabCard,
  VocabJudgeResponse,
  VocabLookup,
  VocabScanResponse,
  VocabularyAddRequest,
  VocabularyBatchItem,
  VocabularyBatchResponse,
  VocabularyListResponse,
  WeekPlan,
} from '@/types/api'

export const authApi = {
  setupStatus: () => http.get<SetupStatus>('/setup/status').then((r) => r.data),
  setup: (body: { email: string; password: string; displayName: string }) =>
    http.post<AuthResponse>('/setup', body).then((r) => r.data),
  login: (body: { email: string; password: string }) =>
    http.post<AuthResponse>('/auth/login', body).then((r) => r.data),
  refresh: () => http.post<AuthResponse>('/auth/refresh').then((r) => r.data),
  logout: () => http.post('/auth/logout').then(() => undefined),
  me: () => http.get<MeResponse>('/me').then((r) => r.data),
  updateProfile: (body: ProfileUpdateRequest) =>
    http.put<MeResponse>('/me/profile', body).then((r) => r.data),
}

export const grammarApi = {
  path: () => http.get<GrammarPath>('/grammar/path').then((r) => r.data),
  lesson: (slug: string) =>
    http.get<GrammarLesson>(`/grammar/topics/${encodeURIComponent(slug)}`).then((r) => r.data),
  generate: (slug: string) =>
    http
      .post<GrammarGenerateResponse>(`/grammar/topics/${encodeURIComponent(slug)}/generate`, null, {
        timeout: 120_000,
      })
      .then((r) => r.data),
  submit: (slug: string, mode: 'PRACTICE' | 'BOSS', answers: { exerciseId: string; answer: string }[]) =>
    http
      .post<AttemptsResponse>(`/grammar/topics/${encodeURIComponent(slug)}/attempts`, { mode, answers })
      .then((r) => r.data),
}

export const vocabularyApi = {
  due: (limit = 20) =>
    http.get<DueVocabResponse>('/vocabulary/due', { params: { limit } }).then((r) => r.data),
  list: () => http.get<VocabularyListResponse>('/vocabulary').then((r) => r.data),
  add: (payload: VocabularyAddRequest) =>
    http.post<VocabCard>('/vocabulary', payload).then((r) => r.data),
  review: (userVocabId: number, rating: number) =>
    http
      .post<ReviewResponse>(`/vocabulary/${userVocabId}/review`, { rating })
      .then((r) => r.data),
  lookup: (word: string) =>
    http.get<VocabLookup>('/vocabulary/lookup', { params: { word } }).then((r) => r.data),
  batchAdd: (items: VocabularyBatchItem[]) =>
    http
      .post<VocabularyBatchResponse>('/vocabulary/batch', { items })
      .then((r) => r.data),
}

export const readingApi = {
  list: (type?: string) =>
    http
      .get<ReadingListResponse>('/reading/articles', { params: type ? { type } : undefined })
      .then((r) => r.data),
  detail: (id: number) =>
    http.get<ReadingArticleDetail>(`/reading/articles/${id}`).then((r) => r.data),
  importText: (body: {
    title: string
    subtitle?: string
    contentMd: string
    sourceUrl?: string
    sourceType?: string
    tagId?: number | null
  }) => http.post<ReadingArticleDetail>('/reading/import', body).then((r) => r.data),
  importUrl: (body: { url: string; title?: string; tagId?: number | null }) =>
    http.post<ReadingArticleDetail>('/reading/import-url', body).then((r) => r.data),
  complete: (id: number, body: { minutes: number; answers: { questionId: number; answer: string }[] }) =>
    http
      .post<ReadingCompleteResponse>(`/reading/articles/${id}/complete`, body)
      .then((r) => r.data),
  saveProgress: (id: number, scrollPercent: number) =>
    http.post(`/reading/articles/${id}/progress`, { scrollPercent }),
  vocabScan: (id: number, knownWords: string[]) =>
    http
      .post<VocabScanResponse>(`/reading/articles/${id}/vocab-scan`, { knownWords })
      .then((r) => r.data),
  previewState: (id: number) =>
    http.get<PreviewStateResponse>(`/reading/articles/${id}/preview`).then((r) => r.data),
  previewGenerate: (id: number) =>
    http.post<PreviewGenerateResponse>(`/reading/articles/${id}/preview/generate`).then((r) => r.data),
  previewSave: (
    id: number,
    body: {
      words: PreviewWord[]
      grammar: PreviewGrammar[]
      promptVersion?: string
      provider?: string
      model?: string
    },
  ) => http.put<PreviewContent>(`/reading/articles/${id}/preview`, body).then((r) => r.data),
  quizGenerate: (
    id: number,
    body: { difficulty: string; sectionTitle?: string; sectionText?: string },
  ) =>
    http.post<QuizGenerateResponse>(`/reading/articles/${id}/quiz/generate`, body).then((r) => r.data),
  quizList: (id: number) =>
    http.get<QuizListResponse>(`/reading/articles/${id}/quiz`).then((r) => r.data),
  quizSubmit: (setId: number, body: { answers: { questionId: number; answer: string }[] }) =>
    http.post<QuizSubmitResponse>(`/reading/quiz/sets/${setId}/submit`, body).then((r) => r.data),
  tags: () => http.get<ReadingTag[]>('/reading/tags').then((r) => r.data),
  createTag: (body: { name: string; sortOrder?: number }) =>
    http.post<ReadingTag>('/reading/tags', body).then((r) => r.data),
  updateTag: (id: number, body: { name: string; sortOrder?: number }) =>
    http.put<ReadingTag>(`/reading/tags/${id}`, body).then((r) => r.data),
  setTagStatus: (id: number, enabled: boolean) =>
    http.put<ReadingTag>(`/reading/tags/${id}/status`, { enabled }).then((r) => r.data),
}

export const planApi = {
  today: () => http.get<TodayPlan>('/plan/today').then((r) => r.data),
  week: () => http.get<WeekPlan>('/plan/week').then((r) => r.data),
  completeTask: (id: number) =>
    http.post<Task>(`/plan/tasks/${id}/complete`).then((r) => r.data),
}

export const dashboardApi = {
  get: () => http.get<Dashboard>('/dashboard').then((r) => r.data),
}

export const aiApi = {
  status: () => http.get<AiStatus>('/ai/status').then((r) => r.data),
  explain: (body: { text: string; context?: string; mode?: string }) =>
    http.post<AiExplainResponse>('/ai/explain', body).then((r) => r.data),
  judgeGuess: (body: { word: string; guess: string; context?: string }) =>
    http.post<VocabJudgeResponse>('/ai/judge-guess', body).then((r) => r.data),
}

export const searchApi = {
  query: (q: string, limit = 8) =>
    http.get<SearchResponse>('/search', { params: { q, limit } }).then((r) => r.data),
  status: () => http.get<VectorStatus>('/search/status').then((r) => r.data),
}
