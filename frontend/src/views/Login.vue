<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const router = useRouter()
const mode = ref('login')
const loading = ref(false)
const form = ref({
  username: 'youdg', password: '123456', nickname: '', phone: '',
  region: '', role: 'FARMER'
})

// 登录页展示的实时数据（不打广告，用系统里真实的数据）
const stats = ref({ regions: 9, plots: 51, days: '1–14' })

onMounted(async () => {
  try {
    const r = await api.get('/demands', { params: { size: 100, status: 'OPEN' } })
    const list = r.content || []
    const regions = new Set(list.map(d => d.region))
    stats.value.plots = r.totalElements ?? list.length
    stats.value.regions = regions.size || 9
    const ds = list.map(d => d.daysLeft).filter(n => typeof n === 'number')
    if (ds.length) stats.value.days = Math.min(...ds) + '–' + Math.max(...ds)
  } catch (e) { /* 取不到就用默认值，不影响登录 */ }
})

async function submit() {
  loading.value = true
  try {
    if (mode.value === 'login') {
      const data = await api.post('/auth/login', {
        username: form.value.username, password: form.value.password
      })
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
      ElMessage.success('登录成功')
      router.push('/')
      setTimeout(() => window.location.reload(), 200)
    } else {
      await api.post('/auth/register', form.value)
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
    }
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    loading.value = false
  }
}

/** 演示账号：点一下就填好 */
const demos = [
  { u: 'ligf',  name: '李桂芳', place: '安徽阜阳', tag: '农户',   kind: 'farmer' },
  { u: 'youdg', name: '尤大哥', place: '陕西渭南', tag: '机手',   kind: 'owner' },
  { u: 'admin', name: '管理员', place: '平台后台', tag: '后台',   kind: 'admin' }
]

function useDemo(d) {
  form.value.username = d.u
  form.value.password = '123456'
  mode.value = 'login'
}

const isRegister = computed(() => mode.value === 'register')

function toggle() {
  mode.value = isRegister.value ? 'login' : 'register'
}
</script>

