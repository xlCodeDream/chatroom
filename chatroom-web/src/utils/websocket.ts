import Router from '@/router'
import { useWsLoginStore } from '@/stores/ws'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useGroupStore } from '@/stores/group'
import { useGlobalStore } from '@/stores/global'
import { useEmojiStore } from '@/stores/emoji'
import { useContactStore } from '@/stores/contacts'
import { useCachedStore } from '@/stores/cached'
import { WsResponseMessageType, WsRequestMsgType } from './wsType'
import type {
  LoginSuccessResType,
  WsReqMsgContentType,
  OnStatusChangeType,
  WSUserChangeType,
} from './wsType'
import type { MessageType, MarkItemType, RevokedMsgType } from '@/services/types'
import { OnlineEnum, ChangeTypeEnum, RoomTypeEnum } from '@/enums'

import { worker } from './initWorker'
import shakeTitle from '@/utils/shakeTitle'
import notify from '@/utils/notification'

class WS {
  #tasks: WsReqMsgContentType[] = []
  // 重连🔐
  #connectReady = false

  constructor() {
    console.log('%c[WS] WebSocket类已构造，开始初始化...', 'color:red;font-weight:bold')
    this.initConnect()
    // 收到消息
    worker.addEventListener('message', this.onWorkerMsg)

    // 后台重试次数达到上限之后，tab 获取焦点再重试
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && !this.#connectReady) {
        this.initConnect()
      }

