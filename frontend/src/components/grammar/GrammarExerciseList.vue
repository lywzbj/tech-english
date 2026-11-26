<script setup lang="ts">
import { Check, Close } from '@element-plus/icons-vue'
import type { AttemptResult, GrammarExercise } from '@/types/api'

const props = defineProps<{
  exercises: GrammarExercise[]
  answers: Record<string, string>
  results: Record<string, AttemptResult>
  disabled?: boolean
}>()

const emit = defineEmits<{
  select: [exerciseId: string, option: string]
}>()

function resultOf(exerciseId: string) {
  return props.results[exerciseId]
}

function optionState(exerciseId: string, option: string) {
  const item = resultOf(exerciseId)
  if (!item) return ''
  if (option === item.correctAnswer) return 'correct'
  if (props.answers[exerciseId] === option) return 'wrong'
  return ''
}

function skillLabel(skill: GrammarExercise['skill']) {
  if (skill === 'recognize') return '识别'
  if (skill === 'form') return '形式'
  if (skill === 'use') return '使用'
  return ''
}

function notesOf(exerciseId: string) {
  const item = resultOf(exerciseId)
  return item ? Object.entries(item.optionNotes) : []
}
</script>

<template>
  <div class="exercise-list">
    <article
      v-for="(exercise, index) in exercises"
      :key="exercise.id"
      class="card exercise"
      :class="{
        correct: resultOf(exercise.id)?.correct,
        wrong: resultOf(exercise.id) && !resultOf(exercise.id)?.correct,
      }"
    >
      <div class="exercise-head">
        <span class="exercise-number">{{ String(index + 1).padStart(2, '0') }}</span>
        <div class="exercise-stem">
          <p>{{ exercise.prompt }}</p>
          <div class="exercise-meta">
            <span v-if="skillLabel(exercise.skill)" class="exercise-pill">
              {{ skillLabel(exercise.skill) }}
            </span>
            <span v-if="exercise.source === 'AI'" class="exercise-pill ai">AI 题</span>
          </div>
        </div>
      </div>

      <el-radio-group
        :model-value="answers[exercise.id]"
        class="option-list"
        :disabled="disabled"
        @update:model-value="emit('select', exercise.id, String($event))"
      >
        <el-radio
          v-for="option in exercise.options"
          :key="option"
          :value="option"
          class="option"
          :class="optionState(exercise.id, option)"
        >
          {{ option }}
        </el-radio>
      </el-radio-group>

      <div v-if="resultOf(exercise.id)" class="answer-feedback">
        <div class="feedback-line">
          <el-icon v-if="resultOf(exercise.id)?.correct" class="ok"><Check /></el-icon>
          <el-icon v-else class="no"><Close /></el-icon>
          <strong>{{ resultOf(exercise.id)?.correct ? '答对了' : '再记一下' }}</strong>
          <span>正确答案：{{ resultOf(exercise.id)?.correctAnswer }}</span>
        </div>
        <p>{{ resultOf(exercise.id)?.explanation }}</p>
        <ul v-if="notesOf(exercise.id).length" class="option-notes">
          <li v-for="[option, note] in notesOf(exercise.id)" :key="option">
            <strong :class="{ right: option === resultOf(exercise.id)?.correctAnswer }">{{ option }}</strong>
            <span>{{ note }}</span>
          </li>
        </ul>
      </div>
    </article>
  </div>
</template>

<style scoped>
.exercise-list {
  display: grid;
  gap: 12px;
}

.exercise {
  padding: 22px;
  transition: 180ms ease;
}

.exercise.correct {
  border-color: var(--green-line);
}

.exercise.wrong {
  border-color: var(--rose-line);
}

.exercise-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.exercise-number {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--surface-3);
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.exercise-stem {
  min-width: 0;
}

.exercise-stem > p {
  padding-top: 2px;
  color: var(--ink);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
}

.exercise-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 6px;
}

.exercise-pill {
  padding: 2px 6px;
  border-radius: 5px;
  background: var(--surface-3);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.04em;
}

.exercise-pill.ai {
  background: var(--green-3);
  color: var(--green);
}

.option-list {
  display: grid;
  gap: 7px;
  margin: 14px 0 0 40px;
}

.option {
  width: 100%;
  min-height: 38px;
  margin: 0;
  padding: 7px 11px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-2);
  transition: 150ms ease;
}

.option:hover {
  border-color: var(--green-2);
}

.option.correct {
  border-color: var(--green-line);
  background: var(--green-soft);
}

.option.wrong {
  border-color: var(--rose-line);
  background: var(--rose-soft);
}

.answer-feedback {
  margin: 13px 0 0 40px;
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
  .option-list,
  .answer-feedback {
    margin-left: 0;
  }

  .option-notes li {
    grid-template-columns: 1fr;
  }
}
</style>
