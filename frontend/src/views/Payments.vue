<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { paymentApi } from '../api'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')

// ---- 详情抽屉 ----
const drawer = ref(false)
const detail = ref(null)
const detailLoading = ref(false)

const user = computed(() => {
  try { return JSON.parse(localStorage.getItem('user') || '{}') } catch (e) { return {} }
})
const canView = computed(() => ['ADMIN', 'DISPATCHER'].includes(user.value.role))

const statusMap = {
  PENDING: { text: '待支付', type: 'info' },
  SUCCESS: { text: '支付成功', type: 'success' },
  FAILED: { text: '支付失败', type: 'danger' },
  REFUNDED: { text: '已退款', type: 'warning' }
}

const orderStatusMap = {
  PENDING_DEPOSIT: { text: '待付定金', type: 'warning' },
  DEPOSIT_PAID: { text: '定金已付 · 履约中', type: 'success' },
  DONE: { text: '已完成', type: 'info' },
  CANCELLED: { text: '已取消', type: 'info' }
}

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '支付成功', value: 'SUCCESS' },
  { label: '已退款', value: 'REFUNDED' },
  { label: '支付失败', value: 'FAILED' },
  { label: '待支付', value: 'PENDING' }
]

/** 当前页的金额合计（后端返回的是分页数据，合计按当前页算并注明） */
const pageAmount = computed(() =>
  rows.value.reduce((s, r) => s + Number(r.amount || 0), 0)
)

/** 审计用：核对「流水金额 = 订单总额 × 30%」 */
const depositCheck = computed(() => {
  const d = detail.value
  if (!d || d.orderAmount == null || d.deposit == null) return null
  const expect = Number(d.orderAmount) * 0.3
  const diff = Math.abs(Number(d.deposit) - expect)
  return { ok: diff < 0.01, expect: expect.toFixed(2), actual: Number(d.deposit).toFixed(2) }
})

