# HR Manager · 人力资源管理系统

前后端分离的招聘全流程管理系统，覆盖求职端投递 → HR 筛选 → 多轮面试 → Offer → 入职的完整闭环。

## 技术栈

| 层 | 技术 |
|--|------|
| 后端框架 | Spring Boot 3.5.16 + Java 17 |
| 前端框架 | Vue 3 + Vite 6 + Element Plus + Pinia |
| 主存储 | MongoDB（文档型，灵活扩展） |
| 缓存 | Redis（旁路缓存 + 分布式锁 + 队列降级） |
| 消息队列 | RabbitMQ（简历投递异步落盘 + 事件广播） |
| 反向代理 | Nginx（请求转发 + SSE 长连接代理） |

---

## 架构总览

```
┌─────────────┐     ┌──────────────┐     ┌──────────┐
│ 求职端 SPA   │────▶│  Nginx 8088  │────▶│ 后端 8080 │────▶ MongoDB
│ (5173)       │     │  /api → :8080│     │          │────▶ Redis
└─────────────┘     │  / → :5173   │     │          │────▶ RabbitMQ
┌─────────────┐     └──────────────┘     └──────────┘
│ HR 端 SPA   │
│ (5173)      │
└─────────────┘
```

两个前端模块（求职端 `/seeker/*`、HR 管理端 `/hr/*`）共享同一套 REST API，通过 Vue Router 路由分离。

---

## 技术亮点

### 1. 状态机 — 三层一致性保障

10 个状态、6 条流转规则，三层防护防止数据不一致：

| 层级 | 技术 | 作用 |
|------|------|------|
| 第 1 层 | MongoDB `@Version` 乐观锁 | 防止并发写覆盖 |
| 第 2 层 | Event Sourcing（`statusHistory[]`） | 每次状态变更生成不可篡改的审计记录 |
| 第 3 层 | Redis `SETNX` 分布式锁 | 跨实例互斥，防止并发状态变更 |

```
NEW → INTERVIEW_INVITED → IN_INTERVIEW → ROUND_1_PASSED → ROUND_2_PASSED → WAITING_OFFER → OFFERED → ONBOARDED
  │         │                 │
  └→ PENDING_ARCHIVE        └→ REJECTED（任意环节失败）
  └→ REJECTED
```

### 2. 简历解析管线 — AI Agent 热插拔

PDF 简历上传后经过三层管道处理：

```
PDF 上传 → PDFBox 文本提取 → AI Agent 结构化解析 → 前端编辑 → 提交落盘
```

AI Agent 通过 `@ConditionalOnProperty` 实现热插拔：

```java
// 每个 Agent 拥有独立的 AI 配置命名空间
hr.agent.resume-parse.enabled=true
hr.agent.resume-parse.ai.mock=true     // 模拟模式，正则提取
hr.agent.resume-parse.ai.model=gpt-4o-mini

hr.agent.candidate-qualify.enabled=true
hr.agent.candidate-qualify.ai.mock=true
```

Agent 接口统一，通过 `AgentRegistry` 动态发现注册的 Agent，修改配置文件即可启停，无需改代码。

### 3. 消息驱动的异步架构 — 三级降级

简历提交采用三级降级策略，确保系统高可用：

```
RabbitMQ（一级）──可用──→ 异步消费，批量落盘（10条/5s）
  │ 不可用
  ▼
Redis List（二级）──可用──→ 定时轮询消费
  │ 不可用
  ▼
MongoDB 直写（三级）──→ 最终保底写入
```

同样模式用于事件通知：面试状态变更事件通过 RabbitMQ → SSE 实时推送到浏览器。

### 4. 实时同步 — Spring Events + RabbitMQ + SSE

岗位信息变更通过事件驱动链路实现求职端实时刷新：

```
HR 增删改岗位
  → ApplicationEventPublisher 发出 PositionChangeEvent
  → @Async 异步处理（缓存清除 + RabbitMQ 发布）
  → RabbitMQ 广播到所有后端实例
  → PositionSseService.broadcast() 推送给 SSE 客户端
  → Vue 前端 EventSource 监听 "position-change" 事件 → 自动刷新
```

SSE 端点 `/api/hr/positions/events` 支持自动断线重连。

### 5. Redis 缓存 — 旁路缓存模式

岗位列表使用 cache-aside 模式，5 分钟 TTL 自动续期：

```
GET /api/hr/positions/list
  → 查 Redis → 命中返回
  → 未命中 → MongoDB 查询 → Redis SETEX 300s → 返回
写操作 → 清除缓存键
```

每次读命中会刷新 TTL，热数据常驻缓存，冷数据自动过期。

### 6. 面试生命周期 — 状态机嵌套

每轮面试拥有独立的生命周期，与候选人状态机协同工作：

```
面试记录状态: PENDING → ACCEPTED → IN_PROGRESS → COMPLETED
                                                      ├→ PASSED → 候选人状态推进
                                                      └→ FAILED → 候选人 REJECTED
```

轮次校验自动检测前一环节是否通过（ROUND_1 → ROUND_2 → ROUND_3），第三轮通过后自动进入待发 Offer 队列。

### 7. 每个职位只能投递一次，可投多个职位

