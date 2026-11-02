import { useUserStore } from '@/stores/user'
import { createAlova } from 'alova'
import GlobalFetch from 'alova/GlobalFetch'
import VueHook from 'alova/vue'
import { ElMessage } from 'element-plus'

export const alovaIns = createAlova({
  baseURL: '',
  statesHook: VueHook,
  requestAdapter: GlobalFetch(),

  beforeRequest({ config }) {
    console.log('\n========== [beforeRequest] ==========')
    console.log('[beforeRequest] config.url:', config.url)
    console.log('[beforeRequest] config.headers (原始):', JSON.stringify(config.headers))

    const token = sessionStorage.getItem('TOKEN')
    console.log('[beforeRequest] TOKEN:', token ? '存在' : '不存在')
    console.log(token ? `当前 TOKEN: ${token}` : '当前 TOKEN: 不存在')

    // 关键修复：创建全新的 headers 对象，确保每次请求都携带 token
    // 不修改原始 config.headers，而是创建新对象
    const existingHeaders = config.headers || {}
    const newHeaders: Record<string, string> = {}

    // 复制原有 headers
    if (existingHeaders) {
      for (const key in existingHeaders) {
        if (Object.prototype.hasOwnProperty.call(existingHeaders, key)) {
          newHeaders[key] = existingHeaders[key]
        }
      }
    }

    // 添加 token
    if (token) {
      newHeaders['Authorization'] = `Bearer ${token}`
    }

    // 添加 Content-Type
    newHeaders['Content-Type'] = 'application/json; charset=utf-8'

    // 赋值新 headers
    config.headers = newHeaders

    console.log('[beforeRequest] 处理后 config.headers:', JSON.stringify(config.headers))
    console.log('==========================================\n')
  },

  responsed: async (response) => {
    console.log('\n========== [] ==========')
    console.log('[] url:', response.url)
    console.log('[responsed] status:', response.status)
console.log(useUserStore().isSign)
    let json: any
    try {
      json = await response.json()
      console.log('[] 响应体:', JSON.stringify(json))
    } catch {
      json = { success: false, errMsg: '服务端响应格式错误' }
    }

    if (response.status === 401) {
      console.log('[responsed] 401 - 清除 token')
      sessionStorage.removeItem('TOKEN')
      sessionStorage.removeItem('USER_INFO')
      ElMessage.error('登录已过期，请重新登录')
      throw new Error('登录已过期')
    }

    if (response.status !== 200 || !json.success) {
      if (json.errMsg) {
        if (!sessionStorage.getItem('TOKEN') && response.status === 401) {
        } else {
          ElMessage.error(json.errMsg)
        }
        throw new Error(json.errMsg)
      } else {
        throw new Error(json.message || '请求失败')
      }
    }

    console.log('[] 请求成功')
    console.log('==========================================\n')
    return json.data
  },
})
