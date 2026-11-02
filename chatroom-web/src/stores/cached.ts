import { reactive, computed } from 'vue'
import { defineStore } from 'pinia'
import apis from '@/services/apis'
import { useGlobalStore } from '@/stores/global'
import type { CacheUserItem, CacheBadgeItem } from '@/services/types'
import { isDiffNow10Min } from '@/utils/computedTime'

export type BaseUserItem = Pick<CacheUserItem, 'uid' | 'avatar' | 'name'>

export const useCachedStore = defineStore(
  'cached',
  () => {
    const globalStore = useGlobalStore()
    const userCachedList = reactive<Record<number, Partial<CacheUserItem>>>({})
    const badgeCachedList = reactive<Record<number, Partial<CacheBadgeItem>>>({})

    const currentRoomId = computed(() => globalStore.currentSession.roomId)

    const atUsersMap = reactive<Record<number, BaseUserItem[]>>({ [currentRoomId.value]: [] }) // 消息Map

    const currentAtUsersList = computed({
      get: () => {
        const current = atUsersMap[currentRoomId.value]
        if (current === undefined) {
          atUsersMap[currentRoomId.value] = []
        }
        if (currentRoomId.value === 1) {
          return Object.values(userCachedList as BaseUserItem[])
        }
        return atUsersMap[currentRoomId.value]
      },
      set: (val) => {
        atUsersMap[currentRoomId.value] = val
      },
    })

    /** 批量获取用户详细信息 */
    const getBatchUserInfo = async (uids: number[]) => {
      // 没有 lastModifyTime 的要更新，lastModifyTime 距离现在 10 分钟已上的也要更新
      const result = uids
        .map((uid) => {
          const cacheUser = userCachedList[uid]
          return { uid, lastModifyTime: cacheUser?.lastModifyTime }
        })
        .filter((item) => !item.lastModifyTime || isDiffNow10Min(item.lastModifyTime))
      if (!result.length) return
      const itemIdSet: Set<number> = new Set()
      const data = await apis.getUserInfoBatch(result).send()
      data?.forEach((item) => {
        // 更新最后更新时间。
        const curItemResult = {
          ...(item?.needRefresh ? item : userCachedList[item.uid]),
          needRefresh: undefined,
          lastModifyTime: Date.now(),
        }
        userCachedList[item.uid] = curItemResult

        // 收集徽章 id并缓存
        // 可以改成 itemIds，可以更快收集完成。
        const wearingItemId = item.wearingItemId
        wearingItemId && itemIdSet.add(wearingItemId)
      })
      // 批量请求徽章详情
      getBatchBadgeInfo([...itemIdSet])
    }

    /** 批量获取用户徽章详细信息 */
    const getBatchBadgeInfo = async (itemIds: number[]) => {
      // 没有 lastModifyTime 的要更新，lastModifyTime 距离现在 10 分钟已上的也要更新
      const result = itemIds
        .map((itemId) => {
          const cacheBadge = badgeCachedList[itemId]
          return { itemId, lastModifyTime: cacheBadge?.lastModifyTime }
        })
        .filter((item) => !item.lastModifyTime || isDiffNow10Min(item.lastModifyTime))
      if (!result.length) return
      const data = await apis.getBadgesBatch(result).send()
      data?.forEach(
        (item) =>
          // 更新最后更新时间。
          (badgeCachedList[item.itemId] = {
            ...(item?.needRefresh ? item : badgeCachedList[item.itemId]),
            needRefresh: undefined,
            lastModifyTime: Date.now(),
          }),
      )
    }

    /** 房间内的所有群成员列表-@专用 */
    const initAllUserBaseInfo = async () => {
      if (sessionStorage.getItem('IS_INIT_USER_BASE') === null) {
        // await getAllUserBaseInfo()
        const data = await apis
          .getAllUserBaseInfo({ params: { roomId: currentRoomId.value } })
          .send()
        data?.forEach((item) => (userCachedList[item.uid] = item))
        sessionStorage.setItem('IS_INIT_USER_BASE', 'true')
      }
    }

    const getGroupAtUserBaseInfo = async () => {
      if (currentRoomId.value === 1) return
      const data = await apis.getAllUserBaseInfo({ params: { roomId: currentRoomId.value } }).send()
      currentAtUsersList.value = data
    }

    // 根据用户名关键字过滤用户，
    // FIXME 是否需要过滤自己
    const filterUsers = (searchKey: string) => {
      return currentAtUsersList.value?.filter((item) => item.name?.startsWith(searchKey))
    }

    /**
     * 通过用户ID列表获取用户基本信息
     * @param uidList
     */
    const filterUsersByUidList = (uidList: number[]) => {
      return currentAtUsersList.value.filter((user) => uidList.includes(user.uid))
    }

    /**
     * 收到用户信息变更推送时，更新缓存的用户信息（头像/昵称/个人介绍）。
     * 消息列表、群成员、联系人、用户卡片等展示均读取此缓存，更新后即同步刷新。
     *
     * @param data.uid      变更的是「谁」——缓存会以这个 uid 为 key 更新，
     *                      例如甲改了头像，data.uid = 甲 uid，那么只会修改 userCachedList[甲 uid]，
     *                      不会污染或改动当前登录用户的信息。
     * @param data.avatar   变更后的头像（可选，传 undefined 表示不改动）
     * @param data.name     变更后的昵称（可选，传 undefined 表示不改动）
     * @param data.signature 变更后的个人介绍（可选，传 undefined 表示不改动）
     */
    const updateUserInfo = (data: {
      uid: number
      avatar?: string
      name?: string
      signature?: string
    }) => {
      const { uid, avatar, name, signature } = data
      const existed = userCachedList[uid]
      // 仅更新已缓存的用户，避免为未拉取过的用户创建不完整记录。
      // 如果这个用户尚未拉取过缓存，第一次 useUserInfo(uid) 访问时会自动请求最新详情。
      if (!existed) return
      userCachedList[uid] = {
        ...existed,
        ...(avatar !== undefined ? { avatar } : {}),
        ...(name !== undefined ? { name } : {}),
        ...(signature !== undefined ? { signature } : {}),
        lastModifyTime: Date.now(),
      }
    }

    return {
      userCachedList,
      badgeCachedList,
      getBatchUserInfo,
      getBatchBadgeInfo,
      initAllUserBaseInfo,
      filterUsers,
      getGroupAtUserBaseInfo,
      currentAtUsersList,
      filterUsersByUidList,
      updateUserInfo,
    }
  },
  { persist: { storage: sessionStorage } },
)
