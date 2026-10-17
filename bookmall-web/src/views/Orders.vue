<template>
  <div>
    <h1>我的订单</h1>
    <p>仅显示当前用户订单，按创建时间倒序。待支付订单可支付或取消。</p>
    <el-radio-group v-model="status" @change="choose">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="PENDING_PAY">待支付</el-radio-button>
      <el-radio-button value="PAID">已支付</el-radio-button>
      <el-radio-button value="CANCELLED">已取消</el-radio-button>
    </el-radio-group>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="message" :title="message" type="success" show-icon :closable="false" />
    <el-skeleton v-if="!ready" :rows="3" animated />
    <el-empty v-else-if="!orders.length && !error" description="还没有订单" />
    <el-table v-else :data="orders">
      <el-table-column prop="orderNo" label="订单号" />
      <el-table-column label="总金额"><template #default="{ row }">¥{{ row.totalAmount }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
      <el-table-column label="创建时间"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
      <el-table-column prop="itemCount" label="商品件数" />
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button link @click="router.push('/orders/' + row.id)">查看详情</el-button>
          <el-button v-if="row.status === 'PENDING_PAY'" :disabled="busyId === row.id" @click="pay(row)">支付</el-button>
          <el-button v-if="row.status === 'PENDING_PAY'" :disabled="busyId === row.id" @click="cancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../http'
import { formatTime, statusLabel } from '../format'
import type { Order, Page } from '../types'

const router = useRouter()
const orders = ref<Order[]>([])
const error = ref('')
const message = ref('')
const ready = ref(false)
const busyId = ref<number | null>(null)
const status = ref('')

async function load() {
  const params: Record<string, string | number> = {}
  if (status.value) params.status = status.value
  const data = await api<Page<Order>>('/api/orders', { params })
  orders.value = data.items
}

async function choose() {
  error.value = ''
  try {
    await load()
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
}

async function pay(order: Order) {
  error.value = ''
  message.value = ''
  busyId.value = order.id
  try {
    await api('/api/orders/' + order.id + '/pay', { method: 'POST' })
    message.value = '订单已支付'
    await load()
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  } finally {
    busyId.value = null
  }
}

async function cancel(order: Order) {
  error.value = ''
  message.value = ''
  busyId.value = order.id
  try {
    await api('/api/orders/' + order.id + '/cancel', { method: 'POST' })
    message.value = '订单已取消'
    await load()
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  } finally {
    busyId.value = null
  }
}

onMounted(async () => {
  try {
    await load()
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  } finally {
    ready.value = true
  }
})
</script>
