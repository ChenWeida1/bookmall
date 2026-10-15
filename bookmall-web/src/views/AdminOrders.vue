<template>
  <main class="page">
    <h1>订单管理</h1>
    <p class="muted">管理员只读。可按状态筛选，不能在这里支付或取消。</p>
    <p>
      <button class="ghost" :disabled="status === ''" @click="choose('')">全部</button>
      <button class="ghost" :disabled="status === 'PENDING_PAY'" @click="choose('PENDING_PAY')">待支付</button>
      <button class="ghost" :disabled="status === 'PAID'" @click="choose('PAID')">已支付</button>
      <button class="ghost" :disabled="status === 'CANCELLED'" @click="choose('CANCELLED')">已取消</button>
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!ready" class="muted">加载中</p>
    <p v-else-if="!orders.length && !error" class="muted">没有订单</p>
    <table v-else>
      <thead>
        <tr><th>用户</th><th>订单号</th><th>总金额</th><th>状态</th><th>创建时间</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="order in orders" :key="order.id">
          <td>{{ order.userId }}</td>
          <td>{{ order.orderNo }}</td>
          <td class="price">¥{{ order.totalAmount }}</td>
          <td><span class="badge">{{ statusLabel(order.status) }}</span></td>
          <td>{{ formatTime(order.createdAt) }}</td>
          <td><button class="ghost" @click="open(order)">详情</button></td>
        </tr>
      </tbody>
    </table>
    <section v-if="detail" class="card" style="padding: 16px; margin-top: 16px">
      <p>订单号：{{ detail.orderNo }}</p>
      <p>用户：{{ detail.userId }}</p>
      <p>状态：{{ statusLabel(detail.status) }}</p>
      <p>金额：¥{{ detail.totalAmount }}</p>
      <ul>
        <li v-for="item in detail.items" :key="item.id">
          {{ item.bookTitle }} × {{ item.quantity }}　¥{{ item.subtotal }}
        </li>
      </ul>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, formatTime, statusLabel } from '../api'

const orders = ref([])
const detail = ref(null)
const error = ref('')
const ready = ref(false)
const status = ref('')

async function load() {
  const query = status.value ? '?status=' + status.value : ''
  const data = await api('/api/admin/orders' + query)
  orders.value = data.items
}

async function choose(next) {
  status.value = next
  error.value = ''
  try {
    await load()
  } catch (ex) {
    error.value = ex.message
  }
}

async function open(order) {
  error.value = ''
  try {
    detail.value = await api('/api/admin/orders/' + order.id)
  } catch (ex) {
    error.value = ex.message
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
