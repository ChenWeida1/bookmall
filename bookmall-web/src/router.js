import { createRouter, createWebHistory } from 'vue-router'
import { currentUser } from './api'
import Home from './views/Home.vue'
import BookDetail from './views/BookDetail.vue'
import Login from './views/Login.vue'
import Register from './views/Register.vue'
import LoginRequired from './views/LoginRequired.vue'
import Cart from './views/Cart.vue'
import Orders from './views/Orders.vue'
import OrderDetail from './views/OrderDetail.vue'
import Notifications from './views/Notifications.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Home },
    { path: '/books/:id', component: BookDetail },
    { path: '/login', component: Login },
    { path: '/register', component: Register },
    { path: '/login-required', component: LoginRequired },
    { path: '/cart', component: Cart, meta: { auth: true } },
    { path: '/orders', component: Orders, meta: { auth: true } },
    { path: '/orders/:id', component: OrderDetail, meta: { auth: true } },
    { path: '/notifications', component: Notifications, meta: { auth: true } }
  ]
})

router.beforeEach((to) => {
  if (to.meta.auth && !currentUser()) {
    return { path: '/login-required', query: { next: to.fullPath } }
  }
  return true
})

export default router
