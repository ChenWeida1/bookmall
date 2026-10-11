<template>
  <main class="page">
    <h1>消息中心</h1>
    <p class="muted">仅显示当前用户通知，按时间倒序。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!ready" class="muted">加载中</p>
    <p v-else-if="!items.length && !error" class="muted">暂无消息</p>
    <article v-for="item in items" :key="item.id" class="card notice" :class="{ unread: !item.read }">
      <strong>{{ item.title }}</strong>
      <span class="muted" style="float: right">{{ item.read ? '已读' : '未读' }} {{ formatTime(item.createdAt) }}</span>
      <p>{{ item.content }}</p>
      <button v-if="!item.read" class="ghost" style="color: var(--green); border-color: var(--green)" @click="mark(item)">标记已读</button>
    </article>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, formatTime } from '../api'

const items = ref([])
const error = ref('')
const ready = ref(false)

async function load() {
  try {
    const data = await api('/api/notifications')
    items.value = data.items
  } finally {
    ready.value = true
  }
}

async function mark(item) {
  await api('/api/notifications/' + item.id + '/read', { method: 'POST' })
  await load()
}

onMounted(async () => {
  try {
    await load()
  } catch (ex) {
    error.value = ex.message
  }
})
</script>
