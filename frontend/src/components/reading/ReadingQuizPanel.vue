<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { readingApi } from '@/api'
import { errorMessage } from '@/utils/error'
import type { QuizDifficulty, QuizSet, QuizSubmitResponse } from '@/types/api'

// 阅读抽屉里的 AI 自测：按当前小节生成单选题、作答、后端判分。
// 独立于静态题的「完成阅读」链路；题目与判分结果都会留档在题库侧。
const props = defineProps<{
  articleId: number
  /** 正在阅读的小节；null = 文章无小节，退回整篇出题。 */
  section: { title: string; text: string } | null
}>()

const DIFFICULTY_LABELS: Record<QuizDifficulty, string> = {
  EASY: '简单',
  NORMAL: '中等',
  HARD: '困难',
}

const difficulty = ref<QuizDifficulty>('NORMAL')
const generating = ref(false)
const genError = ref('')
const activeSet = ref<QuizSet | null>(null)
const answers = reactive<Record<number, string>>({})
const submitting = ref(false)
const result = ref<QuizSubmitResponse | null>(null)
/** 未作答完就关抽屉会丢失选择，打开面板时从后端恢复未判分的那套题。 */
const restoring = ref(false)

const questionCount = computed(() => activeSet.value?.questions.length ?? 0)
const answeredCount = computed(
  () => activeSet.value?.questions.filter((q) => Boolean(answers[q.id])).length ?? 0,
)
const allAnswered = computed(() => questionCount.value > 0 && answeredCount.value === questionCount.value)

watch(
  () => props.articleId,
  () => {
    activeSet.value = null
    result.value = null
    genError.value = ''
    Object.keys(answers).forEach((key) => delete answers[Number(key)])
    void restoreUngraded()
  },
)

onMounted(() => void restoreUngraded())

async function restoreUngraded() {
  restoring.value = true
  try {
    const list = await readingApi.quizList(props.articleId)
    const pending = list.sets.find((set) => !set.submitted && set.questions.length > 0)
    if (pending) activeSet.value = pending
  } catch {
    // 恢复失败不打扰：手动点「AI 生成题目」即可。
  } finally {
    restoring.value = false
  }
}

function clearAnswers() {
  Object.keys(answers).forEach((key) => delete answers[Number(key)])
}

async function generate() {
  generating.value = true
  genError.value = ''
  try {
    const response = await readingApi.quizGenerate(props.articleId, {
      difficulty: difficulty.value,
      sectionTitle: props.section?.title,
      sectionText: props.section?.text,
    })
    activeSet.value = response.set
    result.value = null
    clearAnswers()
  } catch (err) {
    genError.value = errorMessage(err, 'AI 出题失败，请重试')
  } finally {
    generating.value = false
  }
}

/**
 * 选项 → 字母 A–D：优先解析 "A. " 前缀，否则按序号兜底；
 * 若解析结果重复（模型输出不规范），整组退回序号。
 */
function lettersFor(options: string[]): string[] {
  const raw = options.map((option, index) => {
    const match = /^\s*([A-D])[.、．)）]?\s/.exec(option)
    return match ? match[1] : String.fromCharCode(65 + index)
  })
  return new Set(raw).size === raw.length ? raw : options.map((_, index) => String.fromCharCode(65 + index))
}

async function submit() {
  if (!activeSet.value || !allAnswered.value) return
  submitting.value = true
  try {
    result.value = await readingApi.quizSubmit(activeSet.value.id, {
      answers: activeSet.value.questions.map((q) => ({ questionId: q.id, answer: answers[q.id] })),
    })
  } catch (err) {
    ElMessage.error(errorMessage(err, '提交判分失败'))
  } finally {
    submitting.value = false
  }
}

function optionLabel(option: string) {
  return option.replace(/^\s*[A-D][.、．)）]?\s*/, '')
}
</script>

