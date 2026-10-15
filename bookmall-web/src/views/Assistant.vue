<template>
  <main class="page">
    <h1>客服</h1>
    <p class="muted">只查店里的书。价格和库存以查询结果为准。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!ready" class="muted">加载中</p>
    <p v-else-if="!items.length" class="muted">还没有对话</p>
    <article v-for="item in items" :key="item.id || item.localId" class="card notice">
      <strong>{{ item.role === 'user' ? '我' : '客服' }}</strong>
      <span class="muted" style="float: right">{{ formatTime(item.createdAt) }}</span>
      <p style="white-space: pre-wrap">{{ item.content }}</p>
    </article>
    <div class="pager">
      <button type="button" :disabled="page <= 1" @click="load(page - 1)">上一页</button>
      <span>{{ page }} / {{ pages }}</span>
      <button type="button" :disabled="page >= pages" @click="load(page + 1)">下一页</button>
      <span class="muted">每页 10 条</span>
    </div>
    <form class="search" @submit.prevent="send">
      <input v-model="draft" :disabled="sending" placeholder="想找哪本书" />
      <button class="solid" :disabled="sending">发送</button>
    </form>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, formatTime } from '../api'

const base = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
const items = ref([])
const draft = ref('')
const error = ref('')
const ready = ref(false)
const sending = ref(false)
const page = ref(1)
const total = ref(0)
const pages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
let localSeq = 0

async function load(nextPage) {
  error.value = ''
  page.value = nextPage
  try {
    const data = await api('/api/assistant/messages?page=' + nextPage + '&size=10')
    items.value = data.items
    total.value = data.total
  } catch (ex) {
    error.value = ex.message
    items.value = []
    total.value = 0
  }
}

function consumeSse(buffer, onEvent) {
  const parts = buffer.split('\n\n')
  const rest = parts.pop()
  for (const part of parts) {
    let event = 'message'
    const dataLines = []
    for (const line of part.split('\n')) {
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }
    if (dataLines.length) {
      onEvent(event, dataLines.join('\n'))
    }
  }
  return rest
}

async function send() {
  const content = draft.value.trim()
  if (!content || sending.value) {
    return
  }
  error.value = ''
  sending.value = true
  const token = localStorage.getItem('bookmall.token')
  try {
    const response = await fetch(base + '/api/assistant/messages', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token ? `Bearer ${token}` : ''
      },
      body: JSON.stringify({ content })
    })
    if (!response.ok) {
      const body = await response.json()
      throw new Error(body.message || '请求失败')
    }
    draft.value = ''
    items.value.push({ localId: 'u' + (++localSeq), role: 'user', content, createdAt: new Date().toISOString() })
    const reply = { localId: 'a' + (++localSeq), role: 'assistant', content: '', createdAt: new Date().toISOString() }
    items.value.push(reply)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const chunk = await reader.read()
      if (chunk.done) {
        if (buffer.trim()) {
          consumeSse(buffer + '\n\n', (event, data) => {
            const payload = JSON.parse(data)
            if (event === 'delta') {
              reply.content += payload.text || ''
            } else if (event === 'done') {
              reply.content = payload.content || reply.content
            } else if (event === 'error') {
              error.value = payload.message || '请求失败'
            }
          })
        }
        break
      }
      buffer += decoder.decode(chunk.value, { stream: true })
      buffer = consumeSse(buffer, (event, data) => {
        const payload = JSON.parse(data)
        if (event === 'delta') {
          reply.content += payload.text || ''
        } else if (event === 'done') {
          reply.content = payload.content || reply.content
        } else if (event === 'error') {
          error.value = payload.message || '请求失败'
        }
      })
    }
  } catch (ex) {
    error.value = ex.message
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  await load(1)
  ready.value = true
})
</script>