<template>
  <div class="scene">
    <!-- ================= 背景：麦熟黄昏 ================= -->
    <div class="bg" aria-hidden="true">
      <div class="sky"></div>
      <div class="sun"><span class="sun-core"></span></div>
      <div class="haze"></div>
      <div class="terraces">
        <div class="terrace f1"></div>
        <div class="terrace f2"></div>
        <div class="terrace f3"></div>
      </div>
      <div class="wheat w-far"></div>
      <div class="wheat w-mid"></div>
      <div class="wheat w-near"></div>
      <div class="vignette"></div>
      <div class="noise"></div>
    </div>

    <!-- ================= 主体 ================= -->
    <div class="layout">

      <!-- 左：谚语与数据 -->
      <section class="hero">
        <div class="brand">
          <span class="seal">抢</span>
          <span class="brand-txt">
            <b>抢收</b>
            <i>ReapNow</i>
          </span>
        </div>

        <div class="proverb">
          <p class="p1">麦熟一晌</p>
          <p class="p2">虎口夺粮</p>
        </div>

        <p class="lede">
          跨区机收对接与履约平台
        </p>

        <div class="stats">
          <div class="st">
            <b>{{ stats.regions }}</b>
            <span>省麦熟带</span>
          </div>
          <div class="st">
            <b>{{ stats.plots }}</b>
            <span>块待收地块</span>
          </div>
          <div class="st">
            <b>{{ stats.days }}</b>
            <span>天抢收窗口</span>
          </div>
        </div>

        <p class="creed">
          「说好了明天来，机手不来了」<br>
          <em>跨区作业里最贵的一句话。</em><br>
          用一笔 30% 定金，把它管住。
        </p>
      </section>

      <!-- 右：登录面板 -->
      <section class="panel">
        <div class="panel-head">
          <h2>{{ isRegister ? '注册账号' : '登录' }}</h2>
          <p>{{ isRegister ? '选一个身份，加入今年的抢收季' : '用手机号或演示账号进入' }}</p>
        </div>

        <form class="form" @submit.prevent="submit">
          <label class="field">
            <span class="lab">账号</span>
            <input v-model="form.username" type="text" autocomplete="username"
                   placeholder="请输入用户名" />
          </label>

          <label class="field">
            <span class="lab">密码</span>
            <input v-model="form.password" type="password" autocomplete="current-password"
                   placeholder="请输入密码" />
          </label>

          <template v-if="isRegister">
            <label class="field">
              <span class="lab">昵称</span>
              <input v-model="form.nickname" type="text" placeholder="如：李桂芳" />
            </label>
            <div class="two">
              <label class="field">
                <span class="lab">手机号</span>
                <input v-model="form.phone" type="tel" placeholder="11 位手机号" />
              </label>
              <label class="field">
                <span class="lab">所在地区</span>
                <input v-model="form.region" type="text" placeholder="如：安徽阜阳" />
              </label>
            </div>
            <div class="field">
              <span class="lab">我是</span>
              <div class="roles">
                <button type="button" class="role" :class="{ on: form.role === 'FARMER' }"
                        @click="form.role = 'FARMER'">
                  <b>农户</b><span>有地要收</span>
                </button>
                <button type="button" class="role" :class="{ on: form.role === 'MACHINE_OWNER' }"
                        @click="form.role = 'MACHINE_OWNER'">
                  <b>农机手</b><span>有机器接活</span>
                </button>
              </div>
            </div>
          </template>

          <button class="submit" type="submit" :disabled="loading">
            <span v-if="loading" class="spin"></span>
            {{ loading ? '处理中…' : (isRegister ? '注册并进入' : '进入抢收季') }}
          </button>

          <button class="switch" type="button" @click="toggle">
            {{ isRegister ? '已有账号？回去登录' : '没有账号？注册一个' }}
          </button>
        </form>

        <div class="divider">
          <span>演示账号 · 密码均为 123456</span>
        </div>

        <div class="demos">
          <button v-for="d in demos" :key="d.u" type="button"
                  class="demo" :class="d.kind" @click="useDemo(d)">
            <span class="avatar">{{ d.name.slice(0, 1) }}</span>
            <span class="d-info">
              <b>{{ d.name }}</b>
              <i>{{ d.place }} · {{ d.tag }}</i>
            </span>
          </button>
        </div>

        <p class="foot">抢单需要机手账号 · 后台流水仅管理员可见</p>
      </section>

    </div>
  </div>
</template>

<style scoped>
/* ═══════════════════════════════════════════════════════
   设计方向：麦熟黄昏 · 农耕谚语 · 纸质账本
   配色：深褐夜空 + 麦金 + 米纸；字体：楷体大字 + 雅黑正文
   ═══════════════════════════════════════════════════════ */

