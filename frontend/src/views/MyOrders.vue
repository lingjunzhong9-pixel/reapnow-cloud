<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const router = useRouter()
const loading = ref(false)
const list = ref([])

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
      <el-table-column prop="orderNo" label="订单号" min-width="170" />
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
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING_DEPOSIT'" link type="primary" @click="pay(row)">支付定金</el-button>
          <el-button v-if="row.status === 'PENDING_DEPOSIT' || row.status === 'DEPOSIT_PAID'"
            link type="danger" @click="cancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && list.length === 0" description="还没抢过单，去抢单大厅看看" />
  </el-card>
</template>

<style scoped>
.h { font-size: 17px; font-weight: 700; color: #2f3f2f; }
.h-sub { font-size: 12px; color: #909399; margin-left: 10px; }
.dep { color: #e6a23c; font-weight: 700; }
</style>
