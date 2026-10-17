<template>
  <div>
    <h1>订单管理</h1>
    <p>管理员只读。可按状态筛选，不能在这里支付或取消。</p>
    <div ref="chartEl" style="height: 280px"></div>
    <el-radio-group v-model="status" @change="choose">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="PENDING_PAY">待支付</el-radio-button>
      <el-radio-button value="PAID">已支付</el-radio-button>
      <el-radio-button value="CANCELLED">已取消</el-radio-button>
    </el-radio-group>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-skeleton v-if="!ready" :rows="3" animated />
    <el-empty v-else-if="!orders.length && !error" description="没有订单" />
    <el-table v-else :data="orders">
      <el-table-column prop="userId" label="用户" />
      <el-table-column prop="orderNo" label="订单号" />
      <el-table-column label="总金额"><template #default="{ row }">¥{{ row.totalAmount }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
      <el-table-column label="创建时间"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label=""><template #default="{ row }"><el-button @click="open(row)">详情</el-button></template></el-table-column>
    </el-table>
    <el-card v-if="detail" style="margin-top: 16px">
      <p>订单号：{{ detail.orderNo }}</p>
      <p>用户：{{ detail.userId }}</p>
      <p>状态：{{ statusLabel(detail.status) }}</p>
      <p>金额：¥{{ detail.totalAmount }}</p>
      <p v-for="item in detail.items || []" :key="item.id">{{ item.bookTitle }} × {{ item.quantity }}　¥{{ item.subtotal }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { api } from '../http'
import { formatTime, statusLabel } from '../format'
import type { Order, Page } from '../types'

const orders = ref<Order[]>([])
const detail = ref<Order | null>(null)
const error = ref('')
const ready = ref(false)
const status = ref('')
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

async function load() {
  const params: Record<string, string | number> = {}
  if (status.value) params.status = status.value
  const data = await api<Page<Order>>('/api/admin/orders', { params })
  orders.value = data.items
}

async function loadChart() {
  const counts = { PENDING_PAY: 0, PAID: 0, CANCELLED: 0 }
  let page = 1
  let total = 0
  do {
    const data = await api<Page<Order>>('/api/admin/orders', { params: { page, size: 50 } })
    total = data.total
    for (const order of data.items) {
      if (order.status in counts) counts[order.status as keyof typeof counts] += 1
    }
    page += 1
  } while ((page - 1) * 50 < total)
  if (!chart && chartEl.value) chart = echarts.init(chartEl.value)
  chart?.setOption({
    title: { text: '订单状态' },
    xAxis: { type: 'category', data: ['待支付', '已支付', '已取消'] },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', data: [counts.PENDING_PAY, counts.PAID, counts.CANCELLED] }]
  })
}

async function choose() {
  error.value = ''
  try {
    await load()
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
}

async function open(order: Order) {
  error.value = ''
  try {
    detail.value = await api<Order>('/api/admin/orders/' + order.id)
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
}

onMounted(async () => {
  try {
    await load()
    await loadChart()
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  } finally {
    ready.value = true
  }
})

onBeforeUnmount(() => chart?.dispose())
</script>
