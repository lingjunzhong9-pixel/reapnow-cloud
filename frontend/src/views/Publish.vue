<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { orderApi } from '../api'

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
  markupLimit: 0,
  remark: ''
})

// ---------------- 行情锚点：让农户不再盲定价 ----------------
// 数据来自近 30 天已成交订单（定金已付 / 已完成），样本不足时退化为只提示样本数。
// 关键：**按地区取行情**。之前三个页面都不传 region，拿到的是全平台均价，
// 文案却写「本区行情」—— 名不副实。现在默认用农户所在地区，改地区会重新拉。
const market = ref(null)
const marketRegion = ref('')
const marketLoading = ref(false)
/** 本区样本不足时是否退回了全平台行情 */
const marketFallback = ref(false)

const loginUser = (() => {
  try { return JSON.parse(localStorage.getItem('user') || '{}') } catch (e) { return {} }
})()

// 默认地区 = 农户注册时填的地区，省一次输入
if (!form.value.region && loginUser.region) {
  form.value.region = loginUser.region
}

async function loadMarket() {
  const r = (marketRegion.value || '').trim()
  marketLoading.value = true
  try {
    let m = await orderApi.marketPrice(r || undefined)
    let fallback = false
    // 本区成交太少时自动退回全平台 —— 否则新开发的市场永远是「样本不足」，
    // 农户看不到任何锚点，比价这个功能就等于不存在。
    // 退回后文案会明确写「全平台行情」，不会让人误以为是本区数据。
    if (r && m && m.insufficient) {
      const all = await orderApi.marketPrice(undefined).catch(() => null)
      if (all && !all.insufficient) {
        m = all
        fallback = true
      }
    }
    marketFallback.value = fallback
    market.value = m
  } catch (e) {
    market.value = null
    marketFallback.value = false
  } finally {
    marketLoading.value = false
  }
}

let marketTimer = null
function scheduleMarketReload() {
  clearTimeout(marketTimer)
  marketTimer = setTimeout(loadMarket, 400)
}

onMounted(() => {
  marketRegion.value = (form.value.region || '').trim()
  loadMarket()
})

watch(() => form.value.region, (v) => {
  marketRegion.value = (v || '').trim()
  scheduleMarketReload()
})

const marketReady = computed(() => market.value && market.value.median && !market.value.insufficient)

/** 文案里的地区口径：退回全平台时要说全平台，不能冒充本区 */
const marketScope = computed(() => (marketRegion.value && !marketFallback.value ? '本区' : '全平台'))
const marketTitle = computed(() =>
  marketRegion.value && !marketFallback.value ? '本区行情 · 近 30 天' : '全平台行情 · 近 30 天')

