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
            <!-- 行情锚点：农户定价前先看到近 30 天成交区间 -->
            <div v-if="marketReady" class="market-bar">
              <div class="market-bar-scale">
                <div class="seg seg-low"></div>
                <div class="seg seg-mid"></div>
                <div class="seg seg-high"></div>
              </div>
              <div class="market-bar-labels">
                <span>低四分位 ¥{{ market.q1 }}</span>
                <span class="mid">中位 ¥{{ market.median }}</span>
                <span>高四分位 ¥{{ market.q3 }}</span>
              </div>
              <div class="market-bar-src">
                <span v-if="marketLoading">正在获取{{ marketScope }}行情…</span>
                <span v-else>
                  {{ marketTitle }} · {{ market.sample }} 单成交
                  <span v-if="marketFallback" class="fb">（本区成交样本不足，已用全平台行情参考）</span>
                </span>
              </div>
            </div>
            <div v-else-if="market" class="market-weak">
              {{ marketScope }}近 30 天成交样本不足（{{ market.sample }} 单），暂无行情参考
              <span v-if="!marketRegion" style="color:#b3a992">· 填写所在地区可看本区行情</span>
            </div>
            <el-input-number v-model="form.pricePerMu" :min="1" :step="5" style="width:100%" />
            <el-alert v-if="priceHint" :type="priceHint.type" :closable="false" class="price-hint">
              {{ priceHint.text }}
            </el-alert>
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

      <el-form-item label="临期自动加价授权（选填）">
        <div class="markup">
          <el-slider v-model="form.markupLimit" :min="0" :max="20" :step="1"
            show-stops :marks="{ 0: '不加价', 10: '+10%', 20: '+20%' }" />
        </div>
        <div class="markup-tip">
          截止日当天仍无人接单时，系统按 <b>{{ form.markupLimit }}%</b> 幅度一次性上调单价并重新曝光（最多 20%）。
          <span v-if="form.markupLimit === 0">当前不授权：宁可等，也不自动加价。</span>
        </div>
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

/* ---------------- 行情锚点条 ---------------- */
.market-bar { width: 100%; margin-bottom: 10px; }
.market-bar-scale { display: flex; height: 7px; border-radius: 4px; overflow: hidden; }
.market-bar-scale .seg { height: 100%; }
.seg-low  { flex: 1; background: #e4b08a; }
.seg-mid  { flex: 2; background: #9dbb7e; }
.seg-high { flex: 1; background: #d9c47e; }
.market-bar-labels {
  display: flex; justify-content: space-between;
  font-size: 11px; color: #a89a80; margin-top: 4px;
}
.market-bar-labels .mid { color: #3f6b3f; font-weight: 600; }
.market-bar-src { font-size: 11px; color: #b3a992; margin-top: 2px; }
.market-bar-src .fb { color: #c08a2a; }
.market-weak {
  font-size: 12px; color: #a89a80; margin-bottom: 10px;
  background: #fcf8ef; border: 1px solid #f0e7d4; border-radius: 4px; padding: 6px 10px;
}
.price-hint { margin-top: 10px; width: 100%; }

/* ---------------- 加价授权滑杆 ---------------- */
.markup { width: 100%; padding: 0 6px; }
.markup-tip { font-size: 12px; color: #a89a80; margin-top: 10px; line-height: 1.6; }
.markup-tip b { color: #b8862b; font-variant-numeric: tabular-nums; }
</style>
