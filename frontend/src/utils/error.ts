import axios from 'axios'

export function errorMessage(error: unknown, fallback = '操作失败，请稍后重试') {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; code?: string } | undefined
    if (data?.message) return data.message
    if (error.code === 'ECONNABORTED') return '请求超时，请检查后端服务'
    if (!error.response) return '无法连接后端服务，请确认端口 8080 已启动'
  }
  if (error instanceof Error && error.message) return error.message
  return fallback
}
