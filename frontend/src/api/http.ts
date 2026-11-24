import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export const http = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  timeout: 60_000,
})

http.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

function isAuthEndpoint(url?: string) {
  if (!url) return false
  return url.includes('/auth/') || url.includes('/setup')
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    if (error.response?.status !== 401 || !config || config._retry || isAuthEndpoint(config.url)) {
      return Promise.reject(error)
    }
    config._retry = true
    const token = await refreshAccessToken()
    if (!token) {
      return Promise.reject(error)
    }
    config.headers.Authorization = `Bearer ${token}`
    return http(config)
  },
)

export async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = axios
      .post('/api/v1/auth/refresh', null, { withCredentials: true, timeout: 15_000 })
      .then((response) => {
        const token = response.data?.accessToken as string | undefined
        if (!token) {
          setAccessToken(null)
          return null
        }
        setAccessToken(token)
        return token
      })
      .catch(() => {
        setAccessToken(null)
        window.dispatchEvent(new Event('auth:expired'))
        return null
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}
