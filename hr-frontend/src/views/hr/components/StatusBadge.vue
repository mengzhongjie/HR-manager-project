<template>
  <el-tag :type="tagType" :color="tagColor" effect="dark" size="small">
    {{ label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: '' }
})

const statusMap = {
  NEW: { color: '#409eff', label: '新候选人' },
  PENDING_ARCHIVE: { color: '#e6a23c', label: '存档待定' },
  INTERVIEW_INVITED: { color: '#8b5cf6', label: '面试邀约' },
  IN_INTERVIEW: { color: '#f56c6c', label: '面试中' },
  ROUND_1_PASSED: { color: '#67c23a', label: '一面通过' },
  ROUND_2_PASSED: { color: '#67c23a', label: '二面通过' },
  WAITING_OFFER: { color: '#909399', label: '待发Offer' },
  OFFERED: { color: '#67c23a', label: '已发Offer' },
  ONBOARDED: { color: '#303133', label: '已入职' },
  REJECTED: { color: '#c0c4cc', label: '已淘汰' }
}

const tagType = computed(() => {
  const map = {
    NEW: 'primary', PENDING_ARCHIVE: 'warning', INTERVIEW_INVITED: 'primary',
    IN_INTERVIEW: 'danger', ROUND_1_PASSED: 'success', ROUND_2_PASSED: 'success',
    WAITING_OFFER: 'info', OFFERED: 'success',
    ONBOARDED: 'dark', REJECTED: 'info'
  }
  return map[props.status] || 'info'
})

const tagColor = computed(() => statusMap[props.status]?.color)
const label = computed(() => statusMap[props.status]?.label || props.status)
</script>
