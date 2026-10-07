<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, jsonRequest } from '../api'
import { setSession } from '../auth'

const route = useRoute()
const router = useRouter()
const mode = ref('login')
const username = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

/** 提交登录或注册信息并进入对应页面。 */
async function submit() {
  busy.value = true
  error.value = ''
  try {
    const result = await api(`/api/auth/${mode.value}`, jsonRequest('POST', { username: username.value, password: password.value }))
    setSession(result)
    router.push(typeof route.query.next === 'string' ? route.query.next : result.role === 'ADMIN' ? '/admin' : '/shop')
  } catch (cause) {
    error.value = cause.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="auth-layout">
    <div class="auth-intro"><span class="section-kicker">社区团购 · 到点自提</span><h1>新鲜好物，<br>就在家门口。</h1><p>选购邻里好物，在熟悉的自提点轻松领取。</p></div>
    <div class="auth-panel">
      <div class="segmented"><button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'; error = ''">登录</button><button type="button" :class="{ active: mode === 'register' }" @click="mode = 'register'; error = ''">注册</button></div>
      <h2>{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
      <form @submit.prevent="submit">
        <label>用户名<input v-model.trim="username" required minlength="3" maxlength="40" autocomplete="username" placeholder="请输入用户名"></label>
        <label>密码<input v-model="password" required minlength="6" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="至少 6 位"></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="button primary full" :disabled="busy">{{ busy ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录' }}</button>
      </form>
    </div>
  </section>
</template>
