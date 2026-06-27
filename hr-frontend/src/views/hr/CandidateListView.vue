<template>
  <HrLayout>
    <div style="margin-bottom: 16px;">
      <el-button @click="$router.push(`/hr`)" text>
        <el-icon><ArrowLeft /></el-icon> 返回岗位选择
      </el-button>
      <span style="font-size: 18px; font-weight: 600; margin-left: 12px;">{{ position }} - 候选人列表</span>
    </div>

    <el-card shadow="never" style="margin-bottom: 16px;">
      <el-form :inline="true" :model="filter" size="small">
        <el-form-item label="姓名">
          <el-input v-model="filter.nameKeyword" placeholder="搜索姓名" clearable style="width: 150px;" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width: 130px;">
            <el-option v-for="s in statusList" :key="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="学历">
          <el-select v-model="filter.minEducationLevel" placeholder="全部" clearable style="width: 120px;">
            <el-option v-for="e in eduList" :key="e" :value="e" />
          </el-select>
        </el-form-item>
        <el-form-item label="应届">
          <el-select v-model="filter.isFreshGraduate" placeholder="全部" clearable style="width: 100px;">
            <el-option label="是" :value="true" />
            <el-option label="否" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作年限">
          <el-input-number v-model="filter.minExperience" :min="0" :max="50" placeholder="最低" style="width: 100px;" />
          <span style="margin: 0 8px;">-</span>
          <el-input-number v-model="filter.maxExperience" :min="0" :max="50" placeholder="最高" style="width: 100px;" />
        </el-form-item>
        <el-form-item label="毕业年份">
          <el-input-number v-model="filter.minGraduationYear" :min="2000" :max="2030" placeholder="起始" style="width: 110px;" />
          <span style="margin: 0 8px;">-</span>
          <el-input-number v-model="filter.maxGraduationYear" :min="2000" :max="2030" placeholder="结束" style="width: 110px;" />
        </el-form-item>
        <el-form-item label="AI评分">
          <el-input-number v-model="filter.minAiScore" :min="0" :max="100" placeholder="最低" style="width: 100px;" />
          <span style="margin: 0 8px;">-</span>
          <el-input-number v-model="filter.maxAiScore" :min="0" :max="100" placeholder="最高" style="width: 100px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadCandidates">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-table :data="candidates" stripe size="small" @row-click="goDetail">
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusBadge :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="yearsOfExperience" label="工作年限" width="100" />
        <el-table-column prop="educationLevel" label="学历" width="100" />
        <el-table-column label="技术栈" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="s in row.techStack" :key="s" size="small" style="margin: 2px 4px 2px 0;">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="AI资质" width="100" align="center">
          <template #default="{ row }">
            <el-progress v-if="row.aiQualification" :percentage="row.aiQualification.score" :width="40" type="dashboard" />
            <span v-else style="color: #ccc;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click.stop="$router.push(`/hr/candidates/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!candidates.length && !loading" description="暂无候选人" />
    </el-card>
  </HrLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { hrApi } from '../../api'
import HrLayout from './components/HrLayout.vue'
import StatusBadge from './components/StatusBadge.vue'

const route = useRoute()
const position = ref(route.params.position || '')
const loading = ref(false)
const candidates = ref([])

const statusList = ['NEW', 'PENDING_ARCHIVE', 'INTERVIEW_INVITED', 'IN_INTERVIEW', 'WAITING_OFFER', 'OFFERED', 'ONBOARDED', 'REJECTED']
const eduList = ['HIGH_SCHOOL', 'ASSOCIATE', 'BACHELOR', 'MASTER', 'PHD']

const filter = reactive({
  nameKeyword: '',
  status: '',
  minEducationLevel: '',
  isFreshGraduate: null,
  minExperience: null,
  maxExperience: null,
  minGraduationYear: null,
  maxGraduationYear: null,
  minAiScore: null,
  maxAiScore: null
})

onMounted(loadCandidates)

async function loadCandidates() {
  loading.value = true
  try {
    const params = { ...filter }
    // Remove empty values
    Object.keys(params).forEach(k => { if (params[k] === '' || params[k] === null) delete params[k] })
    candidates.value = await hrApi.getCandidates(position.value, params)
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  Object.keys(filter).forEach(k => { filter[k] = null })
  filter.isFreshGraduate = null
  loadCandidates()
}

function goDetail(row) {
  // Row click handled by button
}
</script>
