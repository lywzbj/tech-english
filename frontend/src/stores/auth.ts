import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api'
import { setAccessToken } from '@/api/http'
import type { AuthResponse, Profile, ProfileUpdateRequest, User } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const user = ref<User | null>(null)
  const profile = ref<Profile | null>(null)
  const setupRequired = ref(false)
  const bootstrapped = ref(false)
  const loading = ref(false)

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))
  const isOnboarded = computed(() => Boolean(profile.value?.onboardingCompleted))
  const displayName = computed(() => user.value?.displayName ?? 'Learner')

  function applyAuth(response: AuthResponse) {
    accessToken.value = response.accessToken
    setAccessToken(response.accessToken)
    user.value = response.user
    profile.value = response.profile
    setupRequired.value = false
  }

  async function bootstrap() {
    if (bootstrapped.value) return
    loading.value = true
    try {
      const status = await authApi.setupStatus()
      setupRequired.value = status.needsSetup
      if (!status.needsSetup) {
        try {
          const response = await authApi.refresh()
          applyAuth(response)
        } catch {
          clearSession()
        }
      }
    } catch {
      clearSession()
    } finally {
      bootstrapped.value = true
      loading.value = false
    }
  }

  async function setup(payload: { email: string; password: string; displayName: string }) {
    const response = await authApi.setup(payload)
    applyAuth(response)
    return response
  }

  async function login(payload: { email: string; password: string }) {
    const response = await authApi.login(payload)
    applyAuth(response)
    return response
  }

  async function loadMe() {
    const response = await authApi.me()
    user.value = response.user
    profile.value = response.profile
    return response
  }

  async function updateProfile(payload: ProfileUpdateRequest) {
    const response = await authApi.updateProfile(payload)
    user.value = response.user
    profile.value = response.profile
    return response
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clearSession()
    }
  }

  function clearSession() {
    accessToken.value = null
    setAccessToken(null)
    user.value = null
    profile.value = null
  }

  return {
    accessToken,
    user,
    profile,
    setupRequired,
    bootstrapped,
    loading,
    isAuthenticated,
    isOnboarded,
    displayName,
    bootstrap,
    setup,
    login,
    loadMe,
    updateProfile,
    logout,
    clearSession,
  }
})
