<script setup lang="ts">
import ContentDrawer from '@/components/common/ContentDrawer.vue'
import type { AttemptResult, GrammarExercise } from '@/types/api'

/** One paper shown in the map: practice set or boss paper. */
interface AttemptSection {
  key: string
  label: string
  exercises: GrammarExercise[]
  answers: Record<string, string>
  results: Record<string, AttemptResult>
}

const props = defineProps<{
  modelValue: boolean
  sections: AttemptSection[]
  /** Exercise currently shown in the main practice area. */
  activeExerciseId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [open: boolean]
  select: [exerciseId: string]
}>()

type QuestionStatus = 'correct' | 'wrong' | 'answered' | 'todo'

const STATUS_TEXT: Record<QuestionStatus, string> = {
  correct: '答对',
  wrong: '答错',
  answered: '已作答',
  todo: '未作答',
}

function statusOf(section: AttemptSection, exercise: GrammarExercise): QuestionStatus {
  const result = section.results[exercise.id]
  if (result) return result.correct ? 'correct' : 'wrong'
  return section.answers[exercise.id]?.trim() ? 'answered' : 'todo'
}

function summaryOf(section: AttemptSection) {
  const total = section.exercises.length
  const graded = section.exercises.filter((exercise) => section.results[exercise.id]).length
  if (graded) {
    const correct = section.exercises.filter((exercise) => section.results[exercise.id]?.correct).length
    return `答对 ${correct} / ${total}`
  }
  const answered = section.exercises.filter((exercise) => section.answers[exercise.id]?.trim()).length
  return `已作答 ${answered} / ${total}`
}
</script>

<template>
  <ContentDrawer
    :model-value="modelValue"
    title="做题情况"
    eyebrow="ATTEMPT MAP"
    :size="420"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="log-groups">
      <section v-for="section in sections" :key="section.key" class="log-group">
        <header class="log-group-head">
          <h3>{{ section.label }}</h3>
          <span>{{ summaryOf(section) }}</span>
        </header>
        <button
          v-for="(exercise, index) in section.exercises"
          :key="exercise.id"
          type="button"
          class="log-row"
          :class="{ active: exercise.id === activeExerciseId }"
          title="在左侧查看这道题"
          @click="emit('select', exercise.id)"
        >
          <span class="log-number">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="log-prompt">{{ exercise.prompt }}</span>
          <span class="log-status" :class="statusOf(section, exercise)">
            {{ STATUS_TEXT[statusOf(section, exercise)] }}
          </span>
        </button>
      </section>
      <p v-if="!sections.length" class="log-empty">还没有开始作答。</p>
      <p class="log-hint">点击题目，在左侧做题区域查看该题。</p>
    </div>
  </ContentDrawer>
</template>

<style scoped>
.log-groups {
  display: grid;
  gap: 20px;
}

.log-group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.log-group-head h3 {
  font-family: var(--font-reading);
  font-size: 14px;
  font-weight: 600;
}

.log-group-head span {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.log-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition: 140ms ease;
}

.log-row + .log-row {
  margin-top: 6px;
}

.log-row:hover {
  border-color: var(--green-line);
  background: var(--green-3);
}

.log-row.active {
  border-color: var(--green);
  background: var(--green-3);
}

.log-number {
  flex: 0 0 auto;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.log-prompt {
  flex: 1;
  min-width: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--ink);
  font-size: 11px;
  line-height: 1.5;
}

.log-status {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 6px;
  font-size: 9px;
}

.log-status.correct {
  background: var(--green-3);
  color: var(--green);
}

.log-status.wrong {
  background: var(--rose-soft);
  color: var(--rose);
}

.log-status.answered {
  background: var(--amber-soft);
  color: var(--amber-text);
}

.log-status.todo {
  background: var(--surface-3);
  color: var(--muted);
}

.log-empty {
  padding: 26px 0;
  color: var(--faint);
  font-size: 11px;
  text-align: center;
}

.log-hint {
  color: var(--faint);
  font-size: 10px;
  text-align: center;
}
</style>
