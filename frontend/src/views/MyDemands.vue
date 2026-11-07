<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api, { paymentApi } from '../api'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const drawer = ref(false)
const grabbers = ref([])
const currentPlot = ref('')

// ---- 某个订单的定金流水 ----
const payDialog = ref(false)
const payLoading = ref(false)
const payRecords = ref([])
const payTitle = ref('')

const paymentStatus = {
  PENDING: { text: '待支付', type: 'info' },
  SUCCESS: { text: '支付成功', type: 'success' },
  FAILED: { text: '支付失败', type: 'danger' },
  REFUNDED: { text: '已退款', type: 'warning' }
}

async function viewOrderPayments(row) {
  payTitle.value = `订单 ${row.orderNo} · 机手 ${row.ownerName}`
  payRecords.value = []
  payDialog.value = true
  payLoading.value = true
  try {
    payRecords.value = await paymentApi.byOrder(row.id)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    payLoading.value = false
  }
}

const statusMap = {
  OPEN: { text: '可抢单', type: 'success' },
  FULL: { text: '已抢满', type: 'warning' },
  DONE: { text: '已完成', type: 'info' },
  CANCELLED: { text: '已取消', type: 'info' }
}
const orderStatus = {
  PENDING_DEPOSIT: '待付定金',
  DEPOSIT_PAID: '定金已付·履约中',
  DONE: '已完成',
  CANCELLED: '已取消'
}

async function load() {
  if (!localStorage.getItem('token')) { router.push('/login'); return }
  loading.value = true
  try {
    list.value = await api.get('/demands/mine')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function viewGrabbers(row) {
  currentPlot.value = `${row.plotName}（${row.mu} 亩 / 单价 ¥${row.pricePerMu}）`
  try {
    grabbers.value = await api.get(`/orders/by-demand/${row.id}`)
    drawer.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}
</script>

<template>
  <el-card v-loading="loading">
    <template #header>
      <span class="h">我发布的需求</span>
      <span class="h-sub">看看这块地被哪些机手抢了、谁抢了多少亩</span>
    </template>
    <el-table :data="list">
      <el-table-column prop="demandNo" label="需求号" min-width="170" />
      <el-table-column prop="plotName" label="地块" min-width="130" />
      <el-table-column prop="region" label="地区" min-width="130" />
      <el-table-column label="亩数" width="130">
        <template #default="{ row }">
          <b>{{ row.remainMu }}</b> / {{ row.mu }} 亩
        </template>
      </el-table-column>
      <el-table-column label="期望单价" width="100">
        <template #default="{ row }">¥{{ row.pricePerMu }}</template>
      </el-table-column>
      <el-table-column prop="deadline" label="最迟收割" width="110" />
      <el-table-column label="剩余天数" width="100">
        <template #default="{ row }">
          <span :class="row.daysLeft <= 2 ? 'urgent' : ''">{{ row.daysLeft }} 天</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type" size="small">{{ statusMap[row.status]?.text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="viewGrabbers(row)">谁抢了</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && list.length === 0" description="还没发布过作业需求" />

    <el-drawer v-model="drawer" :title="'接单记录：' + currentPlot" size="620px">
      <el-table :data="grabbers">
        <el-table-column prop="orderNo" label="订单号" min-width="160" />
        <el-table-column prop="ownerName" label="机手" width="100" />
        <el-table-column prop="machineModel" label="机型" min-width="140" />
        <el-table-column label="承接" width="90">
          <template #default="{ row }">{{ row.mu }} 亩</template>
        </el-table-column>
        <el-table-column label="定金" width="100">
          <template #default="{ row }">¥{{ row.deposit }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">{{ orderStatus[row.status] || row.status }}</template>
        </el-table-column>
        <el-table-column label="操作" width="105">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewOrderPayments(row)">定金流水</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="grabbers.length === 0" description="还没有机手接单" />
    </el-drawer>

    <!-- 定金流水弹窗（从「谁抢了」抽屉里点进来） -->
    <el-dialog v-model="payDialog" :title="'定金流水：' + payTitle" width="760px" append-to-body>
      <div v-loading="payLoading">
        <el-alert
          v-if="!payLoading && payRecords.length === 0"
          type="warning" :closable="false" show-icon
          title="这笔定金还没有支付"
          description="机手抢单后需要支付 30% 定金才算锁定履约" />
        <el-table v-else :data="payRecords" stripe>
          <el-table-column prop="paymentNo" label="流水号" width="200" />
          <el-table-column label="金额" width="100">
            <template #default="{ row }"><span class="dep">¥{{ row.amount }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="105">
            <template #default="{ row }">
              <el-tag :type="paymentStatus[row.status]?.type" size="small">
                {{ paymentStatus[row.status]?.text || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="支付时间" min-width="160">
            <template #default="{ row }">
              {{ (row.paidAt || '').replace('T', ' ').slice(0, 19) || '—' }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.h { font-size: 17px; font-weight: 700; color: #2f3f2f; }
.h-sub { font-size: 12px; color: #909399; margin-left: 10px; }
.urgent { color: #f56c6c; font-weight: 700; }
</style>
