<template>
  <main class="page">
    <h1>死信</h1>
    <p class="muted">查看队头消息，不确认。重投最早的一条，或直接丢弃。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="muted">{{ message }}</p>
    <section v-for="queue in queues" :key="queue.key" class="card" style="padding: 16px; margin-bottom: 16px">
      <h2>{{ queue.title }}</h2>
      <p class="muted">{{ queue.name }}　积压 {{ queue.count }}</p>
      <p v-if="queue.head">orderId：{{ queue.head.orderId ?? '—' }}　bizKey：{{ queue.head.bizKey ?? '—' }}</p>
      <p v-else class="muted">队列是空的</p>
      <button class="solid" :disabled="busy" @click="act(queue, 'replay')">重投</button>
      <button class="ghost" :disabled="busy" @click="act(queue, 'discard')">丢弃</button>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'

const error = ref('')
const message = ref('')
const busy = ref(false)
const queues = ref([
  { key: 'pay', title: '支付超时死信', name: '', count: 0, head: null, path: '/api/admin/orders/dead-letters' },
  { key: 'note', title: '通知死信', name: '', count: 0, head: null, path: '/api/admin/notifications/dead-letters' }
])

async function load() {
  for (const queue of queues.value) {
    const data = await api(queue.path)
    queue.name = data.queue
    queue.count = data.count
    queue.head = data.head
  }
}

async function act(queue, action) {
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    await api(queue.path, { method: 'POST', body: JSON.stringify({ action }) })
    message.value = action === 'replay' ? '已重投最早的一条' : '已丢弃最早的一条'
    await load()
  } catch (ex) {
    error.value = ex.message
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    await load()
  } catch (ex) {
    error.value = ex.message
  }
})
</script>
