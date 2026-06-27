<template>
  <el-container style="min-height: 100vh;">
    <el-header style="background: #409eff; padding: 0;">
      <div style="display: flex; align-items: center; height: 60px; padding: 0 20px; color: #fff;">
        <el-menu
          mode="horizontal"
          :default-active="activeMenu"
          router
          background-color="#409eff"
          text-color="#fff"
          active-text-color="#fff"
          style="border-bottom: none; flex: 1;"
        >
          <el-menu-item index="/seeker/upload">
            <el-icon><Upload /></el-icon>
            <span>投递简历</span>
          </el-menu-item>
          <el-menu-item index="/seeker/status">
            <el-icon><List /></el-icon>
            <span>查看状态</span>
          </el-menu-item>
        </el-menu>
        <div style="display: flex; align-items: center; gap: 10px;">
          <el-tag type="warning" effect="plain" size="small">
            {{ seekerName }}
          </el-tag>
          <el-button size="small" round @click="handleLogout">退出</el-button>
        </div>
      </div>
    </el-header>
    <el-main style="background: #f5f7fa; padding: 20px;">
      <slot />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSeekerStore } from '../../../stores/seeker'

const route = useRoute()
const router = useRouter()
const store = useSeekerStore()

const seekerName = computed(() => store.seekerName)
const activeMenu = computed(() => route.path)

function handleLogout() {
  store.logout()
  router.push('/seeker/login')
}
</script>
