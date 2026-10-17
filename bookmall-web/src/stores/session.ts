import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginResult, SessionUser } from '../types'

function readUser(): SessionUser | null {
  const raw = localStorage.getItem('bookmall.user')
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as SessionUser
  } catch {
    return null
  }
}

/** 登录状态。令牌只放在 localStorage 的 bookmall.token。 */
export const useSessionStore = defineStore('session', () => {
  const user = ref<SessionUser | null>(readUser())
  const admin = computed(() => user.value?.role === 'ADMIN')

  function setSession(data: LoginResult) {
    localStorage.setItem('bookmall.token', data.token)
    localStorage.setItem('bookmall.user', JSON.stringify(data.user))
    user.value = data.user
  }

  function clear() {
    localStorage.removeItem('bookmall.token')
    localStorage.removeItem('bookmall.user')
    user.value = null
  }

  return { user, admin, setSession, clear }
})
