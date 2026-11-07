<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(9)
const filters = ref({ status: 'OPEN', region: '', keyword: '', sort: 'default' })

const user = computed(() => {
  const u = localStorage.getItem('user')
  return u ? JSON.parse(u) : null
})
const isOwner = computed(() => user.value && (user.value.role === 'MACHINE_OWNER' || user.value.role === 'ADMIN'))

const stageMap = {
  JUST_RIPE: { text: '刚熟', type: 'success' },
  RIPE: { text: '适收', type: 'warning' },
  OVERRIPE: { text: '过熟易落粒', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const data = await api.get('/demands', {
      params: { ...filters.value, page: page.value, size: size.value }
    })
    list.value = data.content
    total.value = data.totalElements
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(load)

function search() { page.value = 1; load() }

function urgency(d) {
  if (d.daysLeft <= 0) return { cls: 'u-red', text: '今天截止' }
  if (d.daysLeft <= 1) return { cls: 'u-red', text: `仅剩 ${d.daysLeft} 天` }
  if (d.daysLeft <= 3) return { cls: 'u-orange', text: `剩 ${d.daysLeft} 天` }
  return { cls: 'u-green', text: `剩 ${d.daysLeft} 天` }
}

const grabDialog = ref(false)
const current = ref(null)
const grabMu = ref(0)
const machineModel = ref('雷沃谷神 9公斤/秒')
const submitting = ref(false)

function openGrab(d) {
  if (!localStorage.getItem('token')) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  if (!isOwner.value) {
    ElMessage.warning('只有农机手账号可以抢单，请用机手账号登录')
    return
  }
  current.value = d
  grabMu.value = d.remainMu
  grabDialog.value = true
}

const estimate = computed(() => {
  if (!current.value) return { amount: 0, deposit: 0 }
  const amount = (Number(grabMu.value) || 0) * Number(current.value.pricePerMu)
  return { amount: amount.toFixed(2), deposit: (amount * 0.3).toFixed(2) }
})

async function doGrab() {
  submitting.value = true
  try {
    const order = await api.post('/orders/grab', {
      demandId: current.value.id,
      mu: grabMu.value,
      machineModel: machineModel.value
    })
    grabDialog.value = false
    ElMessageBox.alert(
      `抢单成功！订单号 ${order.orderNo}，需支付定金 ¥${order.deposit}（总额 ¥${order.amount} 的 30%）`,
      '抢到了',
      { confirmButtonText: '去支付定金', type: 'success' }
    ).then(() => router.push('/orders')).catch(() => {})
    load()
  } catch (e) {
    ElMessage.error(e.message || '抢单失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <el-card class="filter">
      <el-form inline>
        <el-form-item label="地区">
          <el-input v-model="filters.region" placeholder="如：阜阳 / 项城" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="地块 / 备注" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="filters.sort" style="width: 170px" @change="search">
            <el-option label="最紧急优先" value="default" />
            <el-option label="单价从高到低" value="priceDesc" />
            <el-option label="亩数从大到小" value="muDesc" />
            <el-option label="最新发布" value="newest" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading" class="grid">
      <el-card v-for="d in list" :key="d.id" class="demand" shadow="hover">
        <div class="d-head">
          <span class="plot">{{ d.plotName }}</span>
          <el-tag :class="urgency(d).cls" size="small" effect="dark" style="border:none">
            {{ urgency(d).text }}
          </el-tag>
        </div>
        <div class="d-region">📍 {{ d.region }} · {{ d.farmerName }}</div>

        <div class="d-metrics">
          <div class="metric">
            <div class="mv">{{ d.remainMu }}<span class="mu">亩</span></div>
            <div class="ml">可接 / 共 {{ d.mu }} 亩</div>
          </div>
          <div class="metric">
            <div class="mv price">¥{{ d.pricePerMu }}<span class="mu">/亩</span></div>
            <div class="ml">农户期望单价</div>
          </div>
        </div>

        <div class="d-tags">
          <el-tag size="small" :type="stageMap[d.cropStage]?.type">{{ stageMap[d.cropStage]?.text }}</el-tag>
          <el-tag v-if="d.lodged" size="small" type="danger" effect="plain">倒伏（可加价）</el-tag>
        </div>

        <div class="d-deadline">最迟收割：<b>{{ d.deadline }}</b></div>
        <div v-if="d.remark" class="d-remark">{{ d.remark }}</div>

        <el-button type="primary" class="grab" :disabled="d.remainMu <= 0" @click="openGrab(d)">
          {{ d.remainMu <= 0 ? '已被抢满' : '我要抢这块地' }}
        </el-button>
      </el-card>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="附近暂时没有可抢的作业需求" />
    <div class="pager">
      <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="(p) => { page = p; load(); }" />
    </div>

    <el-dialog v-model="grabDialog" title="确认抢单" width="460px">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="地块">{{ current.plotName }}（{{ current.region }}）</el-descriptions-item>
          <el-descriptions-item label="可接亩数">{{ current.remainMu }} 亩 / 共 {{ current.mu }} 亩</el-descriptions-item>
          <el-descriptions-item label="单价">¥{{ current.pricePerMu }} / 亩</el-descriptions-item>
          <el-descriptions-item label="最迟收割">{{ current.deadline }}（{{ urgency(current).text }}）</el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top" style="margin-top:14px">
          <el-form-item label="承接亩数">
            <el-input-number v-model="grabMu" :min="0.1" :max="Number(current.remainMu)" :step="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="作业机型">
            <el-input v-model="machineModel" />
          </el-form-item>
        </el-form>
        <el-alert type="warning" :closable="false">
          <div>作业总额：<b>¥{{ estimate.amount }}</b></div>
          <div>需付定金（30%）：<b style="color:#e6a23c;font-size:16px">¥{{ estimate.deposit }}</b></div>
        </el-alert>
      </template>
      <template #footer>
        <el-button @click="grabDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doGrab">确认抢单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter { margin-bottom: 16px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 14px; }
.demand { border-top: 3px solid #67c23a; }
.d-head { display: flex; justify-content: space-between; align-items: center; }
.plot { font-size: 17px; font-weight: 700; color: #2f3f2f; }
.d-region { color: #909399; font-size: 12px; margin: 6px 0 10px; }
.d-metrics { display: flex; gap: 10px; }
.metric { flex: 1; background: #f7faf5; border-radius: 6px; padding: 8px 10px; }
.mv { font-size: 20px; font-weight: 700; color: #2f6b2f; }
.mv.price { color: #e6a23c; }
.mu { font-size: 12px; font-weight: 400; color: #909399; margin-left: 2px; }
.ml { font-size: 11px; color: #909399; }
.d-tags { display: flex; gap: 6px; margin: 10px 0 8px; flex-wrap: wrap; }
.d-deadline { font-size: 13px; color: #606266; }
.d-remark { font-size: 12px; color: #a0a8a0; margin-top: 6px; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.grab { width: 100%; margin-top: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
:deep(.u-red) { background: #f56c6c !important; }
:deep(.u-orange) { background: #e6a23c !important; }
:deep(.u-green) { background: #67c23a !important; }
</style>
