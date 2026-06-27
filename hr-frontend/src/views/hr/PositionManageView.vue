<template>
  <HrLayout>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h2 style="margin: 0;">岗位管理</h2>
      <el-button type="primary" @click="showCreateDialog">新增岗位</el-button>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-table :data="positions" stripe size="small">
        <el-table-column prop="name" label="岗位名称" width="150" />
        <el-table-column prop="department" label="部门" width="120" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="requirements" label="任职要求" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!positions.length && !loading" description="暂无岗位" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑岗位' : '新增岗位'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="岗位名称" prop="name">
          <el-input v-model="form.name" placeholder="如: Java后端工程师" />
        </el-form-item>
        <el-form-item label="部门" prop="department">
          <el-input v-model="form.department" placeholder="如: 技术部" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="岗位描述" />
        </el-form-item>
        <el-form-item label="任职要求" prop="requirements">
          <el-input v-model="form.requirements" type="textarea" :rows="3" placeholder="任职要求" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </HrLayout>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hrApi, subscribePositionEvents } from '../../api'
import HrLayout from './components/HrLayout.vue'

const loading = ref(false)
const saving = ref(false)
const positions = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref('')
const formRef = ref(null)

const form = reactive({
  name: '',
  department: '',
  description: '',
  requirements: ''
})

const rules = {
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }]
}

let cleanupSse = null

onMounted(() => {
  loadPositions()
  cleanupSse = subscribePositionEvents(() => loadPositions())
})

onUnmounted(() => { if (cleanupSse) cleanupSse() })

async function loadPositions() {
  loading.value = true
  try { positions.value = await hrApi.getPositionList() }
  finally { loading.value = false }
}

function showCreateDialog() {
  isEdit.value = false
  editId.value = ''
  form.name = ''
  form.department = ''
  form.description = ''
  form.requirements = ''
  dialogVisible.value = true
}

function showEditDialog(row) {
  isEdit.value = true
  editId.value = row.id
  form.name = row.name
  form.department = row.department || ''
  form.description = row.description || ''
  form.requirements = row.requirements || ''
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value) {
      await hrApi.updatePosition(editId.value, { ...form })
      ElMessage.success('岗位更新成功')
    } else {
      await hrApi.createPosition({ ...form })
      ElMessage.success('岗位创建成功')
    }
    dialogVisible.value = false
    await loadPositions()
  } finally { saving.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除岗位「${row.name}」？`, '确认', { type: 'warning' })
  } catch { return }
  try {
    await hrApi.deletePosition(row.id)
    ElMessage.success('删除成功')
    await loadPositions()
  } catch { /* handled by interceptor */ }
}
</script>
