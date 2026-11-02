import type { UserInfoType, UserItem } from '@/services/types'

// 1.用户扫描成功等待授权 2.用户登录成功返回用户信息 3.收到消息 4.上下线推送 5.禁用的用户 6.前端token失效
export enum WsResponseMessageType {
  /** 1.用户登录成功返回用户信息 */
  LoginSuccess = 1,
  /** 2.收到消息 */
  ReceiveMessage = 4,
  /** 3.上下线推送 */
  OnOffLine = 5,
  /** 5.禁用的用户 */
  InValidUser,
  /** 6.前端token失效 */
  TokenExpired = 6,
  /** 7.点赞、倒赞更新通知 */
  WSMsgMarkItem = 7,
  /** 消息撤回 */
  WSMsgRecall = 9,
  /** 新好友申请 */
  RequestNewFriend,
  /** 新好友会话 */
  NewFriendSession,
  /** 群申请通知（群主收到） */
  GroupApply = 1000,
  /** 13.用户信息变更 */
  UserInfoChange = 13,
}

/**
 * ws 请求 消息类型 1心跳检测 2用户认证
 */
export enum WsRequestMsgType {
  /** 1心跳检测 */
  HeartBeatDetection,
  /** 2用户认证 */
  Authorization,
}

export type WsReqMsgContentType = {
  type: WsRequestMsgType
  data?: Record<string, unknown>
}

export type LoginSuccessResType = Pick<UserInfoType, 'avatar' | 'name' | 'uid'> & {
  /** 用户的登录凭证，每次请求携带 */
  token: string
}

export type OnStatusChangeType = {
  changeList: UserItem[]
  onlineNum: number
  totalNum: number
}

/** 用户信息变更推送体 */
export type WSUserChangeType = {
  /** 用户id */
  uid: number
  /** 变更后的头像 */
  avatar?: string
  /** 变更后的昵称 */
  name?: string
  /** 变更后的个人介绍 */
  signature?: string
}
