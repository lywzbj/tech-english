<script setup lang="ts">
/**
 * 通用题目外壳：题号 + 题干 + 元信息徽标（meta 插槽）+ 作答区与解析（默认插槽）。
 * 选择题 / 主观题 / 未来其他题型共用这套排版，业务侧只往里填内容。
 */
withDefaults(
  defineProps<{
    /** 题干文本 */
    prompt: string
    /** 从 1 开始的题号；传 0 则不渲染题号列 */
    index?: number
  }>(),
  { index: 0 },
)
</script>

<template>
  <div class="question-shell">
    <span v-if="index > 0" class="q-number">{{ String(index).padStart(2, '0') }}</span>
    <div class="q-content">
      <p class="q-prompt">{{ prompt }}</p>
      <div v-if="$slots.meta" class="q-meta">
        <slot name="meta" />
      </div>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.question-shell {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.q-number {
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

.q-content {
  flex: 1;
  min-width: 0;
}

.q-prompt {
  padding-top: 2px;
  color: var(--ink);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
}

.q-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 6px;
}
</style>
