# 抢收 ReapNow —— 跨区机收对接与履约平台（微服务版）

面向种粮农户与跨区作业农机手，在两场雨之间的抢收窗口里，把「谁家要收」和「谁的机器在哪」对上，
并用一笔 30% 的定金锁定履约 —— 解决农户「麦子熟了找不到机器」、机手「干完这片不知道下一站去哪」的两头焦虑。

> 本仓库是**微服务版**。改造前的单体版保留在 [lingjunzhong9-pixel/reapnow](https://github.com/lingjunzhong9-pixel/reapnow) 作为对照。

---

## 架构

```
浏览器 → 前端 5174 ──/api/**──→ 网关 8080（JWT 验签）
                                   │
        ┌──────────────┬───────────┼───────────┐
     用户服务       需求服务     订单服务    支付服务
      8081          8082        8083        8084
        └──────────────┴───────────┴───────────┘
                       │
   ┌───────────┬───────┴───────┬──────────────┐
 Nacos 8848  Sentinel 8858  Seata 8091  SkyWalking 11800/8899
                       │
                  MySQL 8.4 :3307
```

## 模块

| 模块 | 端口 | 职责 |
|---|---|---|
| `reapnow-common` | — | 统一返回、业务异常、DTO、Feign 客户端、JWT 工具 |
| `reapnow-gateway` | 8080 | 路由 + JWT 鉴权，注入 `X-User-Id` |
| `reapnow-user-service` | 8081 | 注册登录、签发 JWT、用户查询 |
| `reapnow-demand-service` | 8082 | 发布需求、抢单大厅、原子扣亩数 |
| `reapnow-order-service` | 8083 | 抢单、状态机、取消、Sentinel 规则 |
| `reapnow-payment-service` | 8084 | 定金支付、退款流水 |
| `frontend` | 5174 | Vue 3 + Element Plus |

---

## 快速开始

### 1. 前置依赖

| 组件 | 版本 | 端口 | 说明 |
|---|---|---|---|
| JDK | 17+ | — | 编译与运行 |
| Maven | 3.9+ | — | 构建 |
| MySQL | 8.x | 3307 | 需先建库与 `undo_log` 表 |
| Nacos | 2.3.x | 8848 | 注册 + 配置中心 |
| Sentinel Dashboard | 1.8.7 | 8858 | 限流控制台 |
| Seata Server | 2.0.0 | 8091 | 分布式事务协调器 |
| SkyWalking | 9.7.0 | 11800 / 8899 | 链路追踪（OAP + UI） |

初始化数据库：

```sql
CREATE DATABASE reapnow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE reapnow;

CREATE TABLE undo_log (
  branch_id     BIGINT       NOT NULL,
  xid           VARCHAR(128) NOT NULL,
  context       VARCHAR(128) NOT NULL,
  rollback_info LONGBLOB     NOT NULL,
  log_status    INT          NOT NULL,
  log_created   DATETIME(6)  NOT NULL,
  log_modified  DATETIME(6)  NOT NULL,
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

业务表由 JPA `ddl-auto=update` 自动创建。

### 2. 配置数据库密码（**不写进代码**）

数据库密码通过环境变量 `MYSQL_PASSWORD` 注入。先复制模板：

```powershell
cd reapnow-cloud
Copy-Item env.local.example.ps1 env.local.ps1
# 编辑 env.local.ps1，把 your-mysql-password 换成真实密码
```

`env.local.ps1` 已被 `.gitignore` 排除，不会被提交。`start-all.ps1` 会自动加载它。

若数据库端口不是 3307，需改 4 个服务 `src/main/resources/application.yml` 里的 `url`。

### 3. 编译

```powershell
mvn -s ..\_tools\settings.xml -DskipTests install
```

> 编译前请先停掉正在运行的服务，否则 `target` 下的 jar 被占用会报错。

### 4. 启动

**推荐的顺序**：Nacos → MySQL → Sentinel → Seata → SkyWalking → 业务服务 → 前端。

如果 SkyWalking 装在 `D:\skywalking`，业务服务会自动挂载 agent；找不到 agent 也能启动，只是没有链路数据。

```powershell
# Nacos
nacos\bin\startup.cmd -m standalone

# 业务服务 + 网关（按顺序拉起，自动跳过已运行的）
cd reapnow-cloud
.\start-all.ps1

# 前端
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5174>。

### 5. 演示账号

密码统一 `123456`。

农户沿真实的小麦成熟带分布：湖北 → 安徽 → 江苏 → 河南 → 山东 → 河北 → 山西，
最迟收割日从 1 天后排到 14 天后，对应麦熟由南往北推进。
共 10 位农户、3 位跨区机手，抢单大厅 51 条需求、6 页。

| 账号 | 姓名 | 角色 | 所在地区 |
|---|---|---|---|
| `chenb` | 陈保国 | 农户 | 湖北荆州监利 |
| `ligf` | 李桂芳 | 农户 | 安徽阜阳颍东区 |
| `zhangsht` | 张守田 | 农户 | 安徽阜阳颍东区 |
| `sunxy` | 孙秀英 | 农户 | 江苏徐州铜山区 |
| `wangxl` | 王秀兰 | 农户 | 河南周口项城 |
| `liugh` | 刘桂花 | 农户 | 河南商丘睢阳区 |
| `zhaodz` | 赵德柱 | 农户 | 山东菏泽曹县 |
| `zhouj` | 周建国 | 农户 | 河北邯郸魏县 |
| `guocs` | 郭长顺 | 农户 | 河北石家庄赵县 |
| `wangyf` | 王有福 | 农户 | 山西运城盐湖区 |
| `youdg` | 尤大哥 | 跨区机手 | 陕西渭南 |
| `liuk` | 刘康 | 跨区机手 | 陕西宝鸡岐山 |
| `wangjj` | 王建军 | 跨区机手 | 河南南阳唐河 |
| `dispatcher` | 县农机服务中心 | 调度员 | 安徽阜阳颍东区 |
| `admin` | 平台管理员 | 管理员 | 北京 |

---

## 接口

所有请求经网关 `8080`，统一返回 `{code, message, data}`，鉴权用请求头 `X-Token`（JWT）。

| 服务 | 主要接口 |
|---|---|
| 用户 | `POST /api/auth/register`、`POST /api/auth/login`、`GET /api/users/{id}` |
| 需求 | `POST /api/demands`、`GET /api/demands`、`GET /api/demands/mine`、`POST /api/demands/{id}/deduct` |
| 订单 | `POST /api/orders/grab`、`GET /api/orders/mine`、`GET /api/orders/by-demand/{id}`、`POST /api/orders/{id}/cancel` |
| 支付 | `POST /api/payments/{orderId}/pay-deposit`、`POST /api/payments/{orderId}/refund`、`GET /api/payments/by-order/{orderId}`、`GET /api/payments`、`GET /api/payments/{id}` |

共 28 个接口（含 4 个演示/诊断接口），详见 `项目前置材料` 中的《05-API与数据库设计文档》。

---

## 关键技术点

| 技术点 | 实现 |
|---|---|
| 服务拆分 | 6 个 Maven 模块，4 个业务服务独立进程，跨服务走 OpenFeign |
| 注册发现 | Nacos，Feign 用 `lb://服务名` 解析 |
| 配置中心 | Nacos，`spring.config.import` 拉取，`@RefreshScope` 支持热更新 |
| 网关鉴权 | Spring Cloud Gateway + GlobalFilter，本地验签 JWT，解析出 userId 后注入 `X-User-Id`（覆盖式写入，客户端伪造无效） |
| 限流熔断 | Sentinel，抢单接口 QPS 5 + 熔断规则，规则代码内置一份保证重启不丢 |
| 分布式事务 | Seata AT，`@GlobalTransactional` 覆盖「扣亩数 + 建订单」 |
| 链路追踪 | SkyWalking javaagent，无侵入 |

### 为什么抢单不用「先查后写」

两个人同时抢同一块地，先查剩余亩数再写回必然超卖。改用数据库条件更新做原子 CAS：

```sql
update demands
   set remain_mu = remain_mu - ?
 where id = ? and status = 'OPEN' and remain_mu >= ?
```

实测并发 2 抢 1，恰好成功 1 个。

### 关于 `X-User-Id`：网关注入，但业务服务不直接信任

网关验签后会把 JWT 里的 userId 覆盖写入 `X-User-Id`（客户端传来的同名头会被替换）。

但**业务服务并不依赖这个头做鉴权** —— 它们各自拿 token 向用户服务校验身份。原因：

开发环境下服务端口（8081—8084）是对外可达的。如果服务只信 `X-User-Id`，
绕过网关直连服务就能伪造任意用户。所以真正的身份来源始终是 token，
`X-User-Id` 目前只用于调用链路标记与日志排查。

后续若把服务端口收回内网（只允许网关访问），可以改为信任 `X-User-Id`，
省掉每个请求一次远程身份校验。

---

## 已知限制

- **订单服务与支付服务存在双向调用**（订单→支付退款，支付→订单回写状态）。更干净的做法是「下单时预创建支付单」，把依赖改成单向。
- **Redis / RocketMQ 尚未接入**。Redis 用于缓存抢单大厅与分布式锁，RocketMQ 用于「15 分钟未付定金自动释放亩数」；当前这两处由数据库事务与手工取消覆盖。
- 网关的 WebFlux agent 在 SkyWalking OAP 重启后重注册较慢。若服务列表里没有 `gateway`，
  单独重启一次网关进程即可（插件本身工作正常，不影响其它服务）。

---

## 目录

```
reapnow-cloud/
├── reapnow-common/          公共模块
├── reapnow-gateway/         网关 :8080
├── reapnow-user-service/    用户服务 :8081
├── reapnow-demand-service/  需求服务 :8082
├── reapnow-order-service/   订单服务 :8083
├── reapnow-payment-service/ 支付服务 :8084
├── frontend/                前端 :5174
│   └── src/views/            Hall / Publish / MyOrders / MyDemands / Payments / Login
├── start-all.ps1            拉起 4 个服务 + 网关
├── env.local.example.ps1    环境变量模板（复制为 env.local.ps1 后填密码）
└── .gitignore
```
