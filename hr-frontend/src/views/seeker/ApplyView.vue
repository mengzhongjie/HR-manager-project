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

        <!-- 完整度进度 -->
        <div style="margin-bottom: 16px; display: flex; align-items: center; gap: 12px;">
          <span style="font-size: 13px; color: #666;">信息完整度：</span>
          <el-progress :percentage="completeness" :color="completenessColors" :stroke-width="14" style="flex:1; max-width: 300px;" />
          <span style="font-size: 12px; color: #999;">{{ filledCount }}/{{ totalRequired }} 必填</span>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="form.name" placeholder="请输入姓名">
              <template #suffix>
                <el-icon v-if="parsedFields.has('name')" color="#67c23a"><CircleCheckFilled /></el-icon>
                <el-icon v-else color="#e6a23c"><WarningFilled /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="邮箱">
                <el-input v-model="form.email" placeholder="邮箱">
                  <template #suffix>
                    <el-icon v-if="parsedFields.has('email')" color="#67c23a"><CircleCheckFilled /></el-icon>
                    <el-icon v-else color="#e6a23c"><WarningFilled /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="电话" prop="phone">
                <el-input v-model="form.phone" placeholder="电话">
                  <template #suffix>
                    <el-icon v-if="parsedFields.has('phone')" color="#67c23a"><CircleCheckFilled /></el-icon>
                    <el-icon v-else color="#e6a23c"><WarningFilled /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="应聘岗位" prop="position">
            <el-input v-model="form.position" placeholder="应聘岗位">
              <template #suffix>
                <el-icon v-if="parsedFields.has('position')" color="#67c23a"><CircleCheckFilled /></el-icon>
                <el-icon v-else color="#e6a23c"><WarningFilled /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="工作年限" prop="yearsOfExperience">
                <el-input-number v-model="form.yearsOfExperience" :min="0" :max="50" style="width:100%;" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="应届">
                <el-select v-model="form.isFreshGraduate" style="width:100%;">
                  <el-option label="是" :value="true" />
                  <el-option label="否" :value="false" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="年龄">
                <el-input-number v-model="form.age" :min="0" :max="100" style="width:100%;" />
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
                <el-input v-model="form.school" placeholder="毕业院校">
                  <template #suffix>
                    <el-icon v-if="parsedFields.has('school')" color="#67c23a"><CircleCheckFilled /></el-icon>
                    <el-icon v-else color="#e6a23c"><WarningFilled /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="专业">
                <el-input v-model="form.major" placeholder="专业">
                  <template #suffix>
                    <el-icon v-if="parsedFields.has('major')" color="#67c23a"><CircleCheckFilled /></el-icon>
                    <el-icon v-else color="#e6a23c"><WarningFilled /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="技术栈" prop="techStack">
            <el-select v-model="form.techStack" multiple filterable allow-create default-first-option style="width:100%;" placeholder="输入技术关键词">
              <el-option v-for="t in commonTech" :key="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="工作经历">
            <el-input v-model="form.workHistory" type="textarea" :rows="3" placeholder="请填写工作经历，包括公司、职位、时间段" />
          </el-form-item>
          <el-form-item label="自我评价">
            <el-input v-model="form.selfEvaluation" type="textarea" :rows="2" placeholder="请填写自我评价" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSubmit" :loading="submitting" size="large" style="width:200px;">
              {{ submitting ? '提交中...' : '确认投递' }}
            </el-button>
            <el-button @click="step = 'upload'" style="margin-left: 12px;">重新上传</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </SeekerLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { seekerApi } from '../../api'
import { useSeekerStore } from '../../stores/seeker'
import SeekerLayout from '../hr/components/SeekerLayout.vue'

const route = useRoute()
const router = useRouter()
const store = useSeekerStore()

const position = ref(route.params.position || '')
const commonTech = [
  'Java','Spring','SpringBoot','SpringCloud','MyBatis','MySQL','Redis','MongoDB',
  'RabbitMQ','Kafka','Docker','Kubernetes','K8s',
  'Vue','React','Angular','Node.js','TypeScript','JavaScript','jQuery',
  'Python','Go','Rust','C++','C#',
  'Linux','Nginx','Jenkins','Git','Maven','Gradle',
  'HTML','CSS','Bootstrap','Tailwind','Sass',
  'Oracle','PostgreSQL','SQLite','Elasticsearch',
  'Flask','Django','TensorFlow','PyTorch',
  '微服务','分布式','高并发','JVM','SQL',
  '机器学习','深度学习','NLP','AI','大模型','敏捷开发','Scrum'
]

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

// AI解析成功的字段集合
const parsedFields = ref(new Set())

// 编辑表单
const form = reactive({
  name: '', email: '', phone: '', position: position.value,
  yearsOfExperience: 0, isFreshGraduate: false, age: null,
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

// 完整度计算
const totalRequired = 7 // name, phone, position, yearsOfExperience, educationLevel, techStack, (email非必填)
const filledCount = computed(() => {
  let count = 0
  if (form.name) count++
  if (form.phone) count++
  if (form.position) count++
  if (form.yearsOfExperience !== null && form.yearsOfExperience !== undefined) count++
  if (form.educationLevel) count++
  if (form.techStack && form.techStack.length > 0) count++
  // email 不强制
  return count
})
const completeness = computed(() => Math.round((filledCount.value / totalRequired) * 100))
const completenessColors = computed(() => {
  if (completeness.value >= 80) return '#67c23a'
  if (completeness.value >= 50) return '#e6a23c'
  return '#f56c6c'
})

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

    // 记录哪些字段被AI成功提取
    const extracted = new Set()
    if (c.name) extracted.add('name')
    if (c.email) extracted.add('email')
    if (c.phone) extracted.add('phone')
    if (c.position) extracted.add('position')
    if (c.school) extracted.add('school')
    if (c.major) extracted.add('major')
    if (c.techStack && c.techStack.length > 0) extracted.add('techStack')
    parsedFields.value = extracted

    // 填满表单
    form.name = c.name || ''
    form.email = c.email || ''
    form.phone = c.phone || ''
    form.position = c.position || position.value
    form.yearsOfExperience = c.yearsOfExperience ?? 0
    form.isFreshGraduate = c.isFreshGraduate ?? false
    form.age = c.age ?? null
    form.graduationYear = c.graduationYear ?? null
    form.educationLevel = c.educationLevel || ''
    form.school = c.school || ''
    form.major = c.major || ''
    form.techStack = c.techStack || []
    form.workHistory = c.workHistory || ''
    form.selfEvaluation = c.selfEvaluation || ''

    step.value = 'edit'
    ElMessage.success(`简历解析完成！已提取 ${extracted.size} 个字段，请检查并补充`)
  } catch (e) {
    // handled by axios interceptor
  } finally {
    parsing.value = false
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请填写所有必填项')
    return
  }
  // 检查必填项
  if (!form.name || !form.phone || !form.position || !form.educationLevel || !form.techStack?.length) {
    ElMessage.warning('请补全姓名、电话、岗位、学历和技术栈')
    return
  }
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
