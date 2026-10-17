import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from './stores/session'
import Home from './views/Home.vue'
import BookDetail from './views/BookDetail.vue'
import Login from './views/Login.vue'
import Register from './views/Register.vue'
import LoginRequired from './views/LoginRequired.vue'
import Cart from './views/Cart.vue'
import Orders from './views/Orders.vue'
import OrderDetail from './views/OrderDetail.vue'
import Notifications from './views/Notifications.vue'
import AdminBooks from './views/AdminBooks.vue'
import AdminOrders from './views/AdminOrders.vue'
import AdminDeadLetters from './views/AdminDeadLetters.vue'
import Assistant from './views/Assistant.vue'

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
    { path: '/notifications', component: Notifications, meta: { auth: true } },
    { path: '/assistant', component: Assistant, meta: { auth: true } },
    { path: '/admin/books', component: AdminBooks, meta: { auth: true, admin: true } },
    { path: '/admin/orders', component: AdminOrders, meta: { auth: true, admin: true } },
    { path: '/admin/dead-letters', component: AdminDeadLetters, meta: { auth: true, admin: true } }
  ]
})

router.beforeEach((to) => {
  const session = useSessionStore()
  if (to.meta.auth && !session.user) {
    return { path: '/login-required', query: { next: to.fullPath } }
  }
  if (to.meta.admin && !session.admin) {
    return { path: '/' }
  }
  return true
})

export default router
