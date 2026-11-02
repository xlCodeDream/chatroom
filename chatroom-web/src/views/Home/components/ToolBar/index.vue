<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useGroupStore } from '@/stores/group'
import { useGlobalStore } from '@/stores/global'
import { judgeClient } from '@/utils/detectDevice'

const client = judgeClient()
const visible = ref(false)
const userStore = useUserStore()
const groupStore = useGroupStore()
const globalStore = useGlobalStore()

const avatar = computed(() => userStore?.userInfo.avatar)

// 监听 avatar 变化（验证 ToolBar 是否收到 store 更新）
watch(
  avatar,
  (newVal, oldVal) => {
    console.log('==== [ToolBar] avatar watch 触发 ====')
    console.log('[ToolBar] oldVal:', oldVal)
    console.log('[ToolBar] newVal:', newVal)
    console.trace('[ToolBar] 调用栈')
    debugger
  },
)
const unReadMark = computed(() => globalStore.unReadMark)
// 联系人入口红点 = 好友申请 + 群申请
const totalContactUnread = computed(
  () => globalStore.unReadMark.newFriendUnreadCount + globalStore.unReadMark.groupApplyUnreadCount,
)
const showSettingBox = () => (visible.value = true)
const toggleGroupListShow = () => (groupStore.showGroupList = !groupStore.showGroupList)
const onAddFriend = () => {
  globalStore.addFriendModalInfo.show = true
  globalStore.addFriendModalInfo.uid = undefined
}
const onApplyGroup = () => {
  globalStore.applyGroupModalInfo.show = true
  globalStore.applyGroupModalInfo.roomId = undefined
}
// 是否PC端
const isPc = computed(() => client === 'PC')
</script>

<template>
  <aside class="side-toolbar">
    <Avatar :src="userStore.isSign ? avatar : ''" :size="isPc ? 50 : 40" v-login="showSettingBox" />
    <div class="tool-icons">
      <!-- 会话 -->
      <router-link exactActiveClass="tool-icon-active" to="/">
        <el-badge
          :value="unReadMark.newMsgUnreadCount"
          :hidden="unReadMark.newMsgUnreadCount === 0"
          :max="99"
        >
          <Icon class="tool-icon" icon="chat" :size="28" />
        </el-badge>
      </router-link>
      <!-- 联系人 -->
      <router-link v-login-show exactActiveClass="tool-icon-active" to="/contact">
        <el-badge
          :value="totalContactUnread"
          :hidden="totalContactUnread === 0"
          :max="99"
        >
          <Icon class="tool-icon" icon="group" :size="28" />
        </el-badge>
      </router-link>
      <!-- 添加好友 / 申请加群 -->
      <el-dropdown v-login-show trigger="click" placement="top" @command="(cmd: string) => cmd === 'friend' ? onAddFriend() : onApplyGroup()">
        <div class="tool-icon" title="添加">
          <Icon icon="plus" :size="28" />
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="friend">添加好友</el-dropdown-item>
            <el-dropdown-item command="group">申请加群</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <Icon icon="zhankai" :size="28" @click="toggleGroupListShow" />
    <UserSettingBox v-model="visible" />
  </aside>
</template>

<style lang="scss" src="./styles.scss" scoped />
