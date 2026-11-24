<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'
import { aiApi, searchApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/utils/error'
import type { AiStatus, VectorStatus } from '@/types/api'

const router = useRouter()
const auth = useAuthStore()
const saving = ref(false)
const aiStatus = ref<AiStatus | null>(null)
const vectorStatus = ref<VectorStatus | null>(null)

const form = reactive({
  targetBand: auth.profile?.targetBand ?? 7,
  examDate: auth.profile?.examDate ?? dayjs().add(12, 'month').format('YYYY-MM-DD'),
  dailyMinutes: auth.profile?.dailyMinutes ?? 75,
  cefrLevel: auth.profile?.cefrLevel ?? 'A2',
  techStack: auth.profile?.techStack ?? [],
  interests: auth.profile?.interests ?? [],
})

const bands = [6, 6.5, 7, 7.5]
const levels = ['A1', 'A2', 'B1', 'B2']
const techOptions = ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Vue', 'React', 'Python', 'Go', 'Docker', 'Kubernetes', 'AI']
const interestOptions = ['backend', 'frontend', 'database', 'devops', 'AI', 'architecture', 'product']

function disabledDate(date: Date) {
  return date.getTime() < Date.now() - 86_400_000
}

async function save() {
  saving.value = true
  try {
    await auth.updateProfile({
      targetBand: form.targetBand,
      examDate: form.examDate,
      dailyMinutes: form.dailyMinutes,
      cefrLevel: form.cefrLevel,
      techStack: form.techStack,
      interests: form.interests,
      explanationLanguage: 'zh-CN',
      onboardingCompleted: true,
    })
    ElMessage.success('目标已更新')
  } catch (err) {
    ElMessage.error(errorMessage(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function logout() {
  await auth.logout()
  await router.replace('/login')
}

onMounted(async () => {
  const [ai, vector] = await Promise.allSettled([aiApi.status(), searchApi.status()])
  aiStatus.value = ai.status === 'fulfilled' ? ai.value : null
  vectorStatus.value = vector.status === 'fulfilled' ? vector.value : null
})
</script>

<template>
  <div class="page settings-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">SETTINGS</p>
        <h1 class="page-title">目标、节奏和 AI</h1>
        <p class="page-subtitle">
          目标变化会重新影响计划引擎。调整后，从下一次生成任务开始生效。
        </p>
      </div>
    </header>

    <div class="settings-grid">
      <section class="card settings-card">
        <div class="section-head">
          <div>
            <p class="eyebrow">LEARNING PROFILE</p>
            <h2 class="section-title">学习目标</h2>
          </div>
          <span class="pill">{{ auth.profile?.onboardingCompleted ? '已启用' : '未完成' }}</span>
        </div>

        <div class="field">
          <label>目标雅思分数</label>
          <div class="band-row">
            <button
              v-for="band in bands"
              :key="band"
              type="button"
              class="band-btn"
              :class="{ active: form.targetBand === band }"
              @click="form.targetBand = band"
            >
              {{ band.toFixed(1) }}
            </button>
          </div>
        </div>

        <div class="two-col">
          <div class="field">
            <label>考试日期</label>
            <el-date-picker
              v-model="form.examDate"
              type="date"
              value-format="YYYY-MM-DD"
              size="large"
              :disabled-date="disabledDate"
              style="width: 100%"
            />
          </div>
          <div class="field">
            <label>当前语法水平</label>
            <el-select v-model="form.cefrLevel" size="large" style="width: 100%">
              <el-option v-for="level in levels" :key="level" :label="level" :value="level" />
            </el-select>
          </div>
        </div>

        <div class="field">
          <div class="label-row">
            <label>每日投入</label>
            <strong>{{ form.dailyMinutes }} 分钟</strong>
          </div>
          <el-slider v-model="form.dailyMinutes" :min="30" :max="150" :step="15" :show-tooltip="false" />
        </div>

        <div class="field">
          <label>技术栈</label>
          <el-select v-model="form.techStack" multiple filterable allow-create size="large" style="width: 100%">
            <el-option v-for="item in techOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>

        <div class="field">
          <label>学习兴趣</label>
          <el-select v-model="form.interests" multiple filterable allow-create size="large" style="width: 100%">
            <el-option v-for="item in interestOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>

        <el-button type="primary" size="large" :loading="saving" @click="save">保存设置</el-button>
      </section>

      <aside class="settings-side">
        <section class="card settings-card ai-card">
          <div class="section-head">
            <div>
              <p class="eyebrow">AI PROVIDER</p>
              <h2 class="section-title small-title">AI 状态</h2>
            </div>
            <span
              class="pill"
              :class="aiStatus && aiStatus.provider !== 'mock' && aiStatus.configured ? 'green' : 'amber'"
            >
              {{ aiStatus && aiStatus.provider !== 'mock' && aiStatus.configured ? '已连接' : '演示模式' }}
            </span>
          </div>
          <div v-if="aiStatus" class="ai-detail">
            <div><span>Provider</span><strong>{{ aiStatus.provider }}</strong></div>
            <div><span>Model</span><strong>{{ aiStatus.model }}</strong></div>
            <div><span>模式</span><strong>{{ aiStatus.provider === 'mock' ? '本地固定示例' : 'OpenAI 兼容接口' }}</strong></div>
          </div>
          <p class="ai-note">
            切换到 DeepSeek：在 `backend/.env` 或系统环境变量里设置
            `AI_PROVIDER=deepseek`、`AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL`，然后重启后端。
            API Key 只存在服务端，不会进入前端 bundle。
          </p>
        </section>

        <section class="card settings-card vector-card">
          <div class="section-head">
            <div>
              <p class="eyebrow">VECTOR INDEX</p>
              <h2 class="section-title small-title">知识库索引</h2>
            </div>
            <span class="pill" :class="vectorStatus?.index === 'SQLITE_VEC' ? 'green' : 'amber'">
              {{ vectorStatus?.index === 'SQLITE_VEC' ? 'sqlite-vec' : '余弦回退' }}
            </span>
          </div>
          <div v-if="vectorStatus" class="ai-detail">
            <div><span>向量数</span><strong>{{ vectorStatus.documents }}</strong></div>
            <div><span>维度</span><strong>{{ vectorStatus.dimension }}</strong></div>
            <div><span>嵌入模型</span><strong>{{ vectorStatus.model }}</strong></div>
          </div>
          <p class="ai-note">
            默认使用本地哈希嵌入，不需要 API Key。要换成真实语义向量，设置
            `EMBEDDING_PROVIDER=openai`、`EMBEDDING_API_KEY`、`EMBEDDING_MODEL` 后重启后端，索引会自动重建。
          </p>
          <el-button text type="primary" @click="router.push('/search')">打开知识搜索 →</el-button>
        </section>

        <section class="card settings-card data-card">
          <p class="eyebrow">LOCAL DATA</p>
          <h2 class="section-title small-title">你的数据</h2>
          <p>
            这是一个本机单用户应用。学习记录、文章和词汇默认保存在本机 SQLite 文件里，也可以切换到 MySQL 8；AI 请求会把必要的学习文本发给配置的模型服务。
          </p>
          <div class="data-row">
            <span>账号</span>
            <strong>{{ auth.user?.email }}</strong>
          </div>
          <div class="data-row">
            <span>目标</span>
            <strong>IELTS {{ auth.profile?.targetBand?.toFixed(1) ?? '—' }}</strong>
          </div>
          <el-button class="logout-btn" @click="logout">
            <el-icon><SwitchButton /></el-icon> 退出登录
          </el-button>
        </section>

        <section class="card settings-card note-card">
          <p class="eyebrow">NEXT</p>
          <h3>写作实验室与 AI 导师</h3>
          <p>
            V1.1 会加入雅思 Task 1/2、需求澄清邮件、逐句批改和客户需求模拟。当前先把语法、词汇和阅读闭环用起来。
          </p>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(300px, 0.7fr);
  gap: 20px;
  align-items: start;
}

.settings-card {
  padding: 24px;
}

.settings-side {
  display: grid;
  gap: 14px;
}

.field {
  margin-top: 20px;
}

.field label {
  display: block;
  margin-bottom: 8px;
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 700;
}

.label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.label-row strong {
  color: var(--green);
  font-family: var(--font-mono);
  font-size: 10px;
}

.band-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.band-btn {
  padding: 9px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface-2);
  color: var(--ink-soft);
  font-family: var(--font-reading);
  font-size: 18px;
  cursor: pointer;
}

.band-btn.active {
  border-color: var(--green);
  background: var(--green);
  color: #fff;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.small-title {
  font-size: 19px;
}

.ai-detail {
  display: grid;
  gap: 6px;
  margin-top: 14px;
}

.ai-detail > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 6px;
  border-bottom: 1px dashed var(--line);
}

.ai-detail span {
  color: var(--muted);
  font-size: 10px;
}

.ai-detail strong {
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 9px;
}

.ai-note {
  margin-top: 14px;
  padding: 11px 12px;
  border-radius: 8px;
  background: var(--surface-2);
  color: var(--muted);
  font-size: 9px;
  line-height: 1.7;
}

.data-card > p,
.note-card > p {
  margin-top: 9px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.7;
}

.data-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px dashed var(--line);
  font-size: 10px;
}

.data-row span {
  color: var(--muted);
}

.data-row strong {
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 9px;
}

.logout-btn {
  width: 100%;
  margin-top: 16px;
}

.note-card h3 {
  margin-top: 4px;
  font-family: var(--font-reading);
  font-size: 20px;
  font-weight: 600;
}

@media (max-width: 1000px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .settings-side {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 680px) {
  .two-col,
  .settings-side {
    grid-template-columns: 1fr;
  }

  .settings-card {
    padding: 18px;
  }
}
</style>
