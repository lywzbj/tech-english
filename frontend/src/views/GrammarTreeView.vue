<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { Share } from '@element-plus/icons-vue'
import { use } from 'echarts/core'
import { GraphChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'
import VChart from 'vue-echarts'
import { grammarApi } from '@/api'
import { errorMessage } from '@/utils/error'
import { useTheme } from '@/composables/useTheme'
import type { GrammarTree, GrammarTreeNode } from '@/types/api'

use([GraphChart, TooltipComponent, CanvasRenderer])

const router = useRouter()
const tree = ref<GrammarTree | null>(null)
const loading = ref(true)
const error = ref('')
const worldFilter = ref('ALL')

/** 依赖图布局参数：y=前置深度行（自上而下），x=列内水平位（父节点重心）。 */
const COL_GAP = 240
const ROW_GAP = 72
/** 归一化到容器像素时预留的边距：右侧留给节点标签，上下留给圆形与箭头。 */
const PAD_LEFT = 30
const PAD_RIGHT = 180
const PAD_TOP = 28
const PAD_BOTTOM = 28

async function load() {
  loading.value = true
  error.value = ''
  try {
    tree.value = await grammarApi.tree()
  } catch (err) {
    error.value = errorMessage(err, '加载知识树失败')
    tree.value = null
  } finally {
    loading.value = false
  }
}

/** 画布容器的实际像素尺寸：布局最终归一化到这里（echarts 在 layout:none 下
 *  对两个轴做独立拉伸，退化轴会把节点拉成线条，所以坐标必须由我们标定）。 */
const canvasEl = ref<HTMLElement | null>(null)
const canvasBox = ref({ width: 1000, height: 560 })
/** echarts 的首帧自适应缩放不会随数据更新重算，所以先量好容器再挂载图表，
 *  保证第一次渲染时归一化坐标已经对应真实画布尺寸。 */
const chartReady = ref(false)

function measureCanvas() {
  const el = canvasEl.value
  if (!el || el.clientWidth <= 0 || el.clientHeight <= 0) return
  const { width, height } = canvasBox.value
  if (width !== el.clientWidth || height !== el.clientHeight) {
    canvasBox.value = { width: el.clientWidth, height: el.clientHeight }
  }
}

onMounted(async () => {
  await load()
  await nextTick()
  measureCanvas()
  chartReady.value = true
  window.addEventListener('resize', measureCanvas)
})

onBeforeUnmount(() => window.removeEventListener('resize', measureCanvas))

watch(worldFilter, async () => {
  await nextTick()
  measureCanvas()
})

const worlds = computed(() => {
  const names = new Set((tree.value?.nodes ?? []).map((node) => node.world))
  return Array.from(names)
})

const visibleNodes = computed(() => {
  const nodes = tree.value?.nodes ?? []
  return worldFilter.value === 'ALL'
    ? nodes
    : nodes.filter((node) => node.world === worldFilter.value)
})

const visibleEdges = computed(() => {
  const slugs = new Set(visibleNodes.value.map((node) => node.slug))
  return (tree.value?.edges ?? []).filter(
    (edge) => slugs.has(edge.upSlug) && slugs.has(edge.downSlug),
  )
})

interface LaidOutNode extends GrammarTreeNode {
  x: number
  y: number
}

/**
 * 分层布局：depth = 1 + max(前置节点 depth)，无前置的在最左列。
 * Kahn 拓扑序传播；环（理论上不该有）兜底进最右列。列内按
 * (worldOrder, sortOrder) 排序后垂直居中，保证同世界节点上下相邻。
 */
const layout = computed<LaidOutNode[] | null>(() => {
  const nodes = visibleNodes.value
  const edges = visibleEdges.value
  if (nodes.length === 0) return []

  const prereqs = new Map<string, string[]>()
  nodes.forEach((node) => prereqs.set(node.slug, []))
  edges.forEach((edge) => {
    prereqs.get(edge.upSlug)?.push(edge.downSlug)
  })
  const dependents = new Map<string, string[]>()
  nodes.forEach((node) => dependents.set(node.slug, []))
  edges.forEach((edge) => {
    dependents.get(edge.downSlug)?.push(edge.upSlug)
  })

  const depth = new Map<string, number>()
  const remaining = new Map<string, number>()
  nodes.forEach((node) => remaining.set(node.slug, prereqs.get(node.slug)?.length ?? 0))

  let queue = nodes.filter((node) => (remaining.get(node.slug) ?? 0) === 0).map((node) => node.slug)
  queue.forEach((slug) => depth.set(slug, 0))
  while (queue.length > 0) {
    const next: string[] = []
    for (const slug of queue) {
      for (const child of dependents.get(slug) ?? []) {
        depth.set(child, Math.max(depth.get(child) ?? 0, (depth.get(slug) ?? 0) + 1))
        const left = (remaining.get(child) ?? 1) - 1
        remaining.set(child, left)
        if (left === 0) next.push(child)
      }
    }
    queue = next
  }
  const assignedMax = Math.max(0, ...Array.from(depth.values()))
  nodes.forEach((node) => {
    if (!depth.has(node.slug)) depth.set(node.slug, assignedMax + 1) // 环兜底
  })

  const columns = new Map<number, GrammarTreeNode[]>()
  nodes.forEach((node) => {
    const d = depth.get(node.slug) ?? 0
    if (!columns.has(d)) columns.set(d, [])
    columns.get(d)!.push(node)
  })
  columns.forEach((column) =>
    column.sort(
      (a, b) =>
        a.worldOrder - b.worldOrder || a.sortOrder - b.sortOrder || a.title.localeCompare(b.title),
    ),
  )

  // 自上而下的分层：y=深度行；x 用父节点重心（重心法减少交叉），列内再做去重叠。
  const placedX = new Map<string, number>()
  const maxDepth = Math.max(...Array.from(depth.values()))
  for (let d = 0; d <= maxDepth; d += 1) {
    const column = columns.get(d) ?? []
    if (d === 0) {
      column.forEach((node, index) => {
        placedX.set(node.slug, (index - (column.length - 1) / 2) * COL_GAP)
      })
    } else {
      column.forEach((node) => {
        const parents = (prereqs.get(node.slug) ?? []).map((p) => placedX.get(p)).filter(
          (value): value is number => value !== undefined,
        )
        const barycenter =
          parents.length > 0 ? parents.reduce((sum, x) => sum + x, 0) / parents.length : 0
        placedX.set(node.slug, barycenter)
      })
      const sorted = [...column].sort((a, b) => (placedX.get(a.slug) ?? 0) - (placedX.get(b.slug) ?? 0))
      sorted.forEach((node, index) => {
        const previous = index > 0 ? placedX.get(sorted[index - 1].slug) : undefined
        const ideal = placedX.get(node.slug) ?? 0
        placedX.set(node.slug, previous === undefined ? ideal : Math.max(ideal, previous + COL_GAP))
      })
      const centered = (sorted[sorted.length - 1]
        ? (placedX.get(sorted[0].slug) ?? 0) + (placedX.get(sorted[sorted.length - 1].slug) ?? 0)
        : 0) / 2
      sorted.forEach((node) => placedX.set(node.slug, (placedX.get(node.slug) ?? 0) - centered))
    }
  }

  const laidOut: LaidOutNode[] = nodes.map((node) => ({
    ...node,
    x: placedX.get(node.slug) ?? 0,
    y: (depth.get(node.slug) ?? 0) * ROW_GAP,
  }))

  // 双轴各自线性映射进容器像素：echarts 会按数据 bbox 独立缩放两个轴，
  // 我们先把 bbox 标定为"扣掉边距后的容器"，使缩放比≈1，符号保持正圆。
  const { width, height } = canvasBox.value
  const innerW = Math.max(PAD_LEFT + PAD_RIGHT + 120, width - PAD_LEFT - PAD_RIGHT)
  const innerH = Math.max(200, height - PAD_TOP - PAD_BOTTOM)
  const normalizeAxis = (key: 'x' | 'y', inner: number, padStart: number) => {
    const values = laidOut.map((node) => node[key])
    const min = Math.min(...values)
    const max = Math.max(...values)
    const center = (min + max) / 2
    const span = max - min
    if (span < 1) {
      // 退化轴（纯链式等所有点共线）：均匀展开占满该轴——echarts 对零跨度轴
      // 的自适应缩放会把节点拉成贯穿画布的形状，展开后对角阶梯也更好读。
      laidOut.forEach((node, index) => {
        node[key] = padStart + (laidOut.length === 1 ? inner / 2 : (index / (laidOut.length - 1)) * inner)
      })
      return
    }
    const scale = inner / span
    laidOut.forEach((node) => {
      node[key] = padStart + inner / 2 + (node[key] - center) * scale
    })
  }
  normalizeAxis('x', innerW, PAD_LEFT)
  normalizeAxis('y', innerH, PAD_TOP)
  return laidOut
})

const STATUS_LABELS: Record<string, string> = {
  LOCKED: '未解锁',
  AVAILABLE: '可开始',
  IN_PROGRESS: '进行中',
  MASTERED: '已掌握',
}

const SOURCE_LABELS: Record<string, string> = {
  SEED: '官方',
  USER: '自定义',
  AI_PROPOSED: 'AI 建议',
  FROM_READING: '来自阅读',
}

// echarts 画在 canvas 上，取当前皮肤的 CSS token 上色；themeId 参与依赖使换肤重算。
const { current: themeId } = useTheme()

function cssToken(name: string, fallback: string) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

const palette = computed(() => ({
  theme: themeId.value,
  green: cssToken('--green', '#245c43'),
  amber: cssToken('--amber', '#c97838'),
  blue: cssToken('--blue', '#446b88'),
  locked: cssToken('--line-strong', '#cfd6cf'),
  line: cssToken('--line', '#e3e7e3'),
  edge: cssToken('--muted', '#67746a'),
  ink: cssToken('--ink', '#1e2b23'),
  card: cssToken('--card', '#ffffff'),
}))

function statusColor(node: GrammarTreeNode) {
  const c = palette.value
  switch (node.status) {
    case 'MASTERED':
      return c.green
    case 'IN_PROGRESS':
      return c.amber
    case 'LOCKED':
      return c.locked
    default:
      return c.blue
  }
}

function fmtTime(value: string | null | undefined) {
  return value ? dayjs(value).format('M月D日') : '—'
}

const option = computed<EChartsOption>(() => {
  const c = palette.value
  const nodes = layout.value ?? []
  const edges = visibleEdges.value
  return {
    animationDuration: 350,
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: unknown) => {
        const p = params as { dataType?: string; data?: { raw?: GrammarTreeNode } }
        if (p.dataType === 'edge') {
          return '<div style="font-size:12px">前置 → 依赖方向（箭头指向需要前置的节点）</div>'
        }
        const node = p.data?.raw
        if (!node) return ''
        return `
          <div style="max-width:260px">
            <b>${node.title}</b>
            <span style="color:${c.edge};margin-left:6px">${node.cefrLevel || ''} · ${node.world} · ${SOURCE_LABELS[node.source] ?? node.source}</span>
            <div style="margin:4px 0">${node.summary || ''}</div>
            <div style="font-size:12px;color:${c.edge}">
              ${STATUS_LABELS[node.status] ?? node.status} · 掌握度 ${Math.round(node.mastery)}%
            </div>
            <div style="font-size:12px;color:${c.edge}">
              最近练习 ${fmtTime(node.lastPracticedAt)} · 建议复习 ${fmtTime(node.nextReviewAt)}
            </div>
          </div>`
      },
    },
    series: [
      {
        type: 'graph',
        layout: 'none',
        roam: true,
        left: 0,
        right: 0,
        top: 0,
        bottom: 0,
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 9,
        lineStyle: { color: c.edge, width: 1.4, opacity: 0.55, curveness: 0.1 },
        emphasis: { focus: 'adjacency', lineStyle: { width: 2.4, opacity: 1 } },
        label: {
          show: true,
          position: 'right',
          distance: 10,
          color: c.ink,
          fontSize: 12,
          formatter: (params: unknown) => {
            const name = (params as { name?: string }).name ?? ''
            return name.length > 12 ? `${name.slice(0, 12)}…` : name
          },
        },
        data: nodes.map((node) => ({
          id: node.slug,
          name: node.title,
          x: node.x,
          y: node.y,
          symbolSize: 18 + Math.round(node.mastery * 0.14),
          itemStyle: {
            color: statusColor(node),
            borderColor: c.card,
            borderWidth: 2,
            opacity: node.status === 'LOCKED' ? 0.55 : 1,
            shadowBlur: node.completed ? 8 : 0,
            shadowColor: c.green,
          },
          raw: node,
        })),
        links: edges.map((edge) => ({ source: edge.downSlug, target: edge.upSlug })),
      },
    ],
  }
})

