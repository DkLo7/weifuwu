<script setup>
import { computed, onMounted, ref } from 'vue'
import { ScanLine, RefreshCw, ClipboardList } from 'lucide-vue-next'
import { api, jsonRequest, money, statusText } from '../api'

const orders = ref([])
const filter = ref('ALL')
const pickupCode = ref('')
const busy = ref(false)
const loading = ref(true)
const error = ref('')
const notice = ref('')
const visibleOrders = computed(() => filter.value === 'ALL' ? orders.value : orders.value.filter((order) => order.status === filter.value))

/** 读取管理端订单列表。 */
async function load() {
  loading.value = true
  error.value = ''
  try { orders.value = await api('/api/admin/orders') } catch (cause) { error.value = cause.message } finally { loading.value = false }
}

/** 提交提货码并刷新订单状态。 */
async function pickup() {
  if (!pickupCode.value.trim()) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try { await api('/api/admin/pickups', jsonRequest('POST', { pickupCode: pickupCode.value.trim() })); pickupCode.value = ''; notice.value = '核销成功'; await load() } catch (cause) { error.value = cause.message } finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page-container wide">
    <div class="page-heading"><div><span class="section-kicker">ORDER FULFILLMENT</span><h1>订单与核销</h1><p>核对提货码，完成社区自提。</p></div><button type="button" class="icon-button bordered" title="刷新订单" aria-label="刷新订单" @click="load"><RefreshCw :size="18" /></button></div>
    <form class="pickup-bar" @submit.prevent="pickup"><ScanLine :size="21" /><label for="pickup-code">提货码核销</label><input id="pickup-code" v-model.trim="pickupCode" required maxlength="12" placeholder="输入用户提货码"><button class="button primary" :disabled="busy">{{ busy ? '核销中…' : '确认核销' }}</button></form>
    <div v-if="error" class="notice error" role="alert">{{ error }}</div><div v-if="notice" class="notice success">{{ notice }}</div>
    <div class="list-toolbar"><div class="segmented compact"><button v-for="option in [{ value:'ALL', label:'全部' }, { value:'UNPAID', label:'待支付' }, { value:'PAID', label:'待提货' }, { value:'COMPLETED', label:'已完成' }]" :key="option.value" type="button" :class="{ active: filter === option.value }" @click="filter = option.value">{{ option.label }}</button></div><span class="muted small">共 {{ visibleOrders.length }} 单</span></div>
    <div v-if="loading" class="empty-state">正在加载订单…</div><div v-else-if="!visibleOrders.length" class="empty-state"><ClipboardList :size="34" /><strong>没有符合条件的订单</strong></div>
    <div v-else class="table-wrap"><table><thead><tr><th>订单 / 用户</th><th>商品</th><th>自提点</th><th>金额</th><th>提货码</th><th>状态</th></tr></thead><tbody><tr v-for="order in visibleOrders" :key="order.orderNo"><td><strong>{{ order.orderNo.slice(0, 8) }}</strong><span class="table-secondary">用户 #{{ order.userId }} · {{ order.createdAt }}</span></td><td>{{ order.productName }} × {{ order.quantity }}</td><td>{{ order.pickupPointName }}</td><td>{{ money(order.totalCents) }}</td><td><code v-if="order.pickupCode">{{ order.pickupCode }}</code><span v-else class="muted">—</span></td><td><span class="status" :class="order.status.toLowerCase()">{{ statusText(order.status) }}</span></td></tr></tbody></table></div>
  </div>
</template>