```java
// Seeker 中维护 position → candidateId 映射
Map<String, String> positionCandidates;
```

- 同一岗位重复投递被 `canSubmit()` 拦截
- HR 处理完成后（进入终态 PENDING_ARCHIVE / REJECTED）可重新投递
- 不同岗位互不干扰，求职者可同时应聘多个岗位

### 8. 前端工程化

- **Pinia 状态管理** — `useSeekerStore.ensureSeeker()` 自动创建演示会话，无需登录
- **Axios 响应拦截** — 统一剥离 `{code, message, data}` 包裹，异常自动弹窗
- **Vue Router 懒加载** — 所有视图组件按需加载
- **Element Plus 组件生态** — 状态标签、时间线、描述列表开箱即用

---

## 快速开始

### 环境要求

- Java 17+、Node.js 20+、Maven
- MongoDB（默认 localhost:27017）
- Redis（默认 localhost:6379）
- RabbitMQ（默认 localhost:5672）

### 启动后端

```bash
cd hr_manager_project
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

> 本地敏感配置（API Key、Redis 密码）写入 `application-local.properties`，该文件已被 `.gitignore` 排除。

### 启动前端

```bash
cd hr-frontend
npm install
npm run dev
```

前端开发服务器监听 5173 端口，`/api` 请求自动代理到后端 8080。

### Nginx 部署（可选）

```nginx
upstream hr_backend { server localhost:8080; }
upstream hr_frontend { server localhost:5173; }
server {
    listen 8088;
    location / { proxy_pass http://hr_frontend; }
    location /api/ { proxy_pass http://hr_backend; }
    location /api/hr/positions/events {
        proxy_pass http://hr_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 86400s;
    }
    location /uploads/ { proxy_pass http://hr_backend; }
}
```

> SSE 端点需要单独配置 `proxy_buffering off` 和长超时，否则连接会被 Nginx 截断。

---

## 项目结构

```
hr_manager_project/     ← Spring Boot 后端
├── controller/
│   ├── api/
│   │   ├── HrApiController.java        ← HR 端 REST API
│   │   └── SeekerApiController.java    ← 求职端 REST API
│   └── PositionSseController.java      ← SSE 端点
├── service/
│   ├── CandidateService.java           ← 状态机 + 分布式锁
│   ├── InterviewService.java           ← 面试轮次校验
│   ├── PositionCacheService.java       ← Redis 旁路缓存
│   ├── PositionSseService.java         ← SSE 事件广播
│   └── resume/
│       ├── ResumeSubmitConsumer.java    ← RabbitMQ 消费
│       └── ResumeSubmitFallbackService.java ← 降级策略
├── agent/
│   ├── AgentRegistry.java              ← Agent 注册中心
│   ├── ResumeParseAgent.java           ← 简历解析 AI Agent
│   └── CandidateQualifyAgent.java      ← 候选人评定 AI Agent
├── model/
│   ├── document/
│   │   ├── Candidate.java              ← 候选人（@Version + statusHistory）
│   │   ├── InterviewRecord.java        ← 面试记录
│   │   ├── Position.java               ← 岗位
│   │   ├── Offer.java                  ← Offer
│   │   └── Seeker.java                 ← 求职者
│   └── enums/
│       ├── CandidateStatus.java        ← 10 个状态
│       └── InterviewStatus.java        ← 4 个面试状态
└── config/
    ├── RabbitConfig.java               ← 3 个 Exchange + DLQ
    └── JacksonConfig.java              ← ObjectId / LocalDateTime 序列化

hr-frontend/            ← Vue 3 前端
├── src/
│   ├── api/index.js                    ← Axios 封装（seekerApi / hrApi）
│   ├── stores/seeker.js                ← Pinia 状态
│   ├── router/index.js                 ← Vue Router
│   └── views/
│       ├── seeker/                     ← 求职端页面
│       │   ├── UploadView.vue          ← 简历上传 + SSE 岗位同步
│       │   ├── StatusView.vue          ← 投递状态 + 面试响应
│       │   └── ApplyView.vue           ← 解析→编辑→提交
│       └── hr/                         ← HR 管理端页面
│           ├── DashboardView.vue        ← 岗位仪表盘
│           ├── CandidateListView.vue    ← 候选人列表 + 筛选
│           ├── CandidateDetailView.vue  ← 候选人详情 + 面试管理
│           ├── PositionManageView.vue   ← 岗位 CRUD + SSE
│           └── PendingOffersView.vue    ← 待发 Offer
```

---

## 开发配置

### 关键配置项（`application.properties`）

```properties
# AI Agent 开关（每个有独立 mock/key/model）
hr.agent.resume-parse.enabled=true
hr.agent.candidate-qualify.enabled=true
hr.agent.resume-parse.ai.mock=true

# 简历落盘
hr.resume.batch.size=10
hr.resume.batch.interval-ms=5000

# 文件上传
spring.servlet.multipart.max-file-size=10MB
app.resume.upload-dir=./uploads
```

### 各端口一览

| 服务 | 端口 |
|------|------|
| Spring Boot 后端 | 8080 |
| Vite 前端开发服务器 | 5173 |
| Nginx 统一入口 | 8088 |
| MongoDB | 27017 |
| Redis | 6379 |
| RabbitMQ | 5672 |
