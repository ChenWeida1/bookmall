<template>
  <main class="page">
    <h1>我的订单</h1>
    <p class="muted">仅显示当前用户订单，按创建时间倒序。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!ready" class="muted">加载中</p>
    <p v-else-if="!orders.length && !error" class="muted">还没有订单</p>
    <table v-else>
      <thead>
        <tr><th>订单号</th><th>总金额</th><th>状态</th><th>创建时间</th><th>商品件数</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="order in orders" :key="order.id">
          <td>{{ order.orderNo }}</td>
          <td class="price">¥{{ order.totalAmount }}</td>
          <td><span class="badge">{{ statusLabel(order.status) }}</span></td>
          <td>{{ formatTime(order.createdAt) }}</td>
          <td>{{ order.itemCount }}</td>
          <td><router-link :to="`/orders/${order.id}`">查看详情</router-link></td>
        </tr>
      </tbody>
    </table>
    <p class="muted">状态「已创建」表示订单已创建，第一版无需支付。</p>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, formatTime, statusLabel } from '../api'

const orders = ref([])
const error = ref('')
const ready = ref(false)

onMounted(async () => {
  try {
    const data = await api('/api/orders')
    orders.value = data.items
  } catch (ex) {
    error.value = ex.message
  } finally {
    ready.value = true
  }
})
</script>
