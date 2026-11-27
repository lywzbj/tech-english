<script setup lang="ts">
import { computed } from 'vue'
import { Check, Close } from '@element-plus/icons-vue'

/**
 * 通用判分反馈：对/错结论 + 正确答案（主观题可用「参考答案」）+ 解析 + 逐项选项说明。
 * 放在 QuestionChoice / QuestionOpen 的默认插槽里即可与题干对齐。
 */
const props = withDefaults(
  defineProps<{
    correct: boolean
    correctAnswer: string
    /** 答案前缀，如「正确答案」「参考答案」 */
    answerLabel?: string
    explanation?: string
    /** 选项 → 说明，选择题判分后逐项讲解 */
    optionNotes?: Record<string, string>
    correctText?: string
    wrongText?: string
  }>(),
  {
    answerLabel: '正确答案',
    explanation: '',
    optionNotes: undefined,
    correctText: '答对了',
    wrongText: '再记一下',
  },
)

const notes = computed(() =>
  props.optionNotes ? Object.entries(props.optionNotes) : [],
)
</script>

<template>
  <div class="answer-feedback">
    <div class="feedback-line">
      <el-icon v-if="correct" class="ok"><Check /></el-icon>
      <el-icon v-else class="no"><Close /></el-icon>
      <strong>{{ correct ? correctText : wrongText }}</strong>
      <span>{{ answerLabel }}：{{ correctAnswer }}</span>
    </div>
    <p v-if="explanation">{{ explanation }}</p>
    <ul v-if="notes.length" class="option-notes">
      <li v-for="[option, note] in notes" :key="option">
        <strong :class="{ right: option === correctAnswer }">{{ option }}</strong>
        <span>{{ note }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.answer-feedback {
  margin-top: 13px;
  padding: 12px 14px;
  border-radius: 9px;
  background: var(--surface-2);
}

.feedback-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  font-size: 11px;
}

.feedback-line strong {
  color: var(--ink);
}

.feedback-line span {
  color: var(--muted);
}

.feedback-line .ok {
  color: var(--green);
}

.feedback-line .no {
  color: var(--rose);
}

.answer-feedback > p {
  margin-top: 6px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
}

.option-notes {
  display: grid;
  gap: 5px;
  margin: 9px 0 0;
  padding: 9px 0 0;
  border-top: 1px dashed var(--line);
  list-style: none;
}

.option-notes li {
  display: grid;
  grid-template-columns: minmax(90px, 160px) 1fr;
  gap: 8px;
  color: var(--muted);
  font-size: 10.5px;
  line-height: 1.6;
}

.option-notes strong {
  color: var(--faint);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.option-notes strong.right {
  color: var(--green);
}

@media (max-width: 760px) {
  .option-notes li {
    grid-template-columns: 1fr;
  }
}
</style>
