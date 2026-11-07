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
        <el-table-column prop="paymentNo" label="流水号" min-width="185" />
        <el-table-column label="订单号" width="110">
          <template #default="{ row }">#{{ row.orderId }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">
            <span class="amt">¥{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            {{ row.type === 'DEPOSIT' ? '定金' : row.type }}
          </template>
        </el-table-column>
        <el-table-column prop="channel" label="渠道" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type" size="small">
              {{ statusMap[row.status]?.text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" min-width="165">
          <template #default="{ row }">{{ fmt(row.paidAt) }}</template>
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
</style>