<template>
  <section class="quiz-panel" aria-label="AI 自测">
    <div class="quiz-head">
      <p class="quiz-scope">
        根据当前小节
        <strong>「{{ section ? section.title : '整篇文章' }}」</strong>
        出 5 道单选题
      </p>
      <div class="quiz-controls">
        <el-radio-group v-model="difficulty" size="small" :disabled="generating || Boolean(activeSet && !result)">
          <el-radio-button v-for="(label, key) in DIFFICULTY_LABELS" :key="key" :value="key">
            {{ label }}
          </el-radio-button>
        </el-radio-group>
        <el-button
          type="primary"
          plain
          size="small"
          :loading="generating"
          :disabled="restoring"
          @click="generate"
        >
          <el-icon v-if="!generating"><MagicStick /></el-icon>
          {{ result || !activeSet ? 'AI 生成题目' : '换一组' }}
        </el-button>
      </div>
    </div>
    <p v-if="genError" class="quiz-error">{{ genError }}</p>

    <div v-if="activeSet && !result" class="quiz-list">
      <article v-for="(question, index) in activeSet.questions" :key="question.id" class="quiz-item">
        <div class="quiz-title">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <p>{{ question.prompt }}</p>
        </div>
        <el-radio-group
          :model-value="answers[question.id] ?? ''"
          class="quiz-options"
          @update:model-value="
            (value: string | number | boolean | undefined) => {
              answers[question.id] = String(value)
            }
          "
        >
          <el-radio
            v-for="(option, optionIndex) in question.options"
            :key="option"
            :value="lettersFor(question.options)[optionIndex]"
          >
            <span class="quiz-option-letter">{{ lettersFor(question.options)[optionIndex] }}</span>
            {{ optionLabel(option) || option }}
          </el-radio>
        </el-radio-group>
      </article>
      <el-button type="primary" class="quiz-submit" :loading="submitting" :disabled="!allAnswered" @click="submit">
        提交判分（{{ answeredCount }}/{{ questionCount }}）
      </el-button>
    </div>

    <div v-else-if="result" class="quiz-list">
      <div class="quiz-score">
        <strong>{{ result.correct }} / {{ result.total }}</strong>
        <span>{{ Math.round(result.score) }} 分 · {{ result.message }}</span>
      </div>
      <article v-for="(item, index) in result.items" :key="item.questionId" class="quiz-item" :class="item.correct ? 'is-right' : 'is-wrong'">
        <div class="quiz-title">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <p>{{ item.prompt }}</p>
        </div>
        <p class="quiz-judge">
          你的答案：{{ item.yourAnswer || '（未作答）' }} · 正确答案：{{ item.correctAnswer }}
        </p>
        <p v-if="item.explanation" class="quiz-explain">{{ item.explanation }}</p>
      </article>
    </div>

    <p v-else-if="!restoring" class="quiz-empty">
      选好难度，点「AI 生成题目」即可开始自测。技术文档会出面试风格的题，雅思文章会出学术阅读风格的题。
    </p>
  </section>
</template>

<style scoped>
.quiz-panel {
  padding-bottom: 4px;
}

.quiz-head {
  display: grid;
  gap: 9px;
}

.quiz-scope {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.quiz-scope strong {
  color: var(--ink);
}

.quiz-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.quiz-error {
  margin-top: 8px;
  color: #c4453c;
  font-size: 12px;
}

.quiz-empty {
  margin-top: 12px;
  padding: 11px;
  border-radius: 9px;
  background: var(--surface-2);
  color: var(--faint);
  font-size: 12px;
  line-height: 1.65;
}

.quiz-list {
  display: grid;
  gap: 16px;
  margin-top: 14px;
}

.quiz-item {
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.quiz-item:first-child {
  padding-top: 0;
  border-top: 0;
}

.quiz-title {
  display: flex;
  gap: 8px;
}

.quiz-title > span {
  padding-top: 2px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
}

.quiz-title p {
  color: var(--ink);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
}

.quiz-options {
  display: grid;
  gap: 3px;
  margin: 8px 0 0 18px;
}

.quiz-options :deep(.el-radio) {
  height: auto;
  margin-right: 0;
  padding: 3px 0;
  align-items: flex-start;
}

.quiz-options :deep(.el-radio__label) {
  font-size: 12.5px;
  line-height: 1.5;
  white-space: normal;
}

.quiz-option-letter {
  margin-right: 2px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.quiz-submit {
  width: 100%;
}

.quiz-score {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 11px 13px;
  border-radius: 9px;
  background: var(--green-3);
}

.quiz-score strong {
  color: var(--green);
  font-family: var(--font-reading);
  font-size: 22px;
  font-weight: 600;
}

.quiz-score span {
  color: var(--muted);
  font-size: 12px;
}

.quiz-judge {
  margin-top: 6px;
  margin-left: 18px;
  font-size: 12px;
  font-weight: 600;
}

.is-right .quiz-judge {
  color: var(--green);
}

.is-wrong .quiz-judge {
  color: #c4453c;
}

.quiz-explain {
  margin-top: 4px;
  margin-left: 18px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.65;
}
</style>
