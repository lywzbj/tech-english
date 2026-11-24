<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/utils/error'

const router = useRouter()
const auth = useAuthStore()
const saving = ref(false)

const form = reactive({
  targetBand: auth.profile?.targetBand ?? 7,
  examDate: auth.profile?.examDate ?? dayjs().add(12, 'month').format('YYYY-MM-DD'),
  dailyMinutes: auth.profile?.dailyMinutes ?? 75,
  cefrLevel: auth.profile?.cefrLevel ?? 'A2',
  techStack: auth.profile?.techStack?.length ? [...auth.profile.techStack] : ['Java', 'Spring Boot', 'MySQL'],
  interests: auth.profile?.interests?.length ? [...auth.profile.interests] : ['backend', 'AI'],
})

const bands = [6, 6.5, 7, 7.5]
const levels = [
  { value: 'A1', label: 'A1 · 入门' },
  { value: 'A2', label: 'A2 · 基础' },
  { value: 'B1', label: 'B1 · 中级' },
  { value: 'B2', label: 'B2 · 中高级' },
]
const techOptions = ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Vue', 'React', 'Python', 'Go', 'Docker', 'Kubernetes', 'AI']
const interestOptions = ['backend', 'frontend', 'database', 'devops', 'AI', 'architecture', 'product']

const monthsToExam = computed(() => dayjs(form.examDate).diff(dayjs(), 'month'))
const paceWarning = computed(() => {
  if (form.targetBand >= 7 && (form.dailyMinutes < 60 || monthsToExam.value < 12)) return true
  return false
})
const dailyLabel = computed(() => {
  if (form.dailyMinutes < 45) return '轻量'
  if (form.dailyMinutes < 75) return '稳定'
  if (form.dailyMinutes < 110) return '进阶'
  return '高强度'
})

function disabledDate(date: Date) {
  return date.getTime() < Date.now() - 86_400_000
}

async function submit() {
  if (!form.examDate) {
    ElMessage.warning('请选择考试日期')
    return
  }
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
    ElMessage.success('目标已保存，今日计划已生成')
    await router.replace('/')
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="onboarding-page">
    <header class="onboarding-top">
      <div class="brand">
        <span class="brand-mark">TE</span>
        <span>
          <strong>Tech English</strong>
          <small>Set your direction</small>
        </span>
      </div>
      <span class="step-label">SETUP · 01 / 01</span>
    </header>

    <main class="onboarding-grid">
      <section class="intro">
        <p class="eyebrow">YOUR BASELINE</p>
        <h1>先把目标说清楚，<br />再决定每天学什么。</h1>
        <p class="intro-copy">
          这些信息会进入学习计划引擎。语法路径、词汇复习和阅读任务会围绕你的目标日期、每日时长和当前水平调整。
        </p>
        <div class="principles">
          <div>
            <span>01</span>
            <p><strong>语法不是刷完就好</strong> 每个节点有掌握度和 Boss 测验，前置不达标不会解锁下一关。</p>
          </div>
          <div>
            <span>02</span>
            <p><strong>词汇按遗忘曲线回来</strong> 今天认识不等于明天记得，复习队列会自动排期。</p>
          </div>
          <div>
            <span>03</span>
            <p><strong>阅读连接工作场景</strong> 技术文档、API 说明和雅思学术文章会进入同一套阅读器。</p>
          </div>
        </div>
      </section>

      <section class="form-panel card">
        <div class="panel-head">
          <p class="eyebrow">LEARNING PROFILE</p>
          <h2>你的学习参数</h2>
        </div>

        <div class="field">
          <label>目标雅思分数</label>
          <div class="band-grid">
            <button
              v-for="band in bands"
              :key="band"
              type="button"
              class="band-option"
              :class="{ active: form.targetBand === band }"
              @click="form.targetBand = band"
            >
              <strong>{{ band.toFixed(1) }}</strong>
              <span>{{ band === 7 ? '目标' : band >= 7.5 ? '冲刺' : '阶段' }}</span>
            </button>
          </div>
        </div>

        <div class="field two-col">
          <div>
            <label>计划考试日期</label>
            <el-date-picker
              v-model="form.examDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              size="large"
              :disabled-date="disabledDate"
              style="width: 100%"
            />
          </div>
          <div>
            <label>当前语法水平</label>
            <el-select v-model="form.cefrLevel" size="large" style="width: 100%">
              <el-option v-for="level in levels" :key="level.value" :label="level.label" :value="level.value" />
            </el-select>
          </div>
        </div>

        <div class="field">
          <div class="label-row">
            <label>每天可投入时间</label>
            <span class="minutes">{{ form.dailyMinutes }} 分钟 · {{ dailyLabel }}</span>
          </div>
          <el-slider v-model="form.dailyMinutes" :min="30" :max="150" :step="15" :show-tooltip="false" />
          <div class="slider-scale"><span>30</span><span>75</span><span>150</span></div>
        </div>

        <div class="field">
          <label>技术栈</label>
          <el-select
            v-model="form.techStack"
            multiple
            filterable
            allow-create
            size="large"
            placeholder="选择或输入你的技术栈"
            style="width: 100%"
          >
            <el-option v-for="item in techOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>

        <div class="field">
          <label>学习兴趣</label>
          <el-select
            v-model="form.interests"
            multiple
            filterable
            allow-create
            size="large"
            placeholder="选择主题"
            style="width: 100%"
          >
            <el-option v-for="item in interestOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>

        <div v-if="paceWarning" class="pace-note">
          <strong>目标偏紧</strong>
          <span>
            距离考试约 {{ monthsToExam }} 个月、每天 {{ form.dailyMinutes }} 分钟。7.0 通常需要 B2-C1；如果暂不练听说，建议把首个证书目标设为 6.0-6.5。
          </span>
        </div>

        <el-button type="primary" size="large" class="submit" :loading="saving" @click="submit">
          保存目标并生成今日计划
        </el-button>
        <p class="foot">之后可以在「设置与目标」里调整，计划会重新计算。</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.onboarding-page {
  min-height: 100vh;
  padding: 24px clamp(18px, 4vw, 60px) 48px;
  background:
    radial-gradient(circle at 92% 4%, rgb(220 235 226 / 70%), transparent 30%),
    radial-gradient(circle at 0% 80%, rgb(247 231 215 / 55%), transparent 34%),
    var(--bg);
}

.onboarding-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1240px;
  margin: 0 auto 42px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--green);
  color: #fff;
  font-family: var(--font-reading);
  font-size: 14px;
  font-weight: 700;
}

