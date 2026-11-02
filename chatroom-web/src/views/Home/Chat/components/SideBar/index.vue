<script setup lang="ts">
import { computed, onBeforeMount } from 'vue'
import { ElMessage } from 'element-plus'
import ContextMenu from '@imengyu/vue3-context-menu'
import { useChatStore } from '@/stores/chat'
import { useGlobalStore } from '@/stores/global'
import { useCachedStore } from '@/stores/cached'
import { useUserStore } from '@/stores/user'
import { IsAllUserEnum } from '@/services/types'
import { MsgEnum, RoomTypeEnum } from '@/enums'
import { useUserInfo } from '@/hooks/useCached'
import { formatTimestamp } from '@/utils/computedTime'
import renderReplyContent from '@/utils/renderReplyContent'

const chatStore = useChatStore()
const globalStore = useGlobalStore()
const cachedStore = useCachedStore()
const userStore = useUserStore()

onBeforeMount(() => {
  // 请求回话列表
  chatStore.getSessionList()
})

// 选中的聊天对话
const currentSession = computed(() => globalStore.currentSession)

/**
 * 推导单聊 session 的「对端用户 uid」：
 * 1. 优先从该会话的最后一条消息中找到"非我"的发送者；
 * 2. 其次从 cachedStore.userCachedList 按会话名做匹配；
 * 3. 都拿不到则返回 undefined（此时退回到 item.avatar/name 本身）。
 */
const resolveSinglePeerUid = (item: any): number | undefined => {
  const myUid = userStore.userInfo.uid
  const msgs = Array.from(chatStore.messageMap.get(item.roomId)?.values() || [])
  // 从消息里先挑非我发送者
  const peerMsg = msgs.find((m: any) => m.fromUser.uid !== myUid)
  if (peerMsg) return peerMsg.fromUser.uid as number
  // 兜底：从用户缓存中按名称匹配（userCachedList 是 reactive，访问即收集依赖）
  const entries = Object.values(cachedStore.userCachedList)
  const hit = entries.find((u: any) => u?.name === item.name)
  if (hit) return (hit as any).uid as number
  return undefined
}

const sessionList = computed(() =>
  chatStore.sessionList.map((item) => {
    // 最后一条消息内容
    const lastMsg = Array.from(chatStore.messageMap.get(item.roomId)?.values() || [])?.slice(
      -1,
    )?.[0]
    let LastUserMsg = ''
    if (lastMsg) {
      const lastMsgSenderInfo = cachedStore.userCachedList[lastMsg.fromUser.uid] || {}
      const senderName =
        (lastMsgSenderInfo as any)?.name || (lastMsg.fromUser as any)?.username || ''
      LastUserMsg =
        lastMsg.message?.type === MsgEnum.RECALL
          ? `${senderName}:'撤回了一条消息'`
          : renderReplyContent(
              senderName,
              lastMsg.message?.type,
              lastMsg.message?.body?.content || lastMsg.message?.body,
            )
    }
    // 单聊类型：尝试用对端 uid 从缓存拿最新头像/昵称做覆盖，
    // 让 USER_INFO_CHANGE 推送触发 userCachedList 更新后，左侧列表也立即同步。
    let finalAvatar = item.avatar
    let finalName = item.name
    if (item.type === RoomTypeEnum.Single) {
      const peerUid = resolveSinglePeerUid(item)
      if (peerUid !== undefined) {
        // 触发拉取（如果缓存里没有这个 uid）
        useUserInfo(peerUid)
        const peerInfo = cachedStore.userCachedList[peerUid]
        if (peerInfo) {
          finalAvatar = (peerInfo as any).avatar || item.avatar
          finalName = (peerInfo as any).name || item.name
        }
      }
    }
    return {
      ...item,
      avatar: finalAvatar,
      name: finalName,
      tag: item.hot_Flag === IsAllUserEnum.Yes ? '官方' : '',
      lastMsg: LastUserMsg || item.text || '欢迎使用ChatRoom',
      lastMsgTime: formatTimestamp(item?.activeTime),
    }
  }),
)
// 选中会话
const onSelectSelectSession = (roomId: number, roomType: RoomTypeEnum) => {
  globalStore.currentSession.roomId = roomId
  globalStore.currentSession.type = roomType
}

// 右键菜单
const onContextMenu = (e: MouseEvent, item: { roomId: number; type: RoomTypeEnum }) => {
  e.preventDefault()
  ContextMenu.showContextMenu({
    x: e.x,
    y: e.y,
    theme: 'dark',
    items: [
      {
        label: '隐藏该会话',
        onClick: () => {
          // 如果隐藏的是当前会话，先切换到列表中的其他会话
          if (globalStore.currentSession.roomId === item.roomId) {
            const nextSession = chatStore.sessionList.find((s) => s.roomId !== item.roomId)
            if (nextSession) {
              globalStore.currentSession.roomId = nextSession.roomId
              globalStore.currentSession.type = nextSession.type
            }
          }
          chatStore.removeContact(item.roomId)
          ElMessage.success('已隐藏该会话')
        },
      },
      {
        label: '清空聊天记录',
        onClick: () => {
          chatStore.clearMsgList(item.roomId)
          ElMessage.success('已清空聊天记录')
        },
      },
    ],
  })
}

// 加载更多
const load = () => {
  chatStore.getSessionList()
}
</script>

<template>
  <ul class="chat-message" v-infinite-scroll="load" :infinite-scroll-immediate="false">
    <li
      v-for="(item, index) in sessionList"
      :key="index"
      :data-room-id="item.roomId"
      :class="['chat-message-item', { active: currentSession.roomId === item.roomId }]"
      @click="onSelectSelectSession(item.roomId, item.type)"
      @contextmenu="onContextMenu($event, item)"
    >
      <div class="item-wrapper">
        <el-badge :value="item.unreadCount" :max="999" :hidden="item.unreadCount < 1" class="item">
          <el-avatar shape="circle" :size="38" :src="item.avatar" />
        </el-badge>
        <div class="message-info">
          <div style="white-space: nowrap">
            <span class="person">{{ item.name }}</span>
            <span v-if="item.tag" class="tag">{{ item.tag }}</span>
          </div>
          <div class="message-message">{{ item.lastMsg }}</div>
        </div>
        <span class="message-time">{{ item.lastMsgTime }}</span>
      </div>
    </li>
  </ul>
</template>

<style lang="scss" src="./styles.scss" scoped />
