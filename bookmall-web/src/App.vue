<template>
  <el-container class="shell">
    <el-aside width="208px" class="sider">
      <div class="brand">纸间书店</div>
      <el-menu
        router
        :default-active="activeMenu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/">书城</el-menu-item>
        <el-menu-item index="/cart">购物车</el-menu-item>
        <el-menu-item index="/orders">我的订单</el-menu-item>
        <el-menu-item index="/notifications">消息</el-menu-item>
        <el-menu-item index="/assistant">客服</el-menu-item>
        <el-menu-item v-if="admin" index="/admin/books">管理</el-menu-item>
        <el-menu-item v-if="admin" index="/admin/orders">订单</el-menu-item>
        <el-menu-item v-if="admin" index="/admin/dead-letters">死信</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="workspace">
      <el-header class="bar">
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
      <el-main class="main">
        <div :class="frameClass">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useSessionStore } from './stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { user, admin } = storeToRefs(session)
const soloCard = computed(() => route.path === '/login' || route.path === '/register' || route.path === '/login-required')
const frameClass = computed(() => {
  if (soloCard.value) return 'solo-stage'
  if (route.path === '/assistant') return 'page-card'
  return 'workbench'
})
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/admin/orders')) return '/admin/orders'
  if (path.startsWith('/admin/books')) return '/admin/books'
  if (path.startsWith('/admin/dead-letters')) return '/admin/dead-letters'
  if (path.startsWith('/orders')) return '/orders'
  if (path.startsWith('/books')) return '/'
  return path
})

function logout() {
  session.clear()
  router.push('/')
}
</script>

<style>
html,
body,
#app {
  margin: 0;
  min-height: 100%;
  background: #f0f2f5;
}

.shell {
  min-height: 100vh;
  background: #f0f2f5;
}

.sider {
  background: #304156;
  min-height: 100vh;
}

.brand {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  color: #fff;
  font-weight: 600;
  background: #2b2f3a;
}

.sider .el-menu {
  border-right: none;
}

.sider .el-menu-item.is-active,
.sider .el-menu-item:hover {
  background-color: #263445 !important;
}

.workspace {
  min-width: 0;
}

.bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 16px;
}

.account {
  display: flex;
  align-items: center;
  gap: 8px;
}

.main {
  background: #f0f2f5;
  padding: 16px 20px 24px;
}

.page-card {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  padding: 20px 24px 24px;
}

.workbench h1 {
  margin: 0 0 8px;
  padding: 2px 0 2px 12px;
  border-left: 4px solid #409eff;
  font-size: 18px;
  line-height: 1.4;
}

.workbench h1 + p {
  margin-top: 0;
  margin-bottom: 16px;
  color: #909399;
}

.workbench .el-form--inline {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 16px 16px 0;
  margin-bottom: 16px;
}

.workbench .el-menu-item.is-active {
  background: #ecf5ff;
}

.workbench .el-table {
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.workbench .el-table th.el-table__cell {
  background: #f5f7fa !important;
  color: #606266;
}

.solo-stage .el-card {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
</style>
