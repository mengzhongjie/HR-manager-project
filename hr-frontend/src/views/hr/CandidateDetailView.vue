<template>
  <HrLayout>
    <div style="margin-bottom: 16px;">
      <el-button @click="goBack" text><el-icon><ArrowLeft /></el-icon> 返回列表</el-button>
      <span style="font-size: 18px; font-weight: 600; margin-left: 12px;">候选人详情</span>
    </div>

    <div v-loading="loading">
      <el-card shadow="never" style="margin-bottom: 16px;">
        <template #header><span style="font-weight: 600;">基本信息</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="姓名">{{ candidate?.name }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ candidate?.email }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ candidate?.phone }}</el-descriptions-item>
          <el-descriptions-item label="应聘岗位">{{ candidate?.position }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <StatusBadge :status="candidate?.status" />
          </el-descriptions-item>
          <el-descriptions-item label="简历文件">
            <el-button v-if="candidate?.resumeFilePath" size="small" text type="primary" @click="viewResume">
              查看简历
            </el-button>
            <span v-else style="color: #ccc;">无</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" style="margin-bottom: 16px;">
        <template #header><span style="font-weight: 600;">履历详情</span></template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="工作年限">{{ candidate?.yearsOfExperience }} 年</el-descriptions-item>
          <el-descriptions-item label="应届">{{ candidate?.isFreshGraduate ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="毕业年份">{{ candidate?.graduationYear }}</el-descriptions-item>
          <el-descriptions-item label="学历">{{ candidate?.educationLevel }}</el-descriptions-item>
          <el-descriptions-item label="学校">{{ candidate?.school }}</el-descriptions-item>
          <el-descriptions-item label="专业">{{ candidate?.major }}</el-descriptions-item>
          <el-descriptions-item label="技术栈" :span="2">
            <el-tag v-for="s in candidate?.techStack" :key="s" size="small" style="margin: 2px 4px 2px 0;">{{ s }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="自我评价" :span="2">{{ candidate?.selfEvaluation }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" style="margin-bottom: 16px;">
        <template #header><span style="font-weight: 600;">状态管理</span></template>
        <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">
          <el-select v-model="newStatus" placeholder="选择新状态" style="width: 180px;">
            <el-option v-for="s in statusList" :key="s" :value="s" />
          </el-select>
          <el-input v-model="statusReason" placeholder="原因备注" style="width: 300px;" />
          <el-button type="warning" @click="updateStatus" :disabled="!newStatus">更新状态</el-button>
          <el-button type="info" @click="runAiQualify" :loading="aiLoading">执行AI资质评定</el-button>
        </div>
        <div v-if="candidate?.aiQualification" style="margin-top: 12px;">
          AI评分: <strong :style="{ color: candidate.aiQualification.score >= 80 ? '#67c23a' : candidate.aiQualification.score >= 60 ? '#e6a23c' : '#909399' }">
            {{ candidate.aiQualification.score }}
          </strong>
          - 推荐: {{ candidate.aiQualification.recommendation }}
        </div>
      </el-card>

      <el-card shadow="never" style="margin-bottom: 16px;">
        <template #header><span style="font-weight: 600;">面试记录</span></template>
        <el-empty v-if="!interviews?.length" description="暂无面试记录" />
        <el-table v-else :data="interviews" stripe size="small">
          <el-table-column prop="round" label="轮次" width="100" />
          <el-table-column prop="interviewerName" label="面试官" />
          <el-table-column prop="interviewDate" label="日期" />
          <el-table-column prop="result" label="结果" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.result === 'PASSED'" type="success" size="small">通过</el-tag>
              <el-tag v-else-if="row.result === 'FAILED'" type="danger" size="small">未通过</el-tag>
              <el-tag v-else type="info" size="small">待定</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="评分" width="80" />
          <el-table-column prop="feedback" label="反馈" min-width="150" />
        </el-table>
        <div style="margin-top: 12px;">
          <el-button size="small" type="primary" @click="$router.push(`/hr/interviews/create?candidateId=${candidate?.id}`)">
            添加面试记录
          </el-button>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header><span style="font-weight: 600;">状态变更历史</span></template>
        <el-empty v-if="!candidate?.statusHistory?.length" description="暂无变更记录" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="h in candidate.statusHistory"
            :key="h.eventId"
            :timestamp="h.timestamp"
            :color="h.toStatus === 'REJECTED' ? '#f56c6c' : '#409eff'"
          >
            <p>
              <StatusBadge :status="h.fromStatus" /> → <StatusBadge :status="h.toStatus" />
            </p>
            <p style="font-size: 12px; color: #909399;">操作人: {{ h.actor }} | 原因: {{ h.reason }}</p>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </div>
  </HrLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hrApi } from '../../api'
import HrLayout from './components/HrLayout.vue'
import StatusBadge from './components/StatusBadge.vue'

const route = useRoute()
const router = useRouter()
const candidateId = ref(route.params.id || '')

const loading = ref(false)
const aiLoading = ref(false)
const candidate = ref(null)
const interviews = ref([])
const newStatus = ref('')
const statusReason = ref('')

const statusList = ['NEW', 'PENDING_ARCHIVE', 'INTERVIEW_INVITED', 'IN_INTERVIEW', 'WAITING_OFFER', 'OFFERED', 'ONBOARDED', 'REJECTED']

onMounted(loadDetail)

async function loadDetail() {
  loading.value = true
  try {
    candidate.value = await hrApi.getCandidate(candidateId.value)
    interviews.value = await hrApi.getCandidateInterviews(candidateId.value)
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (candidate.value?.position) {
    router.push(`/hr/positions/${candidate.value.position}/candidates`)
  } else {
    router.push('/hr')
  }
}

function viewResume() {
  if (candidate.value?.resumeFilePath) {
    window.open(candidate.value.resumeFilePath, '_blank')
  }
}

async function updateStatus() {
  if (!newStatus.value) return
  try {
    await ElMessageBox.confirm(`确定将状态变更为 ${newStatus.value}？`, '确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await hrApi.updateStatus(candidateId.value, {
      status: newStatus.value,
      actor: 'HR',
      reason: statusReason.value || 'HR手动操作'
    })
    ElMessage.success('状态更新成功')
    await loadDetail()
    newStatus.value = ''
    statusReason.value = ''
  } catch (e) {
    // handled by interceptor
  }
}

async function runAiQualify() {
  aiLoading.value = true
  try {
    await hrApi.aiQualify(candidateId.value)
    ElMessage.success('AI资质评定完成')
    await loadDetail()
  } finally {
    aiLoading.value = false
  }
}
</script>
