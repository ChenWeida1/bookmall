<template>
  <main class="page">
    <h1>图书管理</h1>
    <p class="muted">仅管理员可改上架状态，可选覆盖库存。调用 PUT /api/admin/books/{id}。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="ok" class="muted">{{ ok }}</p>
    <form class="card" style="padding: 16px; max-width: 420px" @submit.prevent="submit">
      <label>图书 ID
        <input v-model="bookId" required />
      </label>
      <label>状态
        <select v-model="status">
          <option value="ON_SALE">在售</option>
          <option value="OFF_SALE">下架</option>
        </select>
      </label>
      <label>库存（可选）
        <input v-model="stock" type="number" min="0" placeholder="不改则留空" />
      </label>
      <button type="submit" :disabled="busy">保存</button>
    </form>
    <section v-if="book" class="card" style="padding: 16px; margin-top: 16px">
      <p>书名：{{ book.title }}</p>
      <p>状态：{{ statusLabel(book.status) }}</p>
      <p>库存：{{ book.stock }}</p>
    </section>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { api, statusLabel } from '../api'

const bookId = ref('')
const status = ref('ON_SALE')
const stock = ref('')
const book = ref(null)
const error = ref('')
const ok = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  ok.value = ''
  busy.value = true
  try {
    const body = { status: status.value }
    if (stock.value !== '' && stock.value != null) {
      body.stock = Number(stock.value)
    }
    book.value = await api('/api/admin/books/' + bookId.value, {
      method: 'PUT',
      body: JSON.stringify(body)
    })
    ok.value = '已更新'
  } catch (ex) {
    error.value = ex.message
  } finally {
    busy.value = false
  }
}
</script>
