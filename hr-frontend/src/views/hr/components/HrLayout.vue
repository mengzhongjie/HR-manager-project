<template>
  <el-container style="min-height: calc(100vh - 48px);">
    <el-header style="background: #1f2d3d; padding: 0;">
      <div style="display: flex; align-items: center; height: 60px; padding: 0 20px; color: #fff;">
        <el-menu
          mode="horizontal"
          :default-active="activeMenu"
          router
          background-color="#1f2d3d"
          text-color="#bfcbd9"
          active-text-color="#409eff"
          style="border-bottom: none; flex: 1;"
        >
          <el-menu-item index="/hr">
            <el-icon><DataAnalysis /></el-icon>
            <span>HR管理端</span>
          </el-menu-item>
          <el-menu-item index="/hr/positions/manage">
            <el-icon><Briefcase /></el-icon>
            <span>岗位管理</span>
          </el-menu-item>
          <el-menu-item index="/hr/interviews">
            <el-icon><Notebook /></el-icon>
            <span>面试管理</span>
          </el-menu-item>
          <el-menu-item index="/hr/offers/pending">
            <el-icon><WarningFilled /></el-icon>
            <span>待发Offer</span>
          </el-menu-item>
          <el-menu-item index="/hr/offers">
            <el-icon><List /></el-icon>
            <span>Offer管理</span>
          </el-menu-item>
        </el-menu>
        <div style="display: flex; align-items: center; gap: 10px;">
          <el-tag type="success" size="small">HR管理端</el-tag>
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
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/hr/offers/pending')) return '/hr/offers/pending'
  if (path.startsWith('/hr/offers')) return '/hr/offers'
  if (path.startsWith('/hr/interviews')) return '/hr/interviews'
  if (path.startsWith('/hr/positions/manage')) return '/hr/positions/manage'
  return '/hr'
})
</script>
