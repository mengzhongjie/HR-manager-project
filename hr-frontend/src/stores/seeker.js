import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useSeekerStore = defineStore('seeker', () => {
  const seekerId = ref(localStorage.getItem('seekerId') || '')
  const seekerName = ref(localStorage.getItem('seekerName') || '')

  function setSeeker(id, name) {
    seekerId.value = id
    seekerName.value = name
    localStorage.setItem('seekerId', id)
    localStorage.setItem('seekerName', name)
  }

  function logout() {
    seekerId.value = ''
    seekerName.value = ''
    localStorage.removeItem('seekerId')
    localStorage.removeItem('seekerName')
  }

  return { seekerId, seekerName, setSeeker, logout }
})
