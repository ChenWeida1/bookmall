<template>
  <main>
    <form class="card form" @submit.prevent="submit">
      <h1>注册</h1>
      <label>用户名</label>
      <input v-model="username" />
      <p class="muted">3–20 个字符，不可重复</p>
      <label>密码</label>
      <input v-model="password" type="password" />
      <p class="muted">6–32 个字符</p>
      <label>昵称（可选）</label>
      <input v-model="nickname" />
      <p class="muted">留空则默认等于用户名</p>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="solid" style="width: 100%; margin-top: 16px" type="submit">注册</button>
      <p style="text-align: center"><router-link to="/login">已有账号？去登录</router-link></p>
    </form>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, setSession } from '../api'

const router = useRouter()
const username = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')

async function submit() {
  error.value = ''
  try {
    const data = await api('/api/users/register', {
      method: 'POST',
      body: JSON.stringify({
        username: username.value,
        password: password.value,
        nickname: nickname.value || null
      })
    })
    setSession(data)
    router.push('/')
  } catch (ex) {
    error.value = ex.message
  }
}
</script>
