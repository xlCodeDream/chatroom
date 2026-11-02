import { reactive } from 'vue'
import { defineStore } from 'pinia'
import apis from '@/services/apis'
import { useGlobalStore } from '@/stores/global'
import { useChatStore } from '@/stores/chat'
import type { ContactItem, RequestFriendItem } from '@/services/types'
import { RequestFriendAgreeStatus } from '@/services/types'

export const pageSize = 20
export const useContactStore = defineStore('contact', () => {
  const globalStore = useGlobalStore()
  const contactsList = reactive<ContactItem[]>([])
  const requestFriendsList = reactive<RequestFriendItem[]>([])
  const groupApplyList = reactive<RequestFriendItem[]>([])
  const contactsOptions = reactive({ isLast: false, isLoading: false, cursor: '' })
  const requestFriendsOptions = reactive({ isLast: false, isLoading: false, cursor: '' })
  const groupApplyOptions = reactive({ isLast: false, isLoading: false, cursor: '' })
  const getContactList = async (isFresh = false) => {
    if (!isFresh) {
      if (contactsOptions.isLast || contactsOptions.isLoading) return
    }
    contactsOptions.isLoading = true
    const data = await apis
      .getContactList({
        // TODO 先写 100，稍后优化
        pageSize: 100,
        cursor: isFresh || !contactsOptions.cursor ? undefined : contactsOptions.cursor,
      })
      .send()
      .catch(() => {
        contactsOptions.isLoading = false
      })
    if (!data) return
    isFresh
      ? contactsList.splice(0, contactsList.length, ...data.list)
      : contactsList.push(...data.list)
    contactsOptions.cursor = data.cursor
    contactsOptions.isLast = data.isLast
    contactsOptions.isLoading = false
  }

  /** 好友申请未读数（混合计数，同时更新 friend + group 两个红点） */
  const getNewFriendCount = async () => {
    const data = await apis
      .newFriendCount()
      .send()
      .catch(() => {
        //
      })
    if (typeof data?.unReadCount === 'number') {
      // 按列表中待处理项的 type 拆分未读数
      const pendingItems = requestFriendsList.filter(
        (item) => item.status === RequestFriendAgreeStatus.Waiting,
      )
      const friendCount = pendingItems.filter((i) => i.type === 1).length
      const groupCount = pendingItems.filter((i) => i.type === 2).length
      // 如果列表已加载，用实际计数；否则用总数兜底
      globalStore.unReadMark.newFriendUnreadCount =
        pendingItems.length > 0 ? friendCount : data.unReadCount
      globalStore.unReadMark.groupApplyUnreadCount =
        pendingItems.length > 0 ? groupCount : 0
    }
  }

  const getRequestFriendsList = async (isFresh = false) => {
    if (!isFresh) {
      if (requestFriendsOptions.isLast || requestFriendsOptions.isLoading) return
    }
    requestFriendsOptions.isLoading = true
    const data = await apis
      .requestFriendList({
        pageSize,
        cursor: isFresh || !requestFriendsOptions.cursor ? undefined : requestFriendsOptions.cursor,
      })
      .send()
      .catch(() => {
        requestFriendsOptions.isLoading = false
      })
    // 每次加载完新的好友邀请列表都要更新申请未读数
    getNewFriendCount()
    if (!data) return
    isFresh
      ? requestFriendsList.splice(0, requestFriendsList.length, ...data.list)
      : requestFriendsList.push(...data.list)
    requestFriendsOptions.cursor = data.cursor
    requestFriendsOptions.isLast = data.isLast
    requestFriendsOptions.isLoading = false
  }
  // 默认执行一次
  // getContactList()
  // getRequestFriendsList()

  /** 加载群申请列表（独立接口） */
  const getGroupApplyList = async (isFresh = false) => {
    if (!isFresh) {
      if (groupApplyOptions.isLast || groupApplyOptions.isLoading) return
    }
    groupApplyOptions.isLoading = true
    const data = await apis
      .getGroupApplyList({
        pageSize,
        cursor: isFresh || !groupApplyOptions.cursor ? undefined : groupApplyOptions.cursor,
      })
      .send()
      .catch((err) => {
        console.error('==== [getGroupApplyList] 请求失败 ❌ ====')
        console.error('[getGroupApplyList] error:', err)
        groupApplyOptions.isLoading = false
      })
    // 调试：打印返回数据
    console.log('==== [getGroupApplyList] 响应 ====')
    console.log('[getGroupApplyList] data:', data)
    console.log('[getGroupApplyList] data?.list:', data?.list)
    console.log(
      '[getGroupApplyList] list 详情:',
      data?.list?.map((i) => ({ uid: i.uid, type: i.type, status: i.status, msg: i.msg })),
    )
    if (!data) return
    isFresh
      ? groupApplyList.splice(0, groupApplyList.length, ...data.list)
      : groupApplyList.push(...data.list)
    groupApplyOptions.cursor = data.cursor
    groupApplyOptions.isLast = data.isLast
    groupApplyOptions.isLoading = false
    // 按待处理数更新群申请红点
    const pendingCount = groupApplyList.filter(
      (item) => item.status === RequestFriendAgreeStatus.Waiting,
    ).length
    globalStore.unReadMark.groupApplyUnreadCount = pendingCount
  }

  /** 接受申请（好友申请 type=1 / 加群申请 type=2） */
  const onAcceptFriend = (applyId: number, type: number = 1) => {
    console.log('==== [onAcceptFriend] 开始 ====')
    console.log('[onAcceptFriend] applyId:', applyId, 'type:', type)
    // 根据 type 调用不同接口
    const request =
      type === 2
        ? apis.acceptGroupApply({ applyId }) // 加群申请 → PUT /capi/room/group/apply
        : apis.applyFriendRequest({ applyId }) // 好友申请 → PUT /capi/user/friend/apply
    console.log(
      '[onAcceptFriend] 调用接口:',
      type === 2 ? 'PUT /capi/room/group/apply' : 'PUT /capi/user/friend/apply',
    )
    request
      .send()
      .then(() => {
        console.log('[onAcceptFriend] 请求成功 ✅')
        // 刷新好友申请列表
        getRequestFriendsList(true)
        // 刷新群申请列表
        getGroupApplyList(true)
        // 刷新好友列表
        getContactList(true)
        // 刷新会话列表，确保与对方的聊天房间显示在左侧列表（包括之前被隐藏的）
        useChatStore().getSessionList(true)
        // 标识为可以发消息的人
        if (globalStore.currentSelectedContact) {
          // @ts-ignore
          globalStore.currentSelectedContact.status = RequestFriendAgreeStatus.Agree
        }
      })
      .catch((err) => {
        console.error('==== [onAcceptFriend] 请求失败 ❌ ====')
        console.error('[onAcceptFriend] error:', err)
      })
  }
  /** 更新联系人在线状态 */
  const batchUpdateContactStatus = (items: { uid: number; activeStatus: number; lastOptTime: number }[]) => {
    for (let index = 0, len = items.length; index < len; index++) {
      const curUser = items[index]
      const findIndex = contactsList.findIndex((item) => item.uid === curUser.uid)
      if (findIndex > -1) {
        contactsList[findIndex].activeStatus = curUser.activeStatus
        contactsList[findIndex].lastOptTime = curUser.lastOptTime
      }
    }
  }

  /** 删除好友 */
  const onDeleteContact = async (uid: number) => {
    if (!uid) return
    // 同意好友申请
    await apis.deleteFriend({ targetUid: uid }).send()
    // 刷新好友申请列表
    // getRequestFriendsList(true)
    // 刷新好友列表
    getContactList(true)
  }
  return {
    getContactList,
    getRequestFriendsList,
    getGroupApplyList,
    contactsList,
    requestFriendsList,
    groupApplyList,
    contactsOptions,
    requestFriendsOptions,
    groupApplyOptions,
    onAcceptFriend,
    onDeleteContact,
    batchUpdateContactStatus,
  }
})
