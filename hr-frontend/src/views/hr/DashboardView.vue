<template>
  <HrLayout>
    <h2 style="margin-bottom: 20px;">岗位选择</h2>
    <div v-loading="loading">
      <el-row v-if="positions.length" :gutter="20">
        <el-col v-for="pos in positions" :key="pos" :xs="24" :sm="12" :md="8" :lg="6" style="margin-bottom: 20px;">
          <el-card shadow="hover" class="position-card">
            <div style="text-align: center;">
              <el-icon :size="36" color="#409eff"><UserFilled /></el-icon>
              <h3 style="margin: 12px 0;">{{ pos }}</h3>
              <div style="color: #909399; font-size: 13px;">
                候选人: <strong style="color: #409eff;">{{ counts[pos] || 0 }}</strong>
              </div>
            </div>
            <el-divider />
            <div style="display: flex; justify-content: space-around; font-size: 12px;">
              <span>新: <strong style="color: #409eff;">{{ statusCounts[pos]?.NEW || 0 }}</strong></span>
              <span>面试中: <strong style="color: #e6a23c;">{{ statusCounts[pos]?.IN_INTERVIEW || 0 }}</strong></span>
              <span>已入职: <strong style="color: #67c23a;">{{ statusCounts[pos]?.ONBOARDED || 0 }}</strong></span>
            </div>
            <template #footer>
              <el-button type="primary" @click="$router.push(`/hr/positions/${pos}/candidates`)" style="width: 100%;">
                管理候选人
              </el-button>
              <el-button @click="$router.push(`/hr/positions/${pos}/backup`)" style="width: 100%; margin-top: 8px;">
                备选列表
              </el-button>
            </template>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无岗位数据" />
    </div>
  </HrLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { hrApi } from '../../api'
import HrLayout from './components/HrLayout.vue'

const loading = ref(false)
const positions = ref([])
const counts = ref({})
const statusCounts = ref({})

onMounted(async () => {
  loading.value = true
  try {
    const stats = await hrApi.getPositionStats()
    positions.value = stats.positions || []
    counts.value = stats.counts || {}
    statusCounts.value = stats.statusCounts || {}
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.position-card { transition: transform .2s; }
.position-card:hover { transform: translateY(-4px); }
</style>
