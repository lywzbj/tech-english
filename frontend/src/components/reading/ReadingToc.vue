<script setup lang="ts">
import type { TocItem } from '@/utils/markdown'

defineProps<{
  items: TocItem[]
}>()

const emit = defineEmits<{
  (e: 'select', id: string): void
}>()
</script>

<template>
  <nav v-if="items.length" class="toc-list" aria-label="文章目录">
    <button
      v-for="item in items"
      :key="item.id"
      type="button"
      class="toc-item"
      :class="{ sub: item.level === 3, deep: item.level >= 4 }"
      @click="emit('select', item.id)"
    >
      {{ item.text }}
    </button>
  </nav>
  <p v-else class="toc-empty">这篇文章没有分节标题。</p>
</template>

<style scoped>
.toc-list {
  display: grid;
  gap: 1px;
}

.toc-item {
  padding: 7px 9px;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 0 5px 5px 0;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.55;
  cursor: pointer;
  text-align: left;
}

.toc-item:hover {
  border-left-color: var(--green-2);
  background: var(--surface-2);
  color: var(--ink);
}

.toc-item.sub {
  padding-left: 22px;
  font-size: 12px;
}

.toc-item.deep {
  padding-left: 34px;
  font-size: 12px;
  color: var(--muted);
}

.toc-empty {
  color: var(--faint);
  font-size: 12px;
}
</style>
