<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const router = useRouter()
const submitting = ref(false)

// 交互极简：预约时间只用「今天 / 明天 / 后天」这类相对表达，避免「年月日」选择器
// —— 这是从县域农机平台 1.0 版本因注册/操作复杂导致老年用户流失的教训里学到的
const quickDays = [
  { label: '明天', value: 1 },
  { label: '后天', value: 2 },
  { label: '三天后', value: 3 },
  { label: '五天后', value: 5 }
]

const form = ref({
  plotName: '',
  region: '',
  mu: 20,
  pricePerMu: 70,
  cropStage: 'RIPE',
  deadline: '',
  lodged: false,
  remark: ''
})

const selectedDay = ref(null)
const customDate = ref('')

function pickDay(d) {
  selectedDay.value = d.value
  const dt = new Date()
  dt.setDate(dt.getDate() + d.value)
  const p = (n) => String(n).padStart(2, '0')
  form.value.deadline = `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())}`
  customDate.value = form.value.deadline
}

const deadlineText = computed(() => {
  if (!form.value.deadline) return '未选择'
  const days = Math.ceil((new Date(form.value.deadline) - new Date()) / 86400000)
  return days <= 0 ? `${form.value.deadline}（今天）` : `${form.value.deadline}（还有 ${days} 天）`
})

async function submit() {
  if (!localStorage.getItem('token')) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  if (!form.value.deadline) {
    ElMessage.warning('请选择最迟收割时间')
    return
  }
  submitting.value = true
  try {
    const d = await api.post('/demands', form.value)
    await ElMessageBox.alert(
      `发布成功！需求号 ${d.demandNo}，${d.mu} 亩，${d.pricePerMu} 元/亩。附近机手现在就能在抢单大厅看到。`,
      '已发布', { confirmButtonText: '去看抢单大厅', type: 'success' }
    ).catch(() => {})
    router.push('/')
  } catch (e) {
    ElMessage.error(e.message || '发布失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card class="wrap">
    <template #header>
      <span class="h">发布作业需求</span>
      <span class="h-sub">只填 4 个必填项，农忙时在地头单手也能发完</span>
    </template>

    <el-form label-position="top" style="max-width: 720px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="地块名称（必填）">
            <el-input v-model="form.plotName" placeholder="如：村东头三等地" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所在地区">
            <el-input v-model="form.region" placeholder="如：安徽阜阳颍东区" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="亩数（必填）">
            <el-input-number v-model="form.mu" :min="0.1" :step="1" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="期望单价（元/亩，必填）">
            <el-input-number v-model="form.pricePerMu" :min="1" :step="5" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="麦子状态">
        <el-radio-group v-model="form.cropStage">
          <el-radio-button label="JUST_RIPE">刚熟（还能等两三天）</el-radio-button>
          <el-radio-button label="RIPE">适收（最佳窗口）</el-radio-button>
          <el-radio-button label="OVERRIPE">过熟（易落粒，要尽快）</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="最迟哪天必须收完（必填）">
        <div class="days">
          <el-button v-for="d in quickDays" :key="d.value"
            :type="selectedDay === d.value ? 'primary' : 'default'"
            @click="pickDay(d)">{{ d.label }}</el-button>
          <el-date-picker v-model="customDate" type="date" placeholder="或选具体日期"
            value-format="YYYY-MM-DD" style="width: 170px"
            @change="(v) => { form.deadline = v; selectedDay = null }" />
        </div>
        <div class="dl">已选：{{ deadlineText }}</div>
      </el-form-item>

      <el-form-item>
        <el-checkbox v-model="form.lodged">地块有倒伏（作业难度大，通常要加价）</el-checkbox>
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2"
          placeholder="如：麦子已经过熟，粒容易掉，天气预报后天有中雨" />
      </el-form-item>

      <el-button type="primary" size="large" :loading="submitting" @click="submit">
        发布需求
      </el-button>
    </el-form>
  </el-card>
</template>

<style scoped>
.wrap { max-width: 820px; margin: 0 auto; }
.h { font-size: 17px; font-weight: 700; color: #2f3f2f; }
.h-sub { font-size: 12px; color: #909399; margin-left: 10px; }
.days { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.dl { font-size: 12px; color: #e6a23c; margin-top: 6px; }
</style>
