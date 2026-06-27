<template>
  <SeekerLayout>
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span style="font-size: 18px; font-weight: 600;">投递简历</span>
        </div>
      </template>

      <div v-if="!canSubmit" style="text-align: center; padding: 40px 0;">
        <el-icon :size="60" color="#e6a23c"><WarningFilled /></el-icon>
        <p style="color: #e6a23c; margin-top: 16px; font-size: 16px;">
          {{ message || '您当前有一份简历正在被HR处理中，请等待处理结果后再投递' }}
        </p>
      </div>

      <div v-else>
        <div
          class="upload-area"
          @dragover.prevent="dragover = true"
          @dragleave="dragover = false"
          @drop.prevent="handleDrop"
          @click="triggerUpload"
          :class="{ 'upload-active': dragover }"
          style="border: 2px dashed #d9d9d9; border-radius: 8px; padding: 60px 20px; text-align: center; cursor: pointer; transition: all .3s;"
        >
          <el-icon :size="48" color="#909399"><UploadFilled /></el-icon>
          <p style="color: #666; margin-top: 12px;">将 PDF 简历拖拽到此处，或点击上传</p>
          <p style="color: #999; font-size: 12px;">支持 PDF 格式，最大 10MB</p>
        </div>

        <input
          ref="fileInput"
          type="file"
          accept=".pdf,application/pdf"
          style="display: none"
          @change="handleFileChange"
        />

        <div v-if="selectedFile" style="margin-top: 20px;">
          <el-alert :title="'已选择文件: ' + selectedFile.name" type="info" :closable="false" show-icon />
          <el-button type="primary" @click="handleUpload" :loading="uploading" style="margin-top: 16px; width: 100%;">
            {{ uploading ? '上传中...' : '开始上传' }}
          </el-button>
        </div>

        <div v-if="uploadProgress > 0" style="margin-top: 16px;">
          <el-progress :percentage="uploadProgress" :status="uploadStatus" />
        </div>
      </div>
    </el-card>
  </SeekerLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { seekerApi } from '../../api'
import { useSeekerStore } from '../../stores/seeker'
import SeekerLayout from '../hr/components/SeekerLayout.vue'

const store = useSeekerStore()
const fileInput = ref(null)
const selectedFile = ref(null)
const canSubmit = ref(true)
const message = ref('')
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadStatus = ref('')
const dragover = ref(false)

onMounted(async () => {
  await store.ensureSeeker()
  try {
    canSubmit.value = await seekerApi.canSubmit(store.seekerId)
  } catch (e) {
    canSubmit.value = false
  }
})

function triggerUpload() {
  fileInput.value?.click()
}

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
    ElMessage.error('请选择 PDF 格式的文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小超过 10MB 限制')
    return
  }
  selectedFile.value = file
}

async function handleUpload() {
  if (!selectedFile.value || !store.seekerId) return
  uploading.value = true
  uploadProgress.value = 0
  uploadStatus.value = ''

  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('seekerId', store.seekerId)

  // Simulate upload progress
  const progressInterval = setInterval(() => {
    if (uploadProgress.value < 90) {
      uploadProgress.value += Math.random() * 15
    }
  }, 300)

  try {
    await seekerApi.uploadResume(formData)
    clearInterval(progressInterval)
    uploadProgress.value = 100
    uploadStatus.value = 'success'
    ElMessage.success('简历上传成功，正在解析中')
    selectedFile.value = null
  } catch (e) {
    clearInterval(progressInterval)
    uploadProgress.value = 0
    uploadStatus.value = 'exception'
  } finally {
    uploading.value = false
  }
}
</script>
