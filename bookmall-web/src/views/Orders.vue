<template>
  <main class="page">
    <h1>我的订单</h1>
    <p class="muted">仅显示当前用户订单，按创建时间倒序。已创建订单可取消。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="muted">{{ message }}</p>
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
          <td>
            <router-link :to="`/orders/${order.id}`">查看详情</router-link>
            <button
              v-if="order.status === 'CREATED'"
              class="ghost"
              style="margin-left: 8px"
              :disabled="busyId === order.id"
              @click="cancel(order)"
            >取消</button>
          </td>
        </tr>
      </tbody>
    </table>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, formatTime, statusLabel } from '../api'

const orders = ref([])
const error = ref('')
const message = ref('')
const ready = ref(false)
const busyId = ref(null)

async function load() {
  const data = await api('/api/orders')
  orders.value = data.items
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

async function cancel(order) {
  error.value = ''
  message.value = ''
  busyId.value = order.id
  try {
    await api('/api/orders/' + order.id + '/cancel', { method: 'POST' })
    message.value = '订单已取消'
    await load()
  } catch (ex) {
    error.value = ex.message
  } finally {
    busyId.value = null
  }
}
</script>
