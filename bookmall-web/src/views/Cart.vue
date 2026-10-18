<template>
  <div>
    <h1>购物车</h1>
    <p>勾选要结算的图书。不勾选任何一行时不能提交。</p>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-skeleton v-if="!ready" :rows="3" animated />
    <el-empty v-else-if="!items.length && !error" description="购物车是空的" />
    <template v-else>
      <el-table :data="items" stripe>
        <el-table-column width="48">
          <template #default="{ row }">
            <el-checkbox :model-value="selectedIds.includes(row.id)" @change="toggle(row.id)" />
          </template>
        </el-table-column>
        <el-table-column prop="bookTitle" label="书名" />
        <el-table-column label="单价"><template #default="{ row }">¥{{ row.unitPrice }}</template></el-table-column>
        <el-table-column label="数量">
          <template #default="{ row }">
            <el-button @click="change(row, row.quantity - 1)">-</el-button>
            {{ row.quantity }}
            <el-button @click="change(row, row.quantity + 1)">+</el-button>
          </template>
        </el-table-column>
        <el-table-column label="小计"><template #default="{ row }">¥{{ row.subtotal }}</template></el-table-column>
        <el-table-column label="操作"><template #default="{ row }"><el-button link @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
      <p>已选合计：¥{{ selectedTotal }}</p>
      <el-button type="primary" :disabled="submitting || !selectedIds.length" @click="checkout">提交订单</el-button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../http'
import type { CartItem } from '../types'

const router = useRouter()
const items = ref<CartItem[]>([])
const selectedIds = ref<number[]>([])
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
  const data = await api<{ items: CartItem[] }>('/api/cart')
  items.value = data.items
  const alive = new Set(data.items.map((item) => item.id))
  selectedIds.value = selectedIds.value.filter((id) => alive.has(id))
  if (!selectedIds.value.length) {
    selectedIds.value = data.items.map((item) => item.id)
  }
}

function toggle(id: number) {
  if (selectedIds.value.includes(id)) {
    selectedIds.value = selectedIds.value.filter((item) => item !== id)
  } else {
    selectedIds.value = selectedIds.value.concat(id)
  }
}

async function change(item: CartItem, quantity: number) {
  error.value = ''
  try {
    await api('/api/cart/items/' + item.id, { method: 'PUT', body: { quantity } })
    await load()
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
}

async function remove(item: CartItem) {
  await api('/api/cart/items/' + item.id, { method: 'DELETE' })
  selectedIds.value = selectedIds.value.filter((id) => id !== item.id)
  await load()
}

async function checkout() {
  if (submitting.value || !selectedIds.value.length) return
  submitting.value = true
  error.value = ''
  try {
    const order = await api<{ id: number }>('/api/orders', { method: 'POST', body: { itemIds: selectedIds.value } })
    router.push('/orders/' + order.id)
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  } finally {
    submitting.value = false
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
