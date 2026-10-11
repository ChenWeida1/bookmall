<template>
  <main class="page">
    <p class="muted"><router-link to="/">书城</router-link> / {{ book?.title }}</p>
    <p v-if="error" class="error">{{ error }}</p>
    <section v-if="book" class="detail">
      <div class="cover large" :style="{ background: '#1e4d3a' }">{{ book.title }}</div>
      <div class="card" style="padding: 20px">
        <h1>{{ book.title }}</h1>
        <p>作者：{{ book.author }}</p>
        <p>分类：{{ book.categoryName }}</p>
        <p class="price">价格：¥{{ book.price }}</p>
        <p>库存：{{ book.stock }}</p>
        <p>ISBN：{{ book.isbn || '—' }}</p>
        <p>上架状态：{{ statusLabel(book.status) }}</p>
        <div class="stepper">
          <span>数量</span>
          <button @click="quantity = Math.max(1, quantity - 1)">-</button>
          <span>{{ quantity }}</span>
          <button @click="quantity = quantity + 1">+</button>
        </div>
        <button class="solid" :disabled="book.status !== 'ON_SALE'" @click="add">加入购物车</button>
        <p v-if="message" class="muted">{{ message }}</p>
        <h2>简介</h2>
        <p>{{ book.description || '暂无简介' }}</p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, currentUser, statusLabel } from '../api'

const route = useRoute()
const router = useRouter()
const book = ref(null)
const quantity = ref(1)
const error = ref('')
const message = ref('')

async function add() {
  error.value = ''
  message.value = ''
  if (!currentUser()) {
    router.push({ path: '/login-required', query: { next: route.fullPath } })
    return
  }
  try {
    await api('/api/cart/items', { method: 'POST', body: JSON.stringify({ bookId: book.value.id, quantity: quantity.value }) })
    message.value = '已加入购物车'
  } catch (ex) {
    error.value = ex.message
  }
}

onMounted(async () => {
  try {
    book.value = await api('/api/books/' + route.params.id)
  } catch (ex) {
    error.value = ex.message
  }
})
</script>
