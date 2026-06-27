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
          <el-descriptions-item label="学历">{{ eduLabel(candidate?.educationLevel) }}</el-descriptions-item>
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
          <StatusBadge :status="candidate?.status" />
          <span v-if="candidate?.interviewRound > 0" style="color: #909399; font-size: 13px;">
            当前面试轮次：第 {{ candidate.interviewRound }} 轮
          </span>
          <el-button type="success" size="small" @click="showInviteDialog = true" :disabled="candidate?.status === 'WAITING_OFFER' || candidate?.status === 'OFFERED' || candidate?.status === 'ONBOARDED' || candidate?.status === 'REJECTED'">
            发送面试邀请
          </el-button>
          <el-button type="info" size="small" @click="runAiQualify" :loading="aiLoading">AI资质评定</el-button>
        </div>
        <div style="margin-top: 8px; font-size: 13px; color: #666;">
          状态更新：存档（<el-button text size="small" @click="archiveCandidate">存入备选库</el-button>）
          <span v-if="candidate?.status === 'WAITING_OFFER'">
            ｜发Offer：<el-button text size="small" type="success" @click="$router.push(`/hr/offers/create/${candidate?.id}`)">发放Offer</el-button>
          </span>
        </div>
        <div v-if="candidate?.aiQualification" style="margin-top: 8px;">
          AI评分: <strong :style="{ color: candidate.aiQualification.score >= 80 ? '#67c23a' : candidate.aiQualification.score >= 60 ? '#e6a23c' : '#909399' }">
            {{ candidate.aiQualification.score }}
          </strong>
          - 推荐: {{ aiRecLabel(candidate.aiQualification.recommendation) }}
        </div>
      </el-card>

      <!-- 发送面试邀请弹窗 -->
      <el-dialog v-model="showInviteDialog" title="发送面试邀请" width="400px">
        <el-form label-width="100px">
          <el-form-item label="候选人">
            <el-tag>{{ candidate?.name }}</el-tag>
          </el-form-item>
          <el-form-item label="预约面试时间">
            <el-date-picker v-model="inviteDate" type="date" placeholder="选择面试日期" style="width: 100%;" :disabled-date="disablePastDate" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showInviteDialog = false">取消</el-button>
          <el-button type="success" @click="confirmInvite" :loading="inviting">确认发送</el-button>
        </template>
      </el-dialog>

      <el-card shadow="never" style="margin-bottom: 16px;">
        <template #header><span style="font-weight: 600;">面试记录</span></template>
        <el-empty v-if="!interviews?.length" description="暂无面试记录" />
        <el-table v-else :data="interviews" stripe size="small">
          <el-table-column prop="round" label="轮次" width="80" />
          <el-table-column prop="interviewDate" label="预约日期" width="110" />
          <el-table-column label="面试状态" width="120">
            <template #default="{ row }">
              <el-tag :type="ivStatusType(row.interviewStatus)" size="small">{{ ivStatusLabel(row.interviewStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="评分" width="70" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <template v-if="row.interviewStatus === 'COMPLETED'">
                <el-tag v-if="row.result === 'PASSED'" type="success" size="small">通过</el-tag>
                <el-tag v-else-if="row.result === 'FAILED'" type="danger" size="small">未通过</el-tag>
                <el-tag v-else type="info" size="small">已完成</el-tag>
              </template>
              <el-tag v-else type="info" size="small">进行中</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="feedback" label="反馈" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <span v-if="row.interviewStatus === 'PENDING' || !row.interviewStatus" style="color: #909399; font-size: 12px;">等待响应</span>
              <el-button v-if="row.interviewStatus === 'ACCEPTED'" size="small" type="primary" @click="handleStartInterview(row)">开始面试</el-button>
              <el-button v-if="row.interviewStatus === 'IN_PROGRESS'" size="small" type="success" @click="openCompleteDialog(row)">结束面试</el-button>
              <span v-if="row.interviewStatus === 'COMPLETED'" style="color: #67c23a; font-size: 12px;">已完成</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 结束面试弹窗 -->
      <el-dialog v-model="showCompleteDialog" title="结束面试" width="450px">
        <el-form :model="completeForm" label-width="80px">
          <el-form-item label="评分">
            <el-input-number v-model="completeForm.score" :min="0" :max="100" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="结果" required>
            <el-select v-model="completeForm.result" style="width: 100%;">
              <el-option label="通过" value="PASSED" />
              <el-option label="未通过" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item label="反馈">
            <el-input v-model="completeForm.feedback" type="textarea" :rows="3" placeholder="面试反馈" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showCompleteDialog = false">取消</el-button>
          <el-button type="success" @click="handleCompleteInterview" :loading="completing">确认结束</el-button>
        </template>
      </el-dialog>

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

const statusLabelMap = { NEW: '新候选人', PENDING_ARCHIVE: '存档待定', INTERVIEW_INVITED: '面试邀约', IN_INTERVIEW: '面试中', ROUND_1_PASSED: '一面通过', ROUND_2_PASSED: '二面通过', WAITING_OFFER: '待发Offer', OFFERED: '已发Offer', ONBOARDED: '已入职', REJECTED: '已淘汰' }
function statusLabel(s) { return statusLabelMap[s] || s }

const route = useRoute()
const router = useRouter()
const candidateId = ref(route.params.id || '')

const loading = ref(false)
const aiLoading = ref(false)
const inviting = ref(false)
const candidate = ref(null)
const interviews = ref([])
const showInviteDialog = ref(false)
const inviteDate = ref('')
const showCompleteDialog = ref(false)
const completing = ref(false)
const completeForm = ref({ score: null, result: '', feedback: '' })
const activeInterviewId = ref('')

const ivStatusLabelMap = { PENDING: '待响应', ACCEPTED: '待面试', IN_PROGRESS: '面试中', COMPLETED: '已完成' }
function ivStatusLabel(s) { return ivStatusLabelMap[s] || '待响应' }
function ivStatusType(s) {
  const map = { PENDING: 'warning', ACCEPTED: 'primary', IN_PROGRESS: 'danger', COMPLETED: 'success' }
  return map[s] || 'info'
}
const eduLabelMap = { HIGH_SCHOOL: '高中', ASSOCIATE: '大专', BACHELOR: '本科', MASTER: '硕士', PHD: '博士' }
function eduLabel(e) { return eduLabelMap[e] || e }
const aiRecMap = { INTERVIEW_INVITED: '邀请面试', PENDING_REVIEW: '待HR审核', PENDING_ARCHIVE: '存档待定' }
function aiRecLabel(r) { return aiRecMap[r] || r }
function disablePastDate(time) {
  return time.getTime() < Date.now() - 8.64e7
}

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

async function confirmInvite() {
  inviting.value = true
  try {
    const params = {}
    if (inviteDate.value) {
      params.interviewDate = typeof inviteDate.value === 'string' ? inviteDate.value : inviteDate.value.toISOString().split('T')[0]
    }
    await hrApi.inviteInterview(candidateId.value, params)
    ElMessage.success('面试邀请已发送')
    showInviteDialog.value = false
    inviteDate.value = ''
    await loadDetail()
  } finally {
    inviting.value = false
  }
}

async function archiveCandidate() {
  try {
    await ElMessageBox.confirm(`确定将「${candidate.value?.name}」存入备选库？`, '确认', { type: 'warning' })
  } catch { return }
  try {
    await hrApi.updateStatus(candidateId.value, {
      status: 'PENDING_ARCHIVE', actor: 'HR', reason: '存入备选库'
    })
    ElMessage.success('已存入备选库')
    await loadDetail()
  } catch { /* handled */ }
}

function handleStartInterview(row) {
  ElMessageBox.confirm(`确认开始面试「${row.round}」？`, '确认', { type: 'info' }).then(async () => {
    try {
      await hrApi.startInterview(row.id)
      ElMessage.success('已进入面试')
      await loadDetail()
    } catch { /* handled */ }
  }).catch(() => {})
}

function openCompleteDialog(row) {
  activeInterviewId.value = row.id
  completeForm.value = { score: null, result: '', feedback: '' }
  showCompleteDialog.value = true
}

async function handleCompleteInterview() {
  if (!completeForm.value.result) { ElMessage.warning('请选择面试结果'); return }
  completing.value = true
  try {
    await hrApi.completeInterview(activeInterviewId.value, { ...completeForm.value })
    ElMessage.success('面试已结束')
    showCompleteDialog.value = false
    await loadDetail()
  } finally { completing.value = false }
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

async function sendInterviewInvite() {
  try {
    await ElMessageBox.confirm(`确定向「${candidate.value?.name}」发送面试邀请？`, '确认', { type: 'info' })
  } catch { return }
  inviting.value = true
  try {
    await hrApi.inviteInterview(candidateId.value)
    ElMessage.success('面试邀请已发送')
    await loadDetail()
  } finally {
    inviting.value = false
  }
}
</script>
