<template>
  <main class="page">
    <p class="muted"><router-link to="/orders">我的订单</router-link> / 订单详情</p>
    <h1>订单详情</h1>
    <p v-if="error" class="error">{{ error }}</p>
    <section v-if="order" class="card" style="padding: 16px">
      <p>订单号：{{ order.orderNo }}</p>
      <p>状态：<span class="badge">{{ statusLabel(order.status) }}</span></p>
      <p>创建时间：{{ formatTime(order.createdAt) }}</p>
      <p>总金额：<span class="price">¥{{ order.totalAmount }}</span></p>
      <p>商品件数：{{ order.itemCount }}</p>
    </section>
    <h2>订单明细</h2>
    <table v-if="order">
      <thead><tr><th>书名</th><th>单价</th><th>数量</th><th>小计</th></tr></thead>
      <tbody>
        <tr v-for="item in order.items" :key="item.id">
          <td>{{ item.bookTitle }}</td>
          <td>¥{{ item.unitPrice }}</td>
          <td>{{ item.quantity }}</td>
          <td>¥{{ item.subtotal }}</td>
        </tr>
      </tbody>
    </table>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, formatTime, statusLabel } from '../api'

const route = useRoute()
const order = ref(null)
const error = ref('')

onMounted(async () => {
  try {
    order.value = await api('/api/orders/' + route.params.id)
  } catch (ex) {
    error.value = ex.message
  }
})
</script>
