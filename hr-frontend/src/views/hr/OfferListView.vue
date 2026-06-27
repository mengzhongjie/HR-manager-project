<template>
  <HrLayout>
    <h2 style="margin-bottom: 20px;">Offer管理</h2>
    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!offers?.length" description="暂无Offer记录" />
      <el-table v-else :data="offers" stripe size="small">
        <el-table-column prop="candidateName" label="候选人" width="120" />
        <el-table-column prop="candidatePosition" label="应聘岗位" />
        <el-table-column prop="offeredSalary" label="薪资" width="120">
          <template #default="{ row }">{{ row.offeredSalary?.toLocaleString() }} 元</template>
        </el-table-column>
        <el-table-column prop="offerDate" label="发放日期" width="120" />
        <el-table-column prop="expiryDate" label="截止日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.accepted == null" type="warning" size="small">待响应</el-tag>
            <el-tag v-else-if="row.accepted" type="success" size="small">已接受</el-tag>
            <el-tag v-else type="danger" size="small">已拒绝</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-if="row.accepted === true"
              size="small"
              type="primary"
              @click="$router.push(`/hr/onboarding/create/${row.id}`)"
            >
              入职登记
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </HrLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { hrApi } from '../../api'
import HrLayout from './components/HrLayout.vue'

const loading = ref(false)
const offers = ref([])

onMounted(async () => {
  loading.value = true
  try {
    offers.value = await hrApi.getOffers()
  } finally {
    loading.value = false
  }
})
</script>
