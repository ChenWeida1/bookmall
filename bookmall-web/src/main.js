import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api'
import './styles.css'

setUnauthorizedHandler(() => {
  const current = router.currentRoute.value
  if (current.path === '/login' || current.path === '/login-required' || current.path === '/register') {
    return
  }
  router.push({ path: '/login-required', query: { next: current.fullPath } })
})

createApp(App).use(router).mount('#app')
