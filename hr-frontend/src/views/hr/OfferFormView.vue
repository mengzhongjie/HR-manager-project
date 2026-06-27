<template>
  <HrLayout>
    <h2 style="margin-bottom: 20px;">发放Offer</h2>

    <el-card shadow="never" v-loading="loading">
      <el-form :model="form" label-width="120px" ref="formRef" :rules="rules" style="max-width: 500px;">
        <el-form-item label="候选人">
          <el-tag>{{ candidateName }}</el-tag>
        </el-form-item>
        <el-form-item label="薪资 (元/月)" prop="offeredSalary">
          <el-input-number v-model="form.offeredSalary" :min="0" :step="1000" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="Offer日期" prop="offerDate">
          <el-date-picker v-model="form.offerDate" type="date" placeholder="选择日期" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="截止日期" prop="expiryDate">
          <el-date-picker v-model="form.expiryDate" type="date" placeholder="选择日期" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <el-button type="success" @click="handleSave" :loading="saving">确认发放</el-button>
          <el-button @click="$router.push('/hr/offers/pending')">取消</el-button>
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
const candidateId = ref(route.params.candidateId || '')
const formRef = ref(null)
const saving = ref(false)
const loading = ref(false)
const candidateName = ref('')

const form = reactive({
  offeredSalary: null,
  offerDate: new Date(),
  expiryDate: '',
  remark: ''
})

const rules = {
  offeredSalary: [{ required: true, message: '请填写薪资' }],
  offerDate: [{ required: true, message: '请选择日期' }],
  expiryDate: [{ required: true, message: '请选择截止日期' }]
}

onMounted(async () => {
  loading.value = true
  try {
    const c = await hrApi.getCandidate(candidateId.value)
    candidateName.value = c?.name || '未知'
  } finally {
    loading.value = false
  }
})

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await hrApi.createOffer(candidateId.value, { ...form })
    ElMessage.success('Offer发放成功')
    router.push('/hr/offers/pending')
  } finally {
    saving.value = false
  }
}
</script>
