<script setup lang="ts">
import type { Component } from 'vue'
import { Coffee, MoonNight, Sunny } from '@element-plus/icons-vue'
import { THEMES, useTheme, type ThemeId } from '@/composables/useTheme'

const { current, setTheme } = useTheme()

const iconFor: Record<ThemeId, Component> = {
  light: Sunny,
  sepia: Coffee,
  night: MoonNight,
}
</script>

<template>
  <div class="theme-switch" role="group" aria-label="界面主题切换">
    <el-tooltip v-for="t in THEMES" :key="t.id" :content="`${t.label} · ${t.hint}`" placement="bottom">
      <button
        type="button"
        class="theme-button"
        :class="{ active: current === t.id }"
        :aria-pressed="current === t.id"
        @click="setTheme(t.id)"
      >
        <el-icon><component :is="iconFor[t.id]" /></el-icon>
      </button>
    </el-tooltip>
  </div>
</template>

<style scoped>
.theme-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface-2);
}

.theme-button {
  display: grid;
  place-items: center;
  width: 30px;
  height: 26px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--faint);
  font-size: 14px;
  cursor: pointer;
  transition: 140ms ease;
}

.theme-button:hover {
  color: var(--ink);
}

.theme-button.active {
  background: var(--green-solid);
  color: #fff;
  box-shadow: var(--shadow-sm);
}
</style>
