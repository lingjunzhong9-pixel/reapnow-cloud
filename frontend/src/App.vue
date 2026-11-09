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
        <span class="logo" aria-hidden="true">
          <!-- 与登录页同一枚麦穗标志 -->
          <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M20 2.6C27.8 9.6 28.8 21.8 20 31.4C11.2 21.8 12.2 9.6 20 2.6Z" fill="#1a130a" />
            <g stroke="#d9a63c" stroke-width="1.8" stroke-linecap="round" fill="none">
              <path d="M20 9.6L15.9 13.6M20 9.6L24.1 13.6" />
              <path d="M20 15.2L15.5 19.5M20 15.2L24.5 19.5" />
              <path d="M20 20.9L16 24.9M20 20.9L24 24.9" />
            </g>
            <path d="M20 30.2V37.2" stroke="#1a130a" stroke-width="2.4" stroke-linecap="round" />
          </svg>
        </span>
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
          <span class="role" :class="user.role === 'MACHINE_OWNER' ? 'r-owner' : 'r-farmer'">
            {{ roleText[user.role] || user.role }}
          </span>
          <span class="nick">{{ user.nickname || user.username }}</span>
          <el-tooltip v-if="user.role === 'MACHINE_OWNER'" content="机手信誉分">
            <span class="credit">信誉 {{ user.creditScore }}</span>
          </el-tooltip>
          <button class="exit" @click="logout">退出</button>
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
/* ══════════════════════════════════════════════════════════
   全局视觉：米纸 + 麦金 + 深墨
   与登录页（麦熟黄昏）同一套语言，换到浅色工作区
   ══════════════════════════════════════════════════════════ */
:root {
  --ink: #1e1710;
  --ink-2: #2c2318;
  --paper: #f7f2e7;
  --card: #fffdf8;
  --gold: #c9963e;
  --gold-2: #e0ae52;
  --green: #3f6b3f;
  --green-2: #2f5230;
  --line: #e6dcc8;
  --muted: #8d8270;
  --text: #2b2318;

  /* Element Plus 主题 */
  --el-color-primary: #3f6b3f;
  --el-color-primary-light-3: #5c8a5c;
  --el-color-primary-light-5: #87a887;
  --el-color-primary-light-7: #b3c9b3;
  --el-color-primary-light-8: #cbdbcb;
  --el-color-primary-light-9: #eaf1ea;
  --el-color-primary-dark-2: #2f5230;
  --el-border-radius-base: 4px;
  --el-font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  --el-text-color-primary: #2b2318;
  --el-text-color-regular: #4a4032;
  --el-text-color-secondary: #8d8270;
  --el-border-color: #e0d6c2;
  --el-border-color-light: #e9e0ce;
  --el-border-color-lighter: #efe8da;
  --el-fill-color-blank: #fffdf8;
}

body {
  margin: 0;
  min-height: 100vh;
  color: var(--text);
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  -webkit-font-smoothing: antialiased;
  background-color: var(--paper);
  /* 极淡的纸纹，避免纯色平板感 */
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140'%3E%3Cfilter id='p'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='3'/%3E%3C/filter%3E%3Crect width='140' height='140' filter='url(%23p)' opacity='0.5'/%3E%3C/svg%3E");
  background-blend-mode: multiply;
}

.layout { min-height: 100vh; }

