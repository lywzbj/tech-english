<script setup lang="ts">
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import type { AiExplainResponse, VocabLookup } from '@/types/api'

// 右下角悬浮的 AI 阅读助手：只在用户选中文字并触发查词 / AI 解释后弹出。
defineProps<{
  open: boolean
  panel: 'none' | 'word' | 'ai'
  wordLoading: boolean
  wordResult: VocabLookup | null
  wordError: string
  aiLoading: boolean
  aiResult: AiExplainResponse | null
  aiError: string
}>()

const emit = defineEmits<{
  (e: 'update:open', open: boolean): void
  (e: 'explain', mode?: 'word' | 'sentence'): void
  (e: 'add-word'): void
}>()
</script>

<template>
  <section v-if="open" class="assistant-panel card" aria-label="AI 阅读助手">
    <div class="assistant-head">
      <div>
        <p class="eyebrow">AI READING ASSISTANT</p>
        <h2>AI 助手</h2>
      </div>
      <el-button circle size="small" title="关闭助手" @click="emit('update:open', false)">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>

    <div v-if="aiLoading && panel === 'ai'" class="assistant-loading">
      <el-skeleton animated :rows="4" />
    </div>

    <div v-else-if="panel === 'word' && wordLoading" class="assistant-loading">
      <el-skeleton animated :rows="3" />
    </div>

    <div v-else-if="panel === 'word' && wordResult" class="word-panel">
      <div class="word-panel-head">
        <h3>{{ wordResult.lemma }}</h3>
        <span class="pill">{{ wordResult.pos }}</span>
      </div>
      <p class="word-phonetic">{{ wordResult.phonetic }}</p>
      <p class="word-zh">{{ wordResult.definitionZh }}</p>
      <p class="word-en">{{ wordResult.definitionEn }}</p>
      <p v-if="wordResult.exampleEn" class="word-example">{{ wordResult.exampleEn }}</p>
      <div class="word-actions">
        <span class="pill green">{{ wordResult.domain }}</span>
        <el-button size="small" type="primary" plain :disabled="wordResult.inDeck" @click="emit('add-word')">
          {{ wordResult.inDeck ? '已在词汇本' : '加入生词本' }}
        </el-button>
      </div>
    </div>

    <div v-else-if="panel === 'word' && wordError && !aiResult" class="assistant-error">
      <p>{{ wordError }}</p>
      <el-button size="small" @click="emit('explain', 'word')">让 AI 解释</el-button>
    </div>

    <div v-else-if="panel === 'ai' && aiResult" class="ai-panel">
      <div v-if="aiResult.translation" class="ai-block">
        <span class="ai-label">TRANSLATION</span>
        <p class="ai-translation">{{ aiResult.translation }}</p>
      </div>
      <div class="ai-block">
        <span class="ai-label">EXPLANATION</span>
        <p>{{ aiResult.explanation }}</p>
      </div>
      <div v-if="aiResult.grammarPoints.length" class="ai-block">
        <span class="ai-label">GRAMMAR</span>
        <div v-for="point in aiResult.grammarPoints" :key="point.point" class="grammar-point">
          <strong>{{ point.point }}</strong>
          <p>{{ point.detail }}</p>
        </div>
      </div>
      <div v-if="aiResult.vocabulary.length" class="ai-block">
        <span class="ai-label">WORDS</span>
        <div v-for="word in aiResult.vocabulary" :key="word.word" class="vocab-hint">
          <strong>{{ word.word }}</strong>
          <span>{{ word.meaning }}</span>
          <p>{{ word.note }}</p>
        </div>
      </div>
    </div>

    <div v-else-if="aiError" class="assistant-error">
      <p>{{ aiError }}</p>
      <el-button size="small" @click="emit('explain')">重试</el-button>
    </div>

    <div v-else class="assistant-hint">
      <div class="hint-icon"><el-icon><ChatDotRound /></el-icon></div>
      <p>用鼠标选中一个词或一句话，然后选择“查词”或“AI 解释”。</p>
    </div>

    <span v-if="aiResult?.provider" class="ai-provider">{{ aiResult.provider === 'deepseek' ? 'DeepSeek' : 'Demo AI' }}</span>
  </section>
</template>

<style scoped>
.assistant-panel {
  position: relative;
  width: 340px;
  max-width: calc(100vw - 32px);
  max-height: min(480px, 52vh);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 18px;
  animation: rise-in 0.18s ease both;
}

.assistant-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.assistant-head h2 {
  margin-top: 3px;
  font-family: var(--font-reading);
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.ai-provider {
  position: absolute;
  top: 22px;
  right: 58px;
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.assistant-loading {
  margin-top: 16px;
}

.assistant-hint {
  display: grid;
  justify-items: center;
  gap: 9px;
  margin-top: 18px;
  padding: 22px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: 11px;
  text-align: center;
}

.hint-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: var(--green-3);
  color: var(--green);
  font-size: 17px;
}

.assistant-hint p {
  max-width: 230px;
  color: var(--faint);
  font-size: 12px;
  line-height: 1.65;
}

.ai-block {
  margin-top: 15px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.ai-block:first-child {
  border-top: 0;
  padding-top: 0;
}

.ai-label {
  color: var(--faint);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.ai-translation {
  margin-top: 4px;
  color: var(--ink);
  font-family: var(--font-reading);
  font-size: 17px;
  line-height: 1.5;
}

.ai-block > p {
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.grammar-point {
  margin-top: 9px;
  padding: 9px 10px;
  border-radius: 8px;
  background: var(--surface-2);
}

.grammar-point strong {
  font-size: 12px;
}

.grammar-point p {
  margin-top: 2px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.vocab-hint {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 3px 7px;
  margin-top: 9px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--line);
}

.vocab-hint strong {
  font-family: var(--font-reading);
  font-size: 14px;
}

.vocab-hint span {
  color: var(--muted);
  font-size: 12px;
}

.vocab-hint p {
  grid-column: 1 / -1;
  color: var(--faint);
  font-size: 11px;
  line-height: 1.5;
}

.word-panel-head {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin-top: 14px;
}

.word-panel-head h3 {
  font-family: var(--font-reading);
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.word-phonetic {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 11px;
}

.word-zh {
  margin-top: 9px;
  color: var(--ink);
  font-size: 14px;
}

.word-en {
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.word-example {
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
  color: var(--ink-soft);
  font-family: var(--font-reading);
  font-size: 14px;
  line-height: 1.5;
}

.word-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 14px;
}

.assistant-error {
  margin-top: 14px;
  padding: 11px 12px;
  border-radius: 8px;
  background: var(--rose-soft);
  color: var(--rose);
  font-size: 12px;
  line-height: 1.6;
}

.assistant-error .el-button {
  margin-top: 8px;
}
</style>
