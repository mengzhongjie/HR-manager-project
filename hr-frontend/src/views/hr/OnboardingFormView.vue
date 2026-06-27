<template>
  <HrLayout>
    <h2 style="margin-bottom: 20px;">入职登记</h2>

    <el-card shadow="never" v-loading="loading">
      <el-form :model="form" label-width="120px" ref="formRef" :rules="rules" style="max-width: 500px;">
        <el-form-item label="入职日期" prop="onboardDate">
          <el-date-picker v-model="form.onboardDate" type="date" placeholder="选择日期" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="部门" prop="department">
          <el-input v-model="form.department" placeholder="如: 技术部" />
        </el-form-item>
        <el-form-item label="导师">
          <el-input v-model="form.mentorName" placeholder="导师姓名" />
        </el-form-item>
        <el-form-item>
          <el-button type="dark" @click="handleSave" :loading="saving">确认入职</el-button>
          <el-button @click="$router.push('/hr/offers')">取消</el-button>
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
const offerId = ref(route.params.offerId || '')
const formRef = ref(null)
const saving = ref(false)
const loading = ref(false)

const form = reactive({
  onboardDate: '',
  department: '',
  mentorName: ''
})

const rules = {
  onboardDate: [{ required: true, message: '请选择入职日期' }],
  department: [{ required: true, message: '请填写部门' }]
}

onMounted(() => {})

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await hrApi.createOnboarding(offerId.value, { ...form })
    ElMessage.success('入职登记成功')
    router.push('/hr/offers')
  } finally {
    saving.value = false
  }
}
</script>
