<script setup lang="ts">
import { computed, reactive, ref, watch, watchEffect } from 'vue'
import { useRequest } from 'alova'
import { ElMessage } from 'element-plus'
import { Select, CloseBold, EditPen, Camera } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCachedStore } from '@/stores/cached'
import { SexEnum, IsYetEnum } from '@/enums'
import type { BadgeType } from '@/services/types'
import apis from '@/services/apis'
import { judgeClient } from '@/utils/detectDevice'

const client = judgeClient()

const props = defineProps(['modelValue'])
const emit = defineEmits(['update:modelValue'])

const value = computed({
  get() {
    return props.modelValue
  },
  set(value) {
    emit('update:modelValue', value)
  },
})

const editName = reactive({
  isEdit: false,
  tempName: '',
  saving: false,
})

const editSignature = reactive({
  isEdit: false,
  tempSignature: '',
  saving: false,
})

const userStore = useUserStore()
const cachedStore = useCachedStore()

const userInfo = computed(() => userStore.userInfo)
const { send: handlerGetBadgeList, data: badgeList } = useRequest(apis.getBadgeList, {
  initialData: [],
  immediate: false,
})

watchEffect(() => {
  if (value.value) {
    handlerGetBadgeList()
    userStore.getUserDetailAction()
  }
})

// 监听 userInfo.avatar 变化（验证 UserSettingBox 内部 ElAvatar 是否能拿到新值）
watch(
  () => userInfo.value.avatar,
  (newVal, oldVal) => {
    console.log('==== [UserSettingBox] userInfo.avatar watch 触发 ====')
    console.log('[UserSettingBox] oldVal:', oldVal)
    console.log('[UserSettingBox] newVal:', newVal)
    console.trace('[UserSettingBox] 调用栈')
    debugger
  },
)

// ElAvatar 绑定的 src（带 fallback）
const avatarSrc = computed(() => {
  const src =
    userInfo.value?.avatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
  console.log('[UserSettingBox] avatarSrc computed:', src)
  return src
})

// ElAvatar 加载成功
const onAvatarLoad = (e: Event) => {
  const img = e.target as HTMLImageElement
  console.log('==== [UserSettingBox] ElAvatar img onLoad ====')
  console.log('[UserSettingBox] img.src:', img.src)
  console.log('[UserSettingBox] img.naturalWidth:', img.naturalWidth)
}

// ElAvatar 加载失败
const onAvatarError = (e: Event) => {
  const img = e.target as HTMLImageElement
  console.error('==== [UserSettingBox] ElAvatar img onError ====')
  console.error('[UserSettingBox] 失败的 img.src:', img.src)
  console.error('[UserSettingBox] 可能原因: 桶未公开读 / URL不可达 / CORS / 文件不存在')
  debugger
}

const currentBadge = computed(() =>
  badgeList.value.find((item) => item.obtain === IsYetEnum.YES && item.wearing === IsYetEnum.YES),
)

// 更新缓存里面的用户信息
const updateCurrentUserCache = (
  key: 'name' | 'wearingItemId' | 'avatar' | 'signature',
  value: any,
) => {
  const currentUser = userStore.userInfo.uid && cachedStore.userCachedList[userStore.userInfo.uid]
  if (currentUser) {
    currentUser[key] = value // 更新缓存里面的用户信息
  }
}

// 头像修改相关
const avatarUploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const AVATAR_MAX_SIZE = 10 * 1024 * 1024 // 10MB
const AVATAR_ACCEPT_TYPES = ['image/jpeg', 'image/png', 'image/jpg']

// 触发文件选择
const onPickAvatar = () => {
  if (avatarUploading.value) return
  fileInputRef.value?.click()
}

