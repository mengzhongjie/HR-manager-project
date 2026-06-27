<template>
  <SeekerLayout>
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <span style="font-size: 18px; font-weight: 600;">我的投递</span>
      </template>

      <el-empty v-if="!candidates?.length" description="暂无投递记录，去岗位列表投递吧" />

      <div v-else>
        <el-table :data="candidates" stripe size="small" style="margin-bottom: 20px;">
          <el-table-column prop="candidate.position" label="应聘岗位" width="150" />
          <el-table-column label="当前状态" width="120">
            <template #default="{ row }"><StatusBadge :status="row.candidate.status" /></template>
          </el-table-column>
          <el-table-column label="工作年限" width="100">
            <template #default="{ row }">{{ row.candidate.yearsOfExperience }} 年</template>
          </el-table-column>
          <el-table-column label="学历" width="80">
            <template #default="{ row }">{{ eduLabel(row.candidate.educationLevel) }}</template>
          </el-table-column>
          <el-table-column label="技术栈" min-width="180">
            <template #default="{ row }">
              <el-tag v-for="s in row.candidate.techStack" :key="s" size="small" style="margin:2px 4px 2px 0;">{{ s }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="expandRow(row.candidate.id)">详情</el-button>
              <el-button size="small" type="primary" @click="openEditDialog(row.candidate)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 展开的详情 -->
        <template v-if="expandedId">
          <div v-for="item in candidates" :key="item.candidate.id">
            <div v-if="item.candidate.id === expandedId">
              <el-card shadow="never" style="margin-bottom: 20px;">
                <template #header>面试记录 - {{ item.candidate.position }}</template>
                <el-empty v-if="!item.interviews?.length" :description="item.candidate.status === 'INTERVIEW_INVITED' ? 'HR已邀约，面试记录待录入' : '暂无面试记录'" />
                <el-table v-else :data="item.interviews" stripe size="small">
                  <el-table-column prop="round" label="轮次" width="120" />
                  <el-table-column prop="interviewerName" label="面试官" />
                  <el-table-column prop="interviewDate" label="日期" />
                  <el-table-column prop="result" label="结果" width="100">
                    <template #default="{ row: iv }">
                      <el-tag v-if="iv.result === 'PASSED'" type="success" size="small">通过</el-tag>
                      <el-tag v-else-if="iv.result === 'FAILED'" type="danger" size="small">未通过</el-tag>
                      <el-tag v-else type="info" size="small">待定</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="score" label="评分" width="80" />
                  <el-table-column prop="feedback" label="反馈" min-width="150" />
                </el-table>
                <div v-if="item.candidate.status === 'INTERVIEW_INVITED'" style="margin-top: 12px;">
                  <el-button type="primary" size="small" @click="respondInterview(item, true)">接受面试</el-button>
                  <el-button type="danger" size="small" @click="respondInterview(item, false)" style="margin-left:8px;">拒绝面试</el-button>
                </div>
              </el-card>

              <el-card v-if="item.offer" shadow="never" style="margin-bottom: 20px;">
                <template #header>Offer 信息 - {{ item.candidate.position }}</template>
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="薪资">{{ item.offer.offeredSalary?.toLocaleString() }} 元</el-descriptions-item>
                  <el-descriptions-item label="发放日期">{{ item.offer.offerDate }}</el-descriptions-item>
                  <el-descriptions-item label="截止日期">{{ item.offer.expiryDate }}</el-descriptions-item>
                  <el-descriptions-item label="备注">{{ item.offer.remark || '无' }}</el-descriptions-item>
                </el-descriptions>
                <div v-if="item.offer.accepted == null" style="margin-top: 12px;">
                  <el-button type="success" @click="respondOffer(item, true)">接受Offer</el-button>
                  <el-button type="danger" @click="respondOffer(item, false)" style="margin-left:8px;">拒绝Offer</el-button>
                </div>
                <el-tag v-else-if="item.offer.accepted === true" type="success" size="large" effect="dark">已接受</el-tag>
                <el-tag v-else type="danger" size="large" effect="dark">已拒绝</el-tag>
              </el-card>
            </div>
          </div>
        </template>
      </div>
    </el-card>

    <!-- 编辑弹窗（复用 StatusView 的编辑表单） -->
    <el-dialog v-model="editVisible" title="编辑/补充信息" width="650px">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-form-item label="姓名" prop="name"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="editForm.email" /></el-form-item>
        <el-form-item label="电话" prop="phone"><el-input v-model="editForm.phone" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工作年限" prop="yearsOfExperience">
              <el-input-number v-model="editForm.yearsOfExperience" :min="0" :max="50" style="width:100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应届">
              <el-select v-model="editForm.isFreshGraduate" style="width:100%;">
                <el-option label="是" :value="true" /><el-option label="否" :value="false" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="学历" prop="educationLevel">
              <el-select v-model="editForm.educationLevel" style="width:100%;">
                <el-option label="高中" value="HIGH_SCHOOL" /><el-option label="大专" value="ASSOCIATE" />
                <el-option label="本科" value="BACHELOR" /><el-option label="硕士" value="MASTER" /><el-option label="博士" value="PHD" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="毕业年份"><el-input-number v-model="editForm.graduationYear" :min="2000" :max="2030" style="width:100%;" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="学校"><el-input v-model="editForm.school" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="专业"><el-input v-model="editForm.major" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="技术栈" prop="techStack">
          <el-select v-model="editForm.techStack" multiple filterable allow-create default-first-option style="width:100%;" placeholder="输入技术关键词">
            <el-option v-for="t in commonTech" :key="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作经历"><el-input v-model="editForm.workHistory" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="自我评价"><el-input v-model="editForm.selfEvaluation" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </SeekerLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { seekerApi, hrApi } from '../../api'
import { useSeekerStore } from '../../stores/seeker'
import SeekerLayout from '../hr/components/SeekerLayout.vue'
import StatusBadge from '../hr/components/StatusBadge.vue'

const store = useSeekerStore()
const commonTech = ['Java','Spring','SpringBoot','MyBatis','MySQL','Redis','MongoDB','RabbitMQ','Kafka','Docker','Kubernetes','Vue','React','Python','Go','Linux','微服务','分布式','高并发','JVM','SQL','Elasticsearch']

const eduLabelMap = { HIGH_SCHOOL: '高中', ASSOCIATE: '大专', BACHELOR: '本科', MASTER: '硕士', PHD: '博士' }
function eduLabel(e) { return eduLabelMap[e] || e }

const loading = ref(false)
const candidates = ref([])
const expandedId = ref(null)

// 编辑
const editVisible = ref(false)
const saving = ref(false)
const editFormRef = ref(null)
const editCandidateId = ref('')
const editForm = ref({ name: '', email: '', phone: '', yearsOfExperience: 0, isFreshGraduate: false, graduationYear: null, educationLevel: '', school: '', major: '', techStack: [], workHistory: '', selfEvaluation: '' })
const phoneRule = { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号', trigger: 'blur' }
const emailRule = { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
const editRules = { name: [{ required: true, message: '请输入姓名' }], email: [emailRule], phone: [{ required: true, message: '请输入电话' }, phoneRule], yearsOfExperience: [{ required: true, message: '请填写工作年限' }], educationLevel: [{ required: true, message: '请选择学历' }], techStack: [{ required: true, message: '请至少选择一项技术栈' }] }

onMounted(async () => {
  await store.ensureSeeker()
  await loadStatus()
})

async function loadStatus() {
  if (!store.seekerId) return
  loading.value = true
  try {
    const data = await seekerApi.getStatus(store.seekerId)
    if (data.candidates && data.candidates.length) {
      candidates.value = data.candidates
    }
  } finally { loading.value = false }
}

function expandRow(id) {
  expandedId.value = expandedId.value === id ? null : id
}

function openEditDialog(c) {
  if (!c) return
  editCandidateId.value = c.id
  editForm.value = { name: c.name || '', email: c.email || '', phone: c.phone || '', yearsOfExperience: c.yearsOfExperience ?? 0, isFreshGraduate: c.isFreshGraduate ?? false, graduationYear: c.graduationYear ?? null, educationLevel: c.educationLevel || '', school: c.school || '', major: c.major || '', techStack: c.techStack || [], workHistory: c.workHistory || '', selfEvaluation: c.selfEvaluation || '' }
  editVisible.value = true
}

async function handleSaveEdit() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await seekerApi.updateCandidate(editCandidateId.value, { ...editForm.value })
    ElMessage.success('信息已保存')
    editVisible.value = false
    await loadStatus()
  } finally { saving.value = false }
}

async function respondInterview(item, accept) {
  const action = accept ? '接受' : '拒绝'
  try { await ElMessageBox.confirm(`确定${action}面试邀约？`, '确认', { type: 'warning' }) } catch { return }
  const newStatus = accept ? 'IN_INTERVIEW' : 'REJECTED'
  try {
    // 找到待响应的面试记录（可能有多轮，取状态为 PENDING 的那条）
    const iv = item.interviews?.find(iv => iv.interviewStatus === 'PENDING' || !iv.interviewStatus)
    if (iv) {
      // 有面试记录 → 通过面试ID响应
      await seekerApi.respondInterview(iv.id, accept)
    } else {
      // 无面试记录 → 直接更新候选人状态
      await hrApi.updateStatus(item.candidate.id, {
        status: newStatus, actor: 'SEEKER',
        reason: accept ? '求职者接受面试邀约' : '求职者拒绝面试邀约'
      })
    }
    ElMessage.success(action + '成功')
    await loadStatus()
  } catch { /* handled */ }
}

async function respondOffer(item, accept) {
  const action = accept ? '接受' : '拒绝'
  try { await ElMessageBox.confirm(`确定${action}Offer？`, '确认', { type: 'warning' }) } catch { return }
  try {
    await seekerApi.respondOffer(item.offer.id, accept)
    ElMessage.success(action + '成功')
    await loadStatus()
  } catch { /* handled */ }
}
</script>
