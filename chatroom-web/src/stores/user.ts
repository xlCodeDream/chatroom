import { ref } from 'vue'
import apis from '@/services/apis'
import { defineStore } from 'pinia'
import type { UserInfoType } from '@/services/types'

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<Partial<UserInfoType>>({})
  const isSign = ref(false)

  let localUserInfo = {}
  try {
    localUserInfo = JSON.parse(sessionStorage.getItem('USER_INFO') || '{}')
  } catch (error) {
    localUserInfo = {}
  }

  // 从 local读取
  if (!Object.keys(userInfo.value).length && Object.keys(localUserInfo).length) {
    userInfo.value = localUserInfo
  }

  function getUserDetailAction() {
    console.log('==== [getUserDetailAction] 开始拉取用户详情 ====')
    const tokenBefore = sessionStorage.getItem('TOKEN')
    console.log('[getUserDetailAction] 调用前 sessionStorage TOKEN:', tokenBefore ? '存在' : '不存在')
    console.log('[getUserDetailAction] 调用 apis.getUserDetail()...')
    
    const requestInstance = apis.getUserDetail()
    console.log('[getUserDetailAction] 获取到请求实例:', requestInstance)
    
    console.log('[getUserDetailAction] 调用 .send()...')
    return requestInstance
      .send()
      .then((data) => {
        console.log('[getUserDetailAction] 请求成功，返回 data:', data)
        console.log('[getUserDetailAction] 返回 avatar:', data?.avatar)
        console.log('[getUserDetailAction] 返回 id:', data?.id)
        const tokenAfter = sessionStorage.getItem('TOKEN')
        console.log('[getUserDetailAction] 成功后 sessionStorage TOKEN:', tokenAfter ? '存在' : '不存在')
        userInfo.value = { ...userInfo.value, ...data, uid: data.id }
        persistUserInfo()
        console.log('[getUserDetailAction] 赋值后 userInfo.avatar:', userInfo.value.avatar)
        console.log('[getUserDetailAction] 赋值后 userInfo:', userInfo.value)
      })

      .catch((err) => {
        console.error('[getUserDetailAction] 请求失败:', err)
        const tokenAfterError = sessionStorage.getItem('TOKEN')
        console.log('[getUserDetailAction] 失败后 sessionStorage TOKEN:', tokenAfterError ? '存在' : '不存在')
        isSign.value = false
        userInfo.value = {}
        sessionStorage.removeItem('TOKEN')
        sessionStorage.removeItem('USER_INFO')
      })
  }

  // 持久化用户信息到 sessionStorage
  function persistUserInfo() {
    console.log('[persistUserInfo] 写入前 userInfo:', userInfo.value)
    if (Object.keys(userInfo.value).length) {
      sessionStorage.setItem('USER_INFO', JSON.stringify(userInfo.value))
      const stored = sessionStorage.getItem('USER_INFO')
      console.log('[persistUserInfo] 写入后 sessionStorage USER_INFO:', stored)
    }
  }

  // 更新用户头像，立即同步内存态并持久化
  function updateAvatar(avatarUrl: string) {
    console.log('==== [updateAvatar] 开始 ====')
    console.log('[updateAvatar] 新 avatarUrl:', avatarUrl)
    console.log('[updateAvatar] 旧 userInfo.avatar:', userInfo.value.avatar)
    console.log('[updateAvatar] 旧 userInfo 引用:', userInfo.value)
    debugger
    // 整体替换对象引用，保证所有 computed/watch 路径都能触发响应式
    userInfo.value = { ...userInfo.value, avatar: avatarUrl }
    console.log('[updateAvatar] 新 userInfo 引用:', userInfo.value)
    console.log('[updateAvatar] 新 userInfo.avatar:', userInfo.value.avatar)
    persistUserInfo()
    console.log('==== [updateAvatar] 完成 ====')
    debugger
  }

  // 更新用户名，立即同步内存态并持久化
  function updateName(name: string) {
    userInfo.value = { ...userInfo.value, name }
    persistUserInfo()
  }

  // 更新个人介绍，立即同步内存态并持久化
  function updateSignature(signature: string) {
    userInfo.value = { ...userInfo.value, signature }
    persistUserInfo()
  }

  // 收到用户信息变更推送时，更新自身信息并持久化（不消耗改名次数）
  function applyUserInfoChange(data: { avatar?: string; name?: string; signature?: string }) {
    userInfo.value = {
      ...userInfo.value,
      ...(data.avatar !== undefined ? { avatar: data.avatar } : {}),
      ...(data.name !== undefined ? { name: data.name } : {}),
      ...(data.signature !== undefined ? { signature: data.signature } : {}),
    }
    persistUserInfo()
  }

  return { userInfo, isSign, getUserDetailAction, updateAvatar, updateName, updateSignature, applyUserInfoChange }
})
