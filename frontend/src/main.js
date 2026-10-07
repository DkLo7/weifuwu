import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { session } from './auth'
import App from './App.vue'
import LoginPage from './pages/LoginPage.vue'
import ShopPage from './pages/ShopPage.vue'
import OrdersPage from './pages/OrdersPage.vue'
import AdminProductsPage from './pages/AdminProductsPage.vue'
import AdminOrdersPage from './pages/AdminOrdersPage.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/shop' },
    { path: '/login', component: LoginPage },
    { path: '/shop', component: ShopPage },
    { path: '/shop/orders', component: OrdersPage, meta: { auth: true } },
    { path: '/admin', component: AdminProductsPage, meta: { admin: true } },
    { path: '/admin/orders', component: AdminOrdersPage, meta: { admin: true } }
  ]
})

/** 在进入受保护页面前检查本地角色。 */
router.beforeEach((to) => {
  if ((to.meta.auth || to.meta.admin) && !session.token) return { path: '/login', query: { next: to.fullPath } }
  if (to.meta.admin && session.role !== 'ADMIN') return '/shop'
  if (to.path === '/login' && session.token) return session.role === 'ADMIN' ? '/admin' : '/shop'
})

createApp(App).use(router).mount('#app')
