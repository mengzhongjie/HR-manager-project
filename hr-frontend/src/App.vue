<template>
  <div id="app">
    <!-- 全局模块导航栏 -->
    <header class="global-header">
      <div class="header-left">
        <span class="brand">HR 管理系统</span>
      </div>
      <div class="header-right">
        <el-button
          :type="isSeeker ? 'primary' : 'default'"
          size="small"
          round
          @click="$router.push('/seeker/upload')"
        >
          <el-icon style="margin-right: 4px"><User /></el-icon>
          求职端
        </el-button>
        <el-button
          :type="isHr ? 'primary' : 'default'"
          size="small"
          round
          @click="$router.push('/hr')"
          style="margin-left: 8px;"
        >
          <el-icon style="margin-right: 4px"><DataBoard /></el-icon>
          HR管理端
        </el-button>
      </div>
    </header>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const isSeeker = computed(() => route.path.startsWith('/seeker'))
const isHr = computed(() => route.path.startsWith('/hr'))
</script>

<style>
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background-color: #f5f7fa;
}

.global-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 48px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 1000;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
}

.brand {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
}

.main-content {
  margin-top: 48px;
  min-height: calc(100vh - 48px);
}
</style>
