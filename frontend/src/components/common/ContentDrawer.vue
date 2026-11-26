<script setup lang="ts">
import { Close } from '@element-plus/icons-vue'

// 通用抽屉容器：只负责方向、尺寸、头部和滚动，内容全部由插槽提供。
withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    eyebrow?: string
    direction?: 'ltr' | 'rtl' | 'ttb' | 'btt'
    size?: string | number
    ariaLabel?: string
    modal?: boolean
    /** CSS length. Anchors an ltr drawer away from the viewport edge. */
    offset?: string
  }>(),
  {
    eyebrow: '',
    direction: 'rtl',
    size: 360,
    ariaLabel: '',
    modal: false,
    offset: '0px',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', open: boolean): void
}>()
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :direction="direction"
    :size="size"
    :with-header="false"
    :aria-label="ariaLabel || title"
    :modal="modal"
    :lock-scroll="false"
    :style="{ '--drawer-offset': offset }"
    append-to-body
    class="content-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="drawer-shell">
      <header class="drawer-head">
        <div class="drawer-head-main">
          <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
          <h2>{{ title }}</h2>
        </div>
        <div class="drawer-head-side">
          <slot name="header-extra" />
          <el-button circle size="small" :title="`关闭${title}`" @click="emit('update:modelValue', false)">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </header>
      <div class="drawer-body">
        <slot />
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.content-drawer :deep(.el-drawer__body) {
  padding: 0;
}

/* ltr 抽屉默认贴视口左边；offset 让页面把它锚定在侧边栏右缘。 */
:global(.content-drawer.ltr) {
  left: var(--drawer-offset, 0px);
}

/*
 * Element Plus 给 ltr/rtl 抽屉设了 top:0 + height:100%，会盖住应用的 sticky 顶栏。
 * 抽屉属于页面内容区：把它锚定到顶栏下缘，滑出时不再遮挡 header。
 */
:global(.content-drawer.el-drawer.ltr),
:global(.content-drawer.el-drawer.rtl) {
  top: var(--topbar-height, 0px);
  height: calc(100% - var(--topbar-height, 0px));
}

/*
 * Element Plus wraps every drawer in a fixed, full-viewport div even when
 * `modal` is false. Let that wrapper pass pointer events through so the reader
 * can keep selecting text and scrolling behind the drawer; the drawer itself
 * re-enables its own events.
 */
:global(.is-drawer.el-modal-drawer:has(> .content-drawer)) {
  pointer-events: none;
}

:global(.is-drawer.el-modal-drawer > .content-drawer) {
  pointer-events: auto;
}

.drawer-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.drawer-head {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 20px 14px;
  border-bottom: 1px solid var(--line);
}

.drawer-head-main {
  min-width: 0;
}

.drawer-head h2 {
  margin-top: 3px;
  font-family: var(--font-reading);
  font-size: 21px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.drawer-head-side {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.drawer-body {
  flex: 1;
  min-height: 0;
  padding: 16px 20px 24px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
</style>
