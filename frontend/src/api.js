import { session, clearSession } from './auth'

/** 发送 API 请求并将后端统一响应转换成页面数据。 */
export async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }
  if (session.token) headers.Authorization = `Bearer ${session.token}`
  let response
  try {
    response = await fetch(path, { ...options, headers })
  } catch {
    throw new Error('无法连接后端，请检查网关和服务是否已启动')
  }
  let body
  try {
    body = await response.json()
  } catch {
    body = null
  }
  if (!response.ok || body?.code !== 0) {
    if (response.status === 401) clearSession()
    const fallback = response.status === 429 ? '请求过于频繁，请稍后重试' : response.status === 503 ? '服务暂时不可用，请稍后再试' : '操作失败，请重试'
    throw new Error(body?.message || fallback)
  }
  return body.data
}

/** 构造 JSON 请求选项。 */
export function jsonRequest(method, data) {
  return { method, body: JSON.stringify(data) }
}

/** 将分转成用于显示的人民币金额。 */
export function money(cents) {
  return `¥${(Number(cents || 0) / 100).toFixed(2)}`
}

/** 将订单状态转成简短中文。 */
export function statusText(status) {
  return ({ CREATING: '处理中', UNPAID: '待支付', PAID: '待提货', COMPLETED: '已完成', CANCELED: '已取消', FAILED: '下单失败' })[status] || status
}
