<script setup lang="ts">
import { useChatStore } from '@/stores/chat'
import { useContactStore } from '@/stores/contacts'
import { useUserStore } from '@/stores/user'
import { useGlobalStore } from '@/stores/global'
import { useCachedStore } from '@/stores/cached'
import { useGroupStore } from '@/stores/group'
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SettingBox from '@/views/Home/Chat/components/ChatList/RoomName/components/SettingBox/SettingBox.vue'

const chatStore = useChatStore()
const contactStore = useContactStore()
const userStore = useUserStore()
const globalStore = useGlobalStore()
const cachedStore = useCachedStore()
const groupStore = useGroupStore()
const isShowSetting = ref<boolean>(false)

// 调试：打印当前 roomId
const currentRoomId = computed(() => {
  const rid = globalStore.currentSession.roomId
  console.log('==== [RoomName] currentRoomId ====')
  console.log('[RoomName] globalStore.currentSession:', globalStore.currentSession)
  console.log('[RoomName] roomId:', rid)
  console.log('[RoomName] currentSessionInfo:', chatStore.currentSessionInfo)
  return rid
})

// 当前用户是否为群主
const isGroupOwner = computed(
  () => groupStore.currentLordId === userStore.userInfo.uid && !!userStore.userInfo.uid,
)

// 退出/解散群聊
const onExitGroup = () => {
  const roomId = chatStore.currentSessionInfo?.roomId
  if (!roomId) {
    ElMessage.warning('无法获取群聊信息')
    return
  }
  const tipText = isGroupOwner.value ? '确认解散该群聊？解散后将无法恢复' : '确认退出该群聊？'
  ElMessageBox.confirm(tipText, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await groupStore.exitGroup()
      ElMessage.success(isGroupOwner.value ? '解散成功!' : '退出成功!')
    })
    .catch(() => {
      //
    })
}

// 单聊时获取对方 uid（从联系人列表匹配，而非消息列表）
const friendUid = computed(() => {
  const myUid = userStore.userInfo.uid
  const sessionName = chatStore.currentSessionInfo?.name
  // 优先从联系人列表按会话名匹配
  const contact = contactStore.contactsList.find((c) => {
    const cachedUser = cachedStore.userCachedList[c.uid]
    return cachedUser?.name === sessionName
  })
  if (contact) {
    console.log('[friendUid] matched from contactList by name:', contact.uid)
    return contact.uid
  }
  // 备选：从消息列表中找非自己的 uid（可能取不到）
  const list = chatStore.chatMessageList
  const msg = list.find((m) => m.fromUser.uid !== myUid)
  console.log('[friendUid] fallback from msg list:', msg?.fromUser.uid, 'list len:', list.length)
  return msg?.fromUser.uid
})

const onDeleteFriend = () => {
  console.log('[onDeleteFriend debug] friendUid.value:', friendUid.value)
  console.log('[onDeleteFriend debug] currentSessionInfo:', chatStore.currentSessionInfo)
  if (!friendUid.value) {
    ElMessage.warning('无法获取对方信息')
    return
  }
  ElMessageBox.confirm('确认删除该好友？删除后将无法恢复', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await contactStore.onDeleteContact(friendUid.value!)
      // 从会话列表移除当前会话
      const roomId = chatStore.currentSessionInfo?.roomId
      if (roomId) {
        chatStore.removeContact(roomId)
      }
      // 切换到会话列表第一个
      if (chatStore.sessionList.length > 0) {
        globalStore.currentSession.roomId = chatStore.sessionList[0].roomId
        globalStore.currentSession.type = chatStore.sessionList[0].type
      } else {
        globalStore.currentSession.roomId = 0
        globalStore.currentSession.type = 0
      }
      ElMessage.success('删除成功!')
    })
    .catch(() => {
      //
    })
}
</script>

<template>
  <div class="room-name">
    <span class="name">{{ chatStore.currentSessionInfo?.name }}</span>
    <!-- 显示房间ID（调试版：用显眼样式排查是否渲染） -->
    <span class="room-id" style="background: red; color: #fff; padding: 2px 6px; border-radius: 4px; opacity: 1; font-size: 14px;">ID: {{ currentRoomId }}</span>
    <!-- 群聊 -->
    <template v-if="chatStore.isGroup">
      <span
        class="setting"
        @click="isShowSetting = true"
        v-if="!chatStore.currentSessionInfo?.hot_Flag"
      >
        设置
      </span>
      <!-- 群聊省略号菜单：群主显示解散群聊，普通成员显示退出群聊 -->
      <el-dropdown trigger="click" @command="onExitGroup">
        <span class="setting">
          <Icon icon="more" :size="20" />
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="exit">
              <el-text type="danger">{{ isGroupOwner ? '解散群聊' : '退出群聊' }}</el-text>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </template>
    <!-- 单聊时显示省略号菜单 -->
    <el-dropdown v-else trigger="click" @command="(cmd: string) => cmd === 'delete' && onDeleteFriend()">
      <span class="setting">
        <Icon icon="more" :size="20" />
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="delete">
            <el-text type="danger">删除好友</el-text>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
  <el-drawer v-model="isShowSetting" direction="rtl" append-to-body>
    <template #header>
      <h4 class="text-[#f5f5f5]">设置</h4>
    </template>
    <template #default>
      <SettingBox />
    </template>
  </el-drawer>
</template>

<style lang="scss" src="./styles.scss">
.el-drawer__header {
  margin-bottom: 0 !important;
}
</style>
