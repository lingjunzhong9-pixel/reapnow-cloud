<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { paymentApi } from '../api'

const router = useRouter()
const loading = ref(false)
const list = ref([])

// ---- 定金流水查看 ----
const payDrawer = ref(false)
const payLoading = ref(false)
const payRecords = ref([])
const payTitle = ref('')

const paymentStatus = {
  PENDING: { text: '待支付', type: 'info' },
  SUCCESS: { text: '支付成功', type: 'success' },
  FAILED: { text: '支付失败', type: 'danger' },
  REFUNDED: { text: '已退款', type: 'warning' }
}

/** 查某个订单的定金流水（C04-3） */
async function viewPayments(row) {
  payTitle.value = `订单 ${row.orderNo}`
  payRecords.value = []
  payDrawer.value = true
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
  PENDING_DEPOSIT: { text: '待付定金', type: 'warning' },
  DEPOSIT_PAID: { text: '定金已付·履约中', type: 'success' },
  DONE: { text: '作业完成', type: 'info' },
  CANCELLED: { text: '已取消', type: 'info' }
}

async function load() {
  if (!localStorage.getItem('token')) { router.push('/login'); return }
  loading.value = true
  try {
    list.value = await api.get('/orders/mine')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function pay(row) {
  try {
    await api.post(`/payments/${row.id}/pay-deposit`)
    ElMessage.success('定金支付成功，档期已锁定')
    load()
  } catch (e) {
    ElMessage.error(e.message || '支付失败')
  }
}

async function cancel(row) {
  await ElMessageBox.confirm('取消后该地块的亩数会释放回抢单大厅，确定吗？', '提示', { type: 'warning' })
  try {
    await api.post(`/orders/${row.id}/cancel`)
    ElMessage.success('已取消，亩数已释放')
    load()
  } catch (e) {
    ElMessage.error(e.message || '取消失败')
  }
}
</script>

<template>
  <el-card v-loading="loading">
    <template #header>
      <span class="h">我的接单</span>
      <span class="h-sub">抢到地之后要付定金才算锁定档期 —— 这是对农户的履约承诺</span>
    </template>
    <el-table :data="list">
      <el-table-column prop="orderNo" label="订单号" min-width="200" class-name="col-no" />
      <el-table-column prop="plotName" label="地块" min-width="130" />
      <el-table-column prop="region" label="地区" min-width="130" />
      <el-table-column prop="farmerName" label="农户" width="90" />
      <el-table-column label="承接亩数" width="100">
        <template #default="{ row }">{{ row.mu }} 亩</template>
      </el-table-column>
      <el-table-column label="总额" width="100">
        <template #default="{ row }">¥{{ row.amount }}</template>
      </el-table-column>
      <el-table-column label="定金(30%)" width="110">
        <template #default="{ row }"><span class="dep">¥{{ row.deposit }}</span></template>
      </el-table-column>
      <el-table-column label="最迟收割" width="110">
        <template #default="{ row }">{{ (row.serviceDate || '').slice(0, 10) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type" size="small">{{ statusMap[row.status]?.text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING_DEPOSIT'" link type="primary" @click="pay(row)">支付定金</el-button>
          <el-button v-if="row.status === 'PENDING_DEPOSIT' || row.status === 'DEPOSIT_PAID'"
            link type="danger" @click="cancel(row)">取消</el-button>
          <el-button link type="info" @click="viewPayments(row)">定金流水</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && list.length === 0" description="还没抢过单，去抢单大厅看看" />

    <!-- 定金流水抽屉 -->
    <el-drawer v-model="payDrawer" :title="'定金流水：' + payTitle" size="780px">
      <div v-loading="payLoading">
        <el-alert
          v-if="!payLoading && payRecords.length === 0"
          type="info" :closable="false" show-icon
          title="还没有支付记录"
          description="抢单后需要支付 30% 定金才能锁定档期" />
        <el-table v-else :data="payRecords" stripe>
          <el-table-column prop="paymentNo" label="流水号" width="200" />
          <el-table-column label="金额" width="100">
            <template #default="{ row }"><span class="dep">¥{{ row.amount }}</span></template>
          </el-table-column>
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ row.type === 'DEPOSIT' ? '定金' : row.type }}</template>
          </el-table-column>
          <el-table-column prop="channel" label="渠道" width="90" />
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
    </el-drawer>
  </el-card>
</template>

<style scoped>
.h { font-size: 17px; font-weight: 700; color: #2f3f2f; }
.h-sub { font-size: 12px; color: #909399; margin-left: 10px; }
.dep { color: #e6a23c; font-weight: 700; }
</style>
