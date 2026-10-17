<template>
  <div>
    <h1>消息中心</h1>
    <p>仅显示当前用户通知，按时间倒序。</p>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-skeleton v-if="!ready" :rows="3" animated />
    <el-empty v-else-if="!items.length && !error" description="暂无消息" />
    <el-card v-for="item in items" :key="item.id" style="margin-bottom: 12px">
      <template #header>
        <span>{{ item.title }}</span>
        <span style="float: right">{{ item.read ? '已读' : '未读' }} {{ formatTime(item.createdAt) }}</span>
      </template>
      <p>{{ item.content }}</p>
      <el-button v-if="!item.read" @click="mark(item)">标记已读</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../http'
import { formatTime } from '../format'
import type { Notice, Page } from '../types'

const items = ref<Notice[]>([])
const error = ref('')
const ready = ref(false)

async function load() {
  const data = await api<Page<Notice>>('/api/notifications')
  items.value = data.items
}

async function mark(item: Notice) {
  await api('/api/notifications/' + item.id + '/read', { method: 'POST' })
  await load()
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
