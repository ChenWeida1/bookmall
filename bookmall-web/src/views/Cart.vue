<template>
  <main class="page">
    <h1>购物车</h1>
    <p class="muted">第一版结算全部购物车，不支持勾选。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!ready" class="muted">加载中</p>
    <p v-else-if="!items.length && !error" class="muted">购物车是空的</p>
    <table v-else>
      <thead>
        <tr><th>书名</th><th>单价</th><th>数量</th><th>小计</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td>{{ item.bookTitle }}</td>
          <td class="price">¥{{ item.unitPrice }}</td>
          <td>
            <button @click="change(item, item.quantity - 1)">-</button>
            {{ item.quantity }}
            <button @click="change(item, item.quantity + 1)">+</button>
          </td>
          <td>¥{{ item.subtotal }}</td>
          <td><button class="linkish" @click="remove(item)">删除</button></td>
        </tr>
      </tbody>
    </table>
    <div v-if="items.length" style="margin-top: 16px; text-align: right">
      <p>合计：<strong class="price">¥{{ totalAmount }}</strong></p>
      <button class="solid" :disabled="submitting" @click="checkout">提交订单</button>
    </div>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const items = ref([])
const totalAmount = ref('0.00')
const error = ref('')
const ready = ref(false)
const submitting = ref(false)

async function load() {
  try {
    const data = await api('/api/cart')
    items.value = data.items
    totalAmount.value = data.totalAmount
  } finally {
    ready.value = true
  }
}

async function change(item, quantity) {
  error.value = ''
  try {
    await api('/api/cart/items/' + item.id, { method: 'PUT', body: JSON.stringify({ quantity }) })
    await load()
  } catch (ex) {
    error.value = ex.message
  }
}

async function remove(item) {
  await api('/api/cart/items/' + item.id, { method: 'DELETE' })
  await load()
}

async function checkout() {
  if (submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const order = await api('/api/orders', { method: 'POST' })
    router.push('/orders/' + order.id)
  } catch (ex) {
    error.value = ex.message
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    await load()
  } catch (ex) {
    error.value = ex.message
  }
})
</script>
