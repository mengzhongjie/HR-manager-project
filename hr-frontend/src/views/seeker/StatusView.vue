<template>
  <SeekerLayout>
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <span style="font-size: 18px; font-weight: 600;">投递状态</span>
      </template>

      <el-empty v-if="!candidate" description="暂无投递记录" />

      <div v-else>
        <!-- 基本信息 -->
        <el-descriptions title="基本信息" :column="2" border style="margin-bottom: 20px;">
          <el-descriptions-item label="姓名">{{ candidate.name }}</el-descriptions-item>
          <el-descriptions-item label="应聘岗位">{{ candidate.position }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <StatusBadge :status="candidate.status" />
          </el-descriptions-item>
          <el-descriptions-item label="工作年限">{{ candidate.yearsOfExperience }} 年</el-descriptions-item>
          <el-descriptions-item label="学历">{{ candidate.educationLevel }}</el-descriptions-item>
          <el-descriptions-item label="技术栈">
            <el-tag v-for="s in candidate.techStack" :key="s" size="small" style="margin-right: 4px;">{{ s }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 面试记录 -->
        <el-card shadow="never" style="margin-bottom: 20px;">
          <template #header><span style="font-weight: 600;">面试记录</span></template>
          <el-empty v-if="!interviews?.length" description="暂无面试记录" />
          <el-table v-else :data="interviews" stripe size="small">
            <el-table-column prop="round" label="轮次" width="120" />
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
        </el-card>

        <!-- 面试邀约响应 -->
        <el-card v-if="candidate.status === 'INTERVIEW_INVITED'" shadow="never" style="margin-bottom: 20px;">
          <template #header><span style="font-weight: 600; color: #e6a23c;">面试邀约</span></template>
          <p>您有一份面试邀约，请尽快回复：</p>
          <div style="margin-top: 12px;">
            <el-button type="primary" @click="respondInterview(true)" :loading="responding">接受面试</el-button>
            <el-button type="danger" @click="respondInterview(false)" :loading="responding" style="margin-left: 12px;">拒绝面试</el-button>
          </div>
        </el-card>

        <!-- Offer响应 -->
        <el-card v-if="offer" shadow="never" style="margin-bottom: 20px;">
          <template #header>
            <span style="font-weight: 600; color: #67c23a;">Offer 信息</span>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="薪资">{{ offer.offeredSalary?.toLocaleString() }} 元</el-descriptions-item>
            <el-descriptions-item label="发放日期">{{ offer.offerDate }}</el-descriptions-item>
            <el-descriptions-item label="截止日期">{{ offer.expiryDate }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ offer.remark || '无' }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="offer.accepted == null" style="margin-top: 12px;">
            <el-button type="success" @click="respondOffer(true)" :loading="responding">接受Offer</el-button>
            <el-button type="danger" @click="respondOffer(false)" :loading="responding" style="margin-left: 12px;">拒绝Offer</el-button>
          </div>
          <el-tag v-else-if="offer.accepted === true" type="success" size="large" effect="dark">已接受 Offer</el-tag>
          <el-tag v-else type="danger" size="large" effect="dark">已拒绝 Offer</el-tag>
        </el-card>
      </div>
    </el-card>
  </SeekerLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { seekerApi } from '../../api'
import { useSeekerStore } from '../../stores/seeker'
import SeekerLayout from '../hr/components/SeekerLayout.vue'
import StatusBadge from '../hr/components/StatusBadge.vue'

const store = useSeekerStore()

const loading = ref(false)
const responding = ref(false)
const candidate = ref(null)
const interviews = ref([])
const offer = ref(null)

onMounted(async () => {
  await loadStatus()
})

async function loadStatus() {
  if (!store.seekerId) return
  loading.value = true
  try {
    const data = await seekerApi.getStatus(store.seekerId)
    candidate.value = data.candidate
    interviews.value = data.interviews || []
    offer.value = data.offer
  } catch (e) {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function respondInterview(accept) {
  const action = accept ? '接受' : '拒绝'
  try {
    await ElMessageBox.confirm(`确定${action}面试邀约？`, '确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  responding.value = true
  // Find the interview record ID from the list - latest INTERVIEW_INVITED related one
  const interviewId = interviews.value?.[0]?.id
  if (!interviewId) { ElMessage.error('未找到面试记录'); responding.value = false; return }
  try {
    const msg = await seekerApi.respondInterview(interviewId, accept)
    ElMessage.success(msg)
    await loadStatus()
  } finally {
    responding.value = false
  }
}

async function respondOffer(accept) {
  const action = accept ? '接受' : '拒绝'
  try {
    await ElMessageBox.confirm(`确定${action}Offer？此操作不可撤销`, '确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  responding.value = true
  try {
    const msg = await seekerApi.respondOffer(offer.value.id, accept)
    ElMessage.success(msg)
    await loadStatus()
  } finally {
    responding.value = false
  }
}
</script>
