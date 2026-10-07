<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { MapPin, ShoppingCart, Minus, Plus, PackageOpen, ArrowRight } from 'lucide-vue-next'
import { api, jsonRequest, money } from '../api'
import { session } from '../auth'

const router = useRouter()
const products = ref([])
const points = ref([])
const selected = ref(null)
const clientKey = ref('')
const quantity = ref(1)
const pickupPointId = ref('')
const busy = ref(false)
const error = ref('')
const loading = ref(true)

/** 加载商品及自提点列表。 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[products.value, points.value] = await Promise.all([api('/api/products'), api('/api/pickup-points')])
    if (points.value.length) pickupPointId.value = String(points.value[0].id)
  } catch (cause) {
    error.value = cause.message
  } finally {
    loading.value = false
  }
}

/** 打开购买面板并重置数量。 */
function choose(product) {
  if (!session.token) return router.push({ path: '/login', query: { next: '/shop' } })
  selected.value = product
  clientKey.value = crypto.randomUUID()
  quantity.value = 1
  error.value = ''
}

/** 创建订单并跳转至订单列表。 */
async function checkout() {
  if (!selected.value || !pickupPointId.value) return
  busy.value = true
  error.value = ''
  try {
    await api('/api/orders', jsonRequest('POST', { productId: selected.value.id, quantity: quantity.value, pickupPointId: Number(pickupPointId.value), clientKey: clientKey.value }))
    selected.value = null
    router.push('/shop/orders')
  } catch (cause) {
    error.value = cause.message
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-heading"><div><span class="section-kicker">NEIGHBORHOOD MARKET</span><h1>今日团购</h1><p>精选社区好物，选好后到点自提。</p></div><router-link v-if="session.role === 'USER'" class="text-link" to="/shop/orders">查看我的订单 <ArrowRight :size="17" /></router-link></div>
    <div v-if="error && !selected" class="notice error" role="alert">{{ error }} <button type="button" @click="load">重试</button></div>
    <div v-if="loading" class="empty-state">正在加载商品…</div>
    <div v-else-if="!products.length" class="empty-state"><PackageOpen :size="34" /><strong>暂无在售商品</strong><span>管理员上架商品后将在这里显示。</span></div>
    <div v-else class="product-grid">
      <article v-for="product in products" :key="product.id" class="product-card">
        <div class="product-image"><img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" loading="lazy"><PackageOpen v-else :size="38" /></div>
        <div class="product-info"><h2>{{ product.name }}</h2><p>{{ product.description || '社区精选好物' }}</p><div class="product-bottom"><div><strong>{{ money(product.priceCents) }}</strong><span>剩余 {{ product.availableStock }} 件</span></div><button v-if="session.role !== 'ADMIN'" type="button" class="icon-button dark" title="购买商品" :aria-label="`购买${product.name}`" :disabled="product.availableStock < 1" @click="choose(product)"><ShoppingCart :size="18" /></button></div></div>
      </article>
    </div>
    <div v-if="selected" class="modal-backdrop" @click.self="selected = null"><section class="modal" role="dialog" aria-modal="true" aria-label="确认购买"><div class="modal-head"><h2>确认订单</h2><button type="button" class="icon-button" title="关闭" aria-label="关闭" @click="selected = null">×</button></div><h3>{{ selected.name }}</h3><p class="muted">{{ money(selected.priceCents) }} / 件</p><div class="field-row"><span>购买数量</span><div class="stepper"><button type="button" title="减少数量" :disabled="quantity <= 1" @click="quantity--"><Minus :size="16" /></button><strong>{{ quantity }}</strong><button type="button" title="增加数量" :disabled="quantity >= Math.min(selected.availableStock, 99)" @click="quantity++"><Plus :size="16" /></button></div></div><label class="form-field"><span><MapPin :size="16" /> 自提点</span><select v-model="pickupPointId"><option v-for="point in points" :key="point.id" :value="String(point.id)">{{ point.name }} · {{ point.address }}</option></select></label><div class="order-total"><span>应付金额</span><strong>{{ money(selected.priceCents * quantity) }}</strong></div><p v-if="error" class="form-error" role="alert">{{ error }}</p><button type="button" class="button primary full" :disabled="busy || !pickupPointId" @click="checkout">{{ busy ? '正在下单…' : '提交订单' }}</button></section></div>
  </div>
</template>
