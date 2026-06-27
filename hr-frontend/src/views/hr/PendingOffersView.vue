<template>
  <HrLayout>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h2 style="margin: 0;">待发Offer处理</h2>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!candidates?.length" description="全部已处理完毕" />
      <el-table v-else :data="candidates" stripe size="small">
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="position" label="应聘岗位" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusBadge :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="yearsOfExperience" label="工作年限" />
        <el-table-column prop="educationLevel" label="学历" />
        <el-table-column label="技术栈" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="s in row.techStack" :key="s" size="small" style="margin: 2px;">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="$router.push(`/hr/offers/create/${row.id}`)">发Offer</el-button>
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
import StatusBadge from './components/StatusBadge.vue'

const loading = ref(false)
const candidates = ref([])

onMounted(async () => {
  loading.value = true
  try {
    candidates.value = await hrApi.getPendingOffers()
  } finally {
    loading.value = false
  }
})
</script>
