<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Link, Plus, Reading } from '@element-plus/icons-vue'
import { readingApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { ReadingArticleSummary } from '@/types/api'

const router = useRouter()
const articles = ref<ReadingArticleSummary[]>([])
const loading = ref(true)
const error = ref('')
const activeType = ref('ALL')
const dialogOpen = ref(false)
const importMode = ref<'text' | 'url'>('text')
const importing = ref(false)

const form = ref({
  title: '',
  subtitle: '',
  contentMd: '',
  sourceUrl: '',
  sourceType: 'CUSTOM',
})
const urlForm = ref({ url: '', title: '' })

const filters = [
  { value: 'ALL', label: '全部' },
  { value: 'IELTS', label: '雅思学术' },
  { value: 'TECH', label: '技术文档' },
  { value: 'CUSTOM', label: '我的导入' },
]

const filtered = computed(() => {
  if (activeType.value === 'ALL') return articles.value
  return articles.value.filter((article) => article.sourceType === activeType.value)
})
const featured = computed(() => filtered.value.find((article) => !article.read) ?? filtered.value[0] ?? null)
const readCount = computed(() => articles.value.filter((article) => article.read).length)

function typeLabel(type: string) {
  return { IELTS: 'IELTS', TECH: 'TECH', CUSTOM: 'IMPORTED' }[type] ?? type
}

function openArticle(article: ReadingArticleSummary) {
  router.push(`/reading/${article.id}`)
}

function openImport() {
  form.value = { title: '', subtitle: '', contentMd: '', sourceUrl: '', sourceType: 'CUSTOM' }
  urlForm.value = { url: '', title: '' }
  importMode.value = 'text'
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
      detail = await readingApi.importUrl({
        url: urlForm.value.url.trim(),
        title: urlForm.value.title.trim() || undefined,
      })
    } else {
      if (!form.value.title.trim() || form.value.contentMd.trim().length < 80) {
        ElMessage.warning('请填写标题，并粘贴至少一段完整文本')
        return
      }
      detail = await readingApi.importText({
        title: form.value.title.trim(),
        subtitle: form.value.subtitle.trim() || undefined,
        contentMd: form.value.contentMd.trim(),
        sourceUrl: form.value.sourceUrl.trim() || undefined,
        sourceType: form.value.sourceType,
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
    const response = await readingApi.list()
    articles.value = response.articles
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
        <span class="read-stat">{{ readCount }} / {{ articles.length }} 已读</span>
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
            <span class="pill green">{{ typeLabel(featured.sourceType) }}</span>
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
            {{ featured.read ? '重新打开' : '开始阅读' }}
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
            v-for="filter in filters"
            :key="filter.value"
            type="button"
            class="filter"
            :class="{ active: activeType === filter.value }"
            @click="activeType = filter.value"
          >
            {{ filter.label }}
          </button>
        </div>
        <span class="list-count">{{ filtered.length }} 篇</span>
      </div>

      <div v-if="filtered.length === 0" class="empty">
        <span class="empty-icon">Aa</span>
        <strong>这个分类还没有文章</strong>
        <el-button type="primary" plain @click="openImport">导入一篇</el-button>
      </div>

      <div v-else class="article-grid">
        <article
          v-for="article in filtered"
          :key="article.id"
          class="article-card card"
          :class="{ read: article.read }"
          @click="openArticle(article)"
        >
          <div class="article-top">
            <span class="source-tag" :class="article.sourceType.toLowerCase()">{{ typeLabel(article.sourceType) }}</span>
            <span v-if="article.read" class="read-tag">已读</span>
            <span v-else class="unread-tag">未读</span>
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
              <el-form-item label="类型">
                <el-select v-model="form.sourceType" style="width: 100%">
                  <el-option label="我的导入" value="CUSTOM" />
                  <el-option label="雅思学术" value="IELTS" />
                  <el-option label="技术文档" value="TECH" />
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
  font-size: 10px;
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
  background:
    radial-gradient(circle at 88% 18%, rgb(137 181 151 / 28%), transparent 36%),
    linear-gradient(135deg, #1e4230, #173426 64%, #12291e);
  color: #eef5ef;
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
  color: rgb(238 245 239 / 62%);
  font-size: 12px;
  line-height: 1.75;
}

.featured-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  margin: 16px 0 22px;
  color: rgb(238 245 239 / 42%);
  font-family: var(--font-mono);
  font-size: 9px;
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
  padding: 6px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 10px;
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

.list-count {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
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
  border-color: #bfd4c5;
  box-shadow: var(--shadow-md);
}

.article-card.read {
  background: #fbfaf5;
}

.article-top {
  display: flex;
  align-items: center;
  gap: 6px;
}

.source-tag {
  padding: 3px 6px;
  border-radius: 4px;
  background: var(--surface-3);
  color: var(--muted);
  font-size: 7px;
  font-weight: 800;
  letter-spacing: 0.09em;
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

.read-tag,
.unread-tag {
  margin-left: auto;
  font-size: 8px;
}

.read-tag {
  color: var(--green-2);
}

.unread-tag {
  color: var(--faint);
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
  font-size: 10px;
  line-height: 1.5;
}

.article-summary {
  display: -webkit-box;
  margin-top: 12px;
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
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
  font-size: 8px;
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
  font-size: 10px;
  line-height: 1.6;
}

.url-note .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--green);
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