function onNodeClick(params: unknown) {
  const node = (params as { data?: { raw?: GrammarTreeNode } }).data?.raw
  if (!node) return
  if (node.locked) {
    ElMessage.info('该节点尚未解锁，请先完成前置知识点')
    return
  }
  router.push(`/grammar/${node.slug}`)
}

const legend = computed(() => [
  { label: '已掌握', color: palette.value.green },
  { label: '进行中', color: palette.value.amber },
  { label: '可开始', color: palette.value.blue },
  { label: '未解锁', color: palette.value.locked },
])
</script>

<template>
  <div class="page tree-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">KNOWLEDGE TREE</p>
        <h1 class="page-title">语法知识树</h1>
        <p class="page-subtitle">
          树从上往下生长：箭头指向"需要该前置"的节点，上方是地基，下方是上层用法。节点颜色表示状态，大小随掌握度增长。滚轮缩放、拖拽平移，点击节点进入学习。
        </p>
      </div>
      <div class="tree-header-side">
        <el-button
          v-if="worldFilter !== 'ALL'"
          :icon="Share"
          circle
          title="显示全部世界"
          @click="worldFilter = 'ALL'"
        />
        <el-select v-model="worldFilter" class="world-select" placeholder="全部世界">
          <el-option label="全部世界" value="ALL" />
          <el-option v-for="name in worlds" :key="name" :label="name" :value="name" />
        </el-select>
        <div v-if="tree" class="overall-chip">
          总掌握 <strong>{{ tree.overallMastery }}%</strong>
        </div>
      </div>
    </header>

    <div v-if="loading" class="card card-pad">
      <el-skeleton animated :rows="10" />
    </div>
    <div v-else-if="error" class="empty">
      <span class="empty-icon">!</span>
      <strong>{{ error }}</strong>
      <el-button @click="load">重试</el-button>
    </div>
    <div v-else-if="(layout?.length ?? 0) === 0" class="empty">
      <span class="empty-icon">∅</span>
      <strong>这个筛选下没有知识点</strong>
      <el-button @click="worldFilter = 'ALL'">显示全部</el-button>
    </div>

    <section v-else class="card tree-canvas-card">
      <div class="tree-legend">
        <span v-for="item in legend" :key="item.label" class="legend-item">
          <i class="legend-dot" :style="{ background: item.color }" />
          {{ item.label }}
        </span>
        <span class="legend-hint">{{ layout?.length ?? 0 }} 个节点 · {{ visibleEdges.length }} 条依赖</span>
      </div>
      <div ref="canvasEl" class="tree-canvas">
        <VChart
          v-if="chartReady"
          :key="`${canvasBox.width}x${canvasBox.height}`"
          class="tree-inner"
          :option="option"
          autoresize
          @click="onNodeClick"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.tree-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tree-header-side {
  display: flex;
  align-items: center;
  gap: 12px;
}

.world-select {
  width: 160px;
}

.overall-chip {
  padding: 8px 14px;
  border-radius: 999px;
  background: var(--green-soft, #e7f0ea);
  color: var(--green, #245c43);
  font-size: 13px;
  white-space: nowrap;
}

.overall-chip strong {
  margin-left: 4px;
}

.tree-canvas-card {
  padding: 12px 12px 4px;
}

.tree-legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  padding: 4px 8px 10px;
  font-size: 13px;
  color: var(--ink-soft, #3f4c43);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.legend-hint {
  margin-left: auto;
  color: var(--muted, #67746a);
  font-size: 12px;
}

.tree-canvas {
  height: calc(100vh - 330px);
  min-height: 460px;
}

.tree-inner {
  width: 100%;
  height: 100%;
}
</style>
