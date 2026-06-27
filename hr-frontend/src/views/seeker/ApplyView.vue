<template>
  <SeekerLayout>
    <div style="margin-bottom: 16px;">
      <el-button @click="$router.push('/seeker/upload')" text>
        <el-icon><ArrowLeft /></el-icon> 返回岗位列表
      </el-button>
    </div>

    <el-card shadow="never">
      <template #header>
        <span style="font-size: 18px; font-weight: 600;">投递 - {{ position }}</span>
      </template>

      <!-- 第1步：上传简历 -->
      <div v-if="step === 'upload'" style="text-align: center;">
        <div
          class="upload-area"
          @dragover.prevent="dragover = true"
          @dragleave="dragover = false"
          @drop.prevent="handleDrop"
          @click="triggerUpload"
          :class="{ 'upload-active': dragover }"
          style="border: 2px dashed #d9d9d9; border-radius: 8px; padding: 60px 20px; cursor: pointer; transition: all .3s;"
        >
          <el-icon :size="48" color="#909399"><UploadFilled /></el-icon>
          <p style="color: #666; margin-top: 12px;">将 PDF 简历拖拽到此处，或点击上传</p>
          <p style="color: #999; font-size: 12px;">支持 PDF 格式，最大 10MB</p>
        </div>
        <input ref="fileInput" type="file" accept=".pdf,application/pdf" style="display:none" @change="handleFileChange" />
        <div v-if="selectedFile" style="margin-top: 16px;">
          <el-alert :title="'已选择: ' + selectedFile.name" type="info" :closable="false" show-icon />
          <el-button type="primary" @click="startParse" :loading="parsing" style="margin-top: 16px; width: 200px;">
            {{ parsing ? '正在解析...' : '开始解析简历' }}
          </el-button>
        </div>
      </div>

      <!-- 第2步：编辑补充信息 -->
      <div v-if="step === 'edit'">
        <el-alert title="以下信息由AI从简历中提取，请检查并补充完整" type="success" :closable="false" show-icon style="margin-bottom: 16px;" />
        <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="form.name" placeholder="请输入姓名" />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="邮箱">
                <el-input v-model="form.email" placeholder="邮箱" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="电话" prop="phone">
                <el-input v-model="form.phone" placeholder="电话" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="应聘岗位" prop="position">
            <el-input v-model="form.position" placeholder="应聘岗位" />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="工作年限" prop="yearsOfExperience">
                <el-input-number v-model="form.yearsOfExperience" :min="0" :max="50" style="width:100%;" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="应届">
                <el-select v-model="form.isFreshGraduate" style="width:100%;">
                  <el-option label="是" :value="true" />
                  <el-option label="否" :value="false" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="学历" prop="educationLevel">
                <el-select v-model="form.educationLevel" style="width:100%;">
                  <el-option label="高中" value="HIGH_SCHOOL" />
                  <el-option label="大专" value="ASSOCIATE" />
                  <el-option label="本科" value="BACHELOR" />
                  <el-option label="硕士" value="MASTER" />
                  <el-option label="博士" value="PHD" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="毕业年份">
                <el-input-number v-model="form.graduationYear" :min="2000" :max="2030" style="width:100%;" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="学校">
                <el-input v-model="form.school" placeholder="毕业院校" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="专业">
                <el-input v-model="form.major" placeholder="专业" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="技术栈" prop="techStack">
            <el-select v-model="form.techStack" multiple filterable allow-create default-first-option style="width:100%;" placeholder="输入技术关键词">
              <el-option v-for="t in commonTech" :key="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="工作经历">
            <el-input v-model="form.workHistory" type="textarea" :rows="2" placeholder="工作经历摘要" />
          </el-form-item>
          <el-form-item label="自我评价">
            <el-input v-model="form.selfEvaluation" type="textarea" :rows="2" placeholder="自我评价" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSubmit" :loading="submitting" size="large" style="width:200px;">
              {{ submitting ? '提交中...' : '确认投递' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </SeekerLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { seekerApi } from '../../api'
import { useSeekerStore } from '../../stores/seeker'
import SeekerLayout from '../hr/components/SeekerLayout.vue'

const route = useRoute()
const router = useRouter()
const store = useSeekerStore()

const position = ref(route.params.position || '')
const commonTech = ['Java','Spring','SpringBoot','MyBatis','MySQL','Redis','MongoDB','RabbitMQ','Kafka','Docker','Kubernetes','Vue','React','Python','Go','Linux','微服务','分布式','高并发','JVM','SQL','Elasticsearch']

// 步骤
const step = ref('upload')
const fileInput = ref(null)
const selectedFile = ref(null)
const dragover = ref(false)
const parsing = ref(false)
const submitting = ref(false)
const formRef = ref(null)

// 上传结果
const storedFileName = ref('')
const resumeFileName = ref('')

// 编辑表单
const form = reactive({
  name: '', email: '', phone: '', position: position.value,
  yearsOfExperience: 0, isFreshGraduate: false,
  graduationYear: null, educationLevel: '',
  school: '', major: '',
  techStack: [], workHistory: '', selfEvaluation: ''
})

const phoneRule = { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号', trigger: 'blur' }
const emailRule = { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [emailRule],
  phone: [{ required: true, message: '请输入电话', trigger: 'blur' }, phoneRule],
  position: [{ required: true, message: '请输入应聘岗位', trigger: 'blur' }],
  yearsOfExperience: [{ required: true, message: '请填写工作年限', trigger: 'blur' }],
  educationLevel: [{ required: true, message: '请选择学历', trigger: 'change' }],
  techStack: [{ required: true, message: '请至少选择一项技术栈', trigger: 'change' }]
}

function triggerUpload() { fileInput.value?.click() }

function handleDrop(e) {
  dragover.value = false
  const file = e.dataTransfer.files[0]
  validateAndSelect(file)
}

function handleFileChange(e) {
  const file = e.target.files[0]
  validateAndSelect(file)
}

function validateAndSelect(file) {
  if (!file) return
  if (file.type !== 'application/pdf') {
    ElMessage.error('请选择 PDF 格式的文件'); return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小超过 10MB 限制'); return
  }
  selectedFile.value = file
}

async function startParse() {
  if (!selectedFile.value) return
  parsing.value = true
  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('seekerId', store.seekerId)
  formData.append('position', position.value)

  try {
    const result = await seekerApi.parseResumeSync(formData)
    storedFileName.value = result.storedFileName
    resumeFileName.value = result.resumeFileName
    const c = result.candidate

    // 填满表单
    form.name = c.name || ''
    form.email = c.email || ''
    form.phone = c.phone || ''
    form.position = c.position || position.value
    form.yearsOfExperience = c.yearsOfExperience ?? 0
    form.isFreshGraduate = c.isFreshGraduate ?? false
    form.graduationYear = c.graduationYear ?? null
    form.educationLevel = c.educationLevel || ''
    form.school = c.school || ''
    form.major = c.major || ''
    form.techStack = c.techStack || []
    form.workHistory = c.workHistory || ''
    form.selfEvaluation = c.selfEvaluation || ''

    step.value = 'edit'
    ElMessage.success('简历解析完成，请检查并补充信息')
  } catch (e) {
    // handled by axios interceptor
  } finally {
    parsing.value = false
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await seekerApi.submitCandidate({
      seekerId: store.seekerId,
      storedFileName: storedFileName.value,
      resumeFileName: resumeFileName.value,
      candidate: { ...form }
    })
    ElMessage.success('投递成功')
    router.push('/seeker/status')
  } finally {
    submitting.value = false
  }
}
</script>
