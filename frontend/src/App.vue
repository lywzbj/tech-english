<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

function handleExpired() {
  auth.clearSession()
  if (router.currentRoute.value.name !== 'login' && router.currentRoute.value.name !== 'setup') {
    router.replace({ name: auth.setupRequired ? 'setup' : 'login' })
  }
}

onMounted(() => window.addEventListener('auth:expired', handleExpired))
onUnmounted(() => window.removeEventListener('auth:expired', handleExpired))
</script>

<template>
  <router-view />
</template>
