<template>
  <HrLayout>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h2 style="margin: 0;">面试概览</h2>
      <el-button type="primary" @click="$router.push('/hr/interviews/create')">录入面试</el-button>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!interviews?.length" description="暂无面试记录" />
      <el-table v-else :data="interviews" stripe size="small">
        <el-table-column prop="candidateName" label="候选人" width="120" />
        <el-table-column prop="candidatePosition" label="应聘岗位" width="150" />
        <el-table-column prop="round" label="轮次" width="80" />
        <el-table-column prop="interviewerName" label="面试官" width="100" />
        <el-table-column prop="interviewDate" label="日期" width="120" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.result === 'PASSED'" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="row.result === 'FAILED'" type="danger" size="small">未通过</el-tag>
            <el-tag v-else type="info" size="small">待定</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="评分" width="80" />
      </el-table>
    </el-card>
  </HrLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { hrApi } from '../../api'
import HrLayout from './components/HrLayout.vue'

const loading = ref(false)
const interviews = ref([])

onMounted(async () => {
  loading.value = true
  try {
    interviews.value = await hrApi.getInterviews()
  } finally {
    loading.value = false
  }
})
</script>
