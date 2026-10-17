<template>
  <el-card style="max-width: 420px; margin: 40px auto">
    <h1>请先登录</h1>
    <p>查看购物车、订单和消息需要登录。</p>
    <el-form @submit.prevent="submit">
      <el-form-item label="用户名"><el-input v-model="username" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="password" type="password" show-password /></el-form-item>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-button type="primary" native-type="submit" style="width: 100%">登录</el-button>
      <p><router-link to="/register">没有账号？去注册</router-link></p>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../http'
import { useSessionStore } from '../stores/session'
import type { LoginResult } from '../types'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const username = ref('')
const password = ref('')
const error = ref('')

async function submit() {
  error.value = ''
  try {
    const data = await api<LoginResult>('/api/users/login', {
      method: 'POST',
      body: { username: username.value, password: password.value }
    })
    session.setSession(data)
    const next = typeof route.query.next === 'string' ? route.query.next : '/cart'
    router.push(next)
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
}
</script>
