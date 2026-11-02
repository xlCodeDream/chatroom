import { computed, reactive, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import cloneDeep from 'lodash/cloneDeep'
import { useRoute } from 'vue-router'
import apis from '@/services/apis'
import type { MarkItemType, MessageType, RevokedMsgType, SessionItem } from '@/services/types'
import { MarkEnum, MsgEnum, RoomTypeEnum } from '@/enums'
import { computedTimeBlock } from '@/utils/computedTime'
import { useCachedStore } from '@/stores/cached'
import { useUserStore } from '@/stores/user'
import { useGlobalStore } from '@/stores/global'
import { useGroupStore } from '@/stores/group'
import shakeTitle from '@/utils/shakeTitle'
import notify from '@/utils/notification'

export const pageSize = 20
// 标识是否第一次请求
let isFirstInit = false

export const useChatStore = defineStore('chat', () => {
  const route = useRoute()
  const cachedStore = useCachedStore()
  const userStore = useUserStore()
  const globalStore = useGlobalStore()
  const groupStore = useGroupStore()
  const sessionList = reactive<SessionItem[]>([]) // 会话列表
  const sessionOptions = reactive({ isLast: false, isLoading: false, cursor: '' })

  const currentRoomId = computed(() => globalStore.currentSession?.roomId)
  const currentRoomType = computed(() => globalStore.currentSession?.type)

  const messageMap = reactive<Map<number, Map<number, MessageType>>>(
    new Map([[currentRoomId.value, new Map()]]),
  ) // 消息Map
  const messageOptions = reactive<
    Map<number, { isLast: boolean; isLoading: boolean; cursor: string }>
  >(new Map([[currentRoomId.value, { isLast: false, isLoading: false, cursor: '' }]]))
  const replyMapping = reactive<Map<number, Map<number, number[]>>>(
    new Map([[currentRoomId.value, new Map()]]),
  ) // 回复消息映射

  const currentMessageMap = computed({
    get: () => {
      const current = messageMap.get(currentRoomId.value as number)
      if (current === undefined) {
        messageMap.set(currentRoomId.value, new Map())
      }
      return messageMap.get(currentRoomId.value as number)
    },
    set: (val) => {
      messageMap.set(currentRoomId.value, val as Map<number, MessageType>)
    },
  })
  const currentMessageOptions = computed({
    get: () => {
      const current = messageOptions.get(currentRoomId.value as number)
      if (current === undefined) {
        messageOptions.set(currentRoomId.value, { isLast: false, isLoading: true, cursor: '' })
      }
      return messageOptions.get(currentRoomId.value as number)
    },
    set: (val) => {
      messageOptions.set(
        currentRoomId.value,
        val as { isLast: boolean; isLoading: boolean; cursor: string },
      )
    },
  })
  const currentReplyMap = computed({
    get: () => {
      const current = replyMapping.get(currentRoomId.value as number)
      if (current === undefined) {
        replyMapping.set(currentRoomId.value, new Map())
      }
      return replyMapping.get(currentRoomId.value as number)
    },
    set: (val) => {
      replyMapping.set(currentRoomId.value, val as Map<number, number[]>)
    },
  })
  const isGroup = computed(() => currentRoomType.value === RoomTypeEnum.Group)
  /**
   * 获取当前会话信息
   */
  const currentSessionInfo = computed(() =>
    sessionList.find((session) => session.roomId === globalStore.currentSession.roomId),
  )

  const chatListToBottomAction = ref<() => void>() // 外部提供消息列表滚动到底部事件

  // const isStartCount = ref(false) // 是否开始计数
  // const newMsgCount = ref(0) // 新消息计数

  const newMsgCount = reactive<Map<number, { count: number; isStart: boolean }>>(
    new Map([
      [
        currentRoomId.value,
        {
          // 新消息计数
          count: 0,
          // 是否开始计数
          isStart: false,
        },
      ],
    ]),
  )
  const currentNewMsgCount = computed({
    get: () => {
      const current = newMsgCount.get(currentRoomId.value as number)
      if (current === undefined) {
        newMsgCount.set(currentRoomId.value, { count: 0, isStart: false })
      }
      return newMsgCount.get(currentRoomId.value as number)
    },
    set: (val) => {
      newMsgCount.set(currentRoomId.value, val as { count: number; isStart: boolean })
    },
  })

  watch(currentRoomId, (val, oldVal) => {
    if (oldVal !== undefined && val !== oldVal) {
      // 切换会话，滚动到底部
      chatListToBottomAction.value?.()
      // 切换的 rooms是空数据的话就请求消息列表
      if (!currentMessageMap.value || currentMessageMap.value.size === 0) {
        if (!currentMessageMap.value) {
          messageMap.set(currentRoomId.value as number, new Map())
        }
        getMsgList()
      } else {
        // 已有消息时，确保 isLoading 为 false，避免一直显示"消息加载中"
        // currentMessageOptions getter 在首次访问新会话时会默认创建 isLoading: true
        currentMessageOptions.value && (currentMessageOptions.value.isLoading = false)
      }

      // 群组的时候去请求
      if (currentRoomType.value === RoomTypeEnum.Group) {
        groupStore.getGroupUserList(true)
        groupStore.getCountStatistic()
        cachedStore.getGroupAtUserBaseInfo()
      }
    }

    // 重置当前回复的消息
    currentMsgReply.value = {}
  })

  // 当前消息回复
  const currentMsgReply = ref<Partial<MessageType>>({})

  // 将消息列表转换为数组
  const chatMessageList = computed(() => [...(currentMessageMap.value?.values() || [])])

  const getMsgList = async (size = pageSize) => {
    console.log('getMsgList called, roomId:', currentRoomId.value)
    currentMessageOptions.value && (currentMessageOptions.value.isLoading = true)
    try {
      const data = await apis
        .getMsgList({
          params: {
            pageSize: size,
            cursor: currentMessageOptions.value?.cursor,
            roomId: currentRoomId.value,
          },
        })
        .send()
      console.log('getMsgList response:', data)
      if (!data || !data.list || data.list.length === 0) {
        console.warn('getMsgList: no data returned or list is empty')
        currentMessageOptions.value && (currentMessageOptions.value.isLoading = false)
        return
      }
      const computedList = computedTimeBlock(data.list)

      /** 收集需要请求用户详情的 uid */
      const uidCollectYet: Set<number> = new Set() // 去重用
      computedList.forEach((msg) => {
        const replyItem = msg.message.body?.reply
        if (replyItem?.id) {
          const messageIds = currentReplyMap.value?.get(replyItem.id) || []
          messageIds.push(msg.message.id)
          currentReplyMap.value?.set(replyItem.id, messageIds)

          // 查询被回复用户的信息，被回复的用户信息里暂时无 uid
          // collectUidItem(replyItem.uid)
        }
        // 查询消息发送者的信息
        uidCollectYet.add(msg.fromUser.uid)
      })
      // 获取用户信息缓存
      cachedStore.getBatchUserInfo([...uidCollectYet])
      // 为保证获取的历史消息在前面
      const newList = [...computedList, ...chatMessageList.value]
      currentMessageMap.value?.clear() // 清空Map
      newList.forEach((msg) => {
        currentMessageMap.value?.set(msg.message.id, msg)
      })
      console.log('getMsgList: messageMap size:', currentMessageMap.value?.size)

      if (currentMessageOptions.value) {
        currentMessageOptions.value.cursor = data.cursor
        currentMessageOptions.value.isLast = data.isLast
        currentMessageOptions.value.isLoading = false
      }
    } catch (error) {
      console.error('getMsgList error:', error)
      currentMessageOptions.value && (currentMessageOptions.value.isLoading = false)
    }
  }

  const getSessionList = async (isFresh = false) => {
    console.log('getSessionList called, isFresh:', isFresh, 'current sessionList length:', sessionList.length)
    if (!isFresh && (sessionOptions.isLast || sessionOptions.isLoading)) return
    sessionOptions.isLoading = true
    try {
      const data = await apis
        .getSessionList({
          params: {
            pageSize: sessionList.length > pageSize ? sessionList.length : pageSize,
            cursor: isFresh || !sessionOptions.cursor ? undefined : sessionOptions.cursor,
          },
        })
        .send()
      console.log('getSessionList response:', data)
      if (!data || !data.list || data.list.length === 0) {
        console.warn('getSessionList: no data returned or list is empty')
        sessionOptions.isLoading = false
        return
      }
      isFresh
        ? sessionList.splice(0, sessionList.length, ...data.list)
        : sessionList.push(...data.list)
      sessionOptions.cursor = data.cursor
      sessionOptions.isLast = data.isLast
      sessionOptions.isLoading = false

      sortAndUniqueSessionList()
      console.log('getSessionList: sessionList after sort:', sessionList.length, 'items')

      if (sessionList.length > 0) {
        sessionList[0].unreadCount = 0
      }
      if (!isFirstInit && data.list.length > 0) {
        isFirstInit = true
        globalStore.currentSession.roomId = data.list[0].roomId
        globalStore.currentSession.type = data.list[0].type
        console.log('getSessionList: setting current session:', data.list[0].roomId, data.list[0].type)
        // 用会话列表第一个去请求消息列表
        getMsgList()
        // 请求第一个群成员列表
        currentRoomType.value === RoomTypeEnum.Group && groupStore.getGroupUserList(true)
        // 初始化所有用户基本信息
        userStore.isSign && cachedStore.initAllUserBaseInfo()
        // 联系人列表（动态导入，避免与 contactsStore 循环依赖）
        import('@/stores/contacts').then(({ useContactStore }) => {
          useContactStore().getContactList(true)
        })
      }
    } catch (error) {
      console.error('getSessionList error:', error)
      sessionOptions.isLoading = false
    }
  }

  /** 会话列表去重并排序 */
  const sortAndUniqueSessionList = () => {
    const temp: Record<string, SessionItem> = {}
    sessionList.forEach((item) => (temp[item.roomId] = item))
    sessionList.splice(0, sessionList.length, ...Object.values(temp))
    sessionList.sort((pre, cur) => cur.activeTime - pre.activeTime)
  }

  const updateSession = (roomId: number, roomProps: Partial<SessionItem>) => {
    const session = sessionList.find((item) => item.roomId === roomId)
    session && roomProps && Object.assign(session, roomProps)
    sortAndUniqueSessionList()
  }

  const updateSessionLastActiveTime = (roomId: number, room?: SessionItem) => {
    const session = sessionList.find((item) => item.roomId === roomId)
    if (session) {
      Object.assign(session, { activeTime: Date.now() })
    } else if (room) {
      const newItem = cloneDeep(room)
      newItem.activeTime = Date.now()
      sessionList.unshift(newItem)
    }
    sortAndUniqueSessionList()
  }

  // 通过房间ID获取会话信息
  const getSession = (roomId: number): SessionItem => {
    return sessionList.find((item) => item.roomId === roomId) as SessionItem
  }

  const pushMsg = async (msg: MessageType) => {
    // 记录该 roomId 是否是新出现的（用于判断是否需要请求会话详情）
    const isNewRoom = !messageMap.has(msg.message.roomId)
    // 确保该 roomId 的消息 Map 存在，避免新会话的消息丢失
    if (isNewRoom) {
      messageMap.set(msg.message.roomId, new Map())
    }
    messageMap.get(msg.message.roomId)?.set(msg.message.id, msg)

    // 获取用户信息缓存
    // 尝试取缓存user, 如果有 lastModifyTime 说明缓存过了，没有就一定是要缓存的用户了
    const uid = msg.fromUser.uid
    const cacheUser = cachedStore.userCachedList[uid]
    cachedStore.getBatchUserInfo([uid])

    // 发完消息就要刷新会话列表，
    // 如果当前会话已经置顶了，可以不用刷新
    // isNewRoom 表示 messageMap 中没有该 room；!getSession 表示会话列表中被隐藏了
    if (isNewRoom || !getSession(msg.message.roomId)) {
      // 新会话或被隐藏的会话收到消息，刷新会话列表以确保显示在左侧列表
      getSessionList(true)
    } else if (
      globalStore.currentSession &&
      globalStore.currentSession.roomId !== msg.message.roomId
    ) {
      // 已有会话，更新最后活跃时间
      updateSessionLastActiveTime(msg.message.roomId)
    }

    // 如果收到的消息里面是艾特自己的就发送系统通知
    if (msg.message.body.atUidList?.includes(userStore.userInfo.uid) && cacheUser) {
      notify({
        name: cacheUser.name as string,
        text: msg.message.body.content,
        icon: cacheUser.avatar as string,
      })
    }

    // tab 在后台获得新消息，就开始闪烁！
    if (document.hidden && !shakeTitle.isShaking) {
      shakeTitle.start()
    }

    if (
      currentNewMsgCount.value &&
      currentNewMsgCount.value?.isStart &&
      typeof currentNewMsgCount.value.count === 'number'
    ) {
      currentNewMsgCount.value.count++
      return
    }

    // 聊天记录计数
    if (currentRoomId.value !== msg.message.roomId) {
      const item = sessionList.find((item) => item.roomId === msg.message.roomId)
      if (item) {
        item.unreadCount += 1
      }
      // 如果新消息的 roomId 和 当前显示的 room 的 Id 一致，就更新已读
    } else {
      // 且当前路由在 聊天 内
      if (route?.path && route?.path === '/') {
        apis.markMsgRead({ roomId: currentRoomId.value }).send()
      }
    }

    // 如果当前路由不是聊天，就开始计数
    if (route?.path && route?.path !== '/') {
      globalStore.unReadMark.newMsgUnreadCount++
    }

    // 聊天列表滚动到底部
    setTimeout(() => {
      // 如果超过一屏了，不自动滚动到最新消息。
      chatListToBottomAction.value?.()
    }, 0)
  }

  // 过滤掉小黑子的发言
  const filterUser = (uid: number) => {
    if (typeof uid !== 'number') return
    for (const messages of messageMap.values()) {
      messages?.forEach((msg) => {
        if (msg.fromUser.uid === uid) {
          messages.delete(msg.message.id)
        }
      })
    }
  }

  const loadMore = async (size?: number) => {
    if (currentMessageOptions.value?.isLast || currentMessageOptions.value?.isLoading) return
    await getMsgList(size)
  }

  const clearNewMsgCount = () => {
    currentNewMsgCount.value && (currentNewMsgCount.value.count = 0)
  }

  // 查找消息在列表里面的索引
  const getMsgIndex = (msgId: number) => {
    if (!msgId || isNaN(Number(msgId))) return -1
    const keys = currentMessageMap.value ? Array.from(currentMessageMap.value.keys()) : []
    return keys.findIndex((key) => key === msgId)
  }

  // 更新点赞、举报数
  const updateMarkCount = (markList: MarkItemType[]) => {
    markList.forEach((mark: MarkItemType) => {
      const { msgId, markType, markCount } = mark

      const msgItem = currentMessageMap.value?.get(msgId)
      if (msgItem) {
        if (markType === MarkEnum.LIKE) {
          msgItem.message.messageMark.likeCount = markCount
        } else if (markType === MarkEnum.DISLIKE) {
          msgItem.message.messageMark.dislikeCount = markCount
        }
      }
    })
  }
  // 更新消息撤回状态
  const updateRecallStatus = (data: RevokedMsgType) => {
    const { msgId } = data
    const message = currentMessageMap.value?.get(msgId)
    if (message && typeof data.recallUid === 'number') {
      message.message.type = MsgEnum.RECALL
      const cacheUser = cachedStore.userCachedList[data.recallUid]
      // 如果撤回者的 id 不等于消息发送人的 id, 或者你本人就是管理员，那么显示管理员撤回的。
      if (data.recallUid !== message.fromUser.uid) {
        message.message.body = `管理员"${cacheUser.name}"撤回了一条成员消息` // 后期根据本地用户数据修改
      } else {
        // 如果被撤回的消息是消息发送者撤回，正常显示
        message.message.body = `"${cacheUser.name}"撤回了一条消息` // 后期根据本地用户数据修改
      }
    }
    // 更新与这条撤回消息有关的消息
    const messageList = currentReplyMap.value?.get(msgId)
    messageList?.forEach((id) => {
      const msg = currentMessageMap.value?.get(id)
      if (msg) {
        msg.message.body.reply.body = `原消息已被撤回`
      }
    })
  }
  // 删除消息
  const deleteMsg = (msgId: number) => {
    currentMessageMap.value?.delete(msgId)
  }
  // 清空指定房间的消息列表
  const clearMsgList = (roomId: number) => {
    messageMap.get(roomId)?.clear()
  }
  // 更新消息
  const updateMsg = (msgId: number, newMessage: MessageType) => {
    console.log('开始更新消息')
    deleteMsg(msgId)
    pushMsg(newMessage)
  }

  // 标记已读数为 0
  const markSessionRead = (roomId: number) => {
    const session = sessionList.find((item) => item.roomId === roomId)
    const unreadCount = session?.unreadCount || 0
    if (session) {
      session.unreadCount = 0
    }
    return unreadCount
  }

  // 根据消息id获取消息体
  const getMessage = (messageId: number) => {
    return currentMessageMap.value?.get(messageId)
  }

  // 删除会话
  const removeContact = (roomId: number) => {
    const index = sessionList.findIndex((session) => session.roomId === roomId)
    sessionList.splice(index, 1)
  }

  /**
   * 根据 uid 找到对应的"单聊会话"，把左侧会话列表里展示的头像/昵称同步为新的值。
   * 由于 SessionItem 里没有 uid 字段，通过"name 匹配缓存用户名"和"在消息 map 中找该 uid 的最后一条单聊消息"
   * 两条策略结合来定位 session。群聊 session 的名称头像不走这里，由群详情接口控制。
   */
  const updateSingleSessionByUid = (data: {
    uid: number
    avatar?: string
    name?: string
  }) => {
    const { uid, avatar, name } = data
    // 1) 先从消息容器里找该 uid 所有消息对应的单聊 room（最准的策略）
    const candidateRoomIds = new Set<number>()
    for (const [roomId, msgMap] of messageMap.entries()) {
      const session = getSession(roomId)
      if (!session || session.type !== RoomTypeEnum.Single) continue
      for (const msg of msgMap.values()) {
        if (msg.fromUser.uid === uid) {
          candidateRoomIds.add(roomId)
          break
        }
      }
    }
    // 2) 兜底：再按 sessionList.name === 缓存中的用户名匹配一次，避免消息容器还没拉到的会话漏更新
    if (name) {
      sessionList.forEach((s) => {
        if (s.type === RoomTypeEnum.Single && s.name === name) {
          candidateRoomIds.add(s.roomId)
        }
      })
    }
    // 3) 也可以按 uid 对应的缓存用户名（如果用户信息改过名了就用 cachedStore 里的最新值）
    const cachedName = cachedStore.userCachedList[uid]?.name
    if (cachedName) {
      sessionList.forEach((s) => {
        if (s.type === RoomTypeEnum.Single && s.name === cachedName) {
          candidateRoomIds.add(s.roomId)
        }
      })
    }
    // 4) 应用更新
    candidateRoomIds.forEach((roomId) => {
      updateSession(roomId, {
        ...(avatar !== undefined ? { avatar } : {}),
        ...(name !== undefined ? { name } : {}),
      })
    })
  }

  return {
    getMsgIndex,
    chatMessageList,
    pushMsg,
    deleteMsg,
    clearMsgList,
    clearNewMsgCount,
    updateMarkCount,
    updateRecallStatus,
    updateMsg,
    chatListToBottomAction,
    newMsgCount,
    messageMap,
    currentMessageMap,
    currentMessageOptions,
    currentReplyMap,
    currentNewMsgCount,
    loadMore,
    currentMsgReply,
    filterUser,
    sessionList,
    sessionOptions,
    getSessionList,
    updateSession,
    updateSessionLastActiveTime,
    markSessionRead,
    getSession,
    isGroup,
    currentSessionInfo,
    getMessage,
    removeContact,
    updateSingleSessionByUid,
  }
})
