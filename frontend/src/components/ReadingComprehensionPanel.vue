<script setup lang="ts">
import { Close, EditPen } from '@element-plus/icons-vue'
import type { ReadingCompleteResponse, ReadingQuestion } from '@/types/api'

// 右下角悬浮的阅读理解弹窗：由右缘「题目」入口按钮唤出，答题状态仍由父级持有。
defineProps<{
  open: boolean
  questions: ReadingQuestion[]
  answers: Record<number, string>
  submitting: boolean
  completeResult: ReadingCompleteResponse | null
  answeredCount: number
  questionCount: number
  allAnswered: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', open: boolean): void
  (e: 'answer', questionId: number, value: string): void
  (e: 'submit'): void
}>()
</script>

<template>
  <section v-if="open" class="question-panel card" aria-label="阅读理解">
    <div class="question-head">
      <div>
        <p class="eyebrow">COMPREHENSION</p>
        <h2><el-icon class="head-icon"><EditPen /></el-icon> 阅读理解</h2>
      </div>
      <div class="question-head-right">
        <span class="question-count">{{ answeredCount }} / {{ questionCount }}</span>
        <el-button circle size="small" title="收起阅读理解" @click="emit('update:open', false)">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>

    <div v-if="questionCount === 0" class="no-questions">
      <p>这是导入文章，暂时没有题目。完成阅读后仍会记录阅读时长。</p>
    </div>

    <div v-else class="question-list">
      <article v-for="(question, index) in questions" :key="question.id" class="question">
        <div class="question-title">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <p>{{ question.prompt }}</p>
        </div>
        <el-radio-group
          v-if="question.options.length"
          :model-value="answers[question.id] ?? ''"
          class="question-options"
          :disabled="Boolean(completeResult)"
          @update:model-value="emit('answer', question.id, String($event))"
        >
          <el-radio v-for="option in question.options" :key="option" :value="option">
            {{ option }}
          </el-radio>
        </el-radio-group>
        <el-input
          v-else
          :model-value="answers[question.id] ?? ''"
          placeholder="输入你的答案"
          :disabled="Boolean(completeResult)"
          @update:model-value="emit('answer', question.id, String($event))"
        />
      </article>
    </div>

    <div v-if="completeResult" class="reading-result">
      <div class="result-line">
        <strong>{{ completeResult.correct }} / {{ completeResult.total }}</strong>
        <span>答对</span>
        <span class="score">{{ Math.round(completeResult.score) }} 分</span>
      </div>
      <p>{{ completeResult.message }}</p>
    </div>

    <el-button
      v-else
      type="primary"
      class="complete-button"
      :loading="submitting"
      :disabled="!allAnswered"
      @click="emit('submit')"
    >
      提交阅读记录
    </el-button>
    <p class="question-hint">提交后会记录阅读时长，并完成今日阅读任务。</p>
  </section>
</template>

<style scoped>
.question-panel {
  width: 380px;
  max-width: calc(100vw - 32px);
  max-height: min(560px, 64vh);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 18px;
  animation: rise-in 0.18s ease both;
}

.question-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.question-head h2 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  font-family: var(--font-reading);
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.head-icon {
  color: var(--green);
  font-size: 17px;
}

.question-head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.question-count {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 11px;
  white-space: nowrap;
}

.question-list {
  display: grid;
  gap: 18px;
  margin-top: 17px;
}

.question {
  padding-top: 13px;
  border-top: 1px solid var(--line);
}

.question:first-child {
  padding-top: 0;
  border-top: 0;
}

.question-title {
  display: flex;
  gap: 8px;
}

.question-title > span {
  padding-top: 2px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
}

.question-title p {
  color: var(--ink);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
}

.question-options {
  display: grid;
  gap: 3px;
  margin: 8px 0 0 18px;
}

.question-options :deep(.el-radio) {
  height: auto;
  margin-right: 0;
  padding: 3px 0;
  align-items: flex-start;
}

.question-options :deep(.el-radio__label) {
  font-size: 12.5px;
  line-height: 1.5;
  white-space: normal;
}

.no-questions {
  margin-top: 13px;
  padding: 13px;
  border-radius: 9px;
  background: var(--surface-2);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.65;
}

.reading-result {
  margin-top: 16px;
  padding: 13px;
  border-radius: 9px;
  background: var(--green-3);
}

.result-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.result-line strong {
  color: var(--green);
  font-family: var(--font-reading);
  font-size: 24px;
  font-weight: 600;
}

.result-line span {
  color: var(--muted);
  font-size: 11px;
}

.result-line .score {
  margin-left: auto;
  color: var(--green);
  font-weight: 700;
}

.reading-result p {
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.complete-button {
  width: 100%;
  margin-top: 16px;
}

.question-hint {
  margin-top: 8px;
  color: var(--faint);
  font-size: 10px;
  line-height: 1.5;
  text-align: center;
}
</style>
