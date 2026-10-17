<template>
  <el-container>
    <el-header class="bar">
      <el-menu mode="horizontal" router :ellipsis="false" :default-active="route.path">
        <el-menu-item index="/">纸间书店</el-menu-item>
        <el-menu-item index="/cart">购物车</el-menu-item>
        <el-menu-item index="/orders">我的订单</el-menu-item>
        <el-menu-item index="/notifications">消息</el-menu-item>
        <el-menu-item index="/assistant">客服</el-menu-item>
        <el-menu-item v-if="admin" index="/admin/books">管理</el-menu-item>
        <el-menu-item v-if="admin" index="/admin/orders">订单</el-menu-item>
        <el-menu-item v-if="admin" index="/admin/dead-letters">死信</el-menu-item>
      </el-menu>
      <div class="account">
        <template v-if="user">
          <span>{{ user.nickname }}</span>
          <el-button @click="logout">退出</el-button>
        </template>
        <template v-else>
          <el-button @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useSessionStore } from './stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { user, admin } = storeToRefs(session)

function logout() {
  session.clear()
  router.push('/')
}
</script>

<style>
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color);
}
.bar .el-menu {
  border-bottom: none;
  flex: 1;
}
.account {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
