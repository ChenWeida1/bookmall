<template>
  <div>
    <header class="header">
      <div class="header-inner">
        <router-link class="brand" to="/">纸间书店</router-link>
        <nav class="nav">
          <router-link to="/">书城</router-link>
          <router-link to="/cart">购物车</router-link>
          <router-link to="/orders">我的订单</router-link>
          <router-link to="/notifications">消息</router-link>
          <router-link to="/assistant">客服</router-link>
          <router-link v-if="admin" to="/admin/books">管理</router-link>
          <router-link v-if="admin" to="/admin/orders">订单</router-link>
          <router-link v-if="admin" to="/admin/dead-letters">死信</router-link>
        </nav>
        <div class="spacer">
          <template v-if="user">
            <span>{{ user.nickname }}</span>
            <button class="ghost" @click="logout">退出</button>
          </template>
          <template v-else>
            <router-link class="ghost" to="/login">登录</router-link>
            <router-link class="ghost" to="/register">注册</router-link>
          </template>
        </div>
      </div>
    </header>
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { clearSession, isAdmin, session } from './api'

const router = useRouter()
const user = computed(() => session.user)
const admin = computed(() => isAdmin())

function logout() {
  clearSession()
  router.push('/')
}
</script>
