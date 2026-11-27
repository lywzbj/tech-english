<script setup lang="ts">
import type { AttemptResult, GrammarExercise } from '@/types/api'
import QuestionChoice from '@/components/question/QuestionChoice.vue'
import QuestionOpen from '@/components/question/QuestionOpen.vue'
import QuestionFeedback from '@/components/question/QuestionFeedback.vue'

// 语法业务层：题型判定 + 徽标 + 判分反馈；题干/选项/输入框/解析排版复用 question 公共组件。
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

function skillLabel(skill: GrammarExercise['skill']) {
  if (skill === 'recognize') return '识别'
  if (skill === 'form') return '形式'
  if (skill === 'use') return '使用'
  return ''
}

/** Open item types: the learner types an English sentence; the AI judge grades it. */
const OPEN_TYPES: Record<string, { label: string; placeholder: string }> = {
  TRANSFORM: { label: '改写', placeholder: '按题目要求改写句子…' },
  TRANSLATE: { label: '翻译', placeholder: '用英语写出译文…' },
  PRODUCE: { label: '造句', placeholder: '用要求的结构写一个英文句子…' },
}

function openType(exercise: GrammarExercise) {
  return OPEN_TYPES[exercise.type] ?? null
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
      <QuestionOpen
        v-if="openType(exercise)"
        :index="index + 1"
        :prompt="exercise.prompt"
        :model-value="answers[exercise.id] ?? ''"
        :placeholder="openType(exercise)!.placeholder"
        :disabled="disabled"
        @update:model-value="emit('select', exercise.id, String($event))"
      >
        <template #meta>
          <span v-if="exercise.wrongReview" class="exercise-pill review">错题重练</span>
          <span class="exercise-pill open">主观题 · {{ openType(exercise)!.label }}</span>
          <span v-if="skillLabel(exercise.skill)" class="exercise-pill">
            {{ skillLabel(exercise.skill) }}
          </span>
          <span v-if="exercise.source === 'AI'" class="exercise-pill ai">AI 题</span>
        </template>
        <QuestionFeedback
          v-if="resultOf(exercise.id)"
          :correct="Boolean(resultOf(exercise.id)?.correct)"
          :correct-answer="resultOf(exercise.id)?.correctAnswer ?? ''"
          answer-label="参考答案"
          :explanation="resultOf(exercise.id)?.explanation"
        />
      </QuestionOpen>

      <QuestionChoice
        v-else
        :index="index + 1"
        :prompt="exercise.prompt"
        :options="exercise.options"
        :model-value="answers[exercise.id] ?? ''"
        :correct-answer="resultOf(exercise.id)?.correctAnswer ?? ''"
        :disabled="disabled"
        @update:model-value="emit('select', exercise.id, String($event))"
      >
        <template #meta>
          <span v-if="exercise.wrongReview" class="exercise-pill review">错题重练</span>
          <span v-if="skillLabel(exercise.skill)" class="exercise-pill">
            {{ skillLabel(exercise.skill) }}
          </span>
          <span v-if="exercise.source === 'AI'" class="exercise-pill ai">AI 题</span>
        </template>
        <QuestionFeedback
          v-if="resultOf(exercise.id)"
          :correct="Boolean(resultOf(exercise.id)?.correct)"
          :correct-answer="resultOf(exercise.id)?.correctAnswer ?? ''"
          :explanation="resultOf(exercise.id)?.explanation"
          :option-notes="resultOf(exercise.id)?.optionNotes"
        />
      </QuestionChoice>
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

.exercise-pill.open {
  background: var(--amber-soft);
  color: var(--amber-text);
}

.exercise-pill.review {
  background: var(--rose-soft);
  color: var(--rose);
}
</style>