.brand strong {
  display: block;
  font-family: var(--font-reading);
  font-size: 18px;
  font-weight: 600;
}

.brand small {
  display: block;
  color: var(--muted);
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.step-label {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
}

.onboarding-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.85fr) minmax(480px, 1.15fr);
  gap: clamp(30px, 5vw, 80px);
  align-items: start;
  max-width: 1240px;
  margin: 0 auto;
}

.intro {
  position: sticky;
  top: 48px;
  padding-top: 16px;
}

.intro h1 {
  margin-top: 10px;
  font-family: var(--font-reading);
  font-size: clamp(38px, 4.3vw, 58px);
  font-weight: 500;
  letter-spacing: -0.045em;
  line-height: 1.04;
}

.intro-copy {
  max-width: 500px;
  margin-top: 22px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.85;
}

.principles {
  display: grid;
  gap: 18px;
  margin-top: 44px;
}

.principles > div {
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 12px;
}

.principles span {
  color: var(--green-2);
  font-family: var(--font-mono);
  font-size: 10px;
  padding-top: 2px;
}

.principles p {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.principles strong {
  display: block;
  margin-bottom: 2px;
  color: var(--ink);
  font-size: 13px;
}

.form-panel {
  padding: clamp(24px, 3vw, 38px);
}

.panel-head h2 {
  margin-top: 6px;
  font-family: var(--font-reading);
  font-size: 31px;
  font-weight: 600;
  letter-spacing: -0.03em;
}

.field {
  margin-top: 26px;
}

.field label {
  display: block;
  margin-bottom: 9px;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 700;
}

.label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.minutes {
  color: var(--green);
  font-size: 12px;
  font-weight: 700;
}

.band-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.band-option {
  display: grid;
  gap: 1px;
  padding: 12px 8px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-2);
  color: var(--ink-soft);
  cursor: pointer;
  transition: 160ms ease;
}

.band-option strong {
  font-family: var(--font-reading);
  font-size: 22px;
  font-weight: 600;
}

.band-option span {
  color: var(--faint);
  font-size: 10px;
}

.band-option:hover {
  border-color: var(--green-2);
}

.band-option.active {
  border-color: var(--green);
  background: var(--green);
  color: #fff;
  box-shadow: 0 8px 20px rgb(36 92 67 / 18%);
}

.band-option.active span {
  color: rgb(255 255 255 / 62%);
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.slider-scale {
  display: flex;
  justify-content: space-between;
  margin-top: -4px;
  color: var(--faint);
  font-size: 9px;
}

.pace-note {
  display: grid;
  gap: 3px;
  margin-top: 22px;
  padding: 13px 15px;
  border: 1px solid #ecd5be;
  border-radius: 12px;
  background: var(--amber-soft);
  color: #7e4a21;
  font-size: 11px;
  line-height: 1.65;
}

.pace-note strong {
  font-size: 12px;
}

.submit {
  width: 100%;
  margin-top: 26px;
}

.foot {
  margin-top: 12px;
  color: var(--faint);
  font-size: 10px;
  text-align: center;
}

@media (max-width: 980px) {
  .onboarding-grid {
    grid-template-columns: 1fr;
  }

  .intro {
    position: static;
    padding-top: 0;
  }

  .principles {
    display: none;
  }

  .onboarding-top {
    margin-bottom: 26px;
  }
}

@media (max-width: 560px) {
  .two-col {
    grid-template-columns: 1fr;
  }

  .band-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .form-panel {
    padding: 20px 16px;
  }
}
</style>
