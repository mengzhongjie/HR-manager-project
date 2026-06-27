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

  /** 确保有求职者身份，没有则自动创建演示账号 */
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

    // 自动创建演示求职者
    const demoUser = 'demo_' + Date.now()
    try {
      const seeker = await seekerApi.login({
        username: demoUser,
        name: '演示用户',
        email: 'demo@example.com',
        phone: '13800138000'
      })
      setSeeker(seeker.id, seeker.name)
    } catch {
      // fallback: 使用固定 demo 账号
      const seeker = await seekerApi.login({
        username: 'demo',
        name: '演示用户',
        email: 'demo@example.com',
        phone: '13800138000'
      })
      setSeeker(seeker.id, seeker.name)
    }
  }

  return { seekerId, seekerName, initialized, setSeeker, ensureSeeker }
})