      // 获得焦点停止消息闪烁
      if (!document.hidden) {
        shakeTitle.clear()
      }
    })
  }

  initConnect = () => {
    const token = sessionStorage.getItem('TOKEN')
    console.log(
      '[WS] initConnect, token:',
      token ? '有token(' + token.substring(0, 20) + '...)' : '无token!',
    )
    // 如果token 是 null, 而且 sessionStorage 的用户信息有值，需要清空用户信息
    if (token === null && sessionStorage.getItem('USER_INFO')) {
      console.log('[WS] token为null，但sessionStorage有USER_INFO，清空USER_INFO')
      sessionStorage.removeItem('USER_INFO')
    }
    // 初始化 ws
    console.log('[WS] 向Worker发送initWS消息')
    worker.postMessage(`{"type":"initWS","value":${token ? `"${token}"` : null}}`)
  }

  onWorkerMsg = (e: MessageEvent<any>) => {
    const params: { type: string; value: unknown } = JSON.parse(e.data)
    console.log('[WS主线程] 收到Worker消息, type:', params.type, 'value:', params.value)
    switch (params.type) {
      case 'message': {
        this.onMessage(params.value as string)
        break
      }
      case 'open': {
        this.#dealTasks()
        break
      }
      case 'close':
      case 'error': {
        this.#onClose()
        break
      }
    }
  }

  // 重置一些属性
  #onClose = () => {
    this.#connectReady = false
  }

  #dealTasks = () => {
    this.#connectReady = true

    setTimeout(() => {
      const userStore = useUserStore()
      if (userStore.isSign) {
        // 已登录：发送 Authorization 认证消息，让后端将此 WS 连接注册到用户的消息推送通道
        const token = sessionStorage.getItem('TOKEN')
        console.log('[WS] 发送 Authorization 认证消息, token:', token ? '有' : '无')
        this.send({ type: WsRequestMsgType.Authorization, data: { token } })
        // 处理堆积的任务
        this.#tasks.forEach((task) => {
          this.send(task)
        })
        // 清空缓存的消息
        this.#tasks = []
      }
    }, 500)
  }

  #send(msg: WsReqMsgContentType) {
    worker.postMessage(
      `{"type":"message","value":${typeof msg === 'string' ? msg : JSON.stringify(msg)}}`,
    )
  }

  send = (params: WsReqMsgContentType) => {
    if (this.#connectReady) {
      this.#send(params)
    } else {
      // 放到队列
      this.#tasks.push(params)
    }
  }

  // 收到消息回调
  onMessage = (value: string) => {
    // FIXME 可能需要 try catch,
    const params: { type: WsResponseMessageType; data: unknown } = JSON.parse(value)
    // 调试：打印所有收到的 WS 消息（含原始 JSON）
    console.log('%c==== [WS] 收到消息 ====', 'color: #2196F3; font-weight: bold')
    console.log(
      '[WS] type:',
      params.type,
      '枚举名:',
      WsResponseMessageType[params.type] ?? '未知类型',
    )
    console.log('[WS] data:', params.data)
    console.log('[WS] 原始JSON:', value)
    const loginStore = useWsLoginStore()
    const userStore = useUserStore()
    const chatStore = useChatStore()
    const groupStore = useGroupStore()
    const globalStore = useGlobalStore()
    const emojiStore = useEmojiStore()
    const cachedStore = useCachedStore()
    switch (params.type) {
      // 登录成功
      case WsResponseMessageType.LoginSuccess: {
        userStore.isSign = true
        const { token, ...rest } = params.data as LoginSuccessResType
        // FIXME 可以不需要赋值了，单独请求了接口。
        userStore.userInfo = { ...userStore.userInfo, ...rest }
        sessionStorage.setItem('USER_INFO', JSON.stringify(rest))
        // 只有当 token 有值时才覆盖，避免把 undefined 写入覆盖掉有效 token
        if (token) {
          sessionStorage.setItem('TOKEN', token)
        }
        // 获取用户详情
        userStore.getUserDetailAction()
        // 关闭登录弹窗
        loginStore.showLogin = false
        // 自己更新自己上线
        groupStore.batchUpdateUserStatus([
          {
            activeStatus: OnlineEnum.ONLINE,
            avatar: rest.avatar,
            lastOptTime: Date.now(),
            name: rest.name,
            uid: rest.uid,
          },
        ])
        // 获取用户详情
        chatStore.getSessionList(true)
        // 自定义表情列表
        emojiStore.getEmojiList()
        break
      }
      // 收到消息
      case WsResponseMessageType.ReceiveMessage: {
        chatStore.pushMsg(params.data as MessageType)
        break
      }
      // 用户下线
      case WsResponseMessageType.OnOffLine: {
        const data = params.data as OnStatusChangeType
        groupStore.countInfo.onlineNum = data.onlineNum
        // groupStore.countInfo.totalNum = data.totalNum
        groupStore.batchUpdateUserStatus(data.changeList)
        // 兜底：收到上下线通知时也刷新会话列表
        // （群主同意加群后可能推 OnOffLine 而非 NewFriendSession）
        console.log(
          '%c==== [WS] OnOffLine（上下线通知/加群兜底）====',
          'color: #9C27B0; font-weight: bold',
        )
        console.log(
          '[OnOffLine] onlineNum:',
          data.onlineNum,
          'changeList长度:',
          data.changeList?.length,
        )
        console.log('[OnOffLine] 完整data:', JSON.stringify(data))
        chatStore.getSessionList(true)
        break
      }
      // 用户 token 过期
      case WsResponseMessageType.TokenExpired: {
        userStore.isSign = false
        userStore.userInfo = {}
        console.log('❌ 用户 token 过期，清除本地存储的 TOKEN 和 USER_INFO')
        sessionStorage.removeItem('USER_INFO')
        sessionStorage.removeItem('TOKEN')
        break
      }
      // 小黑子的发言在禁用后，要删除他的发言
      case WsResponseMessageType.InValidUser: {
        const data = params.data as { uid: number }
        // 消息列表删掉小黑子发言
        chatStore.filterUser(data.uid)
        // 群成员列表删掉小黑子
        groupStore.filterUser(data.uid)
        break
      }
      // 点赞、倒赞消息通知
      case WsResponseMessageType.WSMsgMarkItem: {
        const data = params.data as { markList: MarkItemType[] }
        chatStore.updateMarkCount(data.markList)
        break
      }
      // 消息撤回通知
      case WsResponseMessageType.WSMsgRecall: {
        const { data } = params as { data: RevokedMsgType }
        chatStore.updateRecallStatus(data)
        break
      }
      // 新好友申请
      case WsResponseMessageType.RequestNewFriend: {
        const data = params.data as { uid: number; unreadCount: number }
        globalStore.unReadMark.newFriendUnreadCount += data.unreadCount
        notify({
          name: '新好友',
          text: '您有一个新好友, 快来看看~',
          onClick: () => {
            Router.push('/contact')
          },
        })
        break
      }
      // 新好友会话
      case WsResponseMessageType.NewFriendSession: {
        // changeType 1 加入群组，2： 移除群组
        const data = params.data as {
          roomId: number
          uid: number
          changeType: ChangeTypeEnum
          activeStatus: OnlineEnum
          lastOptTime: number
        }
        console.log(
          '%c==== [WS] ✅ NewFriendSession（新好友/加群通知）====',
          'color: #4CAF50; font-weight: bold',
        )
        console.log('[NewFriendSession] roomId:', data.roomId, 'uid:', data.uid)
        console.log(
          '[NewFriendSession] changeType:',
          data.changeType,
          '(1=JOIN加入 / 2=REMOVE移除)',
        )
        console.log('[NewFriendSession] 完整data:', JSON.stringify(data))
        if (data.changeType === ChangeTypeEnum.JOIN) {
          // 加入群组（新建群聊被邀请 / 被拉入群聊 / 加群申请被同意），刷新会话列表让群聊出现在左侧
          console.log(
            '%c[NewFriendSession] changeType=JOIN → 刷新会话列表',
            'color: #FF9800; font-weight: bold',
          )
          chatStore.getSessionList(true)
        }
        if (
          data.roomId === globalStore.currentSession.roomId &&
          globalStore.currentSession.type === RoomTypeEnum.Group
        ) {
          if (data.changeType === ChangeTypeEnum.REMOVE) {
            // 移除群成员
            groupStore.filterUser(data.uid)
            // TODO 添加一条退出群聊的消息
          } else {
            // TODO 添加群成员
            // TODO 添加一条入群的消息
          }
        }
        break
      }
      // 群申请通知（群主收到）
      case WsResponseMessageType.GroupApply: {
        console.log('==== [WS] 收到 GroupApply 通知 ====')
        console.log('[WS] params.data:', params.data)
        const data = params.data as { uid: number; unreadCount: number }
        // 群申请红点独立计数
        globalStore.unReadMark.groupApplyUnreadCount += data.unreadCount
        // 同时累加联系人入口的总红点
        globalStore.unReadMark.newFriendUnreadCount += data.unreadCount
        // 刷新群申请列表（独立接口）
        console.log('[WS] 调用 getGroupApplyList(true) 刷新群申请列表')
        useContactStore().getGroupApplyList(true)
        notify({
          name: '群申请',
          text: '有新的加群申请，快来看看~',
          onClick: () => {
            Router.push('/contact')
          },
        })
        break
      }
      // 用户信息变更（头像/昵称/个人介绍）
      case WsResponseMessageType.UserInfoChange: {
        const data = params.data as WSUserChangeType
        console.log('[UserInfoChange] 变更的用户 uid:', data.uid, 'data:', data)
        // 1) 更新用户缓存（消息列表、群成员、联系人卡片、@列表等均读取此缓存）
        cachedStore.updateUserInfo(data)
        // 2) 若变更的是当前登录用户，同步更新 userStore 自身信息
        if (data.uid === userStore.userInfo.uid) {
          userStore.applyUserInfoChange({
            avatar: data.avatar,
            name: data.name,
            signature: data.signature,
          })
        }
        // 3) 同步左侧单聊会话列表的头像/昵称
        chatStore.updateSingleSessionByUid({
          uid: data.uid,
          avatar: data.avatar,
          name: data.name,
        })
        break
      }
      default: {
        console.log('接收到未处理类型的消息:', params)
        break
      }
    }
  }
}

export default new WS()
