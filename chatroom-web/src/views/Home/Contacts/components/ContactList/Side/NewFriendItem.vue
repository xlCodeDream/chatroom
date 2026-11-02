<script setup lang="ts" name="NewFriendItem">
import { toRefs, computed } from 'vue'
import { useContactStore } from '@/stores/contacts'
import { RequestFriendAgreeStatus } from '@/services/types'
import type { RequestFriendItem } from '@/services/types'
import { useUserInfo } from '@/hooks/useCached'

const props = defineProps<{ item: RequestFriendItem; forceType?: number }>()

const { item } = toRefs(props)

const contactStore = useContactStore()

const currentUid = computed(() => item?.value?.uid)
const currentUser = useUserInfo(currentUid.value)

// 申请类型：1加好友 2加群（优先使用 forceType prop，其次用 item.type）
const effectiveType = computed(() => props.forceType ?? item?.value?.type ?? 1)
const isGroupApply = computed(() => effectiveType.value === 2)
// 已同意时的文案
const agreedText = computed(() => (isGroupApply.value ? '已加入' : '已添加'))

const onAccept = () => {
  console.log('==== [NewFriendItem] 点击接受 ====')
  console.log('[NewFriendItem] item:', item.value)
  console.log('[NewFriendItem] item.type:', item.value?.type)
  console.log('[NewFriendItem] forceType prop:', props.forceType)
  console.log('[NewFriendItem] effectiveType:', effectiveType.value)
  console.log('[NewFriendItem] isGroupApply:', isGroupApply.value)
  contactStore.onAcceptFriend(item.value!.applyId, effectiveType.value)
}
</script>

<template>
  <div class="item-info">
    <Avatar class="avatar" :src="currentUser.avatar" :size="40" />
    <div class="item-info-right">
      <span class="item-info-name">
        {{ currentUser.name }}
        <span class="item-info-type-tag" v-if="isGroupApply">加群申请</span>
        <button
          class="item-info-accept-btn"
          v-if="item.status === RequestFriendAgreeStatus.Waiting"
          @click="onAccept"
        >
          接受
        </button>
        <span class="item-info-accept-text" v-else>{{ agreedText }}</span>
      </span>
      <span class="item-info-msg">{{ item?.msg }}</span>
    </div>
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped />
