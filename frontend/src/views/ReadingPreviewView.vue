<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, MagicStick } from '@element-plus/icons-vue'
import { readingApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { PreviewContent, PreviewWord } from '@/types/api'

// 独立预习页：AI 提取草稿 → 勾选 → 确认保存 → 返回文章。
// 预习放在整页空间里做，词表和结构预告都能摊开看，不再挤在文章页的抽屉里。
const route = useRoute()
const router = useRouter()
const articleId = Number(route.params.id)

const articleTitle = ref('')
const cefrLevel = ref('')
const loadingArticle = ref(true)
const pageError = ref('')

// saved=该用户本篇已确认的存档；draft=AI 新提取、待勾选确认的草稿。
// 草稿只在页面上存在，确认保存后才按 (用户, 文章) 落库。
const saved = ref<PreviewContent | null>(null)
const draft = ref<PreviewContent | null>(null)
const generating = ref(false)
const genError = ref('')
const saving = ref(false)
const meta = ref<{ promptVersion?: string; provider?: string; model?: string }>({})

const content = computed(() => draft.value ?? saved.value)
const isDraft = computed(() => Boolean(draft.value))

const selectedWords = ref<string[]>([])

// 换一份草稿（或加载存档）时默认全选。
watch(
  content,
  (value) => {
    selectedWords.value = value ? value.words.map((w) => w.word) : []
  },
  { immediate: true },
)

const chosenWords = computed(() =>
  (content.value?.words ?? []).filter((w) => selectedWords.value.includes(w.word)),
)

onMounted(async () => {
  if (!Number.isInteger(articleId) || articleId <= 0) {
    pageError.value = '文章不存在'
    loadingArticle.value = false
    return
  }
  try {
    const detail = await readingApi.detail(articleId)
    articleTitle.value = detail.article.title
    cefrLevel.value = detail.article.cefrLevel
    const state = await readingApi.previewState(articleId)
    if (state.exists && state.preview) {
      saved.value = state.preview
      meta.value = { promptVersion: state.preview.promptVersion }
    } else {
      // 没有存档就直接开始提取，省一次点击。
      await generate()
    }
  } catch (err) {
    pageError.value = errorMessage(err, '加载预习页失败')
  } finally {
    loadingArticle.value = false
  }
})

async function generate() {
  generating.value = true
  genError.value = ''
  try {
    const result = await readingApi.previewGenerate(articleId)
    draft.value = {
      words: result.words,
      grammar: result.grammar,
      promptVersion: result.promptVersion,
    }
    meta.value = {
      promptVersion: result.promptVersion,
      provider: result.provider,
      model: result.model,
    }
  } catch (err) {
    genError.value = errorMessage(err, 'AI 预习提取失败')
  } finally {
    generating.value = false
  }
}

// 重新提取永远生成新草稿；已存档时草稿覆盖展示，存档在用户确认前保持不变。
function regenerate() {
  void generate()
}

async function confirmSave() {
  if (!chosenWords.value.length) return
  saving.value = true
  try {
    saved.value = await readingApi.previewSave(articleId, {
      words: chosenWords.value,
      grammar: content.value?.grammar ?? [],
      ...meta.value,
    })
    draft.value = null
    ElMessage.success('预习单已保存，下次进入本篇自动带上')
    goBack()
  } catch (err) {
    ElMessage.error(errorMessage(err, '保存预习单失败'))
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push({ name: 'reading-detail', params: { id: articleId } })
}
</script>

<template>
  <div class="preview-page">
    <header class="preview-header">
      <button type="button" class="back-link" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回文章
      </button>
      <span v-if="content?.promptVersion" class="pill">{{ content.promptVersion }}</span>
    </header>

    <div v-if="loadingArticle" class="preview-body">
      <el-skeleton animated :rows="8" />
    </div>

    <template v-else-if="pageError">
      <div class="preview-body">
        <div class="page-error">
          <p>{{ pageError }}</p>
          <el-button size="small" @click="goBack">返回文章</el-button>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="preview-body">
        <p class="preview-eyebrow">PREVIEW SHEET · 本篇预习</p>
        <h1 class="preview-article-title">{{ articleTitle }}</h1>
        <p class="preview-sub">
          <span v-if="cefrLevel" class="pill green">{{ cefrLevel }}</span>
          带着路标进文章：认出核心词和预告过的句型即可，剩下的交给上下文。
        </p>

        <div v-if="generating" class="preview-loading">
          <p class="loading-tip"><el-icon class="is-loading"><MagicStick /></el-icon> AI 正在通读全文，挑核心词、看句型结构…</p>
          <el-skeleton animated :rows="8" />
        </div>

        <div v-else-if="genError && !content" class="gen-error">
          <p>{{ genError }}</p>
          <el-button size="small" type="primary" plain @click="regenerate">
            <el-icon><MagicStick /></el-icon> 重新提取
          </el-button>
        </div>

        <template v-else-if="content">
          <section class="preview-block">
            <div class="preview-block-head">
              <h2>预习词表</h2>
              <span class="word-count">{{ chosenWords.length }} / {{ content.words.length }}</span>
            </div>
            <p class="block-note">只预习“换成中文就讲不清这篇”的词，给的是它在本文中的意思，其余留给上下文猜。</p>
            <el-checkbox-group v-model="selectedWords" class="word-grid">
              <label
                v-for="(item, index) in content.words"
                :key="item.word"
                class="word-row"
                :class="{ unchecked: !selectedWords.includes(item.word) }"
              >
                <span class="word-index">{{ index + 1 }}</span>
                <el-checkbox :value="item.word" />
                <span class="word-body">
                  <span class="word-head">
                    <strong>{{ item.word }}</strong>
                    <span v-if="item.where" class="pill tiny">{{ item.where }}</span>
                  </span>
                  <span class="word-meaning">{{ item.meaning }}</span>
                  <span v-if="item.note" class="word-note">{{ item.note }}</span>
                </span>
              </label>
            </el-checkbox-group>
          </section>

          <section v-if="content.grammar.length" class="preview-block">
            <div class="preview-block-head">
              <h2>结构预告</h2>
              <span class="block-count">≤ {{ content.grammar.length }} 条</span>
            </div>
            <p class="block-note">不是语法课——进文章后见到“哦，预告过的那个”就够了。</p>
            <div class="signpost-grid">
              <div v-for="g in content.grammar" :key="g.structure" class="signpost-card">
                <strong>{{ g.structure }}</strong>
                <p v-if="g.where" class="signpost-where">{{ g.where }}</p>
                <p v-if="g.risk" class="signpost-risk"><em>容易栽在：</em>{{ g.risk }}</p>
                <p v-if="g.example" class="signpost-example">{{ g.example }}</p>
              </div>
            </div>
          </section>

          <p v-if="genError" class="inline-warn">{{ genError }}（下方仍是上一份结果）</p>
        </template>
      </div>

      <footer class="preview-bar">
        <span v-if="!isDraft && saved" class="saved-flag">
          <el-icon><Check /></el-icon> 已保存，下次进入本篇自动带上
        </span>
        <span v-else class="bar-hint">勾选要预习的词，确认后保存并进入阅读</span>
        <div class="bar-actions">
          <el-button :loading="generating" @click="regenerate">
            <el-icon><MagicStick /></el-icon> 重新提取
          </el-button>
          <el-button v-if="!isDraft" @click="goBack">直接阅读</el-button>
          <el-button
            v-else
            type="primary"
            :loading="saving"
            :disabled="!chosenWords.length || !content"
            @click="confirmSave"
          >
            确认并保存
          </el-button>
        </div>
      </footer>
    </template>
  </div>
</template>

<style scoped>
.preview-page {
  display: flex;
  flex-direction: column;
  padding-bottom: 84px; /* 给底部吸底操作条让位 */
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 0;
  background: none;
  color: var(--muted);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
}

.back-link:hover {
  color: var(--ink);
}

.preview-body {
  width: min(880px, 100%);
  margin: 10px auto 0;
}

.preview-eyebrow {
  color: var(--faint);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.preview-article-title {
  margin-top: 6px;
  color: var(--ink);
  font-family: var(--font-reading);
  font-size: 30px;
  font-weight: 600;
  line-height: 1.25;
  letter-spacing: -0.01em;
}

.preview-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.page-error,
.gen-error {
  margin-top: 18px;
  padding: 16px;
  border-radius: 11px;
  background: var(--rose-soft);
  color: var(--rose);
  font-size: 13px;
  line-height: 1.7;
}

.gen-error .el-button {
  margin-top: 10px;
}

.preview-loading {
  margin-top: 22px;
}

.loading-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  color: var(--muted);
  font-size: 13px;
}

.inline-warn {
  margin-top: 14px;
  color: var(--rose);
  font-size: 12px;
}

.preview-block {
  margin-top: 26px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}

.preview-block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.preview-block-head h2 {
  color: var(--ink);
  font-size: 17px;
  font-weight: 700;
}

.word-count,
.block-count {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 12px;
}

.block-note {
  margin-top: 5px;
  color: var(--faint);
  font-size: 12px;
  line-height: 1.6;
}

/* 整页宽度充裕，词表双列排布；窄屏回落单列。 */
.word-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.word-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 11px;
  border: 1px solid var(--line);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.word-row:hover {
  border-color: var(--line-strong);
  background: var(--surface-2);
}

.word-row.unchecked {
  opacity: 0.45;
}

.word-row .el-checkbox {
  margin-top: 2px;
}

.word-index {
  margin-top: 3px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
  min-width: 14px;
  text-align: right;
}

.word-body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.word-head {
  display: flex;
  align-items: baseline;
  gap: 7px;
  flex-wrap: wrap;
}

.word-head strong {
  font-family: var(--font-reading);
  font-size: 16px;
}

.pill.tiny {
  padding: 1px 6px;
  font-size: 10px;
}

.word-meaning {
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.6;
}

.word-note {
  color: var(--faint);
  font-size: 11.5px;
  line-height: 1.55;
}

.signpost-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.signpost-card {
  padding: 12px 13px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-2);
}

.signpost-card strong {
  font-size: 14px;
}

.signpost-where,
.signpost-risk {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.6;
}

.signpost-risk em {
  color: var(--rose);
  font-style: normal;
  font-weight: 700;
}

.signpost-example {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  color: var(--ink-soft);
  font-family: var(--font-reading);
  font-size: 13.5px;
  line-height: 1.55;
}

/* 吸底操作条：勾选与确认始终可达，不用滚回页面底部。 */
.preview-bar {
  position: fixed;
  left: var(--sidebar-width, 0px);
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 26px;
  border-top: 1px solid var(--line);
  background: var(--surface);
  box-shadow: 0 -6px 18px rgb(15 23 42 / 6%);
  z-index: 20;
}

.saved-flag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-right: auto;
  color: var(--green);
  font-size: 13px;
  font-weight: 700;
}

.bar-hint {
  margin-right: auto;
  color: var(--faint);
  font-size: 12.5px;
}

.bar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 900px) {
  .word-grid {
    grid-template-columns: 1fr;
  }

  .preview-article-title {
    font-size: 24px;
  }
}
</style>
