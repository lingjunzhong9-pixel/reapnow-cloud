import { createRouter, createWebHashHistory } from 'vue-router'
import Hall from '../views/Hall.vue'
import Publish from '../views/Publish.vue'
import MyOrders from '../views/MyOrders.vue'
import MyDemands from '../views/MyDemands.vue'
import Login from '../views/Login.vue'

const routes = [
  { path: '/', name: 'hall', component: Hall },
  { path: '/publish', name: 'publish', component: Publish },
  { path: '/orders', name: 'orders', component: MyOrders },
  { path: '/demands', name: 'demands', component: MyDemands },
  { path: '/login', name: 'login', component: Login }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
