<script setup lang="ts" name="ContactSide">
import { computed, ref } from 'vue'
import { useContactStore } from '@/stores/contacts'
import { useGlobalStore } from '@/stores/global'
import type { ContactItem as ContactItemType, RequestFriendItem } from '@/services/types'

import ContactItem from './ContactItem.vue'
import NewFriendItem from './NewFriendItem.vue'

const contactStore = useContactStore()
const globalStore = useGlobalStore()

const active = ref('1')
const onFrozen = ref(true)

// 好友申请列表（仅 type=1）
const friendRequests = computed(() =>
  contactStore.requestFriendsList.filter((item) => item.type === 1),
)
// 群申请列表（独立接口加载）
const groupApplyRequests = computed(() => contactStore.groupApplyList)

// 各分组的待处理数（用于标题旁红点）
const friendPendingCount = computed(
  () =>
    friendRequests.value.filter(
      (i) => i.status === 1 /* RequestFriendAgreeStatus.Waiting */,
    ).length,
)
const groupPendingCount = computed(
  () =>
    groupApplyRequests.value.filter(
      (i) => i.status === 1 /* RequestFriendAgreeStatus.Waiting */,
    ).length,
)

const onNodeClick = (item: RequestFriendItem | ContactItemType) => {
  globalStore.currentSelectedContact = item
}

const onNewFriendLoad = () => {
  contactStore.getRequestFriendsList()
}
const onGroupApplyLoad = () => {
  console.log('==== [ContactSide] onGroupApplyLoad 触发 ====')
  contactStore.getGroupApplyList()
}
const onContactsLoad = () => {
  contactStore.getContactList()
}
const onChange = (activeName: string | string[]) => {
  console.log('==== [ContactSide] onChange ====')
  console.log('[ContactSide] activeName:', activeName)
  onFrozen.value = true
  setTimeout(() => {
    onFrozen.value = false
  }, 500)
  // 展开加群申请时主动加载（无限滚动不会在展开时自动触发首次加载）
  if (activeName === '2') {
    console.log('[ContactSide] 展开加群申请，主动调用 getGroupApplyList(true)')
    contactStore.getGroupApplyList(true)
  }
}
const onCreateGroup = () => {
  globalStore.createGroupModalInfo.show = true
}
</script>

<template>
  <div class="side">
    <ElButton class="add-group" type="primary" size="small" @click="onCreateGroup"
      >创建群聊</ElButton
    >
    <el-collapse class="side-collapse" v-model="active" @change="onChange" accordion>
      <!-- 新的朋友 -->
      <el-collapse-item name="1">
        <template #title>
          <span class="collapse-title">
            新的朋友
            <el-badge
              v-if="friendPendingCount > 0"
              :value="friendPendingCount"
              :max="99"
              class="title-badge"
            />
          </span>
        </template>
        <ul
          v-infinite-scroll="onNewFriendLoad"
          :infinite-scroll-disabled="onFrozen"
          class="read-unread-list"
        >
          <NewFriendItem
            v-for="item in friendRequests"
            :key="item.uid"
            :item="item"
            @click="onNodeClick(item)"
          />
          <li v-if="friendRequests.length === 0" class="read-unread-no-data">
            暂无好友申请~
          </li>
        </ul>
      </el-collapse-item>
      <!-- 加群申请 -->
      <el-collapse-item name="2">
        <template #title>
          <span class="collapse-title">
            加群申请
            <el-badge
              v-if="groupPendingCount > 0"
              :value="groupPendingCount"
              :max="99"
              class="title-badge"
            />
          </span>
        </template>
        <ul
          v-infinite-scroll="onGroupApplyLoad"
          :infinite-scroll-disabled="onFrozen"
          class="read-unread-list"
        >
          <NewFriendItem
            v-for="item in groupApplyRequests"
            :key="item.applyId"
            :item="item"
            :forceType="2"
            @click="onNodeClick(item)"
          />
          <li v-if="groupApplyRequests.length === 0" class="read-unread-no-data">
            暂无加群申请~
          </li>
        </ul>
      </el-collapse-item>
      <!-- 联系人 -->
      <el-collapse-item name="3">
        <template #title>
          <span class="collapse-title">联系人</span>
        </template>
        <ul
          v-infinite-scroll="onContactsLoad"
          :infinite-scroll-disabled="onFrozen"
          class="read-unread-list"
        >
          <ContactItem
            v-for="item in contactStore.contactsList"
            :key="item.uid"
            :item="item"
            @click="onNodeClick(item)"
          />
          <li v-if="contactStore.contactsList?.length === 0" class="read-unread-no-data">
            暂无数据~
          </li>
        </ul>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped>
.collapse-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.title-badge {
  margin-left: 4px;
}
</style>

<style lang="scss">
.contact-tree-item .el-tree-node__content {
  width: 100%;
  height: 60px;
}
</style>
