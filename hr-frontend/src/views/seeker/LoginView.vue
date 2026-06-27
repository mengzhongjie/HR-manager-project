<template>
  <el-container style="min-height: 100vh; justify-content: center; align-items: center; background: #f5f7fa;">
    <el-card style="width: 420px; padding: 20px;">
      <template #header>
        <div style="text-align: center;">
          <h2 style="margin: 0; color: #409eff;">求职者登录</h2>
          <p style="color: #999; font-size: 13px; margin-top: 8px;">输入信息即可注册或登录</p>
        </div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin" :loading="loading" style="width: 100%;">
            {{ loading ? '登录中...' : '登录 / 注册' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </el-container>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { seekerApi } from '../../api'
import { useSeekerStore } from '../../stores/seeker'

const router = useRouter()
const store = useSeekerStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  name: '',
  email: '',
  phone: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ required: true, type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入电话', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const seeker = await seekerApi.login({
      username: form.username,
      name: form.name,
      email: form.email,
      phone: form.phone
    })
    store.setSeeker(seeker.id, seeker.name)
    ElMessage.success('登录成功')
    router.push('/seeker/upload')
  } catch (e) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>