// 直接 PUT 文件到 MinIO 预签名 URL（不带 Authorization 等业务 header）
const uploadToMinIO = (uploadUrl: string, file: File) => {
  return new Promise((resolve, reject) => {
    console.log('==== [uploadToMinIO] 开始 ====')
    console.log('[uploadToMinIO] uploadUrl:', uploadUrl)
    console.log('[uploadToMinIO] file:', file.name, file.type, file.size, 'bytes')
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', uploadUrl, true)
    xhr.setRequestHeader('Content-Type', file.type)
    xhr.onload = () => {
      console.log('[uploadToMinIO] onload status:', xhr.status, xhr.statusText)
      console.log('[uploadToMinIO] response:', xhr.response)
      if (xhr.status === 200) {
        console.log('==== [uploadToMinIO] 成功 ====')
        debugger
        resolve(true)
      } else {
        console.error('==== [uploadToMinIO] 失败 status:', xhr.status, '====')
        debugger
        reject(new Error(`文件上传失败 status=${xhr.status}`))
      }
    }
    xhr.onerror = () => {
      console.error('==== [uploadToMinIO] onerror 网络错误 ====')
      console.error('[uploadToMinIO] 可能原因: CORS / 签名URL过期 / MinIO未启动 / 桶无写权限')
      debugger
      reject(new Error('文件上传失败(网络错误/CORS)'))
    }
    xhr.send(file)
  })
}

// 选择文件后的处理
const onAvatarChange = async (e: Event) => {
  console.log('========== [onAvatarChange] 触发 ==========')
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  console.log('[onAvatarChange] file:', file)
  // 重置 input，便于重复选择同一个文件
  target.value = ''
  if (!file) {
    console.warn('[onAvatarChange] 未选择文件，退出')
    return
  }
  console.log('[onAvatarChange] 文件信息:', { name: file.name, type: file.type, size: file.size })

  // 校验文件类型
  if (!AVATAR_ACCEPT_TYPES.includes(file.type)) {
    console.warn('[onAvatarChange] 类型不通过:', file.type)
    ElMessage.warning('仅支持 jpg/png 格式的图片')
    return
  }
  // 校验文件大小
  if (file.size > AVATAR_MAX_SIZE) {
    console.warn('[onAvatarChange] 大小不通过:', file.size)
    ElMessage.warning('图片大小不能超过 10MB')
    return
  }
  console.log('[onAvatarChange] 校验通过 ✅')

  avatarUploading.value = true
  try {
    // 1. 获取预签名上传链接（文件名加时间戳前缀，保证唯一，避免浏览器对同路径资源缓存旧图）
    const uniqueName = `${Date.now()}_${file.name}`
    console.log('[onAvatarChange] 步骤1 请求预签名URL, fileName:', uniqueName, 'scene: 3')
    debugger
    const { uploadUrl, downloadUrl } = await apis
      .getUploadUrl({ fileName: uniqueName, scene: '3' })
      .send()
    console.log('[onAvatarChange] 步骤1 返回:')
    console.log('  uploadUrl:', uploadUrl)
    console.log('  downloadUrl:', downloadUrl)
    debugger
    if (!uploadUrl || !downloadUrl) {
      console.error('[onAvatarChange] 预签名URL为空 ❌')
      ElMessage.error('获取上传链接失败')
      return
    }
    // 2. 直接 PUT 文件二进制到 MinIO
    console.log('[onAvatarChange] 步骤2 PUT到MinIO...')
    debugger
    await uploadToMinIO(uploadUrl, file)
    console.log('[onAvatarChange] 步骤2 上传完成 ✅')
    // 3. 调用后端写入用户头像
    console.log('[onAvatarChange] 步骤3 调用 modifyAvatar, avatar:', downloadUrl)
    debugger
    await apis.modifyAvatar(downloadUrl).send()
    console.log('[onAvatarChange] 步骤3 后端写入完成 ✅')
    // 4. 本地更新用户信息与缓存（updateAvatar 内部会替换对象引用 + 回写 sessionStorage）
    console.log('[onAvatarChange] 步骤4 调用 updateAvatar, 当前 avatar:', userStore.userInfo.avatar)
    debugger
    userStore.updateAvatar(downloadUrl)
    console.log('[onAvatarChange] 步骤4 updateAvatar 后, avatar:', userStore.userInfo.avatar)
    updateCurrentUserCache('avatar', downloadUrl)
    console.log(
      '[onAvatarChange] 步骤4 缓存已更新, cachedStore.userCachedList:',
      cachedStore.userCachedList,
    )
    debugger
    ElMessage.success('头像修改成功')
    console.log('========== [onAvatarChange] 全部完成 ✅ ==========')
  } catch (error: any) {
    console.error('========== [onAvatarChange] 失败 ❌ ==========')
    console.error('[onAvatarChange] error:', error)
    console.error('[onAvatarChange] error.message:', error?.message)
    console.error('[onAvatarChange] error.stack:', error?.stack)
    debugger
    ElMessage.error(error?.message || '头像修改失败')
  } finally {
    avatarUploading.value = false
  }
}

