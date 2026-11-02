<script lang="ts" setup>
import { ref, watch } from 'vue'

interface Props {
  /** 头像地址 */
  src?: string
  /** 图标尺寸 */
  size?: number
  /** 圆形(circle)和正方形(square)两种 */
  shape?: 'circle' | 'square'
  /** 是否显示状态 */
  showStatus?: boolean
  /** 在线状态 */
  online?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  src: '',
  size: 40,
  shape: 'circle',
  showStatus: false,
  online: true,
})

const hasError = ref(false)

// 监听 src 变化，重置错误状态 + 打印日志
watch(
  () => props.src,
  (newSrc, oldSrc) => {
    console.log('==== [Avatar] src 变化 ====')
    console.log('[Avatar] oldSrc:', oldSrc)
    console.log('[Avatar] newSrc:', newSrc)
    console.trace('[Avatar] src 变化调用栈')
    hasError.value = false
    debugger
  },
)

// img 加载成功
const onLoad = (e: Event) => {
  const img = e.target as HTMLImageElement
  console.log('==== [Avatar] img onLoad 成功 ====')
  console.log('[Avatar] img.src:', img.src)
  console.log('[Avatar] img.naturalWidth:', img.naturalWidth)
}

// img 加载失败
const onError = (e: Event) => {
  const img = e.target as HTMLImageElement
  console.error('==== [Avatar] img onError 加载失败 ====')
  console.error('[Avatar] 失败的 img.src:', img.src)
  console.error('[Avatar] 可能原因: 桶未公开读 / URL不可达 / CORS / 文件不存在')
  hasError.value = true
  debugger
}
</script>

<template>
  <div
    :class="['avatar', `avatar-${shape}`, { downline: !online }]"
    :style="{ width: size + 'px', height: size + 'px' }"
  >
    <template v-if="src">
      <img v-if="!hasError" :src="src" alt="avatar" @load="onLoad" @error="onError" />
      <div v-else class="avatar-fallback">
        <Icon icon="tupian1" :size="Number(size) * 0.6" />
      </div>
    </template>
    <slot v-else>
      <div class="avatar-fallback">
        <Icon icon="tupian1" :size="Number(size) * 0.6" />
      </div>
    </slot>
    <i v-if="showStatus" class="status" />
  </div>
</template>

<style lang="scss" src="./styles.scss" scoped />
