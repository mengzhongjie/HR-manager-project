<template>
  <HrLayout>
    <h2 style="margin-bottom: 20px;">录入面试记录</h2>

    <el-card shadow="never">
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules" style="max-width: 600px;">
        <el-form-item label="候选人" prop="candidateId">
          <el-select v-model="form.candidateId" placeholder="选择候选人" filterable style="width: 100%;" @change="onCandidateChange">
            <el-option v-for="c in candidates" :key="c.id" :value="c.id" :label="c.name + ' - ' + c.position" />
          </el-select>
        </el-form-item>
        <el-form-item label="面试轮次" prop="round">
          <el-select v-model="form.round" placeholder="选择轮次" style="width: 100%;">
            <el-option v-for="r in rounds" :key="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="面试结果" prop="result">
          <el-select v-model="form.result" placeholder="选择结果" style="width: 100%;">
            <el-option v-for="r in results" :key="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="面试官" prop="interviewerName">
          <el-input v-model="form.interviewerName" placeholder="面试官姓名" />
        </el-form-item>
        <el-form-item label="面试日期" prop="interviewDate">
          <el-date-picker v-model="form.interviewDate" type="date" placeholder="选择日期" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="评分">
          <el-input-number v-model="form.score" :min="0" :max="100" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="反馈">
          <el-input v-model="form.feedback" type="textarea" :rows="3" placeholder="面试反馈" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
          <el-button @click="$router.push('/hr/interviews')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </HrLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { hrApi } from '../../api'
import HrLayout from './components/HrLayout.vue'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const saving = ref(false)
const candidates = ref([])

const rounds = ['ROUND_1', 'ROUND_2', 'ROUND_3']
const results = ['PASSED', 'FAILED']

const form = reactive({
  candidateId: route.query.candidateId || '',
  round: '',
  result: '',
  interviewerName: '',
  interviewDate: '',
  score: null,
  feedback: ''
})

const rules = {
  candidateId: [{ required: true, message: '请选择候选人' }],
  round: [{ required: true, message: '请选择轮次' }],
  result: [{ required: true, message: '请选择结果' }],
  interviewerName: [{ required: true, message: '请填写面试官' }],
  interviewDate: [{ required: true, message: '请选择日期' }]
}

onMounted(async () => {
  try {
    const pos = await hrApi.getPositions()
    const allCandidates = []
    for (const p of pos) {
      const list = await hrApi.getCandidates(p, {})
      allCandidates.push(...list)
    }
    candidates.value = allCandidates
  } catch (e) { /* handled */ }
})

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await hrApi.createInterview({ ...form })
    ElMessage.success('面试记录保存成功')
    router.push('/hr/interviews')
  } finally {
    saving.value = false
  }
}

function onCandidateChange(val) {
  // Can auto-detect next round
}
</script>
