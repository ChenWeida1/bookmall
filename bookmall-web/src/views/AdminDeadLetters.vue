<template>
  <div>
    <h1>死信</h1>
    <p>查看队头消息，不确认。重投最早的一条，或直接丢弃。</p>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="message" :title="message" type="success" show-icon :closable="false" />
    <el-card v-for="queue in queues" :key="queue.key" style="margin-bottom: 16px">
      <h2>{{ queue.title }}</h2>
      <p>{{ queue.name }}　积压 {{ queue.count }}</p>
      <p v-if="queue.head">orderId：{{ queue.head.orderId ?? '—' }}　bizKey：{{ queue.head.bizKey ?? '—' }}</p>
      <p v-else>队列是空的</p>
      <el-button type="primary" :disabled="busy" @click="act(queue, 'replay')">重投</el-button>
      <el-button :disabled="busy" @click="act(queue, 'discard')">丢弃</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../http'
import type { DeadHead } from '../types'

interface QueueView {
  key: string
  title: string
  name: string
  count: number
  head: DeadHead | null
  path: string
}

const error = ref('')
const message = ref('')
const busy = ref(false)
const queues = ref<QueueView[]>([
  { key: 'pay', title: '支付超时死信', name: '', count: 0, head: null, path: '/api/admin/orders/dead-letters' },
  { key: 'note', title: '通知死信', name: '', count: 0, head: null, path: '/api/admin/notifications/dead-letters' }
])

async function load() {
  for (const queue of queues.value) {
    const data = await api<{ queue: string; count: number; head: DeadHead | null }>(queue.path)
    queue.name = data.queue
    queue.count = data.count
    queue.head = data.head
  }
}

async function act(queue: QueueView, action: string) {
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    await api(queue.path, { method: 'POST', body: { action } })
    message.value = action === 'replay' ? '已重投最早的一条' : '已丢弃最早的一条'
    await load()
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    await load()
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
})
</script>
