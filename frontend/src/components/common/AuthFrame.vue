<script setup lang="ts">
import ThemeSwitcher from './ThemeSwitcher.vue'

defineProps<{
  eyebrow: string
  title: string
  subtitle: string
}>()
</script>

<template>
  <div class="auth-page">
    <section class="auth-story">
      <div class="story-top">
        <div class="brand">
          <span class="brand-mark">TE</span>
          <span>
            <strong>Tech English</strong>
            <small>AI learning workspace</small>
          </span>
        </div>
        <p class="story-kicker">FOR SOFTWARE ENGINEERS</p>
        <h1>Learn the English your engineering career actually needs.</h1>
        <p class="story-copy">
          不再泛泛背单词。用技术文档、需求沟通和雅思阅读，把英语变成你工作里的第二套工具。
        </p>
      </div>

      <div class="story-points">
        <div class="point">
          <span>01</span>
          <p><strong>诊断起点</strong> 先知道语法和词汇缺口，再安排每天学什么。</p>
        </div>
        <div class="point">
          <span>02</span>
          <p><strong>技术语境</strong> API、部署、代码评审和客户需求，都是练习材料。</p>
        </div>
        <div class="point">
          <span>03</span>
          <p><strong>数据反馈</strong> 复习计划随你的答题、阅读和遗忘曲线变化。</p>
        </div>
      </div>

      <p class="story-foot">A quiet place to become fluent.</p>
    </section>

    <section class="auth-form-wrap">
      <div class="auth-theme">
        <ThemeSwitcher />
      </div>
      <div class="form-card">
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
        <p class="subtitle">{{ subtitle }}</p>
        <slot />
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  grid-template-columns: minmax(420px, 1.02fr) minmax(420px, 0.98fr);
  min-height: 100vh;
  background: var(--bg);
}

.auth-story {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 42px clamp(36px, 6vw, 86px) 34px;
  color: var(--hero-ink);
  background:
    radial-gradient(circle at 78% 8%, var(--hero-glow), transparent 34%),
    radial-gradient(circle at 10% 90%, rgb(201 120 56 / 17%), transparent 30%),
    linear-gradient(150deg, var(--hero-2) 0%, var(--hero-1) 48%, var(--hero-3) 100%);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: clamp(48px, 9vh, 100px);
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 13px;
  background: rgb(255 255 255 / 8%);
  font-family: var(--font-reading);
  font-size: 15px;
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
  margin-top: 1px;
  color: rgb(238 245 239 / 48%);
  font-size: 9px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.story-kicker {
  margin-bottom: 14px;
  color: #a9c9b4;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.2em;
}

h1 {
  max-width: 600px;
  font-family: var(--font-reading);
  font-size: clamp(38px, 4.4vw, 62px);
  font-weight: 500;
  letter-spacing: -0.045em;
  line-height: 1.02;
}

.story-copy {
  max-width: 520px;
  margin-top: 20px;
  color: rgb(238 245 239 / 64%);
  font-size: 15px;
  line-height: 1.8;
}

.story-points {
  display: grid;
  gap: 16px;
  max-width: 580px;
  margin-top: 42px;
}

.point {
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 12px;
  align-items: start;
}

.point > span {
  padding-top: 2px;
  color: #9dbfa8;
  font-family: var(--font-mono);
  font-size: 11px;
}

.point p {
  color: rgb(238 245 239 / 70%);
  font-size: 13px;
  line-height: 1.65;
}

.point strong {
  display: block;
  margin-bottom: 1px;
  color: #f3f8f4;
  font-size: 13px;
  font-weight: 600;
}

.story-foot {
  margin-top: 34px;
  color: rgb(238 245 239 / 33%);
  font-family: var(--font-reading);
  font-size: 13px;
  font-style: italic;
}

.auth-form-wrap {
  position: relative;
  display: grid;
  place-items: center;
  padding: 40px 24px;
}

.auth-theme {
  position: absolute;
  z-index: 5;
  top: 20px;
  right: 26px;
}

.form-card {
  width: min(100%, 440px);
  padding: clamp(28px, 4vw, 42px);
  border: 1px solid var(--line);
  border-radius: 26px;
  background: var(--surface);
  box-shadow: var(--shadow-md);
  animation: rise-in 0.55s ease both;
}

.form-card h2 {
  font-family: var(--font-reading);
  font-size: clamp(30px, 3.4vw, 40px);
  font-weight: 600;
  letter-spacing: -0.04em;
  line-height: 1.1;
}

.subtitle {
  margin: 10px 0 28px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 980px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-story {
    display: none;
  }

  .auth-form-wrap {
    min-height: 100vh;
  }
}

@media (max-width: 560px) {
  .auth-form-wrap {
    align-items: start;
    padding: 24px 16px;
  }

  .form-card {
    padding: 26px 20px;
    border-radius: 20px;
  }
}
</style>