async function load() {
  if (!localStorage.getItem('token')) { router.push('/login'); return }
  if (!canView.value) return

  loading.value = true
  try {
    const res = await paymentApi.list({ status: status.value, page: page.value, size: size.value })
    rows.value = res.content || []
    total.value = res.totalElements || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(load)

function onFilter() {
  page.value = 1
  load()
}

function onPageChange(p) {
  page.value = p
  load()
}

async function openDetail(row) {
  drawer.value = true
  detail.value = null
  detailLoading.value = true
  try {
    detail.value = await paymentApi.detail(row.id)
  } catch (e) {
    ElMessage.error(e.message)
    drawer.value = false
  } finally {
    detailLoading.value = false
  }
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="hd">
        <div>
          <span class="h">定金流水</span>
          <span class="h-sub">全平台定金支付记录 · 只有管理员与调度员能看</span>
        </div>
        <div class="filter">
          <el-select v-model="status" size="small" style="width: 130px"
            placeholder="全部状态" @change="onFilter">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-button size="small" @click="onFilter">刷新</el-button>
        </div>
      </div>
    </template>

    <!-- 无权限 -->
    <el-result v-if="!canView" icon="warning" title="无权访问"
      sub-title="定金流水涉及金额，仅管理员与调度员可查看" />

    <template v-else>
      <el-table :data="rows" stripe>
        <el-table-column prop="paymentNo" label="流水号" min-width="180" />
        <el-table-column label="订单号" width="100">
          <template #default="{ row }">#{{ row.orderId }}</template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">
            <span class="amt">¥{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            {{ row.type === 'DEPOSIT' ? '定金' : row.type }}
          </template>
        </el-table-column>
        <el-table-column prop="channel" label="渠道" width="90" />
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type" size="small">
              {{ statusMap[row.status]?.text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" min-width="160">
          <template #default="{ row }">{{ fmt(row.paidAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && rows.length === 0" description="没有符合条件的流水" />

      <div class="foot">
        <span class="sum">
          共 <b>{{ total }}</b> 条
          <span v-if="rows.length">· 当前页 {{ rows.length }} 条，合计 ¥{{ pageAmount.toFixed(2) }}</span>
        </span>
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </template>

    <!-- ==================== 详情抽屉 ==================== -->
    <el-drawer v-model="drawer" title="定金流水详情" size="640px">
      <div v-loading="detailLoading" class="dt">
        <template v-if="detail">

          <!-- 头部：流水号 + 状态 -->
          <div class="dt-head">
            <div>
              <div class="lbl">流水号</div>
              <div class="no">{{ detail.paymentNo }}</div>
            </div>
            <el-tag :type="statusMap[detail.status]?.type" effect="dark" size="large">
              {{ statusMap[detail.status]?.text || detail.status }}
            </el-tag>
          </div>

          <!-- 交易双方 -->
          <h3 class="sec">交易双方</h3>
          <div class="parties">
            <div class="party">
              <span class="role payer">付款方 · 机手</span>
              <b>{{ detail.payerName || '—' }}</b>
              <i>{{ detail.payerPhone || '—' }}</i>
            </div>
            <div class="flow">
              <span class="money">¥{{ detail.amount }}</span>
              <span class="arrow">→</span>
            </div>
            <div class="party">
              <span class="role payee">收款方 · 农户</span>
              <b>{{ detail.payeeName || '—' }}</b>
              <i>{{ detail.payeePhone || '—' }}</i>
            </div>
          </div>

          <!-- 金额审计 -->
          <el-alert v-if="depositCheck" :type="depositCheck.ok ? 'success' : 'error'"
            :closable="false" show-icon class="audit"
            :title="depositCheck.ok
              ? '金额核对通过：定金 = 订单总额 × 30%'
              : '金额异常：定金与「订单总额 × 30%」不符'"
            :description="`订单总额 ¥${detail.orderAmount} × 30% = ¥${depositCheck.expect}，实付 ¥${depositCheck.actual}`" />

          <!-- 关联订单 -->
          <h3 class="sec">关联订单与地块</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="订单号">{{ detail.orderNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="orderStatusMap[detail.orderStatus]?.type" size="small">
                {{ orderStatusMap[detail.orderStatus]?.text || detail.orderStatus || '—' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="地块">{{ detail.plotName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="地区">{{ detail.region || '—' }}</el-descriptions-item>
            <el-descriptions-item label="承接亩数">{{ detail.mu != null ? detail.mu + ' 亩' : '—' }}</el-descriptions-item>
            <el-descriptions-item label="单价">
              {{ detail.pricePerMu != null ? '¥' + detail.pricePerMu + ' / 亩' : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="订单总额">
              <b class="money-sm">{{ detail.orderAmount != null ? '¥' + detail.orderAmount : '—' }}</b>
            </el-descriptions-item>
            <el-descriptions-item label="应付定金(30%)">
              <b class="money-sm">{{ detail.deposit != null ? '¥' + detail.deposit : '—' }}</b>
            </el-descriptions-item>
            <el-descriptions-item label="预计到场">{{ fmt(detail.serviceDate) }}</el-descriptions-item>
            <el-descriptions-item label="作业机型">{{ detail.machineModel || '—' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 时间线 -->
          <h3 class="sec">时间线</h3>
          <el-timeline class="tl">
            <el-timeline-item :timestamp="fmt(detail.grabAt)" placement="top" color="#2f6b2f">
              机手抢单 · 生成订单
            </el-timeline-item>
            <el-timeline-item :timestamp="fmt(detail.paidAt)" placement="top"
              :color="detail.status === 'REFUNDED' ? '#e6a23c' : '#2f6b2f'">
              支付定金 ¥{{ detail.amount }}（{{ detail.channel }}）
            </el-timeline-item>
            <el-timeline-item v-if="detail.status === 'REFUNDED'" placement="top" color="#e6a23c">
              订单取消，定金已原路退回
            </el-timeline-item>
          </el-timeline>

          <div class="dt-foot">
            流水 ID {{ detail.id }} · 订单 ID {{ detail.orderId }} · 付款方用户 ID {{ detail.payerId }}
          </div>
        </template>
      </div>
    </el-drawer>
  </el-card>
</template>

<style scoped>
.hd { display: flex; align-items: center; justify-content: space-between; }
.h { font-size: 17px; font-weight: 700; color: #2f3f2f; }
.h-sub { font-size: 12px; color: #909399; margin-left: 10px; }
.filter { display: flex; gap: 8px; align-items: center; }
.amt { color: #e6a23c; font-weight: 700; }
.foot { display: flex; align-items: center; justify-content: space-between; margin-top: 14px; }
.sum { font-size: 13px; color: #606266; }
.sum b { color: #2f6b2f; }

/* ---------- 详情抽屉 ---------- */
.dt { min-height: 220px; }

.dt-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; margin-bottom: 20px;
  background: linear-gradient(135deg, #f4f9f0, #eef6e8);
  border: 1px solid #e0ebd6; border-radius: 6px;
}
.dt-head .lbl { font-size: 11.5px; color: #8fa07f; letter-spacing: 1px; }
.dt-head .no {
  font-family: Consolas, monospace; font-size: 15px; font-weight: 700;
  color: #2f4f2f; margin-top: 4px; letter-spacing: .3px;
}

.sec {
  font-size: 13.5px; font-weight: 700; color: #2f6b2f;
  margin: 22px 0 12px; padding-left: 9px;
  border-left: 3px solid #7cb342;
}

/* 交易双方 */
.parties {
  display: grid; grid-template-columns: 1fr auto 1fr;
  align-items: center; gap: 10px;
  padding: 16px 14px;
  background: #fafcf8;
  border: 1px solid #e6eee0; border-radius: 6px;
}
.party { display: flex; flex-direction: column; gap: 4px; text-align: center; }
.party .role {
  font-size: 11px; letter-spacing: .8px;
  padding: 2px 8px; border-radius: 10px; align-self: center;
}
.party .role.payer { background: #fff3e0; color: #b26a00; }
.party .role.payee { background: #e8f5e9; color: #2e7d32; }
.party b { font-size: 17px; color: #2b3a2b; margin-top: 2px; }
.party i { font-style: normal; font-size: 12px; color: #96a396; font-family: Consolas, monospace; }

.flow { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.flow .money {
  font-size: 19px; font-weight: 700; color: #e6a23c;
  font-family: Consolas, monospace;
}
.flow .arrow { font-size: 20px; color: #b8cdb0; line-height: 1; }

.audit { margin-top: 14px; }

.money-sm { color: #e6a23c; font-weight: 700; }

.tl { padding-left: 4px; margin-top: 4px; }

.dt-foot {
  margin-top: 22px; padding-top: 14px;
  border-top: 1px dashed #e4e9e0;
  font-size: 11.5px; color: #a3ada1;
  font-family: Consolas, monospace;
  word-break: break-all;
}
</style>
