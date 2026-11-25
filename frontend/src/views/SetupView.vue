<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthFrame from '@/components/common/AuthFrame.vue'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/utils/error'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  displayName: '',
})

const rules: FormRules<typeof form> = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { pattern: /^[^\s@]+@[^\s@]+$/, message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码长度需要 8-72 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.setup({
      email: form.email.trim(),
      password: form.password,
      displayName: form.displayName.trim() || form.email.split('@')[0],
    })
    ElMessage.success('账号已创建')
    await router.replace('/onboarding')
  } catch (error) {
    ElMessage.error(errorMessage(error, '创建账号失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthFrame
    eyebrow="FIRST RUN"
    title="创建你的学习空间"
    subtitle="这是一台本机单用户应用。创建唯一账号后，学习数据只保存在本机 SQLite（或你配置的 MySQL）里，不开放注册。"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="称呼" prop="displayName">
        <el-input v-model="form.displayName" size="large" placeholder="例如 Alex" maxlength="80" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" size="large" placeholder="you@example.com" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password size="large" placeholder="至少 8 位" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          size="large"
          placeholder="再输入一次"
          @keyup.enter="submit"
        />
      </el-form-item>
      <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
        进入 Tech English
      </el-button>
    </el-form>
    <p class="foot">已经创建过账号？<router-link to="/login">直接登录</router-link></p>
  </AuthFrame>
</template>

<style scoped>
.submit {
  width: 100%;
  margin-top: 6px;
}

.foot {
  margin-top: 18px;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}

.foot a {
  color: var(--green);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
