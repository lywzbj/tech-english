<script setup lang="ts">
import QuestionStem from '@/components/question/QuestionStem.vue'

/**
 * 通用主观题：题干 + 英文作答输入框（改写 / 翻译 / 造句等，由 AI 判分）。
 * 默认插槽放在输入框下方，供业务侧塞解析等反馈内容。
 */
withDefaults(
  defineProps<{
    prompt: string
    index?: number
    modelValue?: string
    placeholder?: string
    disabled?: boolean
    maxlength?: number
  }>(),
  { index: 0, modelValue: '', placeholder: '用英语写出你的答案…', disabled: false, maxlength: 500 },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <QuestionStem :index="index" :prompt="prompt">
    <template v-if="$slots.meta" #meta>
      <slot name="meta" />
    </template>

    <el-input
      class="answer-input"
      type="textarea"
      :autosize="{ minRows: 2, maxRows: 6 }"
      :model-value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :maxlength="maxlength"
      @update:model-value="emit('update:modelValue', String($event))"
    />

    <slot />
  </QuestionStem>
</template>

<style scoped>
.answer-input {
  margin-top: 14px;
  max-width: 560px;
}

.answer-input :deep(.el-textarea__inner) {
  font-family: var(--font-reading);
  font-size: 14px;
  line-height: 1.6;
}
</style>
