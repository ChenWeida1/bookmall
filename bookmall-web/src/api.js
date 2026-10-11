const base = import.meta.env.VITE_API_BASE || 'http://localhost:8080'

import { reactive } from 'vue'

export const session = reactive({
  user: readUser()
})

function readUser() {
  const raw = localStorage.getItem('bookmall.user')
  return raw ? JSON.parse(raw) : null
}

export function currentUser() {
  return session.user
}

export function setSession(data) {
  localStorage.setItem('bookmall.token', data.token)
  localStorage.setItem('bookmall.user', JSON.stringify(data.user))
  session.user = data.user
}

export function clearSession() {
  localStorage.removeItem('bookmall.token')
  localStorage.removeItem('bookmall.user')
  session.user = null
}

let onUnauthorized = () => {}

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

export async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (options.body) {
    headers['Content-Type'] = 'application/json'
  }
  const token = localStorage.getItem('bookmall.token')
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const response = await fetch(base + path, { ...options, headers })
  const body = await response.json()
  if (response.status === 401) {
    clearSession()
    onUnauthorized()
  }
  if (!response.ok || body.code !== 0) {
    const error = new Error(body.message || '请求失败')
    error.status = response.status
    error.code = body.code
    error.data = body.data
    throw error
  }
  return body.data
}

export function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function statusLabel(status) {
  if (status === 'CREATED') return '已创建'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'ON_SALE') return '在售'
  if (status === 'OFF_SALE') return '下架'
  return status
}

/** 当前登录用户是否管理员（登录响应 user.role === 'ADMIN'）。 */
export function isAdmin() {
  return session.user && session.user.role === 'ADMIN'
}