/** 定价校验：只提醒不拦截 —— 定价自由是农户的，平台负责让他明白后果 */
const priceHint = computed(() => {
  const m = market.value
  if (!marketReady.value) return null
  const p = Number(form.value.pricePerMu) || 0
  const scope = marketScope.value
  if (p > 0 && p < m.q1) {
    const gap = (((m.q1 - p) / m.q1) * 100).toFixed(0)
    return {
      type: 'warning',
      text: `低于${scope}近 30 天成交低四分位（¥${m.q1}）约 ${gap}% —— 定得太低可能长时间无人接单，建议 ¥${m.q1} 以上`
    }
  }
  if (p > 0 && p > m.q3) {
    const gap = (((p - m.q3) / m.q3) * 100).toFixed(0)
    return {
      type: 'info',
      text: `高于${scope}高四分位（¥${m.q3}）约 ${gap}% —— 优先被抢，但机手到手价也更高，确认是你的心理价位`
    }
  }
  return { type: 'success', text: `在${scope}常见成交区间（¥${m.q1} ~ ¥${m.q3}）内，中位 ¥${m.median}` }
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
    const markup = form.value.markupLimit > 0
      ? `已授权临期自动加价（最多 +${form.value.markupLimit}%）：截止日当天仍无人接单时系统会自动加价重新曝光。`
      : ''
    await ElMessageBox.alert(
      `发布成功！需求号 ${d.demandNo}，${d.mu} 亩，${d.pricePerMu} 元/亩。附近机手现在就能在抢单大厅看到。${markup}`,
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

      <!-- 行情参考：整宽独立块。放在两个输入框下面，而不是塞进单价输入框里
           —— 否则右列比左列高一截，两列永远对不齐 -->
      <div v-if="marketReady" class="mk">
        <div class="mk-head">
          <span class="mk-title">近 30 天成交行情</span>
          <span class="mk-src">{{ marketTitle }} · {{ market.sample }} 单成交</span>
        </div>
        <div class="mk-scale">
          <div class="seg seg-low"></div>
          <div class="seg seg-mid"></div>
          <div class="seg seg-high"></div>
        </div>
        <div class="mk-labels">
          <span>低四分位 <b>¥{{ market.q1 }}</b></span>
          <span class="mid">中位 <b>¥{{ market.median }}</b></span>
          <span>高四分位 <b>¥{{ market.q3 }}</b></span>
        </div>
        <div v-if="marketFallback" class="mk-fb">本区成交样本不足，已用全平台行情参考</div>
        <div v-if="priceHint" class="mk-hint" :class="'hint-' + priceHint.type">
          {{ priceHint.text }}
        </div>
      </div>
      <div v-else-if="market" class="mk mk-weak">
        {{ marketScope }}近 30 天成交样本不足（{{ market.sample }} 单），暂无行情参考
        <span v-if="!marketRegion"> · 填写所在地区可看本区行情</span>
      </div>

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
        <div class="dl" :class="{ 'is-empty': !form.deadline }">
          {{ form.deadline ? '已选：' + deadlineText : '还没选，点上面任意一天' }}
        </div>
      </el-form-item>

      <el-form-item>
        <el-checkbox v-model="form.lodged">地块有倒伏（作业难度大，通常要加价）</el-checkbox>
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2"
          placeholder="如：麦子已经过熟，粒容易掉，天气预报后天有中雨" />
      </el-form-item>

      <el-form-item label="临期自动加价授权（选填）">
        <div class="mkup">
          <!-- 不用 el-slider 自带的 marks：它是绝对定位的，下面不留空间，
               会和说明文字叠在一起。改成自己排一行刻度 -->
          <el-slider v-model="form.markupLimit" :min="0" :max="20" :step="1" />
          <div class="mkup-scale">
            <span>不加价</span>
            <span class="cur" v-if="form.markupLimit > 0">+{{ form.markupLimit }}%</span>
            <span>+20%</span>
          </div>
          <div class="mkup-tip">
            <template v-if="form.markupLimit > 0">
              截止前 <b>1 天</b>仍无人接单时，系统按 <b>{{ form.markupLimit }}%</b>
              幅度上调一次单价并重新曝光（只加一次）。
            </template>
            <template v-else>
              当前不授权：宁可等，也不自动加价。
            </template>
          </div>
        </div>
      </el-form-item>

      <div class="actions">
        <el-button type="primary" size="large" :loading="submitting" @click="submit">
          发布需求
        </el-button>
      </div>
    </el-form>
  </el-card>
</template>

<style scoped>
.wrap { max-width: 880px; margin: 0 auto; }
.h { font-size: 17px; font-weight: 700; color: #2f3f2f; }
.h-sub { font-size: 12px; color: #909399; margin-left: 10px; }

/* Element Plus 的 form-item content 默认是横向 flex，
   多个子元素会被挤在同一行（日期按钮和「还没选」就挤在一起）。
   这里统一改成纵向，子元素各占一行，表单才立得住 */
:deep(.el-form-item__content) {
  flex-direction: column;
  align-items: stretch;
}

.days { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.dl { font-size: 12px; color: #b8862b; margin-top: 8px; }
.dl.is-empty { color: #b3a992; }

/* ---------------- 行情参考块 ---------------- */
.mk {
  background: linear-gradient(180deg, #fdf8ee, #fbf5e8);
  border: 1px solid #efe4cd;
  border-radius: 5px;
  padding: 11px 14px 12px;
  margin: -4px 0 20px;
}
.mk-head {
  display: flex; align-items: baseline; justify-content: space-between;
  gap: 10px; margin-bottom: 8px;
}
.mk-title { font-size: 12.5px; font-weight: 700; color: #6b5c44; letter-spacing: .5px; }
.mk-src { font-size: 11px; color: #a89a80; }

.mk-scale { display: flex; height: 8px; border-radius: 5px; overflow: hidden; }
.mk-scale .seg { height: 100%; }
.seg-low  { flex: 1; background: linear-gradient(90deg, #e7b992, #dfa87c); }
.seg-mid  { flex: 2; background: linear-gradient(90deg, #a8c48a, #93b475); }
.seg-high { flex: 1; background: linear-gradient(90deg, #ddca86, #d3bd6f); }

.mk-labels {
  display: flex; justify-content: space-between;
  font-size: 11.5px; color: #a89a80; margin-top: 6px;
}
.mk-labels b { color: #5c5140; font-variant-numeric: tabular-nums; }
.mk-labels .mid b { color: #3f6b3f; }

.mk-fb { font-size: 11px; color: #c08a2a; margin-top: 5px; }

.mk-hint {
  font-size: 12px; line-height: 1.6; margin-top: 9px;
  padding: 7px 10px; border-radius: 4px;
  border-left: 3px solid #cbbf9f;
  background: #fffdf7;
  color: #6b5f4a;
}
.mk-hint.hint-warning { border-left-color: #c08a2a; background: #fdf6e8; color: #8a6420; }
.mk-hint.hint-info    { border-left-color: #7f9fb8; background: #f4f8fb; color: #4a6b82; }
.mk-hint.hint-success { border-left-color: #7ba36a; background: #f6faf2; color: #4a6b3d; }

.mk-weak {
  font-size: 12px; color: #a89a80;
  background: #fcfaf4; border-style: dashed;
}

/* ---------------- 加价授权滑杆 ---------------- */
.mkup { width: 100%; }
.mkup-scale {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 11px; color: #a89a80; margin-top: 2px; padding: 0 6px;
}
.mkup-scale .cur {
  font-size: 12px; font-weight: 700; color: #b8862b;
  font-variant-numeric: tabular-nums;
}
.mkup-tip { font-size: 12px; color: #a89a80; margin-top: 10px; line-height: 1.7; }
.mkup-tip b { color: #b8862b; font-variant-numeric: tabular-nums; }

/* ---------------- 提交区 ---------------- */
.actions {
  margin-top: 6px; padding-top: 16px;
  border-top: 1px dashed #e8dfcd;
}
.actions .el-button { width: 100%; }

/* 发布是全页唯一的主操作，用和大厅「我要抢这块地」同一枚麦金，
   全站保持「金色 = 你现在要做的那件事」。
   注意：App.vue 里有一条全局 .el-button--primary 规则（特异性 0,4,0），
   scoped 的 :deep() 只有 0,2,0 压不住，所以这里必须 !important。 */
.actions :deep(.el-button--primary) {
  height: 44px;
  background: linear-gradient(150deg, #f5d78c 0%, #d9a63c 52%, #bf8d2c 100%) !important;
  border: none !important;
  color: #241a0c !important;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 2px;
  box-shadow: 0 3px 12px rgba(191, 141, 44, .3), inset 0 1px 0 rgba(255, 255, 255, .45);
  transition: filter .16s ease, box-shadow .18s ease, transform .14s ease;
}
.actions :deep(.el-button--primary:hover) {
  background: linear-gradient(150deg, #f9e0a0 0%, #e2b04b 52%, #c9962f 100%) !important;
  box-shadow: 0 7px 20px rgba(191, 141, 44, .42), inset 0 1px 0 rgba(255, 255, 255, .5);
  transform: translateY(-1px);
}
</style>
