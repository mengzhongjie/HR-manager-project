<template>
  <HrLayout>
    <div style="margin-bottom: 16px;">
      <el-button @click="$router.push('/hr')" text><el-icon><ArrowLeft /></el-icon> 返回岗位选择</el-button>
      <span style="font-size: 18px; font-weight: 600; margin-left: 12px;">{{ position }} - 备选人才库</span>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-empty v-if="!candidates?.length" description="暂无备选候选人" />
      <el-table v-else :data="candidates" stripe size="small">
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="yearsOfExperience" label="工作年限" />
        <el-table-column prop="educationLevel" label="学历" />
        <el-table-column label="技术栈" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="s in row.techStack" :key="s" size="small" style="margin: 2px;">{{ s }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="restore(row.id)">恢复</el-button>
            <el-button size="small" @click="$router.push(`/hr/candidates/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </HrLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hrApi } from '../../api'
import HrLayout from './components/HrLayout.vue'

const route = useRoute()
const position = ref(route.params.position || '')
const loading = ref(false)
const candidates = ref([])

onMounted(loadBackup)

async function loadBackup() {
  loading.value = true
  try {
    candidates.value = await hrApi.getBackupList(position.value)
  } finally {
    loading.value = false
  }
}

async function restore(id) {
  try {
    await ElMessageBox.confirm('确认将该候选人恢复到候选人列表？', '确认')
  } catch { return }
  try {
    await hrApi.restoreCandidate(id)
    ElMessage.success('已恢复')
    await loadBackup()
  } catch (e) { /* handled */ }
}
</script>
