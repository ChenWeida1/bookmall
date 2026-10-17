<template>
  <el-card style="max-width: 420px; margin: 40px auto">
    <h1>注册</h1>
    <el-form @submit.prevent="submit">
      <el-form-item label="用户名"><el-input v-model="username" /></el-form-item>
      <p>3–20 个字符，不可重复</p>
      <el-form-item label="密码"><el-input v-model="password" type="password" show-password /></el-form-item>
      <p>6–32 个字符</p>
      <el-form-item label="昵称"><el-input v-model="nickname" placeholder="留空则默认等于用户名" /></el-form-item>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-button type="primary" native-type="submit" style="width: 100%">注册</el-button>
      <p><router-link to="/login">已有账号？去登录</router-link></p>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../http'
import { useSessionStore } from '../stores/session'
import type { LoginResult } from '../types'

const router = useRouter()
const session = useSessionStore()
const username = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')

async function submit() {
  error.value = ''
  try {
    const data = await api<LoginResult>('/api/users/register', {
      method: 'POST',
      body: { username: username.value, password: password.value, nickname: nickname.value || null }
    })
    session.setSession(data)
    router.push('/')
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
}
</script>
