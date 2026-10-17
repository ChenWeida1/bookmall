<template>
  <div>
    <h1>图书管理</h1>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="ok" :title="ok" type="success" show-icon :closable="false" />

    <el-card header="修改上下架">
      <el-form label-width="80px" @submit.prevent="saveStatus">
        <el-form-item label="图书 ID"><el-input v-model="bookId" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status">
            <el-option label="在售" value="ON_SALE" />
            <el-option label="下架" value="OFF_SALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="库存"><el-input v-model="stock" placeholder="不改则留空" /></el-form-item>
        <el-button type="primary" :disabled="busy" native-type="submit">保存</el-button>
        <el-button :disabled="busy || !bookId" @click="generate">生成梗概</el-button>
      </el-form>
      <div v-if="book">
        <p>书名：{{ book.title }}</p>
        <p>状态：{{ statusLabel(book.status) }}</p>
        <p>库存：{{ book.stock }}</p>
        <p v-if="book.synopsis">梗概：{{ book.synopsis }}</p>
      </div>
    </el-card>

    <el-card header="新增图书" style="margin-top: 16px">
      <el-form label-width="80px" @submit.prevent="createBook">
        <el-form-item label="分类"><el-input v-model="created.categoryId" /></el-form-item>
        <el-form-item label="书名"><el-input v-model="created.title" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="created.author" /></el-form-item>
        <el-form-item label="价格"><el-input v-model="created.price" /></el-form-item>
        <el-form-item label="库存"><el-input v-model="created.stock" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="created.status">
            <el-option label="在售" value="ON_SALE" />
            <el-option label="下架" value="OFF_SALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="ISBN"><el-input v-model="created.isbn" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="created.description" type="textarea" /></el-form-item>
        <el-form-item label="梗概"><el-input v-model="created.synopsis" type="textarea" /></el-form-item>
        <el-button type="primary" :disabled="busy" native-type="submit">新增</el-button>
      </el-form>
    </el-card>

    <el-card header="编辑内容" style="margin-top: 16px">
      <el-form label-width="80px" @submit.prevent="saveContent">
        <el-form-item label="图书 ID"><el-input v-model="contentId" /></el-form-item>
        <el-form-item label="梗概"><el-input v-model="content.synopsis" type="textarea" /></el-form-item>
        <el-form-item label="目录"><el-input v-model="content.contents" type="textarea" /></el-form-item>
        <el-form-item label="摘录"><el-input v-model="content.excerpt" type="textarea" /></el-form-item>
        <el-button type="primary" :disabled="busy" native-type="submit">保存内容</el-button>
      </el-form>
    </el-card>

    <el-card header="外部书目" style="margin-top: 16px">
      <el-form inline @submit.prevent="searchExternal">
        <el-input v-model="query" placeholder="书名" style="width: 240px" />
        <el-button type="primary" native-type="submit" :disabled="busy">搜索百科</el-button>
      </el-form>
      <div v-if="external">
        <p>{{ external.title }}　{{ external.author || '作者未知' }}</p>
        <p>{{ external.synopsis || '没有摘要' }}</p>
        <el-form inline @submit.prevent="importBook">
          <el-input v-model="importing.categoryId" placeholder="分类 ID" />
          <el-input v-model="importing.price" placeholder="价格" />
          <el-input v-model="importing.stock" placeholder="库存" />
          <el-select v-model="importing.status">
            <el-option label="在售" value="ON_SALE" />
            <el-option label="下架" value="OFF_SALE" />
          </el-select>
          <el-button type="primary" native-type="submit" :disabled="busy">引进</el-button>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { api } from '../http'
import { statusLabel } from '../format'
import type { Book, ExternalBook } from '../types'

const bookId = ref('')
const status = ref('ON_SALE')
const stock = ref('')
const book = ref<Book | null>(null)
const error = ref('')
const ok = ref('')
const busy = ref(false)
const created = reactive({ categoryId: '1', title: '', author: '', price: '', stock: '', status: 'OFF_SALE', isbn: '', description: '', synopsis: '' })
const contentId = ref('')
const content = reactive({ synopsis: '', contents: '', excerpt: '' })
const query = ref('')
const external = ref<ExternalBook | null>(null)
const importing = reactive({ categoryId: '1', price: '', stock: '', status: 'OFF_SALE' })

function fail(ex: unknown) {
  error.value = ex instanceof Error ? ex.message : '请求失败'
}

async function saveStatus() {
  error.value = ''
  ok.value = ''
  busy.value = true
  try {
    const body: { status: string; stock?: number } = { status: status.value }
    if (stock.value !== '') body.stock = Number(stock.value)
    book.value = await api<Book>('/api/admin/books/' + bookId.value, { method: 'PUT', body })
    ok.value = '已更新'
  } catch (ex) {
    fail(ex)
  } finally {
    busy.value = false
  }
}

async function createBook() {
  error.value = ''
  ok.value = ''
  busy.value = true
  try {
    book.value = await api<Book>('/api/admin/books', {
      method: 'POST',
      body: {
        categoryId: Number(created.categoryId),
        title: created.title,
        author: created.author,
        price: created.price,
        stock: Number(created.stock),
        status: created.status,
        isbn: created.isbn || null,
        description: created.description || null,
        synopsis: created.synopsis || null
      }
    })
    ok.value = '已新增'
  } catch (ex) {
    fail(ex)
  } finally {
    busy.value = false
  }
}

async function saveContent() {
  error.value = ''
  ok.value = ''
  busy.value = true
  try {
    book.value = await api<Book>('/api/admin/books/' + contentId.value + '/content', { method: 'PUT', body: { ...content } })
    ok.value = '内容已保存'
  } catch (ex) {
    fail(ex)
  } finally {
    busy.value = false
  }
}

async function searchExternal() {
  error.value = ''
  ok.value = ''
  busy.value = true
  try {
    external.value = await api<ExternalBook | null>('/api/admin/books/external', { params: { q: query.value } })
    ok.value = external.value ? '已查到词条' : '没有查到词条'
  } catch (ex) {
    external.value = null
    fail(ex)
  } finally {
    busy.value = false
  }
}

async function importBook() {
  if (!external.value) return
  error.value = ''
  ok.value = ''
  busy.value = true
  try {
    book.value = await api<Book>('/api/admin/books/import', {
      method: 'POST',
      body: {
        lemmaId: external.value.lemmaId,
        categoryId: Number(importing.categoryId),
        price: importing.price,
        stock: Number(importing.stock),
        status: importing.status
      }
    })
    ok.value = '已引进'
  } catch (ex) {
    fail(ex)
  } finally {
    busy.value = false
  }
}

async function generate() {
  error.value = ''
  ok.value = ''
  busy.value = true
  try {
    await api('/api/assistant/books/' + bookId.value + '/synopsis', { method: 'POST' })
    book.value = await api<Book>('/api/books/' + bookId.value)
    ok.value = '梗概已生成'
  } catch (ex) {
    fail(ex)
  } finally {
    busy.value = false
  }
}
</script>