.scene {
  position: fixed;
  inset: 0;
  overflow-x: hidden;
  overflow-y: auto;
  background: #14100c;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

/* ---------- 背景（固定，不随内容滚动）---------- */
.bg { position: fixed; inset: 0; }

.sky {
  position: absolute; inset: 0;
  background:
    radial-gradient(120% 80% at 28% 78%, rgba(212, 161, 58, .28) 0%, transparent 55%),
    linear-gradient(180deg, #12100d 0%, #1c1710 32%, #2e2416 62%, #4a3518 84%, #6b4a1d 100%);
}

.sun {
  position: absolute; left: 22%; bottom: 22%;
  width: 320px; height: 320px; margin-left: -160px; margin-bottom: -160px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 213, 122, .95) 0%, rgba(233, 174, 68, .55) 38%, rgba(212, 161, 58, 0) 70%);
  filter: blur(2px);
  animation: sun-breathe 9s ease-in-out infinite;
}
.sun-core {
  position: absolute; inset: 38%;
  border-radius: 50%;
  background: radial-gradient(circle, #fff3d0 0%, #ffd882 45%, rgba(255, 200, 96, 0) 72%);
}
@keyframes sun-breathe {
  0%, 100% { transform: scale(1); opacity: .95; }
  50% { transform: scale(1.06); opacity: 1; }
}

.haze {
  position: absolute; left: 0; right: 0; bottom: 0; height: 46%;
  background: linear-gradient(180deg, transparent 0%, rgba(212, 161, 58, .10) 55%, rgba(212, 161, 58, .22) 100%);
}

/* 层叠田垄（注意：类名不要用 .field，会和表单的输入框标签冲突） */
.terraces { position: absolute; left: 0; right: 0; bottom: 0; height: 46%; }
.terrace {
  position: absolute; left: -10%; right: -10%;
  border-radius: 50% 50% 0 0 / 100% 100% 0 0;
}
.f1 { bottom: -12%; height: 52%; background: #3a2a15; opacity: .85; }
.f2 { bottom: -20%; height: 46%; background: #241a0e; opacity: .92; }
.f3 { bottom: -28%; height: 40%; background: #14100a; }

/* 麦穗（SVG 背景，三排制造纵深） */
.wheat {
  position: absolute; left: 0; right: 0;
  background-repeat: repeat-x;
  background-position: bottom left;
  transform-origin: bottom center;
  animation: sway 7s ease-in-out infinite;
}
.w-far {
  bottom: 26%; height: 30%;
  background-size: auto 62%;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='34' height='240' viewBox='0 0 34 240'%3E%3Cg stroke='%238a6a24' stroke-width='1.5' fill='none' stroke-linecap='round'%3E%3Cpath d='M17 240 C16 170 17 120 17 66'/%3E%3C/g%3E%3Cg fill='%23a8812c'%3E%3Cellipse cx='11' cy='72' rx='3.4' ry='6' transform='rotate(-24 11 72)'/%3E%3Cellipse cx='23' cy='72' rx='3.4' ry='6' transform='rotate(24 23 72)'/%3E%3Cellipse cx='11' cy='88' rx='3.4' ry='6' transform='rotate(-24 11 88)'/%3E%3Cellipse cx='23' cy='88' rx='3.4' ry='6' transform='rotate(24 23 88)'/%3E%3Cellipse cx='11' cy='104' rx='3.4' ry='6' transform='rotate(-24 11 104)'/%3E%3Cellipse cx='23' cy='104' rx='3.4' ry='6' transform='rotate(24 23 104)'/%3E%3Cellipse cx='17' cy='62' rx='3.4' ry='7'/%3E%3C/g%3E%3C/svg%3E");
  opacity: .32;
  filter: blur(.6px);
  animation-delay: -2s;
}
.w-mid {
  bottom: 14%; height: 38%;
  background-size: auto 78%;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='44' height='240' viewBox='0 0 44 240'%3E%3Cg stroke='%23b08a2e' stroke-width='1.8' fill='none' stroke-linecap='round'%3E%3Cpath d='M22 240 C21 160 22 110 22 58'/%3E%3C/g%3E%3Cg fill='%23cfa63c'%3E%3Cellipse cx='14' cy='64' rx='4' ry='7' transform='rotate(-24 14 64)'/%3E%3Cellipse cx='30' cy='64' rx='4' ry='7' transform='rotate(24 30 64)'/%3E%3Cellipse cx='14' cy='82' rx='4' ry='7' transform='rotate(-24 14 82)'/%3E%3Cellipse cx='30' cy='82' rx='4' ry='7' transform='rotate(24 30 82)'/%3E%3Cellipse cx='14' cy='100' rx='4' ry='7' transform='rotate(-24 14 100)'/%3E%3Cellipse cx='30' cy='100' rx='4' ry='7' transform='rotate(24 30 100)'/%3E%3Cellipse cx='22' cy='54' rx='4' ry='8'/%3E%3C/g%3E%3C/svg%3E");
  opacity: .55;
}
.w-near {
  bottom: 0; height: 26%;
  background-size: auto 96%;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='58' height='240' viewBox='0 0 58 240'%3E%3Cg stroke='%23d4a13a' stroke-width='2.2' fill='none' stroke-linecap='round'%3E%3Cpath d='M29 240 C28 150 29 100 29 46'/%3E%3C/g%3E%3Cg fill='%23e8bd5c'%3E%3Cellipse cx='19' cy='54' rx='5' ry='9' transform='rotate(-24 19 54)'/%3E%3Cellipse cx='39' cy='54' rx='5' ry='9' transform='rotate(24 39 54)'/%3E%3Cellipse cx='19' cy='74' rx='5' ry='9' transform='rotate(-24 19 74)'/%3E%3Cellipse cx='39' cy='74' rx='5' ry='9' transform='rotate(24 39 74)'/%3E%3Cellipse cx='19' cy='94' rx='5' ry='9' transform='rotate(-24 19 94)'/%3E%3Cellipse cx='39' cy='94' rx='5' ry='9' transform='rotate(24 39 94)'/%3E%3Cellipse cx='29' cy='42' rx='5' ry='10'/%3E%3C/g%3E%3C/svg%3E");
  opacity: .22;
  filter: blur(1.6px);
  animation-delay: -4s;
}
@keyframes sway {
  0%, 100% { transform: skewX(0deg); }
  50% { transform: skewX(1.1deg); }
}

.vignette {
  position: absolute; inset: 0;
  background:
    /* 左下压暗，保证谚语下方的数据与结语可读 */
    linear-gradient(to top right, rgba(8, 6, 4, .94) 0%, rgba(8, 6, 4, .62) 20%, rgba(8, 6, 4, .18) 38%, transparent 52%),
    radial-gradient(120% 100% at 50% 40%, transparent 40%, rgba(10, 8, 5, .82) 100%);
}

/* 颗粒噪点 */
.noise {
  position: absolute; inset: 0; opacity: .18; mix-blend-mode: overlay;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.82' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
}

/* ---------- 布局 ---------- */
.layout {
  position: relative;
  min-height: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(380px, .85fr);
  align-items: center;
  gap: 40px;
  padding: clamp(28px, 5vh, 56px) clamp(28px, 6vw, 96px);
  box-sizing: border-box;
}

/* ---------- 左：谚语 ---------- */
.hero {
  color: #f4ead6;
  animation: rise .9s cubic-bezier(.16, 1, .3, 1) both;
}

.brand { display: flex; align-items: center; gap: 12px; margin-bottom: clamp(26px, 4vh, 52px); }
.seal {
  width: 40px; height: 40px; flex: none;
  display: grid; place-items: center;
  font-family: "KaiTi", "STKaiti", serif;
  font-size: 24px; font-weight: 700; color: #1a130a;
  background: linear-gradient(150deg, #f5d27c, #d4a13a);
  border-radius: 3px;
  box-shadow: 0 2px 12px rgba(212, 161, 58, .45), inset 0 1px 0 rgba(255, 255, 255, .5);
}
.brand-txt { display: flex; align-items: baseline; gap: 9px; }
.brand-txt b { font-size: 19px; letter-spacing: 6px; font-weight: 700; }
.brand-txt i {
  font-style: normal; font-size: 11px; letter-spacing: 2.6px;
  color: #a98c55; text-transform: uppercase;
}

.proverb { margin: 0; }
.proverb p {
  margin: 0;
  font-family: "KaiTi", "STKaiti", "SimSun", serif;
  font-size: clamp(52px, 6.6vw, 96px);
  line-height: 1.02;
  letter-spacing: .1em;
  background: linear-gradient(175deg, #fff6e2 0%, #f0cd85 42%, #c79a45 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: 0 6px 40px rgba(212, 161, 58, .18);
}
.proverb .p2 { padding-left: .62em; }

.lede {
  margin: 18px 0 0;
  font-size: 14px; letter-spacing: 5px; color: #c2ab7d;
  text-shadow: 0 1px 10px rgba(8, 6, 4, .9);
}

.stats {
  display: flex; gap: clamp(26px, 4vw, 58px);
  margin: clamp(26px, 4vh, 44px) 0 0;
  padding-top: 22px;
  border-top: 1px solid rgba(212, 161, 58, .28);
  max-width: 460px;
}
.st { display: flex; flex-direction: column; gap: 5px; }
.st b {
  font-family: "KaiTi", serif;
  font-size: 30px; font-weight: 700; color: #f7d488;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 2px 14px rgba(8, 6, 4, .95);
}
.st span {
  font-size: 11.5px; letter-spacing: 1.4px; color: #c0ac86;
  text-shadow: 0 1px 8px rgba(8, 6, 4, .95);
}

.creed {
  margin: clamp(26px, 4vh, 46px) 0 0;
  font-size: 13.5px; line-height: 2; color: #bdac8c;
  max-width: 430px;
  padding-left: 15px;
  border-left: 2px solid rgba(212, 161, 58, .55);
  text-shadow: 0 1px 10px rgba(8, 6, 4, .95);
}
.creed em { color: #e5bc6e; font-style: normal; }

/* ---------- 右：登录面板 ---------- */
.panel {
  width: 100%;
  max-width: 430px;
  justify-self: end;
  background: linear-gradient(170deg, #fdfaf3 0%, #f6efe0 100%);
  border-radius: 4px;
  padding: clamp(26px, 3.4vh, 40px) clamp(24px, 2.6vw, 38px);
  box-shadow:
    0 32px 80px rgba(0, 0, 0, .55),
    0 0 0 1px rgba(120, 92, 46, .18),
    inset 0 1px 0 rgba(255, 255, 255, .9);
  animation: rise 1s cubic-bezier(.16, 1, .3, 1) .12s both;
}

@keyframes rise {
  from { opacity: 0; transform: translateY(24px); }
  to { opacity: 1; transform: translateY(0); }
}

.panel-head h2 {
  margin: 0;
  font-family: "KaiTi", "STKaiti", serif;
  font-size: 30px; font-weight: 700;
  color: #2b2013;
  letter-spacing: 3px;
}
.panel-head p {
  margin: 7px 0 22px;
  font-size: 12.5px; color: #8d7c62; letter-spacing: .4px;
}

/* 表单 */
.form { display: flex; flex-direction: column; gap: 15px; }
.field { display: flex; flex-direction: column; gap: 7px; }
.lab {
  font-size: 11.5px; letter-spacing: 1.6px; color: #9b8a6e; font-weight: 600;
}
.field input {
  width: 100%; box-sizing: border-box;
  padding: 12px 13px;
  font-size: 14px; font-family: inherit; color: #2b2013;
  background: #fffdf8;
  border: 1px solid #ddd0b6;
  border-radius: 3px;
  outline: none;
  transition: border-color .18s, box-shadow .18s, background .18s;
}
.field input::placeholder { color: #bcae95; }
.field input:focus {
  border-color: #c9a227;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(201, 162, 39, .14);
}

.two { display: grid; grid-template-columns: 1fr 1fr; gap: 13px; }

.roles { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.role {
  display: flex; flex-direction: column; gap: 3px; align-items: flex-start;
  padding: 11px 13px; cursor: pointer;
  background: #fffdf8;
  border: 1px solid #ddd0b6; border-radius: 3px;
  font-family: inherit; text-align: left;
  transition: all .18s;
}
.role b { font-size: 14px; color: #3a2c19; }
.role span { font-size: 11px; color: #a29070; }
.role:hover { border-color: #c9a227; }
.role.on {
  border-color: #c9a227;
  background: #fff8e6;
  box-shadow: inset 0 0 0 1px rgba(201, 162, 39, .5);
}

.submit {
  margin-top: 5px;
  padding: 13px; cursor: pointer;
  font-family: "KaiTi", "STKaiti", serif;
  font-size: 18px; letter-spacing: 4px; font-weight: 700;
  color: #241a0c;
  background: linear-gradient(150deg, #f3d488, #d4a13a 55%, #b8862b);
  border: none; border-radius: 3px;
  box-shadow: 0 6px 20px rgba(184, 134, 43, .35), inset 0 1px 0 rgba(255, 255, 255, .55);
  transition: transform .14s, box-shadow .18s, filter .18s;
  display: flex; align-items: center; justify-content: center; gap: 9px;
}
.submit:hover:not(:disabled) {
  filter: brightness(1.05);
  box-shadow: 0 9px 26px rgba(184, 134, 43, .45), inset 0 1px 0 rgba(255, 255, 255, .6);
  transform: translateY(-1px);
}
.submit:active:not(:disabled) { transform: translateY(0); }
.submit:disabled { opacity: .7; cursor: default; }

.spin {
  width: 14px; height: 14px; border-radius: 50%;
  border: 2px solid rgba(36, 26, 12, .3);
  border-top-color: #241a0c;
  animation: spin .7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.switch {
  background: none; border: none; cursor: pointer;
  font-family: inherit; font-size: 12.5px; color: #9b8a6e;
  text-decoration: underline; text-underline-offset: 3px;
  text-decoration-color: #d5c6a8;
  padding: 2px;
  transition: color .16s;
}
.switch:hover { color: #b8862b; }

/* 分隔 */
.divider {
  display: flex; align-items: center; gap: 12px;
  margin: 22px 0 14px;
  font-size: 11px; letter-spacing: 1.3px; color: #a4957a;
}
.divider::before, .divider::after {
  content: ""; flex: 1; height: 1px;
  background: linear-gradient(90deg, transparent, #d9cdb4, transparent);
}

/* 演示账号卡 */
.demos { display: flex; flex-direction: column; gap: 8px; }
.demo {
  display: flex; align-items: center; gap: 11px;
  padding: 9px 11px; cursor: pointer;
  background: #fffdf8;
  border: 1px solid #e2d7c0; border-radius: 3px;
  font-family: inherit; text-align: left; width: 100%;
  transition: all .16s;
}
.demo:hover {
  border-color: #c9a227;
  background: #fffaf0;
  transform: translateX(2px);
}
.avatar {
  width: 32px; height: 32px; flex: none;
  display: grid; place-items: center;
  font-family: "KaiTi", serif; font-size: 17px; font-weight: 700;
  border-radius: 50%; color: #fff;
}
.demo.farmer .avatar { background: linear-gradient(145deg, #6f9a4a, #4a7030); }
.demo.owner  .avatar { background: linear-gradient(145deg, #d4a13a, #a8781f); }
.demo.admin  .avatar { background: linear-gradient(145deg, #7a6f5f, #4d463c); }
.d-info { display: flex; flex-direction: column; gap: 2px; }
.d-info b { font-size: 13.5px; color: #3a2c19; font-weight: 700; }
.d-info i { font-style: normal; font-size: 11px; color: #a29070; }

.foot {
  margin: 14px 0 0; text-align: center;
  font-size: 11px; color: #b3a488; letter-spacing: .5px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1000px) {
  .layout {
    grid-template-columns: 1fr;
    gap: 26px;
    align-content: center;
    padding: 30px 22px;
    overflow-y: auto;
  }
  .hero { text-align: left; }
  .brand { margin-bottom: 18px; }
  .proverb p { font-size: clamp(42px, 11vw, 64px); }
  .stats { margin-top: 20px; padding-top: 16px; }
  .creed { display: none; }
  .panel { justify-self: center; max-width: 430px; }
}
</style>