// 佩戴卸下徽章
const toggleWarningBadge = async (badge: BadgeType) => {
  if (!badge?.id) return
  await apis.setUserBadge(badge.id).send()
  handlerGetBadgeList()
  badge.img && (userInfo.value.badge = badge.img)
  updateCurrentUserCache('wearingItemId', badge.id) // 更新缓存里面的用户徽章
}

// 编辑用户名
const onEditName = () => {
  editName.isEdit = true
  editName.tempName = userInfo.value.name || ''
}

// 确认保存用户名
const onSaveUserName = async () => {
  if (!editName.tempName || editName.tempName.trim() === '') {
    ElMessage.warning('用户名不能为空哦~')
    return
  }
  if (editName.tempName === userInfo.value.name) {
    ElMessage.warning('用户名和当前一样的哦~')
    return
  }
  editName.saving = true

  await apis.modifyUserName(editName.tempName).send() // 更改用户名
  // updateName 内部整体替换对象引用 + 回写 sessionStorage + 减少更名次数
  userStore.updateName(editName.tempName)
  updateCurrentUserCache('name', editName.tempName) // 更新缓存里面的用户信息
  // 重置状态
  onCancelEditName()
}
// 确认保存用户名
const onCancelEditName = async () => {
  editName.saving = false
  editName.isEdit = false
  editName.tempName = ''
}

// 编辑个人介绍
const onEditSignature = () => {
  editSignature.isEdit = true
  editSignature.tempSignature = userInfo.value.signature || ''
}

// 保存个人介绍
const onSaveSignature = async () => {
  if (editSignature.tempSignature.trim() === '') {
    ElMessage.warning('个人介绍不能为空哦~')
    return
  }
  if (editSignature.tempSignature === (userInfo.value.signature || '')) {
    ElMessage.warning('个人介绍和当前一样的哦~')
    return
  }
  editSignature.saving = true
  try {
    await apis.modifySignature(editSignature.tempSignature).send()
    userStore.updateSignature(editSignature.tempSignature)
    updateCurrentUserCache('signature', editSignature.tempSignature)
    ElMessage.success('个人介绍修改成功')
    onCancelEditSignature()
  } catch (error) {
    ElMessage.error('个人介绍修改失败')
  } finally {
    editSignature.saving = false
  }
}

// 取消编辑个人介绍
const onCancelEditSignature = () => {
  editSignature.isEdit = false
  editSignature.tempSignature = ''
}
</script>

