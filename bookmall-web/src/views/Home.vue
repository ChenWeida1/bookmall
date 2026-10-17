<template>
  <el-row :gutter="16">
    <el-col :span="5">
      <el-menu :default-active="String(categoryId ?? '')" @select="choose">
        <el-menu-item index="">全部</el-menu-item>
        <el-menu-item v-for="item in categories" :key="item.id" :index="String(item.id)">{{ item.name }}</el-menu-item>
      </el-menu>
    </el-col>
    <el-col :span="19">
      <h1>书城</h1>
      <p>游客可浏览图书列表与详情。</p>
      <el-form inline @submit.prevent="load(1)">
        <el-input v-model="keyword" placeholder="书名关键字" style="width: 240px" />
        <el-button type="primary" native-type="submit">搜索</el-button>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-skeleton v-if="!ready" :rows="4" animated />
      <el-empty v-else-if="!books.length && !error" description="没有找到相关图书" />
      <el-row v-else :gutter="12">
        <el-col v-for="book in books" :key="book.id" :span="8">
          <el-card shadow="hover" @click="router.push('/books/' + book.id)">
            <h3>{{ book.title }}</h3>
            <p>作者：{{ book.author }}</p>
            <p>分类：{{ book.categoryName }}</p>
            <p>¥{{ book.price }}</p>
            <p>库存：{{ book.stock }}</p>
          </el-card>
        </el-col>
      </el-row>
      <el-pagination
        layout="prev, pager, next"
        :current-page="page"
        :page-size="10"
        :total="total"
        @current-change="load"
      />
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../http'
import type { Book, Category, Page } from '../types'

const router = useRouter()
const categories = ref<Category[]>([])
const books = ref<Book[]>([])
const categoryId = ref<number | null>(null)
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const error = ref('')
const ready = ref(false)

function choose(index: string) {
  categoryId.value = index ? Number(index) : null
  load(1)
}

async function load(nextPage: number) {
  error.value = ''
  page.value = nextPage
  const params: Record<string, string | number> = { page: page.value, size: 10 }
  if (categoryId.value) params.categoryId = categoryId.value
  if (keyword.value.trim()) params.keyword = keyword.value.trim()
  try {
    const data = await api<Page<Book>>('/api/books', { params })
    books.value = data.items
    total.value = data.total
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
    books.value = []
    total.value = 0
  } finally {
    ready.value = true
  }
}

onMounted(async () => {
  try {
    categories.value = await api<Category[]>('/api/categories')
  } catch (ex) {
    error.value = ex instanceof Error ? ex.message : '请求失败'
  }
  await load(1)
})
</script>
