<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { demandApi, orderApi, userApi } from '../api'

/**
 * 调度工作台 —— 「农户发单后只能干等」的兜底界面。
 * 调度员在这里看到临期无人接的需求，可以：
 *   1. 手动触发巡检（定时任务 30 分钟一次，这里点一下立刻跑）；
 *   2. 点名已认证机手直接指派（走与抢单同一套扣减/建单/定金流程）。
 */
const user = computed(() => {
  const u = localStorage.getItem('user')
  return u ? JSON.parse(u) : null
})
const canView = computed(() =>
  user.value && ['DISPATCHER', 'ADMIN'].includes(user.value.role))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const market = ref(null)
const onlyUrgent = ref(true)
const scanning = ref(false)
const lastScan = ref(null)

const machines = ref([])

async function load() {
  loading.value = true
  try {
    const data = await demandApi.list({
      status: 'OPEN',
      urgent: onlyUrgent.value ? true : undefined,
      sort: 'default',
      page: 1,
      size: 50
    })
    list.value = data.content
    total.value = data.totalElements
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function scan() {
  scanning.value = true
  try {
    const r = await demandApi.scanUrgent()
    lastScan.value = r
    ElMessage.success(`巡检完成：${r.summary}`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    scanning.value = false
  }
}

onMounted(async () => {
  if (!canView.value) return
  await load()
  orderApi.marketPrice().then((m) => { market.value = m }).catch(() => {})
  userApi.list()
    .then((all) => {
      machines.value = (all || []).filter(
        (u) => u.role === 'MACHINE_OWNER' && u.verified)
    })
    .catch(() => {})
})

function daysLeftClass(d) {
  return d.daysLeft <= 1 ? 'dl-red' : d.daysLeft <= 3 ? 'dl-orange' : ''
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

// ---------------- 指派弹窗 ----------------
const assignDialog = ref(false)
const assigning = ref(false)
const current = ref(null)
const assignForm = ref({ machineOwnerId: null, mu: 0, machineModel: '雷沃谷神 9公斤/秒', note: '' })

function openAssign(d) {
  current.value = d
  assignForm.value = {
    machineOwnerId: null,
    mu: Number(d.remainMu),
    machineModel: '雷沃谷神 9公斤/秒',
    note: d.urgent ? '临期无人接单，调度兜底指派' : ''
  }
  assignDialog.value = true
}

const assignEstimate = computed(() => {
  const d = current.value
  if (!d) return { amount: 0, deposit: 0 }
  const amount = (Number(assignForm.value.mu) || 0) * Number(d.pricePerMu)
  return { amount: amount.toFixed(2), deposit: (amount * 0.3).toFixed(2) }
})

async function doAssign() {
  if (!assignForm.value.machineOwnerId) {
    ElMessage.warning('请选择承接的机手')
    return
  }
  assigning.value = true
  try {
    const order = await orderApi.assign({
      demandId: current.value.id,
      machineOwnerId: assignForm.value.machineOwnerId,
      mu: assignForm.value.mu,
      machineModel: assignForm.value.machineModel,
      note: assignForm.value.note
    })
    assignDialog.value = false
    const m = machines.value.find((x) => x.id === assignForm.value.machineOwnerId)
    ElMessageBox.alert(
      `已指派给 ${m ? (m.nickname || m.username) : '机手'}。订单号 ${order.orderNo}，` +
      `机手需支付定金 ¥${order.deposit}（总额 ¥${order.amount} 的 30%）。` +
      `订单会出现在机手的「我的接单」，标记为「调度指派」。`,
      '指派成功', { confirmButtonText: '好的', type: 'success' }
    ).catch(() => {})
    load()
  } catch (e) {
    ElMessage.error(e.message || '指派失败')
  } finally {
    assigning.value = false
  }
}
</script>

<template>
  <div v-if="!canView" class="no-auth">
    <el-empty description="调度工作台仅限调度员与管理员使用" />
  </div>

  <div v-else>
    <!-- 顶部：统计 + 手动巡检 -->
    <el-card class="top">
      <div class="stats">
        <div class="stat">
          <div class="sv urgent">{{ total }}</div>
          <div class="sl">临期未接需求</div>
        </div>
        <div class="stat">
          <div class="sv">{{ machines.length }}</div>
          <div class="sl">可指派认证机手</div>
        </div>
        <div class="stat">
          <div class="sv gold">¥{{ market && market.median ? market.median : '—' }}</div>
          <div class="sl">行情中位价 / 亩{{ market && market.sample ? `（${market.sample} 单）` : '' }}</div>
        </div>
        <div class="stat">
          <div class="sv small">
            {{ lastScan ? (lastScan.weatherAlertOn ? '天气联动 · 72h 窗口' : '常规 · 48h 窗口') : '30 分钟一次' }}
          </div>
          <div class="sl">巡检状态</div>
        </div>
      </div>
      <div class="ops">
        <el-radio-group v-model="onlyUrgent" @change="load">
          <el-radio-button :value="true">只看临期加急</el-radio-button>
          <el-radio-button :value="false">全部待接</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :loading="scanning" @click="scan">
          立即巡检
        </el-button>
      </div>
    </el-card>

    <!-- 临期需求清单 -->
    <el-card v-loading="loading">
      <template #header>
        <span class="h">兜底清单</span>
        <span class="h-sub">截止日临近仍无人接满的需求 —— 加急单在大厅置顶，这里可以直接点名指派</span>
      </template>

      <el-table :data="list" size="small" border stripe>
        <el-table-column prop="plotName" label="地块" min-width="130">
          <template #default="{ row }">
            <b>{{ row.plotName }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="region" label="地区" min-width="110" />
        <el-table-column label="农户" min-width="120">
          <template #default="{ row }">
            {{ row.farmerName }}<br />
            <span class="phone">{{ row.farmerPhone || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可接 / 共" width="95">
          <template #default="{ row }">
            <b>{{ row.remainMu }}</b> / {{ row.mu }} 亩
          </template>
        </el-table-column>
        <el-table-column label="单价" width="85">
          <template #default="{ row }">
            <span class="gold">¥{{ row.pricePerMu }}</span>/亩
          </template>
        </el-table-column>
        <el-table-column label="截止" width="120">
          <template #default="{ row }">
            <span :class="daysLeftClass(row)">{{ row.deadline }}</span>
            <el-tag v-if="row.urgent" size="small" class="u-urgent-mini">加急</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布" width="130">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.remainMu <= 0" @click="openAssign(row)">
              指派机手
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0"
        description="没有临期未接的需求 —— 巡检会自动把截止前 48 小时的单推进来" />
    </el-card>

    <!-- 指派弹窗 -->
    <el-dialog v-model="assignDialog" title="指派机手" width="520px">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="需求">
            {{ current.plotName }}（{{ current.region }}）· 剩余 {{ current.remainMu }} 亩
          </el-descriptions-item>
          <el-descriptions-item label="单价">¥{{ current.pricePerMu }} / 亩</el-descriptions-item>
          <el-descriptions-item label="截止">
            <span :class="daysLeftClass(current)">{{ current.deadline }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top" style="margin-top:14px">
          <el-form-item label="指派给（已认证机手）">
            <el-select v-model="assignForm.machineOwnerId" placeholder="选择机手" style="width:100%"
              :disabled="assigning">
              <el-option v-for="m in machines" :key="m.id" :value="m.id"
                :label="`${m.nickname || m.username} · ${m.region || '未填地区'} · 信誉 ${m.creditScore}`" />
            </el-select>
          </el-form-item>
          <el-form-item label="指派亩数">
            <el-input-number v-model="assignForm.mu" :min="0.1"
              :max="Number(current.remainMu)" :step="1" style="width:100%" :disabled="assigning" />
          </el-form-item>
          <el-form-item label="作业机型">
            <el-input v-model="assignForm.machineModel" :disabled="assigning" />
          </el-form-item>
          <el-form-item label="调度备注">
            <el-input v-model="assignForm.note" type="textarea" :rows="1"
              placeholder="如：雨前抢收，电话确认过机手档期" :disabled="assigning" />
          </el-form-item>
        </el-form>
        <el-alert type="warning" :closable="false">
          <div>作业总额：<b>¥{{ assignEstimate.amount }}</b></div>
          <div>机手需付定金（30%）：<b style="color:#e6a23c;font-size:16px">¥{{ assignEstimate.deposit }}</b></div>
        </el-alert>
      </template>
      <template #footer>
        <el-button @click="assignDialog = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="doAssign">确认指派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.top { margin-bottom: 16px; }
.stats { display: flex; gap: 12px; flex-wrap: wrap; }
.stat {
  flex: 1; min-width: 150px;
  background: #fcf9f2; border: 1px solid #f0e7d4; border-radius: 4px;
  padding: 10px 14px;
}
.sv {
  font-size: 22px; font-weight: 700; color: #3f6b3f;
  font-variant-numeric: tabular-nums;
}
.sv.urgent { color: #b04a30; }
.sv.gold { color: #b8862b; font-size: 20px; }
.sv.small { font-size: 15px; line-height: 30px; }
.sl { font-size: 11.5px; color: #a89a80; margin-top: 2px; }

.ops { display: flex; justify-content: space-between; align-items: center; margin-top: 14px; flex-wrap: wrap; gap: 10px; }

.h { font-size: 15px; font-weight: 700; color: #2f3f2f; }
.h-sub { font-size: 12px; color: #909399; margin-left: 10px; }

.phone { font-size: 11.5px; color: #a89a80; }
.gold { color: #b8862b; font-weight: 600; }
.dl-red { color: #b04a30; font-weight: 700; }
.dl-orange { color: #c08a2a; font-weight: 600; }
:deep(.u-urgent-mini) {
  margin-left: 6px;
  background: #a02c1d !important;
  color: #fff;
  border: none;
  padding: 0 6px;
  height: 18px;
  line-height: 18px;
}
.no-auth { padding: 60px 0; }
</style>
