<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type UploadFile } from 'element-plus'
import { Link, Plus, PriceTag, Reading, UploadFilled } from '@element-plus/icons-vue'
import { readingApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { ReadingArticleSummary, ReadingTag } from '@/types/api'

const MAX_FILE_BYTES = 1024 * 1024
// Keep in sync with ImportArticleRequest @Size(max = 200000) on the backend.
const MAX_CONTENT_CHARS = 200000

const router = useRouter()
const route = useRoute()
const articles = ref<ReadingArticleSummary[]>([])
const tags = ref<ReadingTag[]>([])
const loading = ref(true)
const error = ref('')
type TagFilter = number | 'ALL' | 'NONE'
const activeTag = ref<TagFilter>('ALL')
const listTotal = ref(0)
const listReadCount = ref(0)
const dialogOpen = ref(false)
const importMode = ref<'file' | 'text' | 'url'>('file')
const importing = ref(false)

const form = ref({
  title: '',
  subtitle: '',
  contentMd: '',
  sourceUrl: '',
  tagId: null as number | null,
})
const urlForm = ref({ url: '', title: '', tagId: null as number | null })

const fileForm = ref({ title: '', subtitle: '', tagId: null as number | null })
const fileName = ref('')
const fileContent = ref('')
const fileBytes = ref(0)

function resetFileSelection() {
  fileForm.value = { title: '', subtitle: '', tagId: null }
  fileName.value = ''
  fileContent.value = ''
  fileBytes.value = 0
}

/** Takes over a picked file: validates it and pre-fills title from its leading H1 or its name. */
async function onFilePicked(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  if (!/\.(md|markdown|txt)$/i.test(raw.name)) {
    ElMessage.warning('只支持 .md / .markdown / .txt 文件')
    return
  }
  if (raw.size > MAX_FILE_BYTES) {
    ElMessage.warning('文件超过 1 MB，请拆分后再导入')
    return
  }
  let text = await raw.text()
  const lines = text.split(/\r?\n/)
  const firstIndex = lines.findIndex((line) => line.trim() !== '')
  let title = raw.name.replace(/\.(md|markdown|txt)$/i, '')
  if (firstIndex >= 0 && /^#\s+/.test(lines[firstIndex])) {
    title = lines[firstIndex].replace(/^#\s+/, '').trim() || title
    lines.splice(firstIndex, 1)
    while (lines.length > 0 && lines[0].trim() === '') {
      lines.shift()
    }
    text = lines.join('\n')
  }
  if (text.trim().length < 80) {
    ElMessage.warning('文件正文太短，至少需要一段完整内容')
    return
  }
  if (text.length > MAX_CONTENT_CHARS) {
    ElMessage.warning(`正文约 ${text.length} 字符，超过 ${MAX_CONTENT_CHARS} 上限`)
    return
  }
  fileName.value = raw.name
  fileBytes.value = raw.size
  fileContent.value = text
  fileForm.value.title = title.slice(0, 200)
}

const enabledTags = computed(() => tags.value.filter((tag) => tag.enabled))

type FilterOption = { value: TagFilter; label: string; off?: boolean }

/**
 * Filters follow the tags articles actually use, so disabling a tag no longer
 * hides its articles from the filter row. Untagged rows get their own bucket.
 */
const filterOptions = computed<FilterOption[]>(() => {
  const usedTagIds = new Set<number>()
  let hasUntagged = false
  for (const article of articles.value) {
    if (article.tagId == null) hasUntagged = true
    else usedTagIds.add(article.tagId)
  }
  const options: FilterOption[] = [{ value: 'ALL', label: '全部' }]
  for (const tag of tags.value) {
    if (usedTagIds.has(tag.id) || tag.id === activeTag.value) {
      options.push({ value: tag.id, label: tag.name, off: !tag.enabled })
    }
  }
  if (hasUntagged) options.push({ value: 'NONE', label: '未分类' })
  return options
})

const filtered = computed(() => {
  if (activeTag.value === 'ALL') return articles.value
  if (activeTag.value === 'NONE') return articles.value.filter((article) => article.tagId == null)
  return articles.value.filter((article) => article.tagId === activeTag.value)
})
const featured = computed(
  () => filtered.value.find((article) => !article.read) ?? filtered.value[0] ?? null,
)
const emptyTitle = computed(() => {
  if (activeTag.value === 'NONE') return '没有未分类的文章'
  if (activeTag.value === 'ALL') return '文章库还没有文章'
  return '这个分类还没有文章'
})

/** Raw resume position 0-100. It never implies the article is finished. */
function progressPercent(article: ReadingArticleSummary) {
  return Math.min(100, Math.max(0, article.scrollPercent ?? 0))
}

/** Finished articles show a full bar even though completion resets the resume point. */
function displayPercent(article: ReadingArticleSummary) {
  return article.read ? 100 : progressPercent(article)
}

function actionLabel(article: ReadingArticleSummary) {
  if (article.read) return '重新打开'
  return progressPercent(article) <= 0 ? '开始阅读' : '继续阅读'
}

function parseTagQuery(value: unknown): TagFilter {
  if (typeof value !== 'string' || value === '') return 'ALL'
  if (value === 'NONE') return 'NONE'
  if (!/^\d+$/.test(value)) return 'ALL'
  const id = Number(value)
  return id > 0 ? id : 'ALL'
}

function isKnownTagFilter(value: TagFilter) {
  if (value === 'ALL') return true
  if (value === 'NONE') return articles.value.some((article) => article.tagId == null)
  return tags.value.some((tag) => tag.id === value)
}

/** Filter state lives in `?tag=` so refresh and browser-back keep the same category. */
function syncTagFromQuery() {
  const next = parseTagQuery(route.query.tag)
  if (!isKnownTagFilter(next)) {
    activeTag.value = 'ALL'
    if (route.query.tag !== undefined) {
      const query = { ...route.query }
      delete query.tag
      void router.replace({ query })
    }
    return
  }
  activeTag.value = next
}

function selectTag(value: TagFilter) {
  if (activeTag.value === value) return
  activeTag.value = value
  const query = { ...route.query }
  if (value === 'ALL') delete query.tag
  else query.tag = String(value)
  void router.replace({ query })
}

watch(() => route.query.tag, syncTagFromQuery)

function openArticle(article: ReadingArticleSummary) {
  router.push(`/reading/${article.id}`)
}

function openImport() {
  form.value = { title: '', subtitle: '', contentMd: '', sourceUrl: '', tagId: null }
  urlForm.value = { url: '', title: '', tagId: null }
  resetFileSelection()
  importMode.value = 'file'
  dialogOpen.value = true
}

async function importArticle() {
  importing.value = true
  try {
    let detail
    if (importMode.value === 'url') {
      if (!urlForm.value.url.trim()) {
        ElMessage.warning('请输入网页地址')
        return
      }
      if (urlForm.value.tagId == null) {
        ElMessage.warning('请选择文档类型')
        return
      }
      detail = await readingApi.importUrl({
        url: urlForm.value.url.trim(),
        title: urlForm.value.title.trim() || undefined,
        tagId: urlForm.value.tagId,
      })
    } else if (importMode.value === 'file') {
      if (!fileContent.value) {
        ElMessage.warning('请先选择或拖入一个 Markdown 文件')
        return
      }
      if (!fileForm.value.title.trim()) {
        ElMessage.warning('请填写文章标题')
        return
      }
      if (fileForm.value.tagId == null) {
        ElMessage.warning('请选择文档类型')
        return
      }
      detail = await readingApi.importText({
        title: fileForm.value.title.trim(),
        subtitle: fileForm.value.subtitle.trim() || `来自文件 ${fileName.value}`,
        contentMd: fileContent.value,
        tagId: fileForm.value.tagId,
      })
    } else {
      if (!form.value.title.trim() || form.value.contentMd.trim().length < 80) {
        ElMessage.warning('请填写标题，并粘贴至少一段完整文本')
        return
      }
      if (form.value.tagId == null) {
        ElMessage.warning('请选择文档类型')
        return
      }
      detail = await readingApi.importText({
        title: form.value.title.trim(),
        subtitle: form.value.subtitle.trim() || undefined,
        contentMd: form.value.contentMd.trim(),
        sourceUrl: form.value.sourceUrl.trim() || undefined,
        tagId: form.value.tagId,
      })
    }
    ElMessage.success('文章已加入阅读实验室')
    dialogOpen.value = false
    await router.push(`/reading/${detail.article.id}`)
  } catch (err) {
    ElMessage.error(errorMessage(err, '导入失败'))
  } finally {
    importing.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [response, tagResponse] = await Promise.all([readingApi.list(), readingApi.tags()])
    articles.value = response.articles
    tags.value = tagResponse
    listTotal.value = response.total
    listReadCount.value = response.readCount
    syncTagFromQuery()
  } catch (err) {
    error.value = errorMessage(err, '加载文章失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page reading-list-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">READING LAB</p>
        <h1 class="page-title">读你真正需要读的英文</h1>
        <p class="page-subtitle">
          雅思学术文章训练题型，技术文档训练工作阅读。选中词句可以直接查词或让 AI 拆解。
        </p>
      </div>
      <div class="header-actions">
        <span class="read-stat">{{ listReadCount }} / {{ listTotal }} 读完</span>
        <el-button @click="router.push('/reading/tags')">
          <el-icon><PriceTag /></el-icon> 标签管理
        </el-button>
        <el-button type="primary" @click="openImport">
          <el-icon><Plus /></el-icon> 导入文章
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
      <section v-if="featured" class="featured">
        <div class="featured-copy">
          <div class="featured-tags">
            <span class="pill green">{{ featured.tagName }}</span>
            <span class="pill">{{ featured.cefrLevel }}</span>
            <span class="pill">{{ featured.estimatedMinutes }} min</span>
          </div>
          <h2>{{ featured.title }}</h2>
          <p>{{ featured.summary }}</p>
          <div class="featured-meta">
            <span>{{ featured.wordCount }} words</span>
            <span class="dot" />
            <span>{{ featured.topics.join(' · ') }}</span>
          </div>
          <el-button type="primary" size="large" round @click="openArticle(featured)">
            <el-icon><Reading /></el-icon>
            {{ actionLabel(featured) }}
          </el-button>
        </div>
        <div class="featured-art">
          <span class="art-line line-a" />
          <span class="art-line line-b" />
          <span class="art-line line-c" />
          <span class="art-letter">A</span>
          <span class="art-note">read slowly</span>
        </div>
      </section>

      <div class="list-toolbar">
        <div class="filter-row">
          <button
            v-for="filter in filterOptions"
            :key="filter.value"
            type="button"
            class="filter"
            :class="{ active: activeTag === filter.value, off: filter.off }"
            :aria-pressed="activeTag === filter.value"
            :title="filter.off ? `${filter.label}已停用，仅用于查看历史文章` : undefined"
            @click="selectTag(filter.value)"
          >
            {{ filter.label }}
            <span v-if="filter.off" class="filter-off">停用</span>
          </button>
        </div>
        <span class="list-count">{{ filtered.length }} 篇</span>
      </div>

      <div v-if="filtered.length === 0" class="empty">
        <span class="empty-icon">Aa</span>
        <strong>{{ emptyTitle }}</strong>
        <el-button type="primary" plain @click="openImport">导入一篇</el-button>
      </div>

      <div v-else class="article-grid">
        <article
          v-for="article in filtered"
          :key="article.id"
          class="article-card card"
          :class="{ 'is-done': article.read }"
          @click="openArticle(article)"
        >
          <div class="article-top">
            <span class="source-tag" :class="article.sourceType.toLowerCase()">{{ article.tagName }}</span>
            <span
              class="progress-tag"
              :class="{ done: article.read, reading: !article.read && progressPercent(article) > 0 }"
            >
              {{ article.read ? '已读完' : progressPercent(article) >= 100 ? '待完成' : `${progressPercent(article)}%` }}
            </span>
          </div>
          <div
            class="progress-track"
            role="progressbar"
            :aria-label="`${article.title} 阅读进度`"
            :aria-valuenow="displayPercent(article)"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <div
              class="progress-fill"
              :class="{ done: article.read }"
              :style="{ width: displayPercent(article) + '%' }"
            />
          </div>
          <h3>{{ article.title }}</h3>
          <p class="article-sub">{{ article.subtitle }}</p>
          <p class="article-summary">{{ article.summary }}</p>
          <div class="article-foot">
            <span>{{ article.cefrLevel }}</span>
            <span>{{ article.wordCount }} words</span>
            <span>{{ article.estimatedMinutes }} min</span>
            <span class="article-arrow">→</span>
          </div>
        </article>
      </div>
    </template>

    <el-dialog v-model="dialogOpen" title="导入阅读材料" width="min(720px, 94vw)">
      <el-tabs v-model="importMode">
        <el-tab-pane label="上传 Markdown 文件" name="file">
          <div class="url-note">
            <el-icon><UploadFilled /></el-icon>
            <p>支持 .md / .markdown / .txt（UTF-8 编码，1 MB 以内）。文件若以 # 标题行开头，会自动作为文章标题。</p>
          </div>
          <el-upload
            drag
            :auto-upload="false"
            :show-file-list="false"
            accept=".md,.markdown,.txt"
            :on-change="onFilePicked"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">把 Markdown 文件拖到这里，或<em>点击选择</em></div>
          </el-upload>
          <div v-if="fileName" class="picked-file">
            <strong>{{ fileName }}</strong>
            <span>{{ (fileBytes / 1024).toFixed(1) }} KB · 正文 {{ fileContent.length }} 字符</span>
            <el-button link type="danger" size="small" @click="resetFileSelection">移除</el-button>
          </div>
          <el-form v-if="fileContent" label-position="top" class="file-form">
            <div class="form-row">
              <el-form-item label="标题">
                <el-input v-model="fileForm.title" maxlength="200" />
              </el-form-item>
              <el-form-item label="副标题（可选）">
                <el-input v-model="fileForm.subtitle" placeholder="留空则显示“来自文件 xxx.md”" />
              </el-form-item>
            </div>
            <el-form-item label="文档类型" required>
              <el-select v-model="fileForm.tagId" placeholder="请选择文档类型" style="width: 100%">
                <el-option v-for="tag in enabledTags" :key="tag.id" :label="tag.name" :value="tag.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="粘贴文本 / Markdown" name="text">
          <el-form label-position="top">
            <div class="form-row">
              <el-form-item label="标题">
                <el-input v-model="form.title" placeholder="例如 How a REST API Works" />
              </el-form-item>
              <el-form-item label="副标题（可选）">
                <el-input v-model="form.subtitle" placeholder="一句话说明" />
              </el-form-item>
            </div>
            <el-form-item label="正文">
              <el-input
                v-model="form.contentMd"
                type="textarea"
                :rows="12"
                placeholder="粘贴英文文章、技术文档或 Markdown。代码块请使用 ``` 包裹。"
              />
            </el-form-item>
            <div class="form-row">
              <el-form-item label="原始链接（可选）">
                <el-input v-model="form.sourceUrl" placeholder="https://..." />
              </el-form-item>
              <el-form-item label="文档类型" required>
                <el-select v-model="form.tagId" placeholder="请选择文档类型" style="width: 100%">
                  <el-option v-for="tag in enabledTags" :key="tag.id" :label="tag.name" :value="tag.id" />
                </el-select>
              </el-form-item>
            </div>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="从 URL 导入" name="url">
          <div class="url-note">
            <el-icon><Link /></el-icon>
            <p>只抓取公开网页的正文，不支持登录页、PDF 或内网地址。抓取后仍可编辑。</p>
          </div>
          <el-form label-position="top">
            <el-form-item label="网页地址">
              <el-input v-model="urlForm.url" placeholder="https://example.com/article" />
            </el-form-item>
            <el-form-item label="标题（可选，留空使用网页标题）">
              <el-input v-model="urlForm.title" />
            </el-form-item>
            <el-form-item label="文档类型" required>
              <el-select v-model="urlForm.tagId" placeholder="请选择文档类型" style="width: 100%">
                <el-option v-for="tag in enabledTags" :key="tag.id" :label="tag.name" :value="tag.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="importArticle">
          {{ importMode === 'url' ? '抓取并导入' : '导入文章' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.read-stat {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 12px;
}

.featured {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 30px;
  min-height: 300px;
  padding: clamp(26px, 4vw, 44px);
  overflow: hidden;
  border-radius: var(--radius-lg);
  border: 1px solid var(--hero-ring);
  background:
    radial-gradient(circle at 88% 18%, var(--hero-glow), transparent 36%),
    linear-gradient(135deg, var(--hero-1), var(--hero-2) 64%, var(--hero-3));
  color: var(--hero-ink);
  box-shadow: var(--shadow-md);
}

.featured-copy {
  max-width: 640px;
}

.featured-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 18px;
}

.featured-tags .pill {
  border-color: rgb(255 255 255 / 15%);
  background: rgb(255 255 255 / 7%);
  color: #d7e8dc;
}

.featured h2 {
  font-family: var(--font-reading);
  font-size: clamp(31px, 4vw, 48px);
  font-weight: 500;
  letter-spacing: -0.045em;
  line-height: 1.06;
}

.featured-copy > p {
  max-width: 560px;
  margin-top: 12px;
  color: rgb(238 245 239 / 66%);
  font-size: 14px;
  line-height: 1.75;
}

.featured-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  margin: 16px 0 22px;
  color: rgb(238 245 239 / 52%);
  font-family: var(--font-mono);
  font-size: 11px;
}

.featured-meta .dot {
  background: rgb(238 245 239 / 28%);
}

.featured-art {
  position: relative;
  flex: 0 0 220px;
  min-height: 220px;
}

.art-line {
  position: absolute;
  right: 0;
  height: 1px;
  background: rgb(255 255 255 / 14%);
  transform-origin: right;
}

.line-a {
  top: 40px;
  width: 180px;
}

.line-b {
  top: 90px;
  width: 140px;
}

.line-c {
  top: 140px;
  width: 160px;
}

.art-letter {
  position: absolute;
  right: 30px;
  bottom: 32px;
  color: rgb(255 255 255 / 12%);
  font-family: var(--font-reading);
  font-size: 160px;
  line-height: 0.7;
}

.art-note {
  position: absolute;
  right: 6px;
  bottom: 2px;
  color: rgb(255 255 255 / 30%);
  font-family: var(--font-reading);
  font-size: 11px;
  font-style: italic;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin: 30px 0 14px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}

.filter {
  padding: 7px 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  cursor: pointer;
}

.filter:hover {
  color: var(--ink);
}

.filter.active {
  border-color: var(--line);
  background: var(--surface);
  color: var(--green);
  font-weight: 700;
}

.filter.off {
  opacity: 0.72;
}

.filter-off {
  margin-left: 4px;
  padding: 0 4px;
  border: 1px solid var(--line);
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 500;
}

.list-count {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 12px;
}

.article-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 11px;
}

.article-card {
  display: flex;
  flex-direction: column;
  min-height: 250px;
  padding: 19px;
  cursor: pointer;
  transition: 180ms ease;
}

.article-card:hover {
  transform: translateY(-3px);
  border-color: var(--line-strong);
  box-shadow: var(--shadow-md);
}

.article-card.is-done {
  background: var(--surface-2);
}

.article-top {
  display: flex;
  align-items: center;
  gap: 6px;
}

.source-tag {
  padding: 4px 8px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--muted);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.09em;
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

.progress-tag {
  margin-left: auto;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 11px;
}

.progress-tag.reading {
  color: var(--amber-text);
}

.progress-tag.done {
  color: var(--green-2);
}

.progress-track {
  height: 3px;
  margin-top: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-3);
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--amber);
  transition: width 320ms ease;
}

.progress-fill.done {
  background: var(--green);
}

.article-card h3 {
  margin-top: 14px;
  font-family: var(--font-reading);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.025em;
  line-height: 1.2;
}

.article-sub {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.article-summary {
  display: -webkit-box;
  margin-top: 12px;
  overflow: hidden;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.article-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  padding-top: 16px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 11px;
}

.article-arrow {
  margin-left: auto;
  color: var(--green);
  font-size: 14px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.url-note {
  display: flex;
  gap: 9px;
  margin-bottom: 16px;
  padding: 11px 12px;
  border-radius: 9px;
  background: var(--surface-2);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.url-note .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--green);
}

.picked-file {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-2);
  font-size: 13px;
}

.picked-file span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 12px;
}

.picked-file .el-button {
  margin-left: auto;
}

.file-form {
  margin-top: 14px;
}

@media (max-width: 1100px) {
  .article-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 800px) {
  .featured-art {
    display: none;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .article-grid {
    grid-template-columns: 1fr;
  }

  .featured {
    padding: 24px 20px;
  }
}
</style>
