<template>
  <main>
    <form class="card form" @submit.prevent="submit">
      <h1>登录</h1>
      <label>用户名</label>
      <input v-model="username" />
      <label>密码</label>
      <input v-model="password" type="password" />
      <p v-if="error" class="error">{{ error }}</p>
      <button class="solid" style="width: 100%; margin-top: 16px" type="submit">登录</button>
      <p style="text-align: center"><router-link to="/register">没有账号？去注册</router-link></p>
    </form>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, setSession } from '../api'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')

async function submit() {
  error.value = ''
  try {
    const data = await api('/api/users/login', {
      method: 'POST',
      body: JSON.stringify({ username: username.value, password: password.value })
    })
    setSession(data)
    router.push(route.query.next || '/')
  } catch (ex) {
    error.value = ex.message
  }
}
</script>
