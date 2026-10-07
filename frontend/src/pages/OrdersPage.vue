<script setup>
import { onMounted, ref } from 'vue'
import { RefreshCw, ReceiptText, MapPin, CreditCard, XCircle, Copy } from 'lucide-vue-next'
import { api, money, statusText } from '../api'

const orders = ref([])
const loading = ref(true)
const busy = ref('')
const error = ref('')
const notice = ref('')

/** 读取当前登录用户的订单。 */
async function load() {
  loading.value = true
  error.value = ''
  try { orders.value = await api('/api/orders') } catch (cause) { error.value = cause.message } finally { loading.value = false }
}

/** 模拟支付指定订单并刷新状态。 */
async function pay(orderNo) {
  busy.value = orderNo
  error.value = ''
  try { await api(`/api/orders/${orderNo}/pay`, { method: 'POST' }); await load(); notice.value = '支付成功，请凭提货码到自提点取货' } catch (cause) { error.value = cause.message } finally { busy.value = '' }
}

/** 取消指定待支付订单。 */
async function cancel(orderNo) {
  if (!window.confirm('确定取消这个订单吗？')) return
  busy.value = orderNo
  error.value = ''
  try { await api(`/api/orders/${orderNo}/cancel`, { method: 'POST' }); await load(); notice.value = '订单已取消' } catch (cause) { error.value = cause.message } finally { busy.value = '' }
}

/** 复制提货码。 */
async function copyCode(code) {
  try { await navigator.clipboard.writeText(code); notice.value = '提货码已复制' } catch { notice.value = '复制失败，请手动记录提货码' }
}

onMounted(load)
</script>

<template>
  <div class="page-container narrow">
    <div class="page-heading"><div><span class="section-kicker">MY ORDERS</span><h1>我的订单</h1><p>查看支付状态与自提信息。</p></div><button type="button" class="icon-button bordered" title="刷新订单" aria-label="刷新订单" @click="load"><RefreshCw :size="18" /></button></div>
    <div v-if="error" class="notice error" role="alert">{{ error }}</div><div v-if="notice" class="notice success">{{ notice }}</div>
    <div v-if="loading" class="empty-state">正在加载订单…</div>
    <div v-else-if="!orders.length" class="empty-state"><ReceiptText :size="34" /><strong>还没有订单</strong><router-link class="text-link" to="/shop">去看看团购商品</router-link></div>
    <div v-else class="order-list"><article v-for="order in orders" :key="order.orderNo" class="order-item"><div class="order-item-top"><div><span class="order-number">订单 {{ order.orderNo.slice(0, 8) }}</span><span class="muted small">{{ order.createdAt }}</span></div><span class="status" :class="order.status.toLowerCase()">{{ statusText(order.status) }}</span></div><div class="order-item-body"><div><h2>{{ order.productName }}</h2><p>数量 {{ order.quantity }} · 单价 {{ money(order.unitPriceCents) }}</p><p class="pickup-place"><MapPin :size="15" /> {{ order.pickupPointName }}</p></div><strong class="price">{{ money(order.totalCents) }}</strong></div><div v-if="order.pickupCode" class="pickup-code"><span>提货码</span><strong>{{ order.pickupCode }}</strong><button type="button" class="icon-button" title="复制提货码" aria-label="复制提货码" @click="copyCode(order.pickupCode)"><Copy :size="17" /></button></div><div v-if="order.status === 'UNPAID'" class="order-actions"><span class="muted small">请在 {{ order.expiresAt }} 前支付</span><button type="button" class="button secondary" :disabled="busy === order.orderNo" @click="cancel(order.orderNo)"><XCircle :size="16" /> 取消</button><button type="button" class="button primary" :disabled="busy === order.orderNo" @click="pay(order.orderNo)"><CreditCard :size="16" /> 模拟支付</button></div></article></div>
  </div>
</template>
