import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'

const defaultBaseURL = import.meta.env.VITE_API_BASE_URL || '/api'
const baseURL = import.meta.hot?.data.apiBaseURL || defaultBaseURL
let lastMessage = ''

export const http = axios.create({
  baseURL,
  timeout: 10_000,
})

export function setApiBaseURL(backendBaseURL: string) {
  http.defaults.baseURL = `${backendBaseURL.replace(/\/+$/, '')}/api`
}

if (import.meta.hot) {
  import.meta.hot.dispose((data) => {
    data.apiBaseURL = http.defaults.baseURL || defaultBaseURL
  })
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>

    if (typeof body?.success === 'boolean') {
      if (!body.success) {
        const message = body.message || '请求处理失败'
        showError(message)
        return Promise.reject(new Error(message))
      }

      return body.data
    }

    return response.data
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const message = error.response?.data?.message || statusMessage(status) || '网络连接失败'
    showError(message)
    return Promise.reject(error)
  },
)

function statusMessage(status?: number) {
  switch (status) {
    case 401:
      return '未认证或登录已过期'
    case 403:
      return '没有权限访问该资源'
    case 404:
      return '接口或资源不存在'
    case 500:
      return '后端服务异常'
    default:
      return undefined
  }
}

function showError(message: string) {
  if (message === lastMessage) return
  lastMessage = message
  ElMessage.error(message)
  window.setTimeout(() => {
    lastMessage = ''
  }, 1500)
}
