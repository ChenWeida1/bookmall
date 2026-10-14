<template>
  <main class="page">
    <h1>购物车</h1>
    <p class="muted">勾选要结算的图书。不勾选任何一行时不能提交。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!ready" class="muted">加载中</p>
    <p v-else-if="!items.length && !error" class="muted">购物车是空的</p>
    <table v-else>
      <thead>
        <tr><th></th><th>书名</th><th>单价</th><th>数量</th><th>小计</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td><input type="checkbox" :checked="selectedIds.includes(item.id)" @change="toggle(item.id)" /></td>
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
      <p>已选合计：<strong class="price">¥{{ selectedTotal }}</strong></p>
      <button class="solid" :disabled="submitting || !selectedIds.length" @click="checkout">提交订单</button>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const items = ref([])
const selectedIds = ref([])
const error = ref('')
const ready = ref(false)
const submitting = ref(false)

const selectedTotal = computed(() => {
  const cents = items.value
    .filter((item) => selectedIds.value.includes(item.id))
    .reduce((acc, item) => acc + Math.round(Number(item.subtotal) * 100), 0)
  return (cents / 100).toFixed(2)
})

async function load() {
  const data = await api('/api/cart')
  items.value = data.items
  const alive = new Set(data.items.map((item) => item.id))
  selectedIds.value = selectedIds.value.filter((id) => alive.has(id))
  if (!selectedIds.value.length) {
    selectedIds.value = data.items.map((item) => item.id)
  }
}

function toggle(id) {
  if (selectedIds.value.includes(id)) {
    selectedIds.value = selectedIds.value.filter((item) => item !== id)
  } else {
    selectedIds.value = selectedIds.value.concat(id)
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
  selectedIds.value = selectedIds.value.filter((id) => id !== item.id)
  await load()
}

async function checkout() {
  if (submitting.value || !selectedIds.value.length) return
  submitting.value = true
  error.value = ''
  try {
    const order = await api('/api/orders', {
      method: 'POST',
      body: JSON.stringify({ itemIds: selectedIds.value })
    })
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
  } finally {
    ready.value = true
  }
})
</script>