/* ---------------- 顶栏 ---------------- */
.header {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 62px;
  padding: 0 24px;
  box-sizing: border-box;
  position: sticky;
  top: 0;
  z-index: 100;
  background: linear-gradient(180deg, #261d13 0%, #1b140d 100%);
  border-bottom: 1px solid rgba(201, 150, 62, .28);
  box-shadow: 0 2px 20px rgba(30, 23, 16, .22);
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  white-space: nowrap;
  flex: none;
}
.logo {
  width: 32px; height: 32px;
  display: grid; place-items: center;
  background: linear-gradient(150deg, #f7d98d, #d4a13a 62%, #c0902f);
  border-radius: 3px;
  box-shadow: 0 2px 10px rgba(212, 161, 58, .38), inset 0 1px 0 rgba(255, 255, 255, .5);
}
.logo svg { width: 25px; height: 25px; display: block; }

.name {
  font-family: "KaiTi", "STKaiti", serif;
  font-size: 21px; font-weight: 700; letter-spacing: 3px;
  color: #f3e6c9;
}
.sub {
  font-size: 11.5px; letter-spacing: 1.4px;
  color: #8d7c5e;
  margin-left: 6px;
}

/* 导航 */
.nav {
  flex: 1;
  background: transparent;
  border-bottom: none;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: transparent;
  --el-menu-text-color: #b5a68c;
  --el-menu-active-color: #f1c96a;
}
.nav.el-menu--horizontal { border-bottom: none; }
.nav .el-menu-item {
  height: 62px;
  line-height: 62px;
  font-size: 14px;
  letter-spacing: .6px;
  color: #b5a68c;
  border-bottom: 2px solid transparent;
  transition: color .16s ease, border-color .16s ease;
}
.nav .el-menu-item:hover {
  color: #efe3ca;
  background: transparent;
}
.nav .el-menu-item.is-active {
  color: #f1c96a;
  border-bottom-color: #d9a63c;
  background: transparent;
}
.nav .el-menu-item.is-active::after { display: none; }

/* 用户区 */
.user-box {
  display: flex;
  align-items: center;
  gap: 9px;
  white-space: nowrap;
  flex: none;
}
.role {
  font-size: 11.5px;
  padding: 2px 9px;
  border-radius: 10px;
  letter-spacing: .5px;
}
.role.r-farmer { background: rgba(107, 154, 74, .16); color: #a9c98e; border: 1px solid rgba(107, 154, 74, .34); }
.role.r-owner  { background: rgba(212, 161, 58, .16); color: #e6c583; border: 1px solid rgba(212, 161, 58, .34); }
.nick { font-size: 14px; font-weight: 600; color: #ece1c9; }
.credit {
  font-size: 11.5px;
  color: #e0b96a;
  background: rgba(224, 185, 106, .12);
  border: 1px solid rgba(224, 185, 106, .28);
  padding: 2px 7px;
  border-radius: 3px;
  font-variant-numeric: tabular-nums;
}
.exit {
  font-family: inherit;
  font-size: 12.5px;
  color: #b7a68b;
  background: transparent;
  border: 1px solid rgba(201, 150, 62, .38);
  border-radius: 3px;
  padding: 4px 12px;
  cursor: pointer;
  transition: all .16s ease;
}
.exit:hover {
  color: #f1c96a;
  border-color: #d9a63c;
  background: rgba(217, 166, 60, .1);
}

/* ---------------- 主体 ---------------- */
.main {
  padding: 22px 26px 44px;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

/* ---------------- 卡片 ---------------- */
.el-card {
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--card);
  box-shadow: 0 1px 3px rgba(60, 45, 26, .05), 0 14px 34px rgba(60, 45, 26, .05);
  overflow: hidden;
}
.el-card__header {
  padding: 14px 18px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(180deg, #fffdf8, #faf4e7);
}
.el-card__body { padding: 18px; }

/* ---------------- 表格 ---------------- */
.el-table { --el-table-border-color: #efe7d8; background: transparent; }
.el-table th.el-table__cell {
  background: #f7f1e4 !important;
  color: #6b5c44;
  font-weight: 600;
  font-size: 12.5px;
  letter-spacing: .4px;
}
.el-table td.el-table__cell { border-bottom-color: #f2ebdd; }
.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell {
  background: #fcf9f2;
}
.el-table__body tr:hover > td.el-table__cell { background: #fbf4e4 !important; }

/* 单号列：等宽字体 + 不换行，避免 RC1794… 被折成两行 */
.el-table .col-no .cell {
  font-family: Consolas, "Courier New", monospace;
  font-size: 12.5px;
  color: #6b5f4a;
  white-space: nowrap;
  letter-spacing: -.2px;
}

/* ---------------- 按钮 ---------------- */
/* 注意排除 link / text 类型：它们也带 el-button--primary 类，
   但不该有背景色，否则会变成一坨实心绿块（详情、取消这类文字按钮） */
.el-button--primary:not(.is-plain):not(.is-link):not(.is-text) {
  background: linear-gradient(180deg, #4b7a4b, #3f6b3f);
  border-color: #3f6b3f;
  box-shadow: 0 1px 2px rgba(47, 82, 48, .2);
}
.el-button--primary:not(.is-plain):not(.is-link):not(.is-text):hover {
  background: linear-gradient(180deg, #578a57, #477747);
  border-color: #477747;
}

/* 文字按钮统一成麦金色 */
.el-button.is-link.el-button--primary,
.el-button.is-text.el-button--primary {
  color: #b8862b;
  background: transparent;
}
.el-button.is-link.el-button--primary:hover,
.el-button.is-text.el-button--primary:hover {
  color: #96691c;
  background: transparent;
}

/* ---------------- 其它细节 ---------------- */
.el-divider__text { background: var(--card); color: var(--muted); font-size: 12.5px; }
.el-empty__description p { color: var(--muted); }
.el-pagination.is-background .el-pager li.is-active {
  background: linear-gradient(180deg, #4b7a4b, #3f6b3f);
}
</style>
