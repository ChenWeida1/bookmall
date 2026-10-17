<template>
  <div>
    <el-breadcrumb>
      <el-breadcrumb-item to="/">书城</el-breadcrumb-item>
      <el-breadcrumb-item>{{ book?.title || '详情' }}</el-breadcrumb-item>
    </el-breadcrumb>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-card v-if="book">
      <h1>{{ book.title }}</h1>
      <p>作者：{{ book.author }}</p>
      <p>分类：{{ book.categoryName }}</p>
      <p>价格：¥{{ book.price }}</p>
      <p>库存：{{ book.stock }}</p>
      <p>ISBN：{{ book.isbn || '—' }}</p>
      <p>上架状态：{{ statusLabel(book.status) }}</p>
      <el-input-number v-model="quantity" :min="1" />
      <el-button type="primary" :disabled="book.status !== 'ON_SALE'" @click="add">加入购物车</el-button>
      <p v-if="message">{{ message }}</p>
      <h2>简介</h2>
      <p>{{ book.description || '暂无简介' }}</p>
      <template v-if="book.synopsis">
        <h2>梗概</h2>
        <el-text v-if="book.source === 'ZHIPU'" type="info">这段由客服模型整理</el-text>
        <p>{{ book.synopsis }}</p>
      </template>
      <template v-if="book.contents">
        <h2>目录</h2>
        <p style="white-space: pre-wrap">{{ book.contents }}</p>
      </template>
      <template v-if="book.excerpt">
        <h2>摘录</h2>
        <p>{{ book.excerpt }}</p>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../http'
import { statusLabel } from '../format'
import { useSessionStore } from '../stores/session'
import type { Book } from '../types'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const book = ref<Book | null>(null)
const quantity = ref(1)
const error = ref('')
const message = ref('')

async function add() {
  error.value = ''
  message.value = ''
  if (!session.user) {
    router.push({ path: '/login-required', query: { next: route.fullPath } })
    return
  }
  try {
    await api('/api/cart/items', { method: 'POST', body: { bookId: book.value?.id, quantity: quantity.value } })
    message.value = '已加入购物车'
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
}

onMounted(async () => {
  try {
    book.value = await api<Book>('/api/books/' + route.params.id)
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
})
</script>