<template>
  <ElDialog
    class="setting-box-modal"
    v-model="value"
    :width="client === 'PC' ? 580 : '85%'"
    :close-on-click-modal="false"
    center
  >
    <div class="setting-box">
      <div class="setting-avatar-box">
        <ElAvatar size="large" class="setting-avatar" :src="avatarSrc" />
        <div class="setting-avatar-mask" @click="onPickAvatar">
          <el-icon v-if="!avatarUploading" :size="24" color="#fff"><Camera /></el-icon>
          <el-icon v-else :size="24" color="#fff" class="is-loading"><IEpLoading /></el-icon>
        </div>
        <el-icon
          size="20"
          color="var(--font-main)"
          class="setting-avatar-sex"
          v-if="userInfo.sex && [SexEnum.MAN, SexEnum.REMALE].includes(userInfo.sex)"
          :style="{
            backgroundColor: `var(${
              userInfo.sex === SexEnum.MAN ? '--avatar-sex-bg-man' : '--avatar-sex-bg-female'
            })`,
          }"
        >
          <IEpFemale v-if="userInfo.sex === SexEnum.MAN" />
          <IEpMale v-if="userInfo.sex === SexEnum.REMALE" />
        </el-icon>
        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/png,image/jpg"
          class="setting-avatar-input"
          @change="onAvatarChange"
        />
      </div>

      <div class="setting-name">
        <div class="name-edit-wrapper" v-show="editName.isEdit === false">
          <span class="user-name">
            <el-tooltip effect="dark" :content="currentBadge?.describe" placement="top">
              <img class="setting-badge" :src="currentBadge?.img" v-show="currentBadge" />
            </el-tooltip>
            {{ userInfo.name || '-' }}
          </span>
          <span class="user-uid" v-if="userInfo.uid">ID: {{ userInfo.uid }}</span>
          <el-tooltip class="box-item" effect="dark" content="编辑用户名" placement="right">
            <el-button
              class="name-edit-icon pointer"
              size="small"
              :icon="EditPen"
              circle
              @click="onEditName"
            />
          </el-tooltip>
        </div>
        <div class="name-edit-wrapper" v-show="editName.isEdit">
          <ElInput
            type="text"
            v-model="editName.tempName"
            :maxlength="20"
            show-word-limit
            class="name-edit-input"
          />
          <el-button
            class="name-edit-icon"
            size="small"
            type="primary"
            :icon="Select"
            circle
            @click="onSaveUserName"
          />
          <el-button
            class="name-edit-icon"
            size="small"
            type="danger"
            :icon="CloseBold"
            circle
            @click="onCancelEditName"
          />
        </div>
      </div>

      <div class="setting-signature">
        <div class="signature-label">个人介绍</div>
        <div class="signature-edit-wrapper" v-show="editSignature.isEdit === false">
          <span
            class="signature-content"
            :title="userInfo.signature || '暂无个人介绍，点击编辑添加吧~'"
          >
            {{ userInfo.signature || '暂无个人介绍' }}
          </span>
          <el-tooltip class="box-item" effect="dark" content="编辑个人介绍" placement="right">
            <el-button
              class="signature-edit-icon"
              size="small"
              :icon="EditPen"
              circle
              @click="onEditSignature"
            />
          </el-tooltip>
        </div>
        <div class="signature-edit-wrapper" v-show="editSignature.isEdit">
          <ElInput
            type="textarea"
            v-model="editSignature.tempSignature"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="介绍下自己吧~"
          />
          <div class="signature-edit-actions">
            <el-button
              size="small"
              type="primary"
              :icon="Select"
              @click="onSaveSignature"
              :loading="editSignature.saving"
              >保存</el-button
            >
            <el-button size="small" type="danger" :icon="CloseBold" @click="onCancelEditSignature"
              >取消</el-button
            >
          </div>
        </div>
      </div>

      <ul class="badge-list">
        <li class="badge-item" v-for="badge of badgeList" :key="badge.id">
          <!-- <img
            class="badge-item-icon"
            :class="{ 'badge-item-icon-has': badge.obtain === IsYetEnum.YES }"
            :src="badge.img"
            alt="badge"
          /> -->
          <div class="badge-item-mask">
            <template v-if="badge.obtain === IsYetEnum.YES">
              <el-button
                size="small"
                v-if="badge.wearing === IsYetEnum.NO"
                @click="toggleWarningBadge(badge)"
              >
                佩戴
              </el-button>
              <!-- <el-button size="small" v-if="badge.wearing === IsYetEnum.YES">卸下</el-button> -->
            </template>
            <el-tooltip effect="dark" :content="badge.describe" placement="top">
              <el-icon class="badge-item-info" color="var(--font-main)"><IEpInfoFilled /></el-icon>
            </el-tooltip>
          </div>
        </li>
      </ul>
    </div>
  </ElDialog>
</template>

<style lang="scss" src="./styles.scss" scoped />
