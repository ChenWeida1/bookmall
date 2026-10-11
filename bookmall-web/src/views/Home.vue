<template>
  <main class="page">
    <div class="grid">
      <aside class="card cats">
        <button :class="{ active: !categoryId }" @click="choose(null)">全部</button>
        <button v-for="item in categories" :key="item.id" :class="{ active: categoryId === item.id }" @click="choose(item.id)">
          {{ item.name }}
        </button>
      </aside>
      <section>
        <h1>书城</h1>
        <p class="muted">游客可浏览图书列表与详情。</p>
        <form class="search" @submit.prevent="load(1)">
          <input v-model="keyword" placeholder="书名关键字" />
          <button class="solid" type="submit">搜索</button>
          <span class="muted">可选功能</span>
        </form>
        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="!ready" class="muted">加载中</p>
        <p v-else-if="!books.length && !error" class="muted">没有找到相关图书</p>
        <div class="books">
          <router-link v-for="book in books" :key="book.id" class="card book" :to="`/books/${book.id}`">
            <div class="cover" :style="{ background: color(book.id) }">{{ book.title }}</div>
            <strong>{{ book.title }}</strong>
            <div class="muted">作者：{{ book.author }}</div>
            <div class="muted">分类：{{ book.categoryName }}</div>
            <div class="price">¥{{ book.price }}</div>
            <div class="muted">库存：{{ book.stock }}</div>
          </router-link>
        </div>
        <div class="pager">
          <button :disabled="page <= 1" @click="load(page - 1)">上一页</button>
          <span>{{ page }} / {{ pages }}</span>
          <button :disabled="page >= pages" @click="load(page + 1)">下一页</button>
          <span class="muted">每页 10 条</span>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'

const categories = ref([])
const books = ref([])
const categoryId = ref(null)
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const error = ref('')
const ready = ref(false)
const pages = computed(() => Math.max(1, Math.ceil(total.value / 10)))

function color(id) {
  const hues = ['#8f2d2d', '#1e3a5f', '#5b3a8c', '#b8860b', '#1d4e89', '#0f766e', '#c2410c', '#9d174d']
  return hues[(id - 1) % hues.length]
}

function choose(id) {
  categoryId.value = id
  load(1)
}

async function load(nextPage) {
  error.value = ''
  page.value = nextPage
  const params = new URLSearchParams({ page: String(page.value), size: '10' })
  if (categoryId.value) params.set('categoryId', String(categoryId.value))
  if (keyword.value.trim()) params.set('keyword', keyword.value.trim())
  try {
    const data = await api('/api/books?' + params.toString())
    books.value = data.items
    total.value = data.total
  } catch (ex) {
    error.value = ex.message
    books.value = []
    total.value = 0
  } finally {
    ready.value = true
  }
}

onMounted(async () => {
  try {
    categories.value = await api('/api/categories')
  } catch (ex) {
    error.value = ex.message
  }
  await load(1)
})
</script>
