import axios, { type AxiosError } from 'axios'
import type { Router } from 'vue-router'
import { useSessionStore } from './stores/session'

const base = import.meta.env.VITE_API_BASE || 'http://localhost:8080'

export class ApiError extends Error {
  status: number
  code?: number
  data?: unknown

  constructor(message: string, status: number, code?: number, data?: unknown) {
    super(message)
    this.status = status
    this.code = code
    this.data = data
  }
}

interface Envelope<T> {
  code: number
  message: string
  data: T
}

export const http = axios.create({ baseURL: base })

/** 在 Pinia 安装之后调用，401 时清登录态并去请先登录。 */
export function installHttp(router: Router) {
  http.interceptors.request.use((config) => {
    const token = localStorage.getItem('bookmall.token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })
  http.interceptors.response.use(
    (response) => {
      const body = response.data as Envelope<unknown>
      if (body == null || typeof body.code !== 'number') {
        return response
      }
      if (body.code !== 0) {
        return Promise.reject(new ApiError(body.message || '请求失败', response.status, body.code, body.data))
      }
      response.data = body.data
      return response
    },
    (error: AxiosError<Envelope<unknown>>) => {
      const status = error.response?.status || 0
      const body = error.response?.data
      if (status === 401) {
        useSessionStore().clear()
        const current = router.currentRoute.value
        if (current.path !== '/login' && current.path !== '/login-required' && current.path !== '/register') {
          router.push({ path: '/login-required', query: { next: current.fullPath } })
        }
      }
      const message = body?.message || error.message || '请求失败'
      return Promise.reject(new ApiError(message, status, body?.code, body?.data))
    }
  )
}

export async function api<T>(path: string, options: { method?: string; body?: unknown; params?: Record<string, string | number> } = {}) {
  const response = await http.request<T>({
    url: path,
    method: options.method || 'GET',
    data: options.body,
    params: options.params
  })
  return response.data
}

export function apiBase() {
  return base
}
