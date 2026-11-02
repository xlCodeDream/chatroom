<script setup lang="ts">
import { ref, computed } from 'vue'
import { useWsLoginStore } from '@/stores/ws'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useGroupStore } from '@/stores/group'
import { useEmojiStore } from '@/stores/emoji'
import { ElMessage } from 'element-plus'

import apis from '@/services/apis'
import { OnlineEnum } from '@/enums'
import wsIns from '@/utils/websocket'

const loginStore = useWsLoginStore()
const userStore = useUserStore()
const chatStore = useChatStore()
const groupStore = useGroupStore()
const emojiStore = useEmojiStore()

const visible = computed({
  get() {
    return loginStore.showLogin
  },
  set(value) {
    loginStore.showLogin = value
    if (!value) {
      resetForm()
    }
  },
})

const loading = ref(false)
const form = ref({
  name: '',
  password: '',
})

function resetForm() {
  form.value = { name: '', password: '' }
  loading.value = false
}

async function handlePasswordLogin() {
  if (!form.value.name || !form.value.password) {
    return
  }
  loading.value = true
  try {
    const token = await apis.login(form.value).send()
    await handleLoginSuccess(token)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!form.value.name || !form.value.password) {
    return
  }
  loading.value = true
  try {
    await apis.register(form.value).send()
    visible.value = false
  } finally {
    loading.value = false
  }
}

async function handleLoginSuccess(token: string) {
  if (!token) {
    console.warn('登录失败：后端返回的 token 为空')
    ElMessage.error('登录失败，请重试')
    return
  }
  sessionStorage.setItem('TOKEN', token)
  userStore.isSign = true
  await userStore.getUserDetailAction()
  if (!userStore.isSign) {
    return
  }
  loginStore.showLogin = false
  wsIns.initConnect()
  groupStore.batchUpdateUserStatus([
    {
      activeStatus: OnlineEnum.ONLINE,
      avatar: userStore.userInfo.avatar || '',
      lastOptTime: Date.now(),
      name: userStore.userInfo.name || '',
      uid: userStore.userInfo.uid || 0,
    },
  ])
  chatStore.getSessionList(true)
  emojiStore.getEmojiList()
}
</script>

<template>
  <ElDialog class="login-box-modal" :width="376" v-model="visible" center>
    <div class="login-box">
      <img class="login-logo" src="@/assets/logo1.jpeg" alt="ChatRoom" />
      <!-- <p class="login-slogan">边聊边买，岂不快哉~</p> -->

      <div class="login-form">
        <ElInput
          v-model="form.name"
          placeholder="用户名"
          class="login-input"
          @keyup.enter="handlePasswordLogin"
        />
        <ElInput
          v-model="form.password"
          type="password"
          placeholder="密码"
          class="login-input"
          @keyup.enter="handlePasswordLogin"
        />
        <div class="login-buttons">
          <ElButton
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="handlePasswordLogin"
          >
            登录
          </ElButton>
          <ElButton class="login-btn register-btn" @click="handleRegister"> 注册 </ElButton>
        </div>
      </div>
    </div>
  </ElDialog>
</template>

<style lang="scss" src="./styles.scss" scoped />
