import { defineStore } from 'pinia'
import { ref } from 'vue'
import { seekerApi } from '../api'

export const useSeekerStore = defineStore('seeker', () => {
  const seekerId = ref(localStorage.getItem('seekerId') || '')
  const seekerName = ref(localStorage.getItem('seekerName') || '')
  const initialized = ref(!!seekerId.value)

  function setSeeker(id, name) {
    seekerId.value = id
    seekerName.value = name
    initialized.value = true
    localStorage.setItem('seekerId', id)
    localStorage.setItem('seekerName', name)
  }

  /** 确保有求职者身份：自动创建演示账号，无登录页面 */
  async function ensureSeeker() {
    if (initialized.value) return

    // 尝试从 localStorage 恢复
    const savedId = localStorage.getItem('seekerId')
    const savedName = localStorage.getItem('seekerName')
    if (savedId && savedName) {
      seekerId.value = savedId
      seekerName.value = savedName
      initialized.value = true
      return
    }

    // 自动获取演示求职者
    try {
      const seeker = await seekerApi.demoLogin()
      setSeeker(seeker.id, seeker.name)
    } catch {
      setSeeker('demo', '演示用户')
    }
  }

  return { seekerId, seekerName, initialized, setSeeker, ensureSeeker }
})
