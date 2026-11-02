import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useWsLoginStore = defineStore('wsLogin', () => {
  const showLogin = ref(false)

  return {
    showLogin,
  }
})
