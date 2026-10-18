<template>
  <el-row :gutter="16">
    <el-col :span="5">
      <el-menu class="catalog-nav" :default-active="String(categoryId ?? '')" @select="choose">
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
      <el-row v-else class="book-grid" :gutter="12">
        <el-col v-for="book in books" :key="book.id" :span="8">
          <el-card class="book-card" shadow="hover" :body-style="{ padding: '0' }" @click="router.push('/books/' + book.id)">
            <div class="cover" :style="coverStyle(book)">
              <img v-if="book.coverUrl" :src="book.coverUrl" :alt="book.title" />
              <span v-else class="cover-title">{{ book.title }}</span>
            </div>
            <div class="meta">
              <h3>{{ book.title }}</h3>
              <p class="author">{{ book.author }}</p>
              <p class="price">¥{{ book.price }}</p>
              <p class="stock">{{ book.categoryName }} · 库存 {{ book.stock }}</p>
            </div>
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

const coverTones: Record<string, string[]> = {
  文学: ['#8c3a3a', '#a65d3f', '#6b3a4a', '#7a4e2d'],
  技术: ['#2c4a6e', '#3d5a80', '#245056', '#3e4c6b'],
  生活: ['#3f6b4e', '#5a6b3a', '#4e6248', '#6b5a3a']
}

function coverStyle(book: Book) {
  if (book.coverUrl) return undefined
  const palette = coverTones[book.categoryName || ''] || ['#3d5a80', '#6d4c41', '#2f5d50', '#5c4d7a']
  return { background: palette[book.id % palette.length] }
}

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

<style scoped>
.catalog-nav {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.catalog-nav.el-menu {
  border-right: none;
}

.book-grid {
  margin-top: 4px;
}

.book-grid :deep(.el-col) {
  margin-bottom: 12px;
}

.book-card {
  cursor: pointer;
  overflow: hidden;
}

.cover {
  position: relative;
  height: 148px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f2f3f5;
  color: #fff;
}

.cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-title {
  padding: 16px;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 1px;
  line-height: 1.4;
  text-align: center;
}

.meta {
  padding: 12px 14px 14px;
}

.meta h3 {
  margin: 0 0 4px;
  overflow: hidden;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.author,
.stock {
  margin: 0;
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}

.price {
  margin: 8px 0 2px;
  color: #e1251b;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.2;
}
</style>
