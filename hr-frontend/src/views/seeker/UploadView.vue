<template>
  <SeekerLayout>
    <el-card shadow="never">
      <template #header>
        <span style="font-size: 18px; font-weight: 600;">选择岗位投递</span>
      </template>

      <div v-if="!canSubmit" style="text-align: center; padding: 40px 0;">
        <el-icon :size="60" color="#e6a23c"><WarningFilled /></el-icon>
        <p style="color: #e6a23c; margin-top: 16px; font-size: 16px;">
          {{ message || '您当前有一份简历正在被HR处理中，请等待处理结果后再投递' }}
        </p>
        <el-button type="primary" @click="$router.push('/seeker/status')" style="margin-top: 16px;">
          查看投递状态
        </el-button>
      </div>

      <div v-else v-loading="loading">
        <el-empty v-if="!positions.length" description="暂无开放岗位" />
        <el-row v-else :gutter="20">
          <el-col v-for="p in positions" :key="p.name" :xs="24" :sm="12" :md="8" :lg="6" style="margin-bottom: 20px;">
            <el-card shadow="hover" class="position-card" @click="$router.push(`/seeker/apply/${p.name}`)">
              <div style="text-align: center; padding: 20px 0;">
                <el-icon :size="40" color="#409eff"><Briefcase /></el-icon>
                <h3 style="margin: 12px 0 4px;">{{ p.name }}</h3>
                <p v-if="p.department" style="color: #909399; font-size: 13px; margin: 0;">{{ p.department }}</p>
              </div>
              <div v-if="p.description" style="font-size: 13px; color: #666; margin-bottom: 12px; text-align: center;">
                {{ p.description }}
              </div>
              <template #footer>
                <el-button type="primary" style="width: 100%;" @click.stop="$router.push(`/seeker/apply/${p.name}`)">
                  投递简历
                </el-button>
              </template>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </SeekerLayout>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { seekerApi, hrApi, subscribePositionEvents } from '../../api'
import { useSeekerStore } from '../../stores/seeker'
import SeekerLayout from '../hr/components/SeekerLayout.vue'

const store = useSeekerStore()
const loading = ref(false)
const canSubmit = ref(true)
const message = ref('')
const positions = ref([])
let cleanupSse = null

onMounted(async () => {
  await store.ensureSeeker()
  try {
    const result = await seekerApi.canSubmit(store.seekerId)
    canSubmit.value = result.canSubmit
    if (canSubmit.value) {
      loading.value = true
      positions.value = await hrApi.getPositionList()
    }
  } catch (e) {
    canSubmit.value = false
  } finally {
    loading.value = false
  }

  // SSE 实时同步岗位列表
  cleanupSse = subscribePositionEvents(() => {
    if (canSubmit.value) {
      hrApi.getPositionList().then(list => { positions.value = list })
    }
  })
})

onUnmounted(() => { if (cleanupSse) cleanupSse() })
</script>

<style scoped>
.position-card { cursor: pointer; transition: transform .2s, box-shadow .2s; }
.position-card:hover { transform: translateY(-4px); box-shadow: 0 8px 20px rgba(0,0,0,.1) !important; }
</style>
