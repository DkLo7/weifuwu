<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Pencil, Package, RefreshCw, SlidersHorizontal } from 'lucide-vue-next'
import { api, jsonRequest, money } from '../api'

const products = ref([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const editing = ref(null)
const stockProduct = ref(null)
const stockDelta = ref(1)
const busy = ref(false)
const form = reactive({ name: '', description: '', imageUrl: '', priceYuan: '', initialStock: 0, enabled: true })

/** 加载包含下架商品的管理列表。 */
async function load() {
  loading.value = true
  error.value = ''
  try { products.value = await api('/api/admin/products') } catch (cause) { error.value = cause.message } finally { loading.value = false }
}

/** 打开新建商品表单。 */
function create() {
  editing.value = { id: null }
  Object.assign(form, { name: '', description: '', imageUrl: '', priceYuan: '', initialStock: 0, enabled: true })
  error.value = ''
}

/** 用现有商品资料填充编辑表单。 */
function edit(product) {
  editing.value = product
  Object.assign(form, { name: product.name, description: product.description || '', imageUrl: product.imageUrl || '', priceYuan: (product.priceCents / 100).toFixed(2), initialStock: 0, enabled: Boolean(product.enabled) })
  error.value = ''
}

/** 保存新建或修改的商品。 */
async function save() {
  const cents = Math.round(Number(form.priceYuan) * 100)
  if (!Number.isFinite(cents) || cents <= 0) { error.value = '请输入正确的价格'; return }
  busy.value = true
  error.value = ''
  try {
    const data = { name: form.name, description: form.description, imageUrl: form.imageUrl, priceCents: cents, initialStock: Number(form.initialStock), enabled: form.enabled }
    await api(editing.value.id ? `/api/admin/products/${editing.value.id}` : '/api/admin/products', jsonRequest(editing.value.id ? 'PUT' : 'POST', data))
    editing.value = null
    notice.value = '商品已保存'
    await load()
  } catch (cause) { error.value = cause.message } finally { busy.value = false }
}

/** 提交库存增量调整。 */
async function adjustStock() {
  if (!stockProduct.value || !Number.isInteger(Number(stockDelta.value)) || Number(stockDelta.value) === 0) { error.value = '请输入非零整数'; return }
  busy.value = true
  error.value = ''
  try {
    await api(`/api/admin/products/${stockProduct.value.id}/stock`, jsonRequest('POST', { delta: Number(stockDelta.value) }))
    stockProduct.value = null
    notice.value = '库存已更新'
    await load()
  } catch (cause) { error.value = cause.message } finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page-container wide">
    <div class="page-heading"><div><span class="section-kicker">CATALOG MANAGEMENT</span><h1>商品管理</h1><p>管理商品信息、上下架状态和可售库存。</p></div><div class="heading-actions"><button type="button" class="icon-button bordered" title="刷新商品" aria-label="刷新商品" @click="load"><RefreshCw :size="18" /></button><button type="button" class="button primary" @click="create"><Plus :size="17" /> 新建商品</button></div></div>
    <div v-if="error && !editing && !stockProduct" class="notice error" role="alert">{{ error }}</div><div v-if="notice" class="notice success">{{ notice }}</div>
    <div v-if="loading" class="empty-state">正在加载商品…</div><div v-else-if="!products.length" class="empty-state"><Package :size="34" /><strong>还没有商品</strong><span>创建第一个团购商品，用户端就能看到它。</span></div>
    <div v-else class="table-wrap"><table><thead><tr><th>商品</th><th>售价</th><th>可售库存</th><th>状态</th><th class="align-right">操作</th></tr></thead><tbody><tr v-for="product in products" :key="product.id"><td><div class="table-product"><div class="table-thumb"><img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name"><Package v-else :size="20" /></div><div><strong>{{ product.name }}</strong><span>{{ product.description || '暂无描述' }}</span></div></div></td><td>{{ money(product.priceCents) }}</td><td>{{ product.availableStock }}</td><td><span class="status" :class="product.enabled ? 'paid' : 'canceled'">{{ product.enabled ? '已上架' : '已下架' }}</span></td><td><div class="table-actions"><button type="button" class="icon-button bordered" title="调整库存" aria-label="调整库存" @click="stockProduct = product; stockDelta = 1; error = ''"><SlidersHorizontal :size="16" /></button><button type="button" class="icon-button bordered" title="编辑商品" aria-label="编辑商品" @click="edit(product)"><Pencil :size="16" /></button></div></td></tr></tbody></table></div>
    <div v-if="editing" class="modal-backdrop" @click.self="editing = null"><section class="modal" role="dialog" aria-modal="true" aria-label="商品编辑"><div class="modal-head"><h2>{{ editing.id ? '编辑商品' : '新建商品' }}</h2><button type="button" class="icon-button" title="关闭" aria-label="关闭" @click="editing = null">×</button></div><form class="stack-form" @submit.prevent="save"><label>商品名称<input v-model.trim="form.name" required maxlength="100"></label><label>商品描述<textarea v-model.trim="form.description" maxlength="500" rows="3"></textarea></label><label>图片地址<input v-model.trim="form.imageUrl" type="url" placeholder="https://..."></label><div class="form-columns"><label>售价（元）<input v-model="form.priceYuan" required type="number" min="0.01" step="0.01"></label><label v-if="!editing.id">初始库存<input v-model.number="form.initialStock" required type="number" min="0"></label></div><label v-if="editing.id" class="checkbox-line"><input v-model="form.enabled" type="checkbox"> 上架销售</label><p v-if="error" class="form-error" role="alert">{{ error }}</p><button class="button primary full" :disabled="busy">{{ busy ? '正在保存…' : '保存商品' }}</button></form></section></div>
    <div v-if="stockProduct" class="modal-backdrop" @click.self="stockProduct = null"><section class="modal small-modal" role="dialog" aria-modal="true" aria-label="调整库存"><div class="modal-head"><h2>调整库存</h2><button type="button" class="icon-button" title="关闭" aria-label="关闭" @click="stockProduct = null">×</button></div><p class="muted">{{ stockProduct.name }} · 当前可售 {{ stockProduct.availableStock }} 件</p><form class="stack-form" @submit.prevent="adjustStock"><label>调整数量<input v-model.number="stockDelta" required type="number" step="1" placeholder="增加填正数，减少填负数"></label><p v-if="error" class="form-error" role="alert">{{ error }}</p><button class="button primary full" :disabled="busy">确认调整</button></form></section></div>
  </div>
</template>
