<script setup lang="ts">
import QuestionStem from '@/components/question/QuestionStem.vue'

/**
 * 通用单选题：题干 + 选项。
 * 传入 correctAnswer（判分后）即进入回顾态：正确项标绿，选错的项标红。
 * 默认插槽放在选项下方，供业务侧塞解析等反馈内容。
 */
const props = withDefaults(
  defineProps<{
    prompt: string
    options: string[]
    index?: number
    /** 当前选中的选项 */
    modelValue?: string
    /** 判分后传入正确答案；为空表示仍在作答 */
    correctAnswer?: string
    disabled?: boolean
  }>(),
  { index: 0, modelValue: '', correctAnswer: '', disabled: false },
)

const emit = defineEmits<{
  'update:modelValue': [option: string]
}>()

function optionState(option: string) {
  if (!props.correctAnswer) return ''
  if (option === props.correctAnswer) return 'correct'
  if (option === props.modelValue) return 'wrong'
  return ''
}
</script>

<template>
  <QuestionStem :index="index" :prompt="prompt">
    <template v-if="$slots.meta" #meta>
      <slot name="meta" />
    </template>

    <el-radio-group
      :model-value="modelValue"
      class="option-list"
      :disabled="disabled"
      @update:model-value="emit('update:modelValue', String($event))"
    >
      <el-radio
        v-for="option in options"
        :key="option"
        :value="option"
        class="option"
        :class="optionState(option)"
      >
        {{ option }}
      </el-radio>
    </el-radio-group>

    <slot />
  </QuestionStem>
</template>

<style scoped>
.option-list {
  display: grid;
  gap: 7px;
  margin-top: 14px;
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
</style>
