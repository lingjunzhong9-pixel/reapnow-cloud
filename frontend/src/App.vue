<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from './api'

const router = useRouter()
const route = useRoute()
const user = ref(null)

/** 后台角色才能看到「定金流水」入口 */
const isAdmin = computed(() =>
  ['ADMIN', 'DISPATCHER'].includes(user.value && user.value.role)
)

function loadUser() {
  const u = localStorage.getItem('user')
  user.value = u ? JSON.parse(u) : null
}
onMounted(loadUser)

// 每次路由变化都重新读一次登录态：
// 这样登录后菜单立刻正确，不必依赖 Login.vue 里的整页刷新兜底
watch(() => route.path, loadUser)

function logout() {
  api.post('/auth/logout').catch(() => {})
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  user.value = null
  router.push('/login')
}

const roleText = { FARMER: '农户', MACHINE_OWNER: '农机手', DISPATCHER: '调度员', ADMIN: '管理员' }
</script>

<template>
  <el-container class="layout">
    <!-- 登录页全屏沉浸，不显示顶栏 -->
    <el-header v-if="route.path !== '/login'" class="header">
      <div class="brand" @click="router.push('/')">
        <span class="logo">🌾</span>
        <span class="name">抢收</span>
        <span class="sub">跨区机收对接与履约平台</span>
      </div>
      <el-menu mode="horizontal" :default-active="route.path" :ellipsis="false" router class="nav">
        <el-menu-item index="/">抢单大厅</el-menu-item>
        <el-menu-item index="/publish">发布作业需求</el-menu-item>
        <el-menu-item index="/orders">我的接单</el-menu-item>
        <el-menu-item index="/demands">我发布的需求</el-menu-item>
        <el-menu-item v-if="isAdmin" index="/payments">定金流水</el-menu-item>
      </el-menu>
      <div class="user-box">
        <template v-if="user">
          <el-tag size="small" :type="user.role === 'MACHINE_OWNER' ? 'warning' : 'success'">
            {{ roleText[user.role] || user.role }}
          </el-tag>
          <span class="nick">{{ user.nickname || user.username }}</span>
          <el-tooltip v-if="user.role === 'MACHINE_OWNER'" content="机手信誉分">
            <span class="credit">信誉 {{ user.creditScore }}</span>
          </el-tooltip>
          <el-button size="small" @click="logout">退出</el-button>
        </template>
        <el-button v-else size="small" type="primary" @click="router.push('/login')">登录</el-button>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
body { margin: 0; background: #f5f7f3; font-family: "Microsoft YaHei", Arial, sans-serif; }
.layout { min-height: 100vh; }
.header {
  display: flex; align-items: center; gap: 20px;
  background: #fff; border-bottom: 2px solid #e8f0e0; padding: 0 24px;
}
.brand { display: flex; align-items: center; gap: 6px; cursor: pointer; white-space: nowrap; }
.logo { font-size: 22px; }
.name { font-size: 20px; font-weight: 700; color: #2f6b2f; letter-spacing: 2px; }
.sub { font-size: 12px; color: #90a090; margin-left: 6px; }
.nav { flex: 1; border-bottom: none; }
.user-box { display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.nick { color: #4a5a4a; font-size: 14px; font-weight: 600; }
.credit { font-size: 12px; color: #b8860b; background: #fff8e1; padding: 2px 6px; border-radius: 3px; }
.main { padding: 20px 24px; max-width: 1280px; margin: 0 auto; width: 100%; box-sizing: border-box; }
</style>
