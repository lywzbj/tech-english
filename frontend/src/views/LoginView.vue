<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthFrame from '@/components/common/AuthFrame.vue'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/utils/error'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  email: '',
  password: '',
})

const rules: FormRules<typeof form> = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { pattern: /^[^\s@]+@[^\s@]+$/, message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login({ email: form.email.trim(), password: form.password })
    ElMessage.success('欢迎回来')
    if (!auth.isOnboarded) {
      await router.replace('/onboarding')
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect.startsWith('/') ? redirect : '/')
  } catch (error) {
    ElMessage.error(errorMessage(error, '登录失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthFrame
    eyebrow="WELCOME BACK"
    title="继续你的学习"
    subtitle="今天不需要学很多。先清到期复习，再完成一个语法节点和一段阅读。"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" size="large" placeholder="you@example.com" autofocus />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          size="large"
          placeholder="输入密码"
          @keyup.enter="submit"
        />
      </el-form-item>
      <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
        登录
      </el-button>
    </el-form>
    <p class="hint">
      首次使用？<router-link to="/setup">创建账号</router-link>
    </p>
    <p class="local">本机应用 · 数据保存在本机 SQLite</p>
  </AuthFrame>
</template>

<style scoped>
.submit {
  width: 100%;
  margin-top: 6px;
}

.hint {
  margin-top: 18px;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}

.hint a {
  color: var(--green);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.local {
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  color: var(--faint);
  font-size: 10px;
  letter-spacing: 0.06em;
  text-align: center;
  text-transform: uppercase;
}
</style>
