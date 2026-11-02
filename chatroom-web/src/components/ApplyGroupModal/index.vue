<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRequest } from 'alova'
import apis from '@/services/apis'
import { ElMessage } from 'element-plus'
import { useGlobalStore } from '@/stores/global'
import { useChatStore } from '@/stores/chat'
import { judgeClient } from '@/utils/detectDevice'

const client = judgeClient()

const globalStore = useGlobalStore()
const chatStore = useChatStore()
const requestMsg = ref()
const inputRoomId = ref<number | undefined>(undefined)

// 是否已预置 roomId（从群设置入口进入时）
const isRoomIdPreset = computed(() => globalStore.applyGroupModalInfo.roomId !== undefined)
const targetRoomId = computed(() =>
  isRoomIdPreset.value ? globalStore.applyGroupModalInfo.roomId! : inputRoomId.value,
)

const { send, loading } = useRequest(apis.applyGroup, { immediate: false })
const show = computed(() => globalStore.applyGroupModalInfo.show)
const close = () => {
  globalStore.applyGroupModalInfo.show = false
  globalStore.applyGroupModalInfo.roomId = undefined
  inputRoomId.value = undefined
  requestMsg.value = ''
}
const onSend = async () => {
  console.log('==== [ApplyGroupModal] onSend 开始 ====')
  console.log('[ApplyGroupModal] targetRoomId:', targetRoomId.value)
  console.log('[ApplyGroupModal] isRoomIdPreset:', isRoomIdPreset.value)
  console.log('[ApplyGroupModal] applyGroupModalInfo:', globalStore.applyGroupModalInfo)
  console.log('[ApplyGroupModal] inputRoomId:', inputRoomId.value)
  console.log('[ApplyGroupModal] requestMsg:', requestMsg.value)
  if (!targetRoomId.value) {
    console.warn('[ApplyGroupModal] roomId 为空，退出')
    ElMessage.warning('请输入群ID')
    return
  }
  const params = { msg: requestMsg.value, roomId: targetRoomId.value }
  console.log('[ApplyGroupModal] 发送请求参数:', params)
  console.log('[ApplyGroupModal] 请求 URL: POST /capi/room/group/apply')
  debugger
  try {
    await send(params)
    console.log('[ApplyGroupModal] 请求成功 ✅')
    ElMessage.success('申请已发送，等待群主审批~')
    // 发送申请成功后，主动刷新会话列表（兜底：后端可能不推 NewFriendSession 通知）
    // 延迟 2 秒让后端有时间处理审批，再刷新列表
    console.log('[ApplyGroupModal] 2秒后刷新会话列表...')
    setTimeout(() => {
      console.log('[ApplyGroupModal] 刷新会话列表')
      chatStore.getSessionList(true)
    }, 2000)
    close()
  } catch (error: any) {
    console.error('==== [ApplyGroupModal] 请求失败 ❌ ====')
    console.error('[ApplyGroupModal] error:', error)
    console.error('[ApplyGroupModal] error.message:', error?.message)
    console.error('[ApplyGroupModal] error.stack:', error?.stack)
    debugger
  }
}
</script>

<template>
  <ElDialog
    class="setting-box-modal"
    :model-value="show"
    :width="client === 'PC' ? 350 : '50%'"
    :close-on-click-modal="false"
    center
    :show-close="false"
    @close="close"
  >
    <ElInput
      v-if="!isRoomIdPreset"
      v-model="inputRoomId"
      type="number"
      placeholder="请输入群ID"
      style="margin-bottom: 12px"
    />
    <ElInput
      :autosize="{ minRows: 4, maxRows: 4 }"
      :maxlength="50"
      type="textarea"
      resize="none"
      v-model="requestMsg"
      placeholder="发条申请消息吧~"
    />
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" @click="onSend" :loading="loading">发送</el-button>
      </span>
    </template>
  </ElDialog>
</template>

<style lang="scss" src="../AddFriendModal/styles.scss" scoped />
