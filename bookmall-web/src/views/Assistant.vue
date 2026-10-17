<template>
  <div>
    <h1>客服</h1>
    <p>只查店里的书。价格和库存以查询结果为准。外部书会说明未上架。</p>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-skeleton v-if="!ready" :rows="3" animated />
    <el-empty v-else-if="!items.length" description="还没有对话" />
    <el-card v-for="item in items" :key="item.id || item.localId" style="margin-bottom: 12px">
      <template #header>
        <span>{{ item.role === 'user' ? '我' : '客服' }}</span>
        <span style="float: right">{{ formatTime(item.createdAt) }}</span>
      </template>
      <p style="white-space: pre-wrap">{{ item.content }}</p>
    </el-card>
    <el-pagination layout="prev, pager, next" :current-page="page" :page-size="10" :total="total" @current-change="load" />
    <el-form inline @submit.prevent="send">
      <el-input v-model="draft" :disabled="sending" placeholder="想找哪本书" style="width: 360px" />
      <el-button type="primary" native-type="submit" :disabled="sending">发送</el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, apiBase } from '../http'
import { formatTime } from '../format'
import type { ChatItem, Page } from '../types'

const items = ref<ChatItem[]>([])
const draft = ref('')
const error = ref('')
const ready = ref(false)
const sending = ref(false)
const page = ref(1)
const total = ref(0)
const pages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
let localSeq = 0
void pages

async function load(nextPage: number) {
  error.value = ''
  page.value = nextPage
  try {
    const data = await api<Page<ChatItem>>('/api/assistant/messages', { params: { page: nextPage, size: 10 } })
    items.value = data.items
    total.value = data.total
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
    items.value = []
    total.value = 0
  }
}

function consumeSse(buffer: string, onEvent: (event: string, data: string) => void) {
  const parts = buffer.split('\n\n')
  const rest = parts.pop() || ''
  for (const part of parts) {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of part.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    if (dataLines.length) onEvent(event, dataLines.join('\n'))
  }
  return rest
}

async function send() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  error.value = ''
  sending.value = true
  const token = localStorage.getItem('bookmall.token')
  try {
    const response = await fetch(apiBase() + '/api/assistant/messages', {
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
    const replyIndex = items.value.length
    items.value.push({ localId: 'a' + (++localSeq), role: 'assistant', content: '', createdAt: new Date().toISOString() })
    const reader = response.body?.getReader()
    if (!reader) return
    const decoder = new TextDecoder()
    let buffer = ''
    const apply = (event: string, data: string) => {
      const payload = JSON.parse(data) as { text?: string; content?: string; message?: string }
      const reply = items.value[replyIndex]
      if (!reply) return
      if (event === 'delta') reply.content += payload.text || ''
      else if (event === 'done') reply.content = payload.content || reply.content
      else if (event === 'error') error.value = payload.message || '请求失败'
    }
    while (true) {
      const chunk = await reader.read()
      if (chunk.done) {
        if (buffer.trim()) consumeSse(buffer + '\n\n', apply)
        break
      }
      buffer += decoder.decode(chunk.value, { stream: true })
      buffer = consumeSse(buffer, apply)
    }
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  await load(1)
  ready.value = true
})
</script>
