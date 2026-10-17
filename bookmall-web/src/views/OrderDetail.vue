<template>
  <div>
    <el-breadcrumb>
      <el-breadcrumb-item to="/orders">我的订单</el-breadcrumb-item>
      <el-breadcrumb-item>订单详情</el-breadcrumb-item>
    </el-breadcrumb>
    <h1>订单详情</h1>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="message" :title="message" type="success" show-icon :closable="false" />
    <el-card v-if="order">
      <p>订单号：{{ order.orderNo }}</p>
      <p>状态：{{ statusLabel(order.status) }}</p>
      <p>创建时间：{{ formatTime(order.createdAt) }}</p>
      <p>总金额：¥{{ order.totalAmount }}</p>
      <p>商品件数：{{ order.itemCount }}</p>
      <el-button v-if="order.status === 'PENDING_PAY'" type="primary" :disabled="busy" @click="pay">支付</el-button>
      <el-button v-if="order.status === 'PENDING_PAY'" :disabled="busy" @click="cancel">取消订单</el-button>
    </el-card>
    <h2>订单明细</h2>
    <el-table v-if="order" :data="order.items || []">
      <el-table-column prop="bookTitle" label="书名" />
      <el-table-column label="单价"><template #default="{ row }">¥{{ row.unitPrice }}</template></el-table-column>
      <el-table-column prop="quantity" label="数量" />
      <el-table-column label="小计"><template #default="{ row }">¥{{ row.subtotal }}</template></el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../http'
import { formatTime, statusLabel } from '../format'
import type { Order } from '../types'

const route = useRoute()
const order = ref<Order | null>(null)
const error = ref('')
const message = ref('')
const busy = ref(false)

onMounted(async () => {
  try {
    order.value = await api<Order>('/api/orders/' + route.params.id)
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
})

async function pay() {
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    order.value = await api<Order>('/api/orders/' + route.params.id + '/pay', { method: 'POST' })
    message.value = '支付成功'
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  } finally {
    busy.value = false
  }
}

async function cancel() {
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    order.value = await api<Order>('/api/orders/' + route.params.id + '/cancel', { method: 'POST' })
    message.value = '订单已取消'
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  } finally {
    busy.value = false
  }
}
</script>
