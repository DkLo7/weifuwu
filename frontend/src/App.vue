<script setup>
import { useRouter } from 'vue-router'
import { ShoppingBasket, ClipboardList, Package, ScanLine, LogIn, LogOut } from 'lucide-vue-next'
import { session, clearSession } from './auth'

const router = useRouter()

/** 退出当前账号并返回登录页。 */
function logout() {
  clearSession()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <router-link class="brand" to="/shop"><span class="brand-mark">邻</span><span>邻里集</span></router-link>
      <nav class="primary-nav" aria-label="主导航">
        <router-link to="/shop"><ShoppingBasket :size="18" /> 团购商品</router-link>
        <router-link v-if="session.role === 'USER'" to="/shop/orders"><ClipboardList :size="18" /> 我的订单</router-link>
        <router-link v-if="session.role === 'ADMIN'" to="/admin"><Package :size="18" /> 商品管理</router-link>
        <router-link v-if="session.role === 'ADMIN'" to="/admin/orders"><ScanLine :size="18" /> 订单核销</router-link>
      </nav>
      <div class="account">
        <span v-if="session.token" class="account-name">{{ session.username }}</span>
        <button v-if="session.token" type="button" class="icon-button" title="退出登录" aria-label="退出登录" @click="logout"><LogOut :size="18" /></button>
        <router-link v-else class="login-link" to="/login"><LogIn :size="18" /> 登录</router-link>
      </div>
    </header>
    <main class="main-content"><router-view /></main>
  </div>
</template>
