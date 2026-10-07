import { reactive } from 'vue'

const saved = JSON.parse(localStorage.getItem('groupbuy-session') || '{}')
export const session = reactive({ token: saved.token || '', username: saved.username || '', role: saved.role || '' })

/** 保存当前登录信息供页面和接口调用。 */
export function setSession(data) {
  Object.assign(session, data)
  localStorage.setItem('groupbuy-session', JSON.stringify(data))
}

/** 清除本机登录状态。 */
export function clearSession() {
  Object.assign(session, { token: '', username: '', role: '' })
  localStorage.removeItem('groupbuy-session')
}
