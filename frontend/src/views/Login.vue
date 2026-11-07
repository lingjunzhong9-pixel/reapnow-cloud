<script setup>
import { ref } from 'vue'
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

function quick(u) {
  form.value.username = u
  form.value.password = '123456'
  mode.value = 'login'
}
</script>

<template>
  <div class="wrap">
    <el-card class="card">
      <div class="title">🌾 抢收</div>
      <div class="sub">{{ mode === 'login' ? '登录账号' : '注册新账号' }}</div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <template v-if="mode === 'register'">
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" placeholder="如：李桂芳" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" />
          </el-form-item>
          <el-form-item label="所在地区">
            <el-input v-model="form.region" placeholder="如：安徽阜阳颍东区" />
          </el-form-item>
          <el-form-item label="我是">
            <el-radio-group v-model="form.role">
              <el-radio-button label="FARMER">农户</el-radio-button>
              <el-radio-button label="MACHINE_OWNER">农机手</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </template>
        <el-button type="primary" class="submit" :loading="loading" @click="submit">
          {{ mode === 'login' ? '登录' : '注册' }}
        </el-button>
        <div class="switch">
          <el-link type="primary" @click="mode = mode === 'login' ? 'register' : 'login'">
            {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
          </el-link>
        </div>
      </el-form>

      <el-divider>演示账号（密码均为 123456）</el-divider>
      <div class="quick">
        <el-button size="small" @click="quick('ligf')">李桂芳 · 农户</el-button>
        <el-button size="small" @click="quick('youdg')">尤大哥 · 农机手</el-button>
        <el-button size="small" @click="quick('liuk')">刘康 · 农机手</el-button>
      </div>
      <div class="hint">提示：抢单需要机手账号</div>
    </el-card>
  </div>
</template>

<style scoped>
.wrap { display: flex; justify-content: center; padding-top: 30px; }
.card { width: 420px; }
.title { font-size: 26px; font-weight: 700; text-align: center; color: #2f6b2f; }
.sub { text-align: center; color: #909399; margin: 6px 0 18px; }
.submit { width: 100%; }
.switch { text-align: center; margin-top: 10px; }
.quick { display: flex; justify-content: center; gap: 6px; flex-wrap: wrap; }
.hint { text-align: center; color: #c0c4cc; font-size: 12px; margin-top: 8px; }
</style>
